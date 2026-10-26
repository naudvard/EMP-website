package fr.nathanaudvard.emp.database

import fr.nathanaudvard.emp.db.EmpDatabase
import fr.nathanaudvard.emp.db.Exported_players
import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.ExportedPlayer
import fr.nathanaudvard.emp.domain.model.chainRankOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ExportedPlayerRepository(database: EmpDatabase) {
    private val queries = database.exportedPlayerQueries

    suspend fun replaceVersion(version: String, players: List<ExportedPlayer>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.deleteVersion(version)
                players.forEach { queries.insert(it.toRow(version)) }
            }
        }

    suspend fun latestMembers(allianceTag: String): List<AllianceMember> =
        withContext(Dispatchers.IO) {
            queries.latestMembers(allianceTag) { id, pseudo, masterPlayerId, grade ->
                AllianceMember(id, pseudo, masterPlayerId, chainRankOf(grade))
            }.executeAsList()
        }

    suspend fun versionsSince(version: String): List<String> = withContext(Dispatchers.IO) {
        queries.versionsSince(version).executeAsList()
    }

    suspend fun averageFields(
        playerIds: Collection<Long>,
        versions: Collection<String>
    ): Map<Long, Double> =
        withContext(Dispatchers.IO) {
            queries.averageFields(playerIds, versions).executeAsList()
                .associate { it.id to checkNotNull(it.average_field) }
        }
}

private fun ExportedPlayer.toRow(version: String) = Exported_players(
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
