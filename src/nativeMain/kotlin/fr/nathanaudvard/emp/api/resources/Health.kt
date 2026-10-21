package fr.nathanaudvard.emp.api.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/health")
class Health
