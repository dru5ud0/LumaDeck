package com.nuvio.tv.data.jellyfin

import com.nuvio.tv.data.local.JellyfinConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class JellyfinVideo(
    val id: String,
    val name: String,
    val durationMs: Long?,
    val overview: String?
)

data class JellyfinSession(
    val userId: String,
    val username: String,
    val accessToken: String
)

@Singleton
class JellyfinLocalLibraryClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val deviceId = UUID.nameUUIDFromBytes("nuvio-local-jellyfin".toByteArray()).toString()

    suspend fun signIn(serverUrl: String, username: String, password: String): JellyfinSession {
        val baseUrl = normalize(serverUrl)
        val body = JSONObject()
            .put("Username", username.trim())
            .put("Pw", password)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/Users/AuthenticateByName")
            .post(body)
            .header("X-Emby-Authorization", authorizationHeader())
            .build()
        return executeJson(request).let { json ->
            val user = json.optJSONObject("User") ?: error("Jellyfin did not return a user.")
            val token = json.optString("AccessToken")
            if (token.isBlank()) error("Jellyfin did not return a session token.")
            JellyfinSession(
                userId = user.optString("Id").takeIf { it.isNotBlank() }
                    ?: error("Jellyfin did not return a user id."),
                username = user.optString("Name").ifBlank { username.trim() },
                accessToken = token
            )
        }
    }

    suspend fun videos(connection: JellyfinConnection): List<JellyfinVideo> {
        check(connection.isConnected) { "Sign in to your local server first." }
        val query = "IncludeItemTypes=Video,Movie,Episode&Recursive=true&Fields=RunTimeTicks,Overview,DateCreated&SortBy=SortName&SortOrder=Ascending"
        val request = Request.Builder()
            .url("${normalize(connection.serverUrl)}/Users/${connection.userId}/Items?$query")
            .header("X-Emby-Token", connection.accessToken)
            .header("X-Emby-Authorization", authorizationHeader())
            .build()
        val items = executeJson(request).optJSONArray("Items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("Id")
                if (id.isBlank()) continue
                add(
                    JellyfinVideo(
                        id = id,
                        name = item.optString("Name").ifBlank { "Untitled video" },
                        durationMs = item.optLong("RunTimeTicks").takeIf { it > 0 }?.div(10_000L),
                        overview = item.optString("Overview").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    fun streamUrl(connection: JellyfinConnection, videoId: String): String =
        "${normalize(connection.serverUrl)}/Videos/$videoId/stream?static=true"

    private fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "Use the full server address, for example http://YOUR_SERVER_IP:8096"
        }
        return trimmed
    }

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"Nuvio\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"0.8.2\""

    private suspend fun executeJson(request: Request): JSONObject = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("Jellyfin ${response.code}: ${body.ifBlank { response.message }}")
            }
            runCatching { JSONObject(body) }
                .getOrElse { error("Jellyfin sent an invalid response.") }
        }
    }
}
