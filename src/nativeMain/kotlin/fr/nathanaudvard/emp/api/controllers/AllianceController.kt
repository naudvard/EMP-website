package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.usecase.ComputeWages
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.allianceController(computeWages: ComputeWages) {
    post<Alliances.Tag.Wages> { request ->
        val distribution = WageDistribution.entries.find { it.name.equals(request.distribution, ignoreCase = true) }
            ?: throw BadRequestException("Distribution must be \"minimal\" or \"proportional\", got ${request.distribution}")
        call.respond(computeWages(request.parent.tag, distribution).toResponse())
    }
}
