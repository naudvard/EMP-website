package fr.nathanaudvard.emp.api.controllers

import fr.nathanaudvard.emp.api.dto.toResponse
import fr.nathanaudvard.emp.api.resources.Alliances
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.service.WageService
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

// The daily computation is a cron calling this route. Without a distribution, the one the chiefs saved
fun Route.allianceController(wages: WageService) {
    post<Alliances.Tag.Wages> { request ->
        val tag = request.parent.tag
        val distribution = request.distribution?.let { name ->
            WageDistribution.entries.find { it.name.equals(name, ignoreCase = true) }
                ?: throw BadRequestException("Distribution must be \"minimal\" or \"proportional\", got $name")
        } ?: wages.settings(tag).distribution
        call.respond(wages.compute(tag, distribution).toResponse())
    }
}
