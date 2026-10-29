package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.AllianceOverview
import fr.nathanaudvard.emp.domain.model.MemberOverview
import fr.nathanaudvard.emp.service.AccessService
import fr.nathanaudvard.emp.service.ExportService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.WageService

// Everything about the members: the page shows a member what they may see only
class GetAllianceUseCase(
    private val exports: ExportService,
    private val players: PlayerService,
    private val access: AccessService,
    private val wages: WageService,
) {
    suspend operator fun invoke(allianceTag: String): AllianceOverview {
        val members = players.latestMembers(allianceTag)
        val states = players.states(members)
        val sent = access.sentLinks(members.map { it.id })
        val receiving = wages.latest(allianceTag)?.computation?.wages.orEmpty()
            .filter { it.netWage > 0 }
            .map { it.playerId }
            .toSet()
        return AllianceOverview(
            alliance = exports.latestAlliance(allianceTag),
            syncedAt = exports.latestPlayersSync(allianceTag),
            members = members.map { MemberOverview(it, states.getValue(it.id), sent[it.id], it.id in receiving) },
        )
    }
}
