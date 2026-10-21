package fr.nathanaudvard.emp.api.dto

import fr.nathanaudvard.emp.domain.model.SyncResult
import kotlinx.serialization.Serializable

@Serializable
data class SyncResponse(
    val version: String,
    val count: Int,
)

fun SyncResult.toResponse() = SyncResponse(version = version, count = count)
