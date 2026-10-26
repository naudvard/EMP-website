package fr.nathanaudvard.emp.api.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("exports")
class Exports(val parent: ApiV1 = ApiV1()) {
    @Serializable
    @Resource("alliances")
    class Alliances(val parent: Exports = Exports()) {
        @Serializable
        @Resource("sync")
        class Sync(val parent: Alliances = Alliances(), val version: String = "latest")
    }

    @Serializable
    @Resource("players")
    class Players(val parent: Exports = Exports()) {
        @Serializable
        @Resource("sync")
        class Sync(val parent: Players = Players(), val version: String = "latest")
    }
}
