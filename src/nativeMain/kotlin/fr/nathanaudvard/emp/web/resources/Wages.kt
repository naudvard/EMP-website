package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// Without id, the latest computation of the date (ISO, game time), else the latest computation
@Serializable
@Resource("/wages")
class Wages(val id: Long? = null, val date: String? = null)
