package fr.nathanaudvard.emp.web.resources

import fr.nathanaudvard.emp.domain.model.WageSettlement
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

// The members, with a filter (a MemberFilter's key) and a part of a pseudo. Synced is the members count of a sync just
// made; settled the player whose settlement was just chosen, and previous their former one, for it to be put back
@Serializable
@Resource("/alliance")
class Alliance(
    val filter: String? = null,
    val pseudo: String? = null,
    val synced: Int? = null,
    val settled: Long? = null,
    val previous: WageSettlement? = null,
) {
    @Serializable
    @Resource("sync")
    class Sync(val parent: Alliance = Alliance())

    // A member's page, for the chiefs. Change is a change of their levels just saved, notice what was just done
    @Serializable
    @Resource("members/{pseudo}")
    class Member(
        val parent: Alliance = Alliance(),
        val pseudo: String,
        val change: Long? = null,
        val notice: MemberNotice? = null,
        val previous: WageSettlement? = null,
    ) {
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
