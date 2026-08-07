package com.streamvault.data.local.dao

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeriesIdRemapTest {

    @Test
    fun `resolves existing id by remote key when present`() {
        val id = resolveExistingSeriesId(
            remoteKey = "s100",
            seriesId = 100L,
            existingByRemoteKey = mapOf("s100" to 42L),
            existingBySeriesId = mapOf(100L to 42L)
        )
        assertThat(id).isEqualTo(42L)
    }

    @Test
    fun `falls back to series id when remote key representation changed`() {
        // Existing row was stored with provider_series_id ("s100"), but the incoming
        // summary has no provider_series_id so its remoteKey is the numeric series id.
        // The stable (provider, series_id) identity must still resolve the existing row
        // so its episodes are not orphaned.
        val id = resolveExistingSeriesId(
            remoteKey = "100",
            seriesId = 100L,
            existingByRemoteKey = mapOf("s100" to 42L),
            existingBySeriesId = mapOf(100L to 42L)
        )
        assertThat(id).isEqualTo(42L)
    }

    @Test
    fun `returns zero for a genuinely new series`() {
        val id = resolveExistingSeriesId(
            remoteKey = "999",
            seriesId = 999L,
            existingByRemoteKey = mapOf("s100" to 42L),
            existingBySeriesId = mapOf(100L to 42L)
        )
        assertThat(id).isEqualTo(0L)
    }

    @Test
    fun `does not match on series id zero`() {
        // series_id == 0 is not a stable identity (e.g. some Stalker rows), so it must
        // never be used as a fallback key.
        val id = resolveExistingSeriesId(
            remoteKey = "portal-x",
            seriesId = 0L,
            existingByRemoteKey = mapOf("portal-a" to 1L),
            existingBySeriesId = mapOf(0L to 1L)
        )
        assertThat(id).isEqualTo(0L)
    }
}
