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

    // Replaces the members of these alliances only: the version keeps the other alliances synced before
    suspend fun replaceVersion(version: String, allianceTags: Set<String>, players: List<ExportedPlayer>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.deleteVersion(version, allianceTags)
                players.forEach { queries.insert(it.toRow(version)) }
                // Recorded even without any member: the alliance is known to be empty in this version
                allianceTags.forEach { queries.insertSync(version, it) }
            }
        }

    suspend fun latestMembers(allianceTag: String): List<AllianceMember> =
        withContext(Dispatchers.IO) {
            queries.latestMembers(allianceTag) { id, pseudo, masterPlayerId, grade, field ->
                AllianceMember(id, pseudo, masterPlayerId, chainRankOf(grade), field)
            }.executeAsList()
        }

    // The latest version synced for the alliance, null when none
    suspend fun latestVersion(allianceTag: String): String? = withContext(Dispatchers.IO) {
        queries.latestVersion(allianceTag).executeAsOne().version
    }

    // Versions synced for the alliance
    suspend fun versionsSince(allianceTag: String, version: String): List<String> = withContext(Dispatchers.IO) {
        queries.versionsSince(allianceTag, version).executeAsList()
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
