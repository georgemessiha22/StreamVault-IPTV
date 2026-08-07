package com.streamvault.app.update

import com.streamvault.app.BuildConfig
import java.time.Instant
import kotlin.math.max

enum class AppUpdateActionState {
    None,
    DownloadLatest,
    Downloading,
    InstallLatest,
    InstallPermissionRequired
}

fun isRemoteVersionNewer(
    remoteVersionCode: Int?,
    remoteVersionName: String,
    remotePublishedAt: String? = null
): Boolean {
    return isRemoteVersionNewerForBuild(
        remoteVersionCode = remoteVersionCode,
        remoteVersionName = remoteVersionName,
        remotePublishedAt = remotePublishedAt,
        currentVersionCode = BuildConfig.VERSION_CODE,
        currentVersionName = BuildConfig.VERSION_NAME,
        currentBuildTimestampUtc = BuildConfig.BUILD_TIMESTAMP_UTC,
        currentChannel = AppUpdateChannel.fromCurrentBuild()
    )
}

fun isRemoteVersionNewerForBuild(
    remoteVersionCode: Int?,
    remoteVersionName: String,
    remotePublishedAt: String?,
    currentVersionCode: Int,
    currentVersionName: String,
    currentBuildTimestampUtc: Long,
    currentChannel: AppUpdateChannel
): Boolean {
    val remoteDescriptor = parseAppVersionDescriptor(remoteVersionName)
    if (remoteDescriptor.channel != currentChannel) {
        return false
    }

    if (remoteVersionCode != null && remoteVersionCode > currentVersionCode) {
        return true
    }

    val versionComparison = compareVersionNamesStatic(
        remoteDescriptor.baseVersionName,
        parseAppVersionDescriptor(currentVersionName).baseVersionName
    )
    if (versionComparison != 0) {
        return versionComparison > 0
    }

    if (currentChannel == AppUpdateChannel.Beta) {
        // Prefer the explicit beta build sequence (e.g. "-beta.000002.01") when both
        // the installed build and the remote release carry one. This lets a beta
        // install detect the newest beta and, crucially, avoids flagging its own
        // release as an update just because the release was published a few minutes
        // after the APK was built.
        val remoteSequence = remoteDescriptor.betaSequence
        val currentSequence = parseAppVersionDescriptor(currentVersionName).betaSequence
        if (remoteSequence != null && currentSequence != null) {
            return compareIntListsStatic(remoteSequence, currentSequence) > 0
        }

        val remotePublishedAtMillis = remotePublishedAt.toEpochMillisOrNull() ?: return false
        return remotePublishedAtMillis > currentBuildTimestampUtc
    }

    return false
}

private fun compareIntListsStatic(left: List<Int>, right: List<Int>): Int {
    val length = max(left.size, right.size)
    for (index in 0 until length) {
        val leftValue = left.getOrNull(index) ?: 0
        val rightValue = right.getOrNull(index) ?: 0
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

fun compareVersionNamesStatic(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").split('.')
    val rightParts = right.removePrefix("v").split('.')
    val length = max(leftParts.size, rightParts.size)
    for (index in 0 until length) {
        val leftValue = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val rightValue = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

fun isLatestAppUpdateDownloaded(
    latestVersionName: String?,
    downloadState: AppUpdateDownloadState
): Boolean {
    return !latestVersionName.isNullOrBlank() &&
        downloadState.status == AppUpdateDownloadStatus.Downloaded &&
        downloadState.versionName == latestVersionName
}

fun latestAppUpdateAction(
    latestVersionName: String?,
    downloadUrl: String?,
    isUpdateAvailable: Boolean,
    downloadState: AppUpdateDownloadState
): AppUpdateActionState {
    if (latestVersionName.isNullOrBlank()) {
        return AppUpdateActionState.None
    }

    val latestDownloaded = isLatestAppUpdateDownloaded(latestVersionName, downloadState)
    if (latestDownloaded) {
        return if (downloadState.installPermissionRequired) {
            AppUpdateActionState.InstallPermissionRequired
        } else {
            AppUpdateActionState.InstallLatest
        }
    }

    if (downloadState.status == AppUpdateDownloadStatus.Downloading &&
        downloadState.versionName == latestVersionName
    ) {
        return AppUpdateActionState.Downloading
    }

    return if (isUpdateAvailable && !downloadUrl.isNullOrBlank()) {
        AppUpdateActionState.DownloadLatest
    } else {
        AppUpdateActionState.None
    }
}

private data class ParsedAppVersionDescriptor(
    val baseVersionName: String,
    val channel: AppUpdateChannel,
    /**
     * Numeric beta build sequence parsed from the part after `-beta` (e.g.
     * "-beta.000002.01" -> [2, 1]). Null when there is no beta marker or the
     * suffix is not a purely numeric dot-separated sequence (e.g. a git sha
     * suffix like "-beta-deadbee"), in which case callers fall back to the
     * publish-time comparison.
     */
    val betaSequence: List<Int>?
)

private fun parseAppVersionDescriptor(versionName: String): ParsedAppVersionDescriptor {
    val normalized = versionName.removePrefix("v").trim()
    val betaIndex = normalized.indexOf("-beta", ignoreCase = true)
    return if (betaIndex >= 0) {
        ParsedAppVersionDescriptor(
            baseVersionName = normalized.substring(0, betaIndex),
            channel = AppUpdateChannel.Beta,
            betaSequence = parseBetaSequence(normalized.substring(betaIndex + "-beta".length))
        )
    } else {
        ParsedAppVersionDescriptor(
            baseVersionName = normalized,
            channel = AppUpdateChannel.Stable,
            betaSequence = null
        )
    }
}

private fun parseBetaSequence(afterBetaMarker: String): List<Int>? {
    val trimmed = afterBetaMarker.trim().trimStart('.', '-')
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    return parts.map { part -> part.toIntOrNull() ?: return null }
}

private fun String?.toEpochMillisOrNull(): Long? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
