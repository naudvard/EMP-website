package fr.nathanaudvard.emp.domain.helper

import fr.nathanaudvard.emp.database.PlayerRepository
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevels
import fr.nathanaudvard.emp.domain.model.PlayerState
import fr.nathanaudvard.emp.domain.model.WageSettlement

// What a member never saved is given
class PlayerHelper(private val players: PlayerRepository) {
    // At level 0 everywhere
    suspend fun levels(members: List<AllianceMember>): Map<Long, PlayerLevels> {
        val saved = players.levels(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: PlayerLevels()) }
    }

    // Delivers
    suspend fun wageSettlements(members: List<AllianceMember>): Map<Long, WageSettlement> {
        val saved = players.wageSettlements(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: WageSettlement.DELIVER) }
    }

    // Owes nothing
    suspend fun debts(members: List<AllianceMember>): Map<Long, Long> {
        val saved = players.debts(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: 0) }
    }

    // Delivers, owes nothing, levels at 0
    suspend fun states(members: List<AllianceMember>): Map<Long, PlayerState> {
        val saved = players.states(members.map { it.id })
        return members.associate { it.id to (saved[it.id] ?: PlayerState()) }
    }
}
