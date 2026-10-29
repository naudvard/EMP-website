package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// Without id, the latest computation of the date (ISO, game time), else the latest computation.
// Detail asks for the full table, shown to chiefs only
@Serializable
@Resource("/wages")
class Wages(val id: Long? = null, val date: String? = null, val detail: Int? = null) {
    // The daily computation's time and distribution, for the chiefs
    @Serializable
    @Resource("settings")
    class Settings(val parent: Wages = Wages())

    // Asks for a confirmation, posted to compute
    @Serializable
    @Resource("compute")
    class Compute(val parent: Wages = Wages())

    // Texts to copy, for the chiefs: plain text for the copy button's script
    @Serializable
    @Resource("{computationId}/reminders/{playerId}")
    class Reminder(val parent: Wages = Wages(), val computationId: Long, val playerId: Long)

    @Serializable
    @Resource("{computationId}/announcement")
    class Announcement(val parent: Wages = Wages(), val computationId: Long)
}
