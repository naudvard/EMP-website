package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/debts")
class Debts(val saved: Boolean? = null)
