package fr.nathanaudvard.emp.service

import fr.nathanaudvard.emp.database.ExportedPlayerRepository
import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.WageSettlement

class PlayerService(
    private val exportedPlayers: ExportedPlayerRepository,
    private val players: PlayerRepository,
) {
    suspend fun latestMembers(allianceTag: String): List<AllianceMember> =
        exportedPlayers.latestMembers(allianceTag)

    suspend fun averageFields(
        members: List<AllianceMember>,
        versions: Collection<String>
    ): Map<Long, Double> =
        exportedPlayers.averageFields(members.map { it.id }, versions)

    // A member without saved levels is at level 0 everywhere
    suspend fun levels(members: List<AllianceMember>): Map<Long, PlayerLevels> {
        val saved = players.levels(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: PlayerLevels()) }
    }

    suspend fun saveLevel(playerId: Long, kind: PlayerLevel, level: Long) =
        players.saveLevel(playerId, kind, level)

    // A member without a saved choice delivers
    suspend fun wageSettlements(members: List<AllianceMember>): Map<Long, WageSettlement> {
        val saved = players.wageSettlements(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: WageSettlement.DELIVER) }
    }

    // A member never saved owes nothing
    suspend fun debts(members: List<AllianceMember>): Map<Long, Long> {
        val saved = players.debts(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: 0) }
    }

    suspend fun saveWageSettlements(settlements: Map<Long, WageSettlement>) =
        players.saveWageSettlements(settlements)
}
