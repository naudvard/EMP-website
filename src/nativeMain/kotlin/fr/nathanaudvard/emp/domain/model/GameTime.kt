package fr.nathanaudvard.emp.domain.model

import kotlinx.datetime.TimeZone

// The game's server is in France: its messages and the pages give its times
val gameTimeZone = TimeZone.of("Europe/Paris")
