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
)

@Serializable
data class AllianceConfig(
    val tag: String,
)
