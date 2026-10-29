package fr.nathanaudvard.emp.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val database: DatabaseConfig,
    val fourmizzz: FourmizzzConfig,
    val alliance: AllianceConfig,
    // The address the members reach the site at, for the links sent to them. Empty: the address of the request
    val publicUrl: String = "",
    val api: ApiConfig = ApiConfig(),
)

// Empty: the API only answers a chief's session
@Serializable
data class ApiConfig(
    val token: String = "",
)

@Serializable
data class DatabaseConfig(
    val path: String,
)

@Serializable
data class FourmizzzConfig(
    val baseUrl: String,
    val timeoutMs: Long,
    // A request failing with a server error or no answer is made again, after a delay growing with each retry
    val retries: Int = 2,
    val retryDelayMs: Long = 1_000,
)

@Serializable
data class AllianceConfig(
    val tag: String,
)
