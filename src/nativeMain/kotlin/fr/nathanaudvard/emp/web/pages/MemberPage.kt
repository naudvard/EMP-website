package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.MemberDetail
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.MemberNotice
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.tr
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The sections of the member's page that are not their own page's: the posts come back to them
enum class MemberSection(val id: String) {
    HEADER("member-header"),
    SETTLEMENT("settlement"),
    ACCESS("access"),
}

// What was just done on the page. Copied is the access message just made, shown to be copied by hand when the
// clipboard could not be written
class MemberView(
    val notice: MemberNotice? = null,
    val previous: WageSettlement? = null,
    val copied: String? = null,
)

const val CHIEF_FIELD = "chief"

fun memberForms(pseudo: String): PlayerForms {
    val member = Alliance.Member(pseudo = pseudo)
    return PlayerForms(
        own = false,
        level = { href(Alliance.Member.Level(member, it.name)) },
        confirmLevels = href(Alliance.Member.ConfirmLevels(member)),
        undoLevelChange = { href(Alliance.Member.UndoLevelChange(member, it)) },
        declare = { computationId, to -> href(Alliance.Member.Declare(member, computationId, to)) },
        deleteConvoys = href(Alliance.Member.Convoys.Delete(Alliance.Member.Convoys(member))),
    )
}

// A member's page worked out once, for the whole page as for the sections a post sends back
class MemberPage(val viewer: Viewer, val detail: MemberDetail, val now: Instant) {
    val pseudo = detail.member.member.pseudo
    val forms = memberForms(pseudo)
    val mine = detail.latest?.let { Mine(detail.member.member.id, it) }
    val previous = previousLeft(mine, detail.previous)
}

// For the chiefs only: everything the member does on their page, done in their name, and their access and role
fun HTML.memberPage(page: MemberPage, view: MemberView = MemberView()) =
    layout(page.pseudo, Tab.ALLIANCE, page.viewer) {
        a(href(Alliance()), classes = "member-back") { +"‹ Membres" }
        memberHeader(page)
        div("member") {
            memberSendingColumn(page)
            div("member__column member__column--side") {
                settlementCard(page, view)
                memberLevels(page)
                page.mine?.wage?.let { calculationDetail(page.mine.computation, it) }
                accessCard(page, view)
            }
        }
    }

fun FlowContent.memberHeader(page: MemberPage) = div("member-header") {
    val member = page.detail.member
    id = MemberSection.HEADER.id
    h1("page-title member-header__title") {
        +page.pseudo
        member.member.chainRank?.let { empBadge(BadgeVariant.RANK, it) }
        if (member.state.chief) empBadge(BadgeVariant.LEADER, "chef")
    }
    p("text-secondary") {
        +listOf(
            "TdC ${formatAmount(member.member.field)}${NBSP}cm²",
            colonyOf(member, page.detail.alliance) ?: "pas de colonie",
            member.state.debt.takeIf { it > 0 }?.let { "dette ${formatAmount(it)}" } ?: "aucune dette",
        ).joinToString(" · ")
    }
}

// Its own page's sending column, under the same id, for the same forms
fun FlowContent.memberSendingColumn(page: MemberPage) = div("member__column member__column--main") {
    id = MeSection.SENDING.id
    val mine = page.mine
    val previous = page.previous
    val now = page.now
    previous?.let { (saved, left) -> previousCard(saved, left, now, page.forms, withPaste = false, paste = null) }
    if (mine != null && mine.sends.isNotEmpty()) {
        sendCard(mine, now, page.forms, paste = null)
    } else {
        empCard(cssClass = "me-send") {
            h2("card-title") { +"Envois du calcul" }
            p("text-secondary") {
                +when {
                    mine == null -> "Pas encore de calcul."
                    mine.wage == null -> "Pas dans le dernier calcul : il sera dans le prochain."
                    else -> "Rien à envoyer pour lui dans ce calcul."
                }
            }
        }
    }
    val late = previous?.let { mine!!.lateConvoys(it.first) }.orEmpty()
    if (mine != null && (mine.sends.isNotEmpty() || mine.convoys.isNotEmpty() || late.isNotEmpty())) {
        convoysCard(mine, page.forms, late, previous?.first, now)
    }
    p("small text-secondary") { +"Un convoi saisi par un chef garde le nom du chef dans l'historique." }
}

fun FlowContent.memberLevels(page: MemberPage) {
    val levels = page.detail.member.state.levels
    levelsCard(levels, page.detail.change, page.now, page.forms, updatedBy = levels.updatedBy?.let { page.detail.alliance.pseudo(it) })
}

private fun FORM.live(vararg sections: MemberSection) = live(*sections.map { it.id }.toTypedArray())

fun HTML.unknownMemberPage(viewer: Viewer, pseudo: String) = layout(pseudo, Tab.ALLIANCE, viewer) {
    a(href(Alliance()), classes = "member-back") { +"‹ Membres" }
    h1("page-title") { +pseudo }
    empMessage(MessageVariant.DANGER) { +"Aucun membre de l'alliance n'a ce pseudo." }
}

fun FlowContent.settlementCard(page: MemberPage, view: MemberView) =
    empCard(cssClass = "member-settlement") {
        val detail = page.detail
        val now = page.now
        val member = detail.member
        id = MemberSection.SETTLEMENT.id
        attributes["aria-labelledby"] = "${MemberSection.SETTLEMENT.id}-title"
        div("me-card-head") {
            h2("card-title") {
                id = "${MemberSection.SETTLEMENT.id}-title"
                +"Quand son solde est négatif"
            }
            empBadge(BadgeVariant.LEADER, "Chef")
        }
        val action = href(Alliance.Member.Settlement(Alliance.Member(pseudo = member.member.pseudo)))
        form(action = action, method = FormMethod.post, classes = "member-settlement__form") {
            live(MemberSection.SETTLEMENT)
            attributes["data-submit-on-change"] = ""
            settlementChoice(member, compact = false)
        }
        val previous = view.previous
        if (view.notice == MemberNotice.SETTLED && previous != null) {
            empMessage(MessageVariant.SUCCESS, inline = true) {
                span("alliance-members__saved") { +"✓ ${member.state.settlement.label} au prochain calcul" }
                form(action = action, method = FormMethod.post, classes = "me-inline-form") {
                    live(MemberSection.SETTLEMENT)
                    input(InputType.hidden, name = SETTLEMENT_FIELD) { value = previous.name }
                    empBtn(BtnVariant.LINK, BtnSize.COMPACT) {
                        attributes["aria-label"] = "Annuler : ${previous.label.replaceFirstChar { it.lowercase() }}"
                        +"Annuler"
                    }
                }
            }
        }
        div("member-settlement__notes") {
            member.state.settlementUpdatedAt?.let { at ->
                val by = member.state.settlementUpdatedBy?.let { detail.alliance.pseudo(it) }
                span("small text-secondary") { +"Choisi ${by?.let { "par $it " }.orEmpty()}${formatRelative(at, now)}" }
            }
            if (member.state.settlement == WageSettlement.ACCUMULATE_DEBT && member.receives) {
                span("small alliance-settlement__void") { +"Cumule sans effet : reçoit" }
            }
        }
        p("small text-secondary") {
            +"Enregistré aussitôt, avec Annuler. Vaut pour le prochain calcul (${formatDue(detail.nextComputation, now)}), "
            +"sans effet les jours où il reçoit."
        }
    }

// The figures of the computation as stored, for a chief who checks it
private fun FlowContent.calculationDetail(computation: WageComputation, wage: Wage) =
    empDisclosure("Détail de son calcul", card = true, id = "member-detail", cssClass = "member-detail") {
        table("emp-table member-detail__table") {
            tbody {
                figure("TdC moyen", "${formatAmount(wage.averageField.roundToLong())}${NBSP}cm²")
                wage.baseProduction?.let { figure("Production", formatAmount(it)) }
                wage.colonyTransfer?.takeIf { it != 0L }?.let { figure("Versement colonie", formatBalance(it)) }
                figure("Production propre", formatAmount(wage.selfProduction))
                figure("Temps de ponte", wage.layingTime.toString())
                figure("Recherche de la technique suivante", formatSpan(wage.buildTimeSeconds.seconds))
                figure("Besoin relatif", "${formatAmount(wage.relativeNeed)} (${formatPercent(computation.needShare(wage.playerId), decimals = 2)})")
                figure("Part de la récolte", formatAmount(wage.grossWage))
                figure("Solde", formatBalance(wage.netWage))
                wage.settledAmount?.let { figure("Après dette", formatBalance(it)) }
                figure("Dette", formatAmount(computation.debts.getValue(wage.playerId)))
            }
        }
    }

private fun TBODY.figure(label: String, value: String) = tr {
    th { attributes["scope"] = "row"; +label }
    td("number amount") { +value }
}

fun FlowContent.accessCard(page: MemberPage, view: MemberView) =
    empCard(cssClass = "member-access") {
        val detail = page.detail
        val now = page.now
        val member = detail.member
        val pseudo = member.member.pseudo
        val resource = Alliance.Member(pseudo = pseudo)
        id = MemberSection.ACCESS.id
        attributes["aria-labelledby"] = "${MemberSection.ACCESS.id}-title"
        div("me-card-head") {
            h2("card-title") {
                id = "${MemberSection.ACCESS.id}-title"
                +"Accès et rôle"
            }
            empBadge(BadgeVariant.LEADER, "Chef")
        }
        p("text-secondary") {
            +listOfNotNull(
                member.linkSentAt?.let { "lien envoyé le ${formatDay(it)}" },
                member.state.lastSeenAt?.let { "dernière visite ${formatRelative(it, now)}" } ?: "jamais connecté",
            ).joinToString(" · ").replaceFirstChar { it.uppercase() }
        }
        when (view.notice) {
            MemberNotice.REPLACED -> empMessage(MessageVariant.SUCCESS) {
                +"Lien remplacé : ses appareils sont déconnectés. Copie son nouveau message pour lui rendre l'accès."
            }
            MemberNotice.CHIEF -> empMessage(MessageVariant.SUCCESS) { +"✓ $pseudo est chef de l'alliance." }
            MemberNotice.NOT_CHIEF -> empMessage(MessageVariant.SUCCESS) { +"✓ $pseudo n'est plus chef." }
            MemberNotice.LAST_CHIEF -> empMessage(MessageVariant.DANGER) {
                +"$pseudo est le dernier chef : nomme d'abord un autre chef."
            }
            else -> {}
        }
        view.copied?.let { message ->
            label {
                htmlFor = "access-message"
                +"Message pour $pseudo, objet « $ACCESS_MESSAGE_SUBJECT »"
            }
            textArea(rows = "6", classes = "emp-textarea") {
                id = "access-message"
                readonly = true
                autoFocus = true
                attributes["onfocus"] = "this.select()"
                +message
            }
            empCopyButton("Copier", BtnVariant.SECONDARY, target = "access-message")
        }
        div("member-access__actions") {
            form(href(Alliance.Member.Message(resource)), method = FormMethod.post) {
                empCopyButton("Copier le message privé", BtnVariant.PRIMARY)
            }
            p("small text-secondary") {
                +"À envoyer en message privé dans Fourmizzz, objet « $ACCESS_MESSAGE_SUBJECT ». Chaque copie renouvelle son lien : "
                +"seul le dernier message copié ouvre une session, une seule fois, ses appareils déjà connectés le restent."
            }
            if (member.linkSentAt != null) {
                empDisclosure("Remplacer le lien…", cssClass = "member-access__replace") {
                    form(href(Alliance.Member.Replace(resource)), method = FormMethod.post) {
                        p("small text-secondary") { +"Demande confirmation : l'ancien lien et ses appareils seront déconnectés." }
                        empBtn(BtnVariant.SECONDARY, BtnSize.SMALL) { +"Remplacer le lien de $pseudo" }
                    }
                }
            }
        }
        // The script asks for a confirmation as soon as the box is changed
        form(href(Alliance.Member.Chief(resource)), method = FormMethod.post, classes = "member-access__chief") {
            live(MemberSection.ACCESS, MemberSection.HEADER)
            attributes["data-submit-on-change"] = ""
            attributes["data-confirm"] =
                if (member.state.chief) "Retirer le rôle de chef à $pseudo ?" else "Nommer $pseudo chef de l'alliance ?"
            label("member-access__checkbox") {
                span("member-access__role") {
                    +"Chef de l'alliance"
                    span("small text-secondary") { +"Confirmation demandée" }
                }
                input(InputType.checkBox, name = CHIEF_FIELD) {
                    id = "member-chief"
                    value = "true"
                    checked = member.state.chief
                    attributes["role"] = "switch"
                }
            }
        }
    }
