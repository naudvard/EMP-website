package fr.nathanaudvard.emp.input.http.dto

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val statusCode: Int,
    val error: String,
    val message: String,
)

fun ErrorResponse(status: HttpStatusCode, message: String) =
    ErrorResponse(status.value, status.description, message)
