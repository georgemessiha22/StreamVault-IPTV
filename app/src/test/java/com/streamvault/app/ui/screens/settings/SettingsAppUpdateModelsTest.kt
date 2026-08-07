package com.streamvault.app.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.streamvault.app.update.AppUpdateActionState
import com.streamvault.app.update.AppUpdateChannel
import com.streamvault.app.update.AppUpdateDownloadStatus
import com.streamvault.app.update.isRemoteVersionNewerForBuild
import org.junit.Test

class SettingsAppUpdateModelsTest {

    @Test
    fun stableBuildIgnoresBetaRelease() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 12,
            remoteVersionName = "1.0.11-beta-deadbee",
            remotePublishedAt = "2026-05-14T10:00:00Z",
            currentVersionCode = 12,
            currentVersionName = "1.0.11",
            currentBuildTimestampUtc = 1_747_216_000_000L,
            currentChannel = AppUpdateChannel.Stable
        )

        assertThat(result).isFalse()
    }

    @Test
    fun betaBuildIgnoresStableRelease() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 12,
            remoteVersionName = "1.0.11",
            remotePublishedAt = "2026-05-14T10:00:00Z",
            currentVersionCode = 12,
            currentVersionName = "1.0.11-beta",
            currentBuildTimestampUtc = 1_747_216_000_000L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isFalse()
    }

    @Test
    fun betaBuildAcceptsNewerBaseVersionBetaRelease() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 13,
            remoteVersionName = "1.0.12-beta-cafebad",
            remotePublishedAt = "2026-05-14T10:00:00Z",
            currentVersionCode = 12,
            currentVersionName = "1.0.11-beta",
            currentBuildTimestampUtc = 1_747_216_000_000L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isTrue()
    }

    @Test
    fun betaBuildAcceptsSameVersionBetaReleaseWhenPublishedLater() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 12,
            remoteVersionName = "1.0.11-beta-deadbee",
            remotePublishedAt = "2026-05-14T12:30:00Z",
            currentVersionCode = 12,
            currentVersionName = "1.0.11-beta",
            currentBuildTimestampUtc = 0L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isTrue()
    }

    @Test
    fun betaBuildRejectsSameVersionBetaReleaseWhenNotPublishedLater() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 12,
            remoteVersionName = "1.0.11-beta-deadbee",
            remotePublishedAt = "2026-05-14T08:00:00Z",
            currentVersionCode = 12,
            currentVersionName = "1.0.11-beta",
            currentBuildTimestampUtc = Long.MAX_VALUE,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isFalse()
    }

    @Test
    fun betaBuildRejectsSameVersionBetaReleaseWhenPublishedAtMissing() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = 12,
            remoteVersionName = "1.0.11-beta-deadbee",
            remotePublishedAt = null,
            currentVersionCode = 12,
            currentVersionName = "1.0.11-beta",
            currentBuildTimestampUtc = 1_747_216_000_000L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isFalse()
    }

    @Test
    fun betaBuildDetectsNewerBetaSequenceForSameBaseVersion() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = null,
            remoteVersionName = "1.1.18-beta.000002.01",
            remotePublishedAt = null,
            currentVersionCode = 18,
            currentVersionName = "1.1.18-beta.000001.01",
            currentBuildTimestampUtc = 0L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isTrue()
    }

    @Test
    fun betaBuildRejectsIdenticalBetaSequenceEvenWhenPublishedLater() {
        // The installed beta must not flag its own release as an update just because
        // the release was published a few minutes after the APK was built.
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = null,
            remoteVersionName = "1.1.18-beta.000002.01",
            remotePublishedAt = "2999-01-01T00:00:00Z",
            currentVersionCode = 18,
            currentVersionName = "1.1.18-beta.000002.01",
            currentBuildTimestampUtc = 0L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isFalse()
    }

    @Test
    fun betaBuildRejectsOlderBetaSequenceForSameBaseVersion() {
        val result = isRemoteVersionNewerForBuild(
            remoteVersionCode = null,
            remoteVersionName = "1.1.18-beta.000001.01",
            remotePublishedAt = "2999-01-01T00:00:00Z",
            currentVersionCode = 18,
            currentVersionName = "1.1.18-beta.000002.01",
            currentBuildTimestampUtc = 0L,
            currentChannel = AppUpdateChannel.Beta
        )

        assertThat(result).isFalse()
    }

    @Test
    fun downloadedLatestShowsInstallAction() {
        val update = AppUpdateUiModel(
            latestVersionName = "1.0.12",
            downloadUrl = "https://example.com/StreamVault.apk",
            isUpdateAvailable = true,
            downloadStatus = AppUpdateDownloadStatus.Downloaded,
            downloadedVersionName = "1.0.12"
        )

        assertThat(update.latestActionState()).isEqualTo(AppUpdateActionState.InstallLatest)
    }

    @Test
    fun staleDownloadedUpdateShowsDownloadAction() {
        val update = AppUpdateUiModel(
            latestVersionName = "1.0.13",
            downloadUrl = "https://example.com/StreamVault.apk",
            isUpdateAvailable = true,
            downloadStatus = AppUpdateDownloadStatus.Downloaded,
            downloadedVersionName = "1.0.12"
        )

        assertThat(update.latestActionState()).isEqualTo(AppUpdateActionState.DownloadLatest)
    }

    @Test
    fun downloadedLatestWithMissingInstallPermissionShowsPermissionAction() {
        val update = AppUpdateUiModel(
            latestVersionName = "1.0.12",
            downloadUrl = "https://example.com/StreamVault.apk",
            isUpdateAvailable = true,
            downloadStatus = AppUpdateDownloadStatus.Downloaded,
            downloadedVersionName = "1.0.12",
            installPermissionRequired = true
        )

        assertThat(update.latestActionState()).isEqualTo(AppUpdateActionState.InstallPermissionRequired)
    }

    @Test
    fun downloadingLatestShowsDownloadingAction() {
        val update = AppUpdateUiModel(
            latestVersionName = "1.0.12",
            downloadUrl = "https://example.com/StreamVault.apk",
            isUpdateAvailable = true,
            downloadStatus = AppUpdateDownloadStatus.Downloading,
            downloadedVersionName = "1.0.12"
        )

        assertThat(update.latestActionState()).isEqualTo(AppUpdateActionState.Downloading)
    }
}
