package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

// What the chiefs steer the computations with: the settings, the members the next computation will count and what
// they lack
data class WagesPanel(
    val settings: AllianceSettings,
    val nextComputation: Instant,
    val alliance: AllianceOverview,
)
