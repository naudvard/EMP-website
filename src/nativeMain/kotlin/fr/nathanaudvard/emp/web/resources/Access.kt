package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
enum class AccessNotice { LEFT }

// Without a session: where to find one's link, or a code shown by a device already signed in
@Serializable
@Resource("/access")
class Access(val notice: AccessNotice? = null) {
    // The player's personal link, which signs a single browser in
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
}
