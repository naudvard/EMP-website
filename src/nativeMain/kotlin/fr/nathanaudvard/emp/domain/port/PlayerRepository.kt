package fr.nathanaudvard.emp.domain.port

import fr.nathanaudvard.emp.domain.model.Player

interface PlayerRepository {
    suspend fun replaceVersion(version: String, players: List<Player>)
}
