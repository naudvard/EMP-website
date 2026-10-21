package fr.nathanaudvard.emp.output.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Players
import fr.nathanaudvard.emp.domain.model.Player
import fr.nathanaudvard.emp.domain.port.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SqlPlayerRepository(database: EmpDatabase) : PlayerRepository {
    private val queries = database.playerQueries

    override suspend fun replaceVersion(version: String, players: List<Player>) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.deleteVersion(version)
            players.forEach { queries.insert(it.toRow()) }
        }
    }
}

private fun Player.toRow() = Players(
    version = version,
    id = id,
    pseudo = pseudo,
    alliance_tag = alliance,
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
