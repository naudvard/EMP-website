package fr.nathanaudvard.emp.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val database: DatabaseConfig,
    val fourmizzz: FourmizzzConfig,
    val alliance: AllianceConfig,
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
