package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

// What a player's page shows. Latest is null while the alliance has no computation, previous when there is no older one
data class MyOverview(
    val latest: SavedComputation?,
    val previous: SavedComputation?,
    val levels: PlayerLevelsState,
    // A change of the player's levels to confirm, with the way to undo it
    val change: LevelChange?,
    // The latest computation came since the player's previous visit
    val newComputation: Boolean,
    val nextComputation: Instant,
)
