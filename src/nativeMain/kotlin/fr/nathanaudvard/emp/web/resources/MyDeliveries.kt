package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// What the player of this pseudo sends or receives in the latest computation, and the convoys they recorded
@Serializable
@Resource("/my-deliveries")
class MyDeliveries(val pseudo: String? = null, val recorded: Int? = null) {
    // Removes a convoy of the player of the parent's pseudo
    @Serializable
    @Resource("convoys/{id}/delete")
    class DeleteConvoy(val parent: MyDeliveries, val id: Long)
}
