package fr.nathanaudvard.emp.domain.service

import fr.nathanaudvard.emp.database.AccessRepository
import fr.nathanaudvard.emp.database.AllianceSettingsRepository
import fr.nathanaudvard.emp.database.ExportedAllianceRepository
import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.helper.PlayerHelper
import fr.nathanaudvard.emp.domain.helper.WageHelper
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.AllianceOverview
import fr.nathanaudvard.emp.domain.model.ExportVersion
import fr.nathanaudvard.emp.domain.model.MemberDetail
import fr.nathanaudvard.emp.domain.model.MemberOverview
import fr.nathanaudvard.emp.domain.model.WageSettlement
import kotlin.time.Clock

class AllianceService(
    private val exportedAlliances: ExportedAllianceRepository,
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerHelper,
    private val playerRepository: PlayerRepository,
    private val access: AccessRepository,
    private val wages: WageHelper,
    private val settings: AllianceSettingsRepository,
    private val clock: Clock,
) {
    suspend fun members(allianceTag: String): List<AllianceMember> = exportedPlayers.latestMembers(allianceTag)

    // Everything about the members: the page shows a member what they may see only
    suspend fun overview(allianceTag: String): AllianceOverview {
        val members = exportedPlayers.latestMembers(allianceTag)
        val states = players.states(members)
        val sent = access.sentLinks(members.map { it.id })
        val receiving = wages.latest(allianceTag)?.computation?.wages.orEmpty()
            .filter { it.netWage > 0 }
            .map { it.playerId }
            .toSet()
        return AllianceOverview(
            alliance = exportedAlliances.latest(allianceTag),
            syncedAt = exportedPlayers.latestVersion(allianceTag)?.let(ExportVersion::instant),
            members = members.map { MemberOverview(it, states.getValue(it.id), sent[it.id], it.id in receiving) },
        )
    }

    // A member's page, by their pseudo whatever its case. Null when no member has it. Unlike the member's own page, the
    // latest computation is not counted as seen
    suspend fun member(allianceTag: String, pseudo: String, changeId: Long? = null): MemberDetail? {
        val alliance = overview(allianceTag)
        val member = alliance.members.find { it.member.pseudo.equals(pseudo, ignoreCase = true) } ?: return null
        val (latest, previous) = wages.latestTwo(allianceTag)
        return MemberDetail(
            alliance = alliance,
            member = member,
            latest = latest,
            previous = previous,
            change = changeId?.let { playerRepository.levelChange(it) }?.takeIf { it.playerId == member.member.id },
            nextComputation = settings.settings(allianceTag).nextComputation(clock.now()),
        )
    }

    // What the member does with a negative balance, from the next computation on. By is the chief who chose it.
    // The former choice is returned, for it to be put back; null when the player is not a member
    suspend fun saveSettlement(allianceTag: String, playerId: Long, settlement: WageSettlement, by: Long): WageSettlement? {
        val member = exportedPlayers.latestMembers(allianceTag).find { it.id == playerId } ?: return null
        val former = players.states(listOf(member)).getValue(playerId).settlement
        playerRepository.saveWageSettlement(playerId, settlement, by, clock.now())
        return former
    }
}
