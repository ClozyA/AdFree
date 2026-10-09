package xyz.fearr.adfree

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

internal enum class UpdateStatus { IDLE, CHECKING, AVAILABLE, CURRENT, NO_RELEASE, RATE_LIMITED, FAILED }

internal data class UpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val version: String? = null,
    val releaseUrl: String? = null,
)

internal object GitHubUpdateChecker {
    const val RELEASES_URL = "https://github.com/ClozyA/AdFree/releases/latest"
    private const val API_URL = "https://api.github.com/repos/ClozyA/AdFree/releases/latest"

    // Call on a background executor. No token, mirror, or automatic APK installation.
    fun check(currentVersion: String): UpdateState {
        val connection = URI(API_URL).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "AdFree/$currentVersion")
            when (connection.responseCode) {
                404 -> return UpdateState(UpdateStatus.NO_RELEASE)
                403, 429 -> return UpdateState(UpdateStatus.RATE_LIMITED)
                200 -> Unit
                else -> throw IOException("GitHub HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = JSONObject(body)
            if (release.getBoolean("draft") || release.getBoolean("prerelease")) {
                return UpdateState(UpdateStatus.NO_RELEASE)
            }
            val tag = release.getString("tag_name")
            val latest = ReleaseVersion.parse(tag)
            val current = ReleaseVersion.parse(currentVersion)
            return if (latest > current) {
                UpdateState(UpdateStatus.AVAILABLE, tag.removePrefix("v"),
                    "https://github.com/ClozyA/AdFree/releases/tag/$tag")
            } else {
                UpdateState(UpdateStatus.CURRENT)
            }
        } finally {
            connection.disconnect()
        }
    }
}
