package fr.nathanaudvard.emp.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    // Alliance tags to store: the rest of the export is ignored
    val tags: List<String>,
)
