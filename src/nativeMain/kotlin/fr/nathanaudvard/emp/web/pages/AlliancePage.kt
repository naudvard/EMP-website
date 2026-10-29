package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceOverview
import fr.nathanaudvard.emp.domain.model.MemberOverview
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.web.resources.Alliance
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.TBODY
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlin.time.Instant

// The filters of the members list, for the chiefs, under their key in the URL
enum class MemberFilter(val key: String, val label: String, val warning: Boolean = false, val shortLabel: String? = null) {
    ALL("all", "Tous"),
    STALE_LEVELS("stale-levels", "Niveaux de plus de 7 j", warning = true, shortLabel = "Niveaux > 7 j"),
    ACCUMULATE("accumulate", "Cumulent"),
    NEVER_SIGNED_IN("never-signed-in", "Jamais connectés");

    fun matches(member: MemberOverview, now: Instant): Boolean = when (this) {
        ALL -> true
        STALE_LEVELS -> member.state.levels.updatedAt.let { it == null || now - it > LEVELS_STALE_AFTER }
        ACCUMULATE -> member.state.settlement == WageSettlement.ACCUMULATE_DEBT
        NEVER_SIGNED_IN -> member.state.lastSeenAt == null
    }

    companion object {
        fun of(key: String?) = entries.find { it.key == key } ?: ALL
    }
}

// What the list was asked for, and what was just done: a sync, or a settlement chosen with the one it replaced
class AllianceView(
    val filter: MemberFilter = MemberFilter.ALL,
    val pseudo: String = "",
    val synced: Int? = null,
    val syncError: String? = null,
    val settled: Long? = null,
    val previous: WageSettlement? = null,
)

const val MEMBERS_SECTION = "members"

const val SETTLEMENT_FIELD = "settlement"
const val FILTER_FIELD = "filter"
const val PSEUDO_FIELD = "pseudo"

// A member sees ranks, pseudos, colonies, fields and debts only; levels, settlements and accesses are the chiefs'
fun HTML.alliancePage(allianceTag: String, viewer: Viewer, overview: AllianceOverview, now: Instant, view: AllianceView = AllianceView()) =
    layout("Alliance", Tab.ALLIANCE, viewer) {
        div("alliance-header") {
            div("alliance-header__title") {
                h1("page-title") { +"Alliance $allianceTag" }
                p("text-secondary") {
                    overview.alliance?.name?.let { +it }
                    overview.syncedAt?.let { syncedAt ->
                        if (overview.alliance?.name != null) +" · "
                        // "Fourmizzz 12:00" on a phone, for the data of the day
                        if (formatRelativeDay(syncedAt, now) == "aujourd'hui") {
                            span("abridged") { +"Fourmizzz ${formatTime(syncedAt)}" }
                            span("whole") { +"données Fourmizzz ${formatOfDayTime(syncedAt, now)}" }
                        } else {
                            +"données Fourmizzz ${formatOfDayTime(syncedAt, now)}"
                        }
                    }
                }
            }
            if (viewer.chief) {
                form(action = href(Alliance.Sync()), method = FormMethod.post, classes = "alliance-header__sync") {
                    empBadge(BadgeVariant.LEADER, "Chef")
                    empBtn(BtnVariant.SECONDARY, BtnSize.COMPACT) {
                        span("alliance-sync__long") { +"Synchroniser Fourmizzz" }
                        span("alliance-sync__short") { +"Synchro" }
                    }
                }
            }
        }
        view.synced?.let { empMessage(MessageVariant.SUCCESS) { +"✓ ${plural(it, "membre synchronisé", "membres synchronisés")}" } }
        view.syncError?.let { empMessage(MessageVariant.DANGER) { +it } }
        if (overview.members.isEmpty()) {
            empMessage(MessageVariant.INFO) { +"Aucun membre connu : l'export des joueurs n'a pas encore été synchronisé." }
            return@layout
        }
        tiles(overview)
        members(viewer, overview, now, view)
    }

private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count > 1) many else one}"

private fun FlowContent.tiles(overview: AllianceOverview) = div("alliance-tiles") {
    val alliance = overview.alliance
    tile("Joueurs") { +overview.members.size.toString() }
    tile("TdC total") {
        val field = alliance?.totalField ?: overview.members.sumOf { it.member.field }
        span("abridged") { +"${formatMillions(field, 2)}${NBSP}cm²" }
        span("whole") { +"${formatAmount(field)}${NBSP}cm²" }
    }
    tile("Guerres") { +(alliance?.wars?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Aucune") }
    tile("Pactes") { +(alliance?.pacts?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.tag } ?: "Aucun") }
}

private fun FlowContent.tile(label: String, figure: DIV.() -> Unit) = div("emp-tile") {
    span("caption text-secondary") { +label }
    div("alliance-tile__figure") { figure() }
}

private fun FlowContent.members(viewer: Viewer, overview: AllianceOverview, now: Instant, view: AllianceView) =
    section("alliance-members") {
        id = MEMBERS_SECTION
        attributes["aria-labelledby"] = "$MEMBERS_SECTION-title"
        val chief = viewer.chief
        // A member has no filter: they would tell the settlements and the accesses
        val filter = if (chief) view.filter else MemberFilter.ALL
        h2("card-title") {
            id = "$MEMBERS_SECTION-title"
            +"Membres"
        }
        div("alliance-members__filters") {
            if (chief) {
                nav("alliance-filters") {
                    attributes["aria-label"] = "Filtrer les membres"
                    MemberFilter.entries.forEach { entry ->
                        val count = overview.members.count { entry.matches(it, now) }
                        val link = href(Alliance(filter = entry.key.takeIf { entry != MemberFilter.ALL }, pseudo = view.pseudo.ifEmpty { null }))
                        a(link, classes = if (entry.warning) "emp-filter emp-filter--warning" else "emp-filter") {
                            if (entry == filter) attributes["aria-current"] = "true"
                            // Whole spans: a chip is a flex box, which drops the spaces between its children
                            if (entry.shortLabel != null) {
                                span("abridged") { +"${entry.shortLabel} · $count" }
                                span("whole") { +"${entry.label} · $count" }
                            } else {
                                +"${entry.label} · $count"
                            }
                        }
                    }
                }
            }
            pseudoFilter(filter, view.pseudo)
        }
        if (chief) settledMessage(overview, view)
        val shown = overview.members.filter { filter.matches(it, now) && it.member.pseudo.contains(view.pseudo, ignoreCase = true) }
        if (shown.isEmpty()) {
            p("text-secondary") { +"Aucun membre pour ce filtre." }
        } else {
            // A list on a phone, a table from a desktop width
            ul("emp-frame alliance-list") { shown.forEach { item(it, viewer, overview, now) } }
            div("emp-frame alliance-members__frame") {
                table("emp-table alliance-members__table") {
                    thead {
                        tr {
                            th { attributes["scope"] = "col"; +"Rang" }
                            th { attributes["scope"] = "col"; +"Joueur" }
                            th(classes = "number") { attributes["scope"] = "col"; +"TdC" }
                            th(classes = "number") { attributes["scope"] = "col"; +"Dette" }
                            if (chief) {
                                th(classes = "alliance-members__levels") { attributes["scope"] = "col"; +"Niveaux" }
                                PlayerLevel.entries.forEach {
                                    th(classes = "number alliance-members__level") { attributes["scope"] = "col"; +it.shortLabel }
                                }
                                th { attributes["scope"] = "col"; +"Màj niveaux" }
                                th { attributes["scope"] = "col"; +"Si solde négatif" }
                                th { attributes["scope"] = "col"; +"Accès" }
                            }
                        }
                    }
                    tbody { shown.forEach { row(it, viewer, overview, now, view) } }
                }
            }
        }
        if (chief) {
            p("small text-secondary alliance-members__note") {
                +"Un clic sur un pseudo ouvre sa fiche : niveaux, convois en son nom, lien d'accès, rôle. "
                +"« Si solde négatif » s'enregistre aussitôt (avec Annuler) et vaut pour le prochain calcul. "
                +"Les membres voient cette liste avec seulement le rang, le pseudo, la colonie, le TdC et la dette."
            }
            p("small text-secondary") { +"Niveaux : couveuse · solarium · technique de ponte · étable." }
        }
    }

// A line per member on a phone: a chief opens their page from it
private fun UL.item(member: MemberOverview, viewer: Viewer, overview: AllianceOverview, now: Instant) = li("alliance-list__item") {
    val player = member.member
    val state = member.state
    val content: FlowContent.() -> Unit = {
        span("alliance-list__rank") {
            player.chainRank?.let { empBadge(BadgeVariant.RANK, it) } ?: span("text-secondary") { +"—" }
        }
        span("alliance-list__text") {
            span("alliance-members__pseudo") {
                +player.pseudo
                if (state.chief) empBadge(BadgeVariant.LEADER, "chef")
                if (player.id == viewer.playerId) empBadge(BadgeVariant.YOU, "toi")
                colonyOf(member, overview)?.let { span("small text-secondary") { +it } }
            }
            span("small text-secondary") {
                val debt = state.debt.takeIf { it > 0 }?.let { "dette ${formatMillions(it)}" }
                if (viewer.chief) {
                    val levels = state.levels
                    val stale = MemberFilter.STALE_LEVELS.matches(member, now)
                    if (levels.updatedAt != null) +"${PlayerLevel.entries.joinToString(" · ") { levels.levels[it].toString() }} · "
                    span(if (stale) "alliance-members__stale" else null) {
                        +"màj ${levels.updatedAt?.let { formatRelativeDay(it, now) } ?: "jamais"}"
                    }
                    if (state.lastSeenAt == null) {
                        +" · "
                        span("alliance-members__never") { +"jamais connecté" }
                    }
                    val accumulates = state.settlement == WageSettlement.ACCUMULATE_DEBT
                    if (accumulates || debt != null) {
                        +" · "
                        span("alliance-list__debt") { +listOfNotNull("cumule".takeIf { accumulates }, debt).joinToString(" · ") }
                    }
                } else {
                    +listOfNotNull("TdC ${formatAmount(player.field)}${NBSP}cm²", debt).joinToString(" · ")
                }
            }
        }
    }
    if (viewer.chief) {
        a(href(Alliance.Member(pseudo = player.pseudo)), classes = "alliance-list__row") {
            content()
            span("alliance-list__chevron") {
                attributes["aria-hidden"] = "true"
                +"›"
            }
        }
    } else {
        div("alliance-list__row") { content() }
    }
}

// Without the script, the filter is sent; with it, the rows are filtered as the pseudo is typed
private fun FlowContent.pseudoFilter(filter: MemberFilter, pseudo: String) =
    form(action = href(Alliance()), method = FormMethod.get, classes = "alliance-search") {
        attributes["role"] = "search"
        if (filter != MemberFilter.ALL) input(InputType.hidden, name = FILTER_FIELD) { value = filter.key }
        label("visually-hidden") {
            htmlFor = "alliance-pseudo"
            +"Filtrer par pseudo"
        }
        input(InputType.search, name = PSEUDO_FIELD, classes = "emp-field emp-field--compact") {
            id = "alliance-pseudo"
            value = pseudo
            placeholder = "Filtrer par pseudo"
            attributes["autocomplete"] = "off"
        }
        empBtn(BtnVariant.SECONDARY, BtnSize.COMPACT) {
            this.classes += "alliance-search__submit"
            +"Filtrer"
        }
    }

private fun FlowContent.settledMessage(overview: AllianceOverview, view: AllianceView) {
    val member = overview.members.find { it.member.id == view.settled } ?: return
    val previous = view.previous ?: return
    empMessage(MessageVariant.SUCCESS, inline = true) {
        span("alliance-members__saved") { +"✓ ${member.member.pseudo} ${member.state.settlement.choice} au prochain calcul" }
        settlementForm(member, view, classes = "me-inline-form") {
            input(InputType.hidden, name = SETTLEMENT_FIELD) { value = previous.name }
            empBtn(BtnVariant.LINK, BtnSize.COMPACT) {
                attributes["aria-label"] = "Annuler : ${member.member.pseudo} ${previous.choice}"
                +"Annuler"
            }
        }
    }
}

private fun FlowContent.settlementForm(
    member: MemberOverview,
    view: AllianceView,
    classes: String,
    submitOnChange: Boolean = false,
    block: FORM.() -> Unit,
) = form(action = href(Alliance.Member.Settlement(Alliance.Member(pseudo = member.member.pseudo))), method = FormMethod.post, classes = classes) {
    attributes["data-emp-live"] = MEMBERS_SECTION
    if (submitOnChange) attributes["data-submit-on-change"] = ""
    // Back to the list as it was filtered
    input(InputType.hidden, name = FILTER_FIELD) { value = view.filter.key }
    if (view.pseudo.isNotEmpty()) input(InputType.hidden, name = PSEUDO_FIELD) { value = view.pseudo }
    block()
}

private fun TBODY.row(member: MemberOverview, viewer: Viewer, overview: AllianceOverview, now: Instant, view: AllianceView) {
    val chief = viewer.chief
    val player = member.member
    tr(if (player.id == viewer.playerId) "emp-table__you" else null) {
        attributes["data-pseudo"] = player.pseudo.lowercase()
        td {
            val rank = player.chainRank
            if (rank == null) span("text-secondary") { +"—" } else empBadge(BadgeVariant.RANK, rank)
        }
        td("alliance-members__member") {
            span("alliance-members__pseudo") {
                if (chief) a(href(Alliance.Member(pseudo = player.pseudo))) { +player.pseudo } else +player.pseudo
                if (member.state.chief) empBadge(BadgeVariant.LEADER, "chef")
                if (player.id == viewer.playerId) empBadge(BadgeVariant.YOU, "toi")
            }
            colonyOf(member, overview)?.let { span("small text-secondary") { +it } }
        }
        td("number amount") { +"${formatAmount(player.field)}${NBSP}cm²" }
        td("number") {
            val debt = member.state.debt
            if (debt > 0) span("amount debt-amount") { +formatAmount(debt) } else span("text-secondary") { +"—" }
        }
        if (!chief) return@tr
        val levels = member.state.levels
        td("alliance-members__levels amount") { +PlayerLevel.entries.joinToString(" · ") { levels.levels[it].toString() } }
        PlayerLevel.entries.forEach { td("number alliance-members__level") { +levels.levels[it].toString() } }
        td {
            val stale = MemberFilter.STALE_LEVELS.matches(member, now)
            span(if (stale) "alliance-members__stale" else "text-secondary") {
                +(levels.updatedAt?.let { formatRelativeDay(it, now) } ?: "jamais")
            }
        }
        td("alliance-members__settlement") {
            settlementForm(member, view, classes = "alliance-settlement", submitOnChange = true) {
                settlementChoice(member, compact = true)
                empBtn(BtnVariant.SECONDARY, BtnSize.SMALL) {
                    this.classes += "alliance-settlement__submit"
                    +"Enregistrer"
                }
            }
            settlementNote(member, now)
        }
        td {
            val lastSeen = member.state.lastSeenAt
            if (lastSeen != null) {
                span("text-secondary") { +formatRelative(lastSeen, now) }
            } else {
                div("alliance-members__access") {
                    form(href(Alliance.Member.Message(Alliance.Member(pseudo = player.pseudo))), method = FormMethod.post) {
                        empCopyButton("Copier le MP d'accès", BtnVariant.SECONDARY, BtnSize.SMALL)
                    }
                }
            }
        }
    }
}

// "colonie de Messor", or of a player outside the alliance
fun colonyOf(member: MemberOverview, overview: AllianceOverview): String? {
    val master = member.member.masterPlayerId ?: return null
    return overview.pseudo(master)?.let { "colonie ${ofPseudo(it)}" } ?: "colonie hors de l'alliance"
}

// Envoie / Cumule, each radio focused back by its id once the list is replaced
fun FlowContent.settlementChoice(member: MemberOverview, compact: Boolean) =
    fieldSet(if (compact) "emp-segmented emp-segmented--compact" else "emp-segmented") {
        legend("visually-hidden") { +"Si le solde de ${member.member.pseudo} est négatif" }
        WageSettlement.entries.forEach { settlement ->
            val classes = if (settlement == WageSettlement.ACCUMULATE_DEBT) "emp-segmented__option emp-segmented__option--warning" else "emp-segmented__option"
            label(classes) {
                input(InputType.radio, name = SETTLEMENT_FIELD) {
                    id = "settlement-${member.member.id}-${settlement.name.lowercase()}"
                    value = settlement.name
                    checked = settlement == member.state.settlement
                }
                +(if (compact) settlement.shortLabel else settlement.label)
            }
        }
    }

// When it was last chosen, and why accumulating changes nothing today
fun FlowContent.settlementNote(member: MemberOverview, now: Instant) {
    member.state.settlementUpdatedAt?.let { span("small text-secondary alliance-settlement__date") { +formatRelative(it, now) } }
    if (member.state.settlement == WageSettlement.ACCUMULATE_DEBT && member.receives) {
        span("small alliance-settlement__void") { +"Cumule sans effet : reçoit" }
    }
}

val PlayerLevel.shortLabel
    get() = when (this) {
        PlayerLevel.INCUBATOR -> "Couv."
        PlayerLevel.SOLARIUM -> "Sol."
        PlayerLevel.LAYING_TECHNIQUE -> "Tech. ponte"
        PlayerLevel.APHID_FARM -> "Étable"
    }

val WageSettlement.shortLabel
    get() = when (this) {
        WageSettlement.DELIVER -> "Envoie"
        WageSettlement.ACCUMULATE_DEBT -> "Cumule"
    }

val WageSettlement.label
    get() = when (this) {
        WageSettlement.DELIVER -> "Il envoie"
        WageSettlement.ACCUMULATE_DEBT -> "Il cumule en dette"
    }

// "Fourmi envoie au prochain calcul"
private val WageSettlement.choice
    get() = when (this) {
        WageSettlement.DELIVER -> "envoie"
        WageSettlement.ACCUMULATE_DEBT -> "cumule sa dette"
    }

