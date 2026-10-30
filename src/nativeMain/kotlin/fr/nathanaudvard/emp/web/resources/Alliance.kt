package fr.nathanaudvard.emp.web.resources

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// The members, with a filter (a MemberFilter's key)
@Serializable
@Resource("/alliance")
class Alliance(val filter: String? = null) {
    // A member's page, for the chiefs. Notice is what was just done
    @Serializable
    @Resource("members/{pseudo}")
    class Member(val parent: Alliance = Alliance(), val pseudo: String, val notice: MemberNotice? = null) {
        @Serializable
        @Resource("levels/{kind}")
        class Level(val parent: Member, val kind: String)

        @Serializable
        @Resource("levels/confirm")
        class ConfirmLevels(val parent: Member)

        @Serializable
        @Resource("levels/changes/{id}/undo")
        class UndoLevelChange(val parent: Member, val id: Long)

        @Serializable
        @Resource("computations/{computationId}/deliveries/{to}/sent")
        class Declare(val parent: Member, val computationId: Long, val to: Long)

        @Serializable
        @Resource("convoys")
        class Convoys(val parent: Member) {
            @Serializable
            @Resource("delete")
            class Delete(val parent: Convoys)
        }

        // Posted with the settlement, and the list's filter when it comes from the list
        @Serializable
        @Resource("settlement")
        class Settlement(val parent: Member)

        @Serializable
        @Resource("chief")
        class Chief(val parent: Member)

        // The access message: plain text for the copy button's script
        @Serializable
        @Resource("message")
        class Message(val parent: Member)

        @Serializable
        @Resource("replace")
        class Replace(val parent: Member)
    }
}

@Serializable
enum class MemberNotice { SETTLED, CHIEF, NOT_CHIEF, LAST_CHIEF, REPLACED }
