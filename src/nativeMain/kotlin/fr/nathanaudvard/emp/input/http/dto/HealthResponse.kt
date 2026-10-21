package fr.nathanaudvard.emp.input.http.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)
