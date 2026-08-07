package com.streamvault.domain.manager

/**
 * Cleartext credentials for a single provider. Matching uses `(serverUrl, username)`.
 */
data class ProviderCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)
