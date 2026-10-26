package fr.nathanaudvard.emp.api.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("alliances")
class Alliances(val parent: ApiV1 = ApiV1()) {
    @Serializable
    @Resource("{tag}")
    class Tag(val parent: Alliances = Alliances(), val tag: String) {
        @Serializable
        @Resource("wages")
        class Wages(val parent: Tag, val distribution: String = "minimal")
    }
}
