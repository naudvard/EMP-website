package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
enum class AccessNotice { LEFT }

// Without a session: where to find one's link, or a code shown by a device already signed in
@Serializable
@Resource("/access")
class Access(val notice: AccessNotice? = null) {
    // The player's personal link, opened once per device
    @Serializable
    @Resource("{token}")
    class Link(val parent: Access = Access(), val token: String)

    @Serializable
    @Resource("code")
    class Code(val parent: Access = Access())

    // Shows a code that signs another device in
    @Serializable
    @Resource("device")
    class Device(val parent: Access = Access())

    // Where the chiefs give the members their links. Replaced is the player whose link was just replaced
    @Serializable
    @Resource("links")
    class Links(val parent: Access = Access(), val replaced: Long? = null) {
        @Serializable
        @Resource("{playerId}/message")
        class Message(val parent: Links = Links(), val playerId: Long)

        @Serializable
        @Resource("{playerId}/replace")
        class Replace(val parent: Links = Links(), val playerId: Long)
    }
}
