package fr.nathanaudvard.emp.api.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("players")
class Players(val parent: ApiV1 = ApiV1()) {
    @Serializable
    @Resource("sync")
    class Sync(val parent: Players = Players(), val version: String = "latest")
}
