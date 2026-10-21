package fr.nathanaudvard.emp.api.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("alliances")
class Alliances(val parent: ApiV1 = ApiV1()) {
    @Serializable
    @Resource("sync")
    class Sync(val parent: Alliances = Alliances(), val version: String = "latest")
}
