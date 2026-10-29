package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.MemberDetail
import fr.nathanaudvard.emp.service.AllianceSettingsService
import fr.nathanaudvard.emp.service.PlayerService
import fr.nathanaudvard.emp.service.WageService
import kotlin.time.Clock

// A member's page, by their pseudo whatever its case. Null when no member has it. Unlike the member's own page, the
// latest computation is not counted as seen
class GetMemberUseCase(
    private val getAlliance: GetAllianceUseCase,
    private val wages: WageService,
    private val players: PlayerService,
    private val settings: AllianceSettingsService,
    private val clock: Clock,
) {
    suspend operator fun invoke(allianceTag: String, pseudo: String, changeId: Long? = null): MemberDetail? {
        val alliance = getAlliance(allianceTag)
        val member = alliance.members.find { it.member.pseudo.equals(pseudo, ignoreCase = true) } ?: return null
        val (latest, previous) = wages.latestTwo(allianceTag)
        return MemberDetail(
            alliance = alliance,
            member = member,
            latest = latest,
            previous = previous,
            change = changeId?.let { players.levelChange(it) }?.takeIf { it.playerId == member.member.id },
            nextComputation = settings.settings(allianceTag).nextComputation(clock.now()),
        )
    }
}
