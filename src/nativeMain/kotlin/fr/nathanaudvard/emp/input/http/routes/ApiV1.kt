package fr.nathanaudvard.emp.input.http.routes

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/api/v1")
class ApiV1 {
    @Serializable
    @Resource("alliances")
    class Alliances(val parent: ApiV1 = ApiV1()) {
        @Serializable
        @Resource("sync")
        class Sync(val parent: Alliances = Alliances(), val version: String = "latest")
    }

    @Serializable
    @Resource("players")
    class Players(val parent: ApiV1 = ApiV1()) {
        @Serializable
        @Resource("sync")
        class Sync(val parent: Players = Players(), val version: String = "latest")
    }
}
