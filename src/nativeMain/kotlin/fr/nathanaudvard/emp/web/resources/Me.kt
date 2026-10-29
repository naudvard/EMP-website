package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// The viewer's page. Change is a change of their levels just saved, shown with the way to undo it
@Serializable
@Resource("/me")
class Me(val change: Long? = null) {
    // Posted with the level a stepper's button steps to, or the one typed
    @Serializable
    @Resource("levels/{kind}")
    class Level(val parent: Me = Me(), val kind: String)

    // The levels did not change
    @Serializable
    @Resource("levels/confirm")
    class ConfirmLevels(val parent: Me = Me())

    @Serializable
    @Resource("levels/changes/{id}/undo")
    class UndoLevelChange(val parent: Me = Me(), val id: Long)

    // "J'ai envoyé": what is left of the delivery to the player, in the given computation
    @Serializable
    @Resource("computations/{computationId}/deliveries/{to}/sent")
    class Declare(val parent: Me = Me(), val computationId: Long, val to: Long)

    // Posted with the game's messages
    @Serializable
    @Resource("convoys")
    class Convoys(val parent: Me = Me()) {
        // Posted with the ids of the convoys, and the section to come back to
        @Serializable
        @Resource("delete")
        class Delete(val parent: Convoys = Convoys())
    }
}
