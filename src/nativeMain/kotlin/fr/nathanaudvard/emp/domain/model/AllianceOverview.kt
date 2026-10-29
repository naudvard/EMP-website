package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

// What is stored of a player beside the exports, with the defaults of a member never saved
data class PlayerState(
    val levels: PlayerLevelsState = PlayerLevelsState(PlayerLevels(), null, null),
    val settlement: WageSettlement = WageSettlement.DELIVER,
    // When a chief last chose the settlement and who, null when never
    val settlementUpdatedAt: Instant? = null,
    val settlementUpdatedBy: Long? = null,
    val debt: Long = 0,
    val chief: Boolean = false,
    val lastSeenAt: Instant? = null,
)

data class MemberOverview(
    val member: AllianceMember,
    val state: PlayerState,
    // When the access message was last copied, null when never
    val linkSentAt: Instant?,
    // The member receives in the latest computation: accumulating their debt changes nothing then
    val receives: Boolean,
)

// Alliance is null until its export is synced; syncedAt is the players export the members come from
data class AllianceOverview(
    val alliance: ExportedAlliance?,
    val syncedAt: Instant?,
    val members: List<MemberOverview>,
) {
    fun pseudo(playerId: Long): String? = members.find { it.member.id == playerId }?.member?.pseudo
}

// A member's page for the chiefs. Previous is null when there is no older computation
data class MemberDetail(
    val alliance: AllianceOverview,
    val member: MemberOverview,
    val latest: SavedComputation?,
    val previous: SavedComputation?,
    // A change of the member's levels just saved, with the way to undo it
    val change: LevelChange?,
    val nextComputation: Instant,
)
