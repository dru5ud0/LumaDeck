package com.nuvio.tv.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Backs up the app's restorable private and external files, including databases,
 * preferences, DataStore, WebView data and provider credentials. Disposable cache,
 * compiled code and native libraries are intentionally excluded.
 *
 * Call this only from the isolated backup process while the main app is stopped.
 * The archive contains secrets and must be kept in a private place after pulling it.
 */
internal object AppDataBackup {
    private const val FORMAT_VERSION = 1
    private const val MANIFEST_NAME = "manifest.json"
    private val excludedPrivateChildren = setOf("cache", "code_cache", "lib")

    fun export(context: Context, archive: File): JSONObject {
        check(!archive.exists()) { "Backup archive already exists; preserve it before exporting again" }
        val temporary = File(archive.parentFile, "${archive.name}.partial")
        check(!temporary.exists()) { "Incomplete backup exists; inspect it before retrying" }
        val sources = collectSources(context)
        val entries = JSONArray()
        var totalBytes = 0L

        try {
            ZipOutputStream(FileOutputStream(temporary).buffered()).use { zip ->
                for (source in sources) {
                    val beforeSize = source.file.length()
                    val beforeModified = source.file.lastModified()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L
                    zip.putNextEntry(ZipEntry(source.name))
                    FileInputStream(source.file).buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            zip.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            written += count
                        }
                    }
                    zip.closeEntry()
                    check(written == beforeSize && source.file.lastModified() == beforeModified) {
                        "Source changed during backup: ${source.name}"
                    }
                    entries.put(
                        JSONObject().put("name", source.name)
                            .put("size", written)
                            .put("sha256", digest.digest().toHex())
                    )
                    totalBytes += written
                }
                val manifest = JSONObject()
                    .put("formatVersion", FORMAT_VERSION)
                    .put("packageName", context.packageName)
                    .put("createdAtUtcMs", System.currentTimeMillis())
                    .put("fileCount", entries.length())
                    .put("totalBytes", totalBytes)
                    .put("files", entries)
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            check(temporary.renameTo(archive)) { "Could not finalize backup archive" }
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
        // A separate read pass catches truncated or malformed output before success.
        return verify(context, archive)
    }

    fun verify(context: Context, archive: File): JSONObject {
        check(archive.isFile) { "Backup archive not found" }
        val parsed = readAndVerify(context, archive)
        return JSONObject()
            .put("archive", archive.name)
            .put("archiveBytes", archive.length())
            .put("archiveSha256", sha256(FileInputStream(archive)).first)
            .put("fileCount", parsed.entries.size)
            .put("totalBytes", parsed.totalBytes)
    }

    fun restore(context: Context, archive: File): JSONObject {
        // Validate every byte and path before touching current app data.
        val parsed = readAndVerify(context, archive)
        val privateRoot = File(context.applicationInfo.dataDir).canonicalFile
        val externalRoot = externalRoot(context).canonicalFile
        clearRestorableChildren(privateRoot, excludedPrivateChildren)
        clearRestorableChildren(externalRoot, setOf(AppDataBackupActivity.BACKUP_DIRECTORY))

        ZipFile(archive).use { zip ->
            for (entry in parsed.entries) {
                val targetRoot = if (entry.name.startsWith("internal/")) privateRoot else externalRoot
                val relative = entry.name.substringAfter('/')
                val target = File(targetRoot, relative).canonicalFile
                check(target.path.startsWith(targetRoot.path + File.separator)) { "Unsafe restore path" }
                check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) {
                    "Could not create restore directory"
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                zip.getInputStream(zip.getEntry(entry.name)).use { input ->
                    FileOutputStream(target).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            written += count
                        }
                        output.flush()
                    }
                }
                check(written == entry.size && digest.digest().toHex() == entry.sha256) {
                    "Restore copy failed verification: ${entry.name}"
                }
            }
        }
        return JSONObject()
            .put("restoredFiles", parsed.entries.size)
            .put("restoredBytes", parsed.totalBytes)
            .put("sourceSha256", sha256(FileInputStream(archive)).first)
    }

    private fun collectSources(context: Context): List<Source> {
        val privateRoot = File(context.applicationInfo.dataDir).canonicalFile
        val externalRoot = externalRoot(context).canonicalFile
        val result = mutableListOf<Source>()
        collectRoot(privateRoot, "internal", excludedPrivateChildren, result)
        collectRoot(externalRoot, "external", setOf(AppDataBackupActivity.BACKUP_DIRECTORY), result)
        return result.sortedBy { it.name }
    }

    private fun collectRoot(root: File, prefix: String, excluded: Set<String>, result: MutableList<Source>) {
        check(root.isDirectory) { "Missing backup source: $prefix" }
        for (child in listChildren(root)) {
            if (child.name in excluded) continue
            collectNode(root, child, prefix, result)
        }
    }

    private fun collectNode(root: File, node: File, prefix: String, result: MutableList<Source>) {
        check(!Files.isSymbolicLink(node.toPath())) { "Symlink in backup source" }
        if (node.isDirectory) {
            for (child in listChildren(node)) collectNode(root, child, prefix, result)
        } else {
            check(node.isFile && node.canRead()) { "Unreadable backup source" }
            val relative = node.relativeTo(root).invariantSeparatorsPath
            result += Source(node, "$prefix/$relative")
        }
    }

    private fun readAndVerify(context: Context, archive: File): Parsed {
        check(archive.isFile) { "Backup archive not found" }
        ZipFile(archive).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_NAME) ?: error("Backup manifest missing")
            val manifest = JSONObject(zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() })
            check(manifest.getInt("formatVersion") == FORMAT_VERSION) { "Unsupported backup version" }
            check(manifest.getString("packageName") == context.packageName) { "Wrong app package in backup" }
            val jsonEntries = manifest.getJSONArray("files")
            check(manifest.getInt("fileCount") == jsonEntries.length()) { "Backup file count mismatch" }
            val expected = LinkedHashMap<String, Entry>()
            var totalBytes = 0L
            for (index in 0 until jsonEntries.length()) {
                val item = jsonEntries.getJSONObject(index)
                val name = item.getString("name")
                validateName(name)
                val size = item.getLong("size")
                check(size >= 0) { "Invalid backup file size" }
                val hash = item.getString("sha256")
                check(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid backup hash" }
                check(expected.put(name, Entry(name, size, hash)) == null) { "Duplicate backup path" }
                totalBytes += size
            }
            check(manifest.getLong("totalBytes") == totalBytes) { "Backup byte count mismatch" }
            val actual = mutableSetOf<String>()
            val zipEntries = zip.entries()
            while (zipEntries.hasMoreElements()) {
                val name = zipEntries.nextElement().name
                check(actual.add(name)) { "Duplicate ZIP entry" }
                check(name == MANIFEST_NAME || name in expected) { "Unexpected ZIP entry" }
            }
            check(actual.size == expected.size + 1) { "Missing backup files" }
            for (entry in expected.values) {
                val zipped = zip.getEntry(entry.name) ?: error("Missing ZIP entry")
                check(!zipped.isDirectory) { "Directory where file expected" }
                val (hash, length) = sha256(zip.getInputStream(zipped))
                check(length == entry.size && hash == entry.sha256) {
                    "Backup file failed checksum: ${entry.name}"
                }
            }
            return Parsed(expected.values.toList(), totalBytes)
        }
    }

    private fun validateName(name: String) {
        val parts = name.split('/')
        check(parts.size >= 2 && parts.first() in setOf("internal", "external")) { "Invalid backup path" }
        check(parts.all { it.isNotEmpty() && it != "." && it != ".." && '\\' !in it }) {
            "Unsafe backup path"
        }
        check(!(parts[0] == "internal" && parts[1] in excludedPrivateChildren)) { "Excluded private path" }
        check(!(parts[0] == "external" && parts[1] == AppDataBackupActivity.BACKUP_DIRECTORY)) {
            "Excluded backup path"
        }
    }

    private fun clearRestorableChildren(root: File, excluded: Set<String>) {
        for (child in listChildren(root)) {
            if (child.name in excluded) continue
            deleteNode(child)
        }
    }

    private fun deleteNode(node: File) {
        check(!Files.isSymbolicLink(node.toPath())) { "Symlink in restore destination" }
        if (node.isDirectory) for (child in listChildren(node)) deleteNode(child)
        check(node.delete()) { "Could not clear old app data" }
    }

    private fun listChildren(directory: File): List<File> =
        directory.listFiles()?.sortedBy { it.name } ?: error("Cannot list app data directory")

    private fun externalRoot(context: Context): File =
        context.getExternalFilesDir(null) ?: error("External app storage is unavailable")

    private fun sha256(input: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var length = 0L
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                length += count
            }
        }
        return digest.digest().toHex() to length
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class Source(val file: File, val name: String)
    private data class Entry(val name: String, val size: Long, val sha256: String)
    private data class Parsed(val entries: List<Entry>, val totalBytes: Long)
}
