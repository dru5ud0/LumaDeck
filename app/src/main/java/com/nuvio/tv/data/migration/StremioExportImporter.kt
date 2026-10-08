package com.nuvio.tv.data.migration

import android.content.ContentResolver
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports the user-owned JSON produced by Stremio's Export data action. */
@Singleton
class StremioExportImporter @Inject constructor(
    private val libraryPreferences: LibraryPreferences,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchedItemsPreferences: WatchedItemsPreferences,
    private val addonRepository: AddonRepository
) {
    suspend fun import(contentResolver: ContentResolver, uri: Uri): StremioImportResult =
        withContext(Dispatchers.IO) {
            val export = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader -> Gson().fromJson(reader, StremioExport::class.java) }
                ?: throw IllegalArgumentException("Unable to read the selected file")

            applyExport(export)
        }

    /** Android TV fallback for devices that do not ship a system document picker. */
    suspend fun import(file: File): StremioImportResult =
        withContext(Dispatchers.IO) {
            if (!file.isFile) throw IllegalArgumentException("Export file not found")
            val export = file.bufferedReader().use { reader ->
                Gson().fromJson(reader, StremioExport::class.java)
            }
            applyExport(export)
        }

    private suspend fun applyExport(export: StremioExport): StremioImportResult {
            val libraryItems = export.library.orEmpty()
                .mapNotNull { record -> record.data?.toLibraryItem() }
                .distinctBy { item -> item.id to item.type }
            libraryItems.forEach { item -> libraryPreferences.addItem(item) }

            val progress = export.library.orEmpty()
                .mapNotNull { record -> record.data?.toWatchProgress() }
                .distinctBy { item -> item.contentId to (item.season to item.episode) }
            watchProgressRepository.saveProgressBatch(progress, syncRemote = false)

            val watchedItems = progress
                .filter { it.isCompleted() }
                .map { item ->
                    WatchedItem(
                        contentId = item.contentId,
                        contentType = item.contentType,
                        title = item.name,
                        season = item.season,
                        episode = item.episode,
                        watchedAt = item.lastWatched,
                        poster = item.poster,
                        releaseInfo = null
                    )
                }
            watchedItemsPreferences.markAsWatchedBatch(watchedItems)

            val addonUrls = export.addons?.addons.orEmpty()
                .mapNotNull(StremioAddon::transportUrl)
                .filter { url -> url.startsWith("https://") || url.startsWith("http://") }
                .distinct()
            var addonsAdded = 0
            addonUrls.forEach { url ->
                runCatching { addonRepository.addAddon(url) }
                    .onSuccess { addonsAdded++ }
            }

            return StremioImportResult(
                libraryCount = libraryItems.size,
                progressCount = progress.size,
                watchedCount = watchedItems.size,
                addonCount = addonsAdded
            )
    }

    private fun StremioLibraryData.toLibraryItem(): SavedLibraryItem? {
        val itemId = id?.trim().orEmpty()
        val title = name?.trim().orEmpty()
        if (itemId.isEmpty() || title.isEmpty()) return null

        return SavedLibraryItem(
            id = itemId,
            type = type.toNuvioType(itemId),
            name = title,
            poster = poster,
            posterShape = PosterShape.fromString(posterShape),
            background = background,
            description = null,
            releaseInfo = year,
            imdbRating = null,
            genres = emptyList(),
            addonBaseUrl = null,
            logo = logo,
            addedAt = createdAt.toEpochMillisOrZero()
        )
    }

    private fun StremioLibraryData.toWatchProgress(): WatchProgress? {
        val watchState = state ?: return null
        val itemId = id?.trim().orEmpty()
        val title = name?.trim().orEmpty()
        if (itemId.isEmpty() || title.isEmpty()) return null

        val position = watchState.timeWatched.coerceAtLeast(0L)
        val totalDuration = watchState.duration.coerceAtLeast(0L)
        val completed = watchState.watched == true ||
            watchState.flaggedWatched > 0 ||
            (totalDuration > 0 && position.toDouble() / totalDuration >= 0.9)
        if (!completed && position <= 0L && watchState.overallTimeWatched <= 0L) return null

        return WatchProgress(
            contentId = itemId,
            contentType = type.toNuvioType(itemId),
            name = title,
            poster = poster,
            backdrop = background,
            logo = logo,
            videoId = watchState.videoId?.takeIf { it.isNotBlank() } ?: itemId,
            season = watchState.season.takeIf { it > 0 },
            episode = watchState.episode.takeIf { it > 0 },
            episodeTitle = null,
            position = if (completed && totalDuration > 0) totalDuration else position,
            duration = totalDuration,
            lastWatched = watchState.lastWatched.toEpochMillisOrZero(),
            progressPercent = if (completed) 100f else null
        )
    }

    private fun String?.toNuvioType(itemId: String): String = when (this?.trim()?.lowercase()) {
        "tv" -> if (itemId.startsWith("tt")) "series" else "tv"
        "movie", "series", "channel" -> this.trim().lowercase()
        else -> "movie"
    }

    private fun String?.toEpochMillisOrZero(): Long = runCatching {
        this?.let(Instant::parse)?.toEpochMilli() ?: 0L
    }.getOrDefault(0L)
}

data class StremioImportResult(
    val libraryCount: Int,
    val progressCount: Int,
    val watchedCount: Int,
    val addonCount: Int
)

private data class StremioExport(
    val addons: StremioAddonCollection? = null,
    val library: List<StremioLibraryRecord>? = null
)

private data class StremioAddonCollection(
    val addons: List<StremioAddon>? = null
)

private data class StremioAddon(
    val transportUrl: String? = null
)

private data class StremioLibraryRecord(
    @SerializedName("d") val data: StremioLibraryData? = null
)

private data class StremioLibraryData(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("_ctime") val createdAt: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val posterShape: String? = null,
    val state: StremioWatchState? = null,
    val type: String? = null,
    val year: String? = null
)

private data class StremioWatchState(
    val duration: Long = 0L,
    val episode: Int = 0,
    val flaggedWatched: Int = 0,
    val lastWatched: String? = null,
    val overallTimeWatched: Long = 0L,
    val season: Int = 0,
    val timeWatched: Long = 0L,
    @SerializedName("video_id") val videoId: String? = null,
    val watched: Boolean? = null
)
