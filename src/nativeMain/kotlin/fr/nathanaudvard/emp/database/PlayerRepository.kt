package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Players
import fr.nathanaudvard.emp.domain.model.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class PlayerRepository(database: EmpDatabase) {
    private val queries = database.playerQueries

    suspend fun replaceVersion(version: String, players: List<Player>) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.deleteVersion(version)
            players.forEach { queries.insert(it.toRow(version)) }
        }
    }
}

private fun Player.toRow(version: String) = Players(
    version = version,
    id = id,
    pseudo = pseudo,
    alliance_tag = allianceTag,
    master_player_id = masterPlayerId,
    x = x,
    y = y,
    field_ = field,
    grade = grade,
    building_score = buildingScore,
    technology_score = technologyScore,
    trophy_score = trophyScore,
    on_holiday = onHoliday,
    is_banned = isBanned,
)
