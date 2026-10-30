package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceSettings
import fr.nathanaudvard.emp.domain.model.ComputationFailure
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.domain.model.WagesPanel
import fr.nathanaudvard.emp.domain.model.gameTimeZone
import fr.nathanaudvard.emp.service.COLONY_BASE_SHARE_PERCENT
import fr.nathanaudvard.emp.service.COLONY_SHARE_PERCENT_PER_APHID_FARM_LEVEL
import fr.nathanaudvard.emp.service.WOOD_PER_FIELD_PER_DAY
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.Wages
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.OL
import kotlinx.html.TR
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.classes
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Instant

const val PANEL_SECTION = "panel"

object WagesFields {
    const val TIME = "time"
    const val DISTRIBUTION = "distribution"
    const val ID = "id"
}

// A text to copy shown on the page, when the clipboard could not be written
class CopiedText(val label: String, val text: String)

// The searched date is the one asked for in the URL, when no computation id is. The viewer comes first in the
// deliveries and is marked in the balances; the full detail, the panel and the texts to copy are only ever shown to a
// chief
fun HTML.wagesPage(
    history: WageHistory,
    viewer: Viewer? = null,
    searchedDate: String? = null,
    detail: Boolean = false,
    panel: WagesPanel? = null,
    now: Instant? = null,
    copied: CopiedText? = null,
) = layout("Salaires", Tab.WAGES, viewer) {
    val viewerId = viewer?.playerId
    val chief = viewer?.chief == true
    val computation = history.selected
    val pilot = panel.takeIf { chief }?.let { panel -> now?.let { panel to it } }
    if (history.bounds.latestId == null || computation == null) {
        h1("page-title") { +"Salaires" }
        pilot?.let { (panel, now) -> wagesPanel(panel, history, now, copied) }
        if (history.bounds.latestId == null) {
            empMessage(MessageVariant.INFO) { +"Aucun calcul de salaires pour l'instant." }
        } else {
            empMessage(MessageVariant.DANGER) {
                +(if (searchedDate != null) "Aucun calcul à cette date ou avant." else "Ce calcul n'existe pas.")
                a(href(Wages())) { +"Voir le dernier calcul" }
            }
            dateForm(history, searchedDate)
        }
        return@layout
    }

    div("wages-header") {
        h1("page-title wages-header__title") { +"Salaires" }
        selector(history, computation, detail && chief)
        p("wages-header__period text-secondary") {
            +"Récolte du ${formatDateTime(computation.since)} au ${formatDateTime(computation.computedAt)}"
            +" · ${plural(computation.hours, "mesure horaire", "mesures horaires")}"
            +" · répartition ${computation.distribution.label}"
        }
    }
    pilot?.let { (panel, now) -> wagesPanel(panel, history, now, copied) }
    val latest = history.selectedId == history.bounds.latestId
    if (!latest) {
        empMessage(MessageVariant.INFO, inline = true) {
            +"Ancien calcul ·"
            a(href(Wages())) { +"voir le dernier" }
        }
    }
    tiles(computation, chief)
    filter()
    // Reminders tell what is due by the next computation: the latest computation's only
    deliveries(computation, viewerId, chief, history.selectedId.takeIf { chief && latest })
    balances(computation, history.selectedId, viewerId, chief, detail && chief)
    method(computation)
}

val WageDistribution.label
    get() = when (this) {
        WageDistribution.MINIMAL -> "minimale"
        WageDistribution.PROPORTIONAL -> "proportionnelle"
    }

private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count > 1) many else one}"

private val WageDistribution.title get() = label.replaceFirstChar { it.uppercase() }

val ComputationFailure.reason
    get() = when (this) {
        ComputationFailure.NO_EXPORT -> "aucun export joueurs Fourmizzz depuis le calcul précédent"
        ComputationFailure.NO_MEMBERS -> "aucun membre de l'alliance dans le dernier export joueurs"
        ComputationFailure.FOURMIZZZ_UNAVAILABLE -> "Fourmizzz ne répond pas"
        ComputationFailure.UNEXPECTED -> "erreur inattendue, détaillée dans les journaux du serveur"
    }

// The settings of the daily computation, the computation on demand, and what needs a chief before the next one
fun FlowContent.wagesPanel(panel: WagesPanel, history: WageHistory, now: Instant, copied: CopiedText? = null) =
    empCard(CardVariant.ACCENT, "wages-panel") {
        id = PANEL_SECTION
        attributes["aria-labelledby"] = "$PANEL_SECTION-title"
        div("wages-panel__header") {
            h2("card-title") {
                id = "$PANEL_SECTION-title"
                +"Prochain calcul : ${formatDue(panel.nextComputation, now)}"
            }
            empBadge(BadgeVariant.LEADER, "Chef")
        }
        copied?.let { copiedText(it) }
        val announcement = history.selectedId?.takeIf { history.selected != null }
        settingsForm(panel, history, now)
        p("wages-panel__wide small text-secondary") {
            +"« Calculer maintenant » demande confirmation : il clôt la période en cours, les envois non faits restent à faire, "
            +"et le calcul de ${panel.settings.computationTime} partira de ce nouveau calcul."
        }
        alerts(panel, history, now, announcement)
        // On a phone the actions come last, under the alerts
        div("wages-panel__actions") {
            compute()
            announcement?.let { announcement(it, "Annonce forum", BtnSize.DEFAULT) }
        }
    }

private fun FlowContent.compute() = empBtnLink(href(Wages.Compute()), BtnVariant.PRIMARY) { +"Calculer maintenant…" }

private fun FlowContent.announcement(computationId: Long, label: String, size: BtnSize) =
    form(href(Wages.Announcement(computationId = computationId)), method = FormMethod.post, classes = "wages-panel__announcement") {
        empCopyButton(label, BtnVariant.SECONDARY, size)
    }

private fun FlowContent.copiedText(copied: CopiedText) = div("wages-panel__copied") {
    label {
        htmlFor = "copied-text"
        +copied.label
    }
    textArea(rows = "8", classes = "emp-textarea") {
        id = "copied-text"
        readonly = true
        autoFocus = true
        attributes["onfocus"] = "this.select()"
        +copied.text
    }
    empCopyButton("Copier", BtnVariant.SECONDARY, BtnSize.COMPACT, target = "copied-text")
}

// Saved as soon as a setting changes, back to the computation shown
private fun FlowContent.settingsForm(panel: WagesPanel, history: WageHistory, now: Instant) {
    val settings = panel.settings
    form(href(Wages.Settings()), method = FormMethod.post, classes = "wages-panel__settings") {
        live(PANEL_SECTION)
        attributes["data-submit-on-change"] = ""
        history.selectedId?.takeIf { it != history.bounds.latestId }?.let { id ->
            input(InputType.hidden, name = WagesFields.ID) { value = id.toString() }
        }
        div("wages-panel__setting") {
            label("small") {
                htmlFor = "computation-time"
                +"Chaque jour à"
            }
            input(InputType.time, name = WagesFields.TIME, classes = "emp-field emp-field--compact") {
                id = "computation-time"
                required = true
                value = settings.computationTime.toString()
            }
        }
        div("wages-panel__setting") {
            span("small") {
                id = "distribution-label"
                +"Répartition"
            }
            fieldSet("emp-segmented") {
                attributes["aria-labelledby"] = "distribution-label"
                WageDistribution.entries.forEach { distribution ->
                    label("emp-segmented__option") {
                        input(InputType.radio, name = WagesFields.DISTRIBUTION) {
                            id = "distribution-${distribution.name.lowercase()}"
                            value = distribution.name
                            checked = distribution == settings.distribution
                        }
                        if (distribution == WageDistribution.PROPORTIONAL) {
                            span("abridged") { +"Proport." }
                            span("whole") { +distribution.title }
                        } else {
                            +distribution.title
                        }
                    }
                }
            }
        }
        // From a desktop width, on the line of the settings
        div("wages-panel__wide") { compute() }
        settings.updatedAt?.let { updatedAt ->
            p("small text-secondary wages-panel__updated") {
                +listOfNotNull(
                    "Réglé ${formatRelative(updatedAt, now)}",
                    settings.updatedBy?.let { panel.alliance.pseudo(it) }?.let { "par $it" },
                ).joinToString(" ")
            }
        }
    }
}

// Abridged on a phone. From a desktop width, the announcement to copy closes the list
private fun FlowContent.alerts(panel: WagesPanel, history: WageHistory, now: Instant, announcement: Long?) = ul("wages-panel__alerts") {
    val alliance = panel.alliance
    li {
        val syncedAt = alliance.syncedAt
        val today = syncedAt != null && formatRelativeDay(syncedAt, now) == "aujourd'hui"
        if (syncedAt == null) {
            +"Aucun export joueurs Fourmizzz synchronisé"
        } else {
            +"✓ Export "
            span("whole") { +"joueurs " }
            +"Fourmizzz ${if (today) "de ${formatTime(syncedAt)}" else formatOfDayTime(syncedAt, now)}, ${plural(alliance.members.size, "membre")}"
        }
        // An export of the day needs nothing more
        if (!today) {
            +" · "
            a(href(Alliance())) { +"Synchroniser" }
        }
    }
    val settings = panel.settings
    val failedAt = settings.failedAt
    val latestComputedAt = history.bounds.latestComputedAt
    if (failedAt != null && settings.failure != null && (latestComputedAt == null || failedAt > latestComputedAt)) {
        li("wages-panel__alert--danger") {
            +"Le calcul automatique ${formatOfDayTime(failedAt, now)} a échoué : ${settings.failure.reason}."
        }
    }
    val stale = alliance.members.count { MemberFilter.STALE_LEVELS.matches(it, now) }
    if (stale > 0) {
        li {
            a(href(Alliance(filter = MemberFilter.STALE_LEVELS.key)), classes = "wages-panel__alert--warning") {
                span("abridged") { +"${plural(stale, "membre")} : niveaux de plus de 7 j ›" }
                span("whole") {
                    +(if (stale > 1) "$stale membres n'ont pas touché leurs niveaux depuis 7 j ›" else "1 membre n'a pas touché ses niveaux depuis 7 j ›")
                }
            }
        }
    }
    // What the latest computation added to the debts
    history.selected?.takeIf { history.selectedId == history.bounds.latestId }?.let { computation ->
        val day = if (formatRelativeDay(computation.computedAt, now) == "aujourd'hui") "aujourd'hui" else "au calcul du ${formatDay(computation.computedAt)}"
        computation.wages.filter { it.accumulates }.sortedBy { it.pseudo.lowercase() }.forEach { wage ->
            li {
                a(href(Alliance(filter = MemberFilter.ACCUMULATE.key))) {
                    +"${wage.pseudo} cumule $day"
                    span("whole") { +" : +${formatAmount(-wage.netWage)} de dette" }
                    +" ›"
                }
            }
        }
    }
    announcement?.let { li("wages-panel__wide") { announcement(it, "Copier l'annonce pour le forum", BtnSize.COMPACT) } }
}

// Confirmed on a page of its own: no script is needed
fun HTML.computePage(viewer: Viewer, settings: AllianceSettings, now: Instant, failure: ComputationFailure? = null) =
    layout("Calculer maintenant", Tab.WAGES, viewer) {
        h1("page-title") { +"Calculer maintenant ?" }
        failure?.let { empMessage(MessageVariant.DANGER) { +"Rien n'a été calculé : ${it.reason}." } }
        empCard(cssClass = "wages-compute") {
            ul("wages-compute__effects") {
                li { +"Il clôt la période en cours : la récolte depuis le dernier calcul est partagée, en répartition ${settings.distribution.label}." }
                li { +"Les envois non faits restent à faire." }
                li { +"Le calcul de ${settings.computationTime} partira de ce nouveau calcul (prochain : ${formatDue(settings.nextComputation(now), now)})." }
            }
            form(href(Wages.Compute()), method = FormMethod.post, classes = "wages-compute__actions") {
                empBtn(BtnVariant.PRIMARY) { +"Calculer maintenant" }
                empBtnLink(href(Wages()), BtnVariant.SUBTLE) { +"Annuler" }
            }
        }
    }

private fun Instant.gameDate() = toLocalDateTime(gameTimeZone).date.toString()

// Abridged on a phone, whole from a desktop width
private fun FlowContent.abridged(amount: Long, classes: String? = null) = span(classes) {
    if (abs(amount) < 1_000_000) {
        +formatAmount(amount)
    } else {
        span("abridged") { +formatMillions(amount) }
        span("whole") { +formatAmount(amount) }
    }
}

// Steps to the neighbour computations, keeping the full detail, or jumps to a date: the history is never listed whole
private fun FlowContent.selector(history: WageHistory, computation: WageComputation, detail: Boolean) {
    nav("emp-picker wages-picker") {
        attributes["aria-label"] = "Choisir un calcul"
        fun step(id: Long?, label: String, disabledLabel: String, icon: String) = step(id, detail, label, disabledLabel, icon)
        step(history.olderId, "Calcul précédent", "Pas de calcul plus ancien", PREVIOUS_ICON)
        details("wages-date") {
            summary("wages-date__button") {
                +formatDateTime(computation.computedAt, separator = " · ")
                if (history.selectedId == history.bounds.latestId) empBadge(BadgeVariant.NEUTRAL, "dernier")
            }
            div("emp-card wages-date__choice") { dateForm(history, computation.computedAt.gameDate()) }
        }
        step(history.newerId, "Calcul suivant", "Pas de calcul plus récent", NEXT_ICON)
    }
}

private fun FlowContent.step(id: Long?, detail: Boolean, label: String, disabledLabel: String, icon: String) {
    if (id != null) {
        a(href(Wages(id, detail = if (detail) 1 else null)), classes = "wages-picker__step") {
            attributes["aria-label"] = label
            unsafe { +icon }
        }
    } else {
        span("wages-picker__step") {
            attributes["aria-disabled"] = "true"
            attributes["aria-label"] = disabledLabel
            unsafe { +icon }
        }
    }
}

private fun FlowContent.dateForm(history: WageHistory, date: String?) {
    form(action = href(Wages()), method = FormMethod.get, classes = "wages-date__form") {
        label("small") {
            htmlFor = "date"
            +"Calcul du"
        }
        input(InputType.date, name = "date", classes = "emp-field emp-field--compact") {
            id = "date"
            required = true
            date?.let { value = it }
            history.bounds.firstComputedAt?.let { min = it.gameDate() }
            history.bounds.latestComputedAt?.let { max = it.gameDate() }
        }
        empBtn(BtnVariant.PRIMARY, BtnSize.COMPACT) { +"Voir" }
    }
}

// Three tiles on a phone: a member's what to send, a chief's what is left, the panel taking the place of the rest
private fun FlowContent.tiles(computation: WageComputation, chief: Boolean) {
    val toSend = computation.deliveries.sumOf { it.amount }
    val sent = computation.deliveries.sumOf { computation.covered(it) }
    val giversLeft = computation.deliveries.filter { computation.covered(it) < it.amount }.distinctBy { it.fromPlayerId }
    div(if (chief) "wages-tiles wages-tiles--chief" else "wages-tiles") {
        tile("Récolte", " de l'alliance") { abridged(computation.wages.sumOf { it.selfProduction }, "wages-tile__figure") }
        tile("À envoyer", " · ${plural(computation.deliveries.size, "envoi")}", "wages-tile--to-send") {
            abridged(toSend, "wages-tile__figure")
        }
        tile("Envoyé") {
            if (toSend == 0L) {
                span("wages-tile__figure") { +"—" }
            } else {
                // Rounded down: 100 % only once everything is sent
                span("wages-tile__figure") { +formatPercent(floor(sent * 100.0 / toSend) / 100) }
                empProgress(sent, toSend, "Envoyé", ProgressThickness.HAIRLINE)
            }
        }
        tile("Reste · ${giversLeft.size}", if (giversLeft.size > 1) " livreurs" else " livreur", "wages-tile--left") {
            abridged(toSend - sent, "wages-tile__figure")
        }
    }
}

// The end of the label is shown from a desktop width
private fun FlowContent.tile(label: String, wide: String? = null, cssClass: String? = null, figure: DIV.() -> Unit) =
    div(listOfNotNull("emp-tile", cssClass).joinToString(" ")) {
        span("caption text-secondary") {
            +label
            wide?.let { span("whole") { +it } }
        }
        figure()
    }

private fun FlowContent.filter() = div("wages-filter") {
    label("visually-hidden") {
        htmlFor = "filter"
        +"Filtrer par pseudo"
    }
    unsafe { +SEARCH_ICON }
    input(InputType.search, classes = "emp-field") {
        id = "filter"
        placeholder = "Filtrer par pseudo"
        attributes["autocomplete"] = "off"
    }
}

private fun FlowContent.sectionHeader(id: String, title: String, legend: String, extra: DIV.() -> Unit = {}) =
    div("wages-section__header") {
        h2("card-title") {
            this.id = id
            +title
        }
        span("small text-secondary") { +legend }
        extra()
    }

private fun FlowContent.noMatch() = p("wages-no-match text-secondary") {
    attributes["hidden"] = "hidden"
    +"Aucun pseudo ne correspond au filtre."
}

private class Giver(val playerId: Long, val pseudo: String, val deliveries: List<WageDelivery>, val sent: Long) {
    val total = deliveries.sumOf { it.amount }
    val left = total - sent
}

// A member who has something left to send first, then the largest amount left; those who sent everything folded
// below. With a computation id, each giver with something left gets a reminder to copy
private fun FlowContent.deliveries(computation: WageComputation, viewerId: Long?, chief: Boolean, remindersOf: Long?) {
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val givers = computation.deliveries.groupBy { it.fromPlayerId }.map { (playerId, deliveries) ->
        Giver(playerId, pseudos.getValue(playerId), deliveries.sortedByDescending { it.amount }, deliveries.sumOf { computation.covered(it) })
    }
    val first = givers.find { it.playerId == viewerId && it.left > 0 && !chief }
    val left = givers.filter { it.left > 0 }.sortedWith(
        compareByDescending<Giver> { it === first }.thenByDescending { it.left }.thenBy { it.pseudo.lowercase() }
    )
    val done = givers.filter { it.left == 0L }.sortedWith(compareByDescending<Giver> { it.total }.thenBy { it.pseudo.lowercase() })
    section("wages-section") {
        attributes["aria-labelledby"] = "deliveries"
        sectionHeader("deliveries", "Envois", if (first != null) "toi, puis le plus gros reste" else "le plus gros reste d'abord")
        if (givers.isEmpty()) {
            empMessage(MessageVariant.INFO) { +"Aucun envoi à faire." }
            return@section
        }
        if (left.isNotEmpty()) {
            div("givers") {
                left.forEach { giver(computation, pseudos, it, it.playerId == viewerId, remindersOf) }
            }
        }
        if (done.isNotEmpty()) {
            div("givers givers--done") {
                done.forEach { giver(computation, pseudos, it, it.playerId == viewerId, remindersOf) }
            }
        }
        noMatch()
    }
}

private fun FlowContent.giver(computation: WageComputation, pseudos: Map<Long, String>, giver: Giver, viewer: Boolean, remindersOf: Long?) {
    val name: FlowContent.() -> Unit = {
        span("player-name giver__pseudo") {
            +giver.pseudo
            if (viewer) empBadge(BadgeVariant.YOU, "toi")
        }
    }
    if (giver.left == 0L) {
        article("emp-card giver giver--done") {
            attributes["data-pseudo"] = giver.pseudo.lowercase()
            name()
            span("amount") { +formatAmount(giver.total) }
            empRowDone("✓ tout envoyé")
        }
        return
    }
    article(if (viewer) "emp-card giver giver--you" else "emp-card giver") {
        attributes["data-pseudo"] = giver.pseudo.lowercase()
        div("giver__header") {
            name()
            span("giver__left text-secondary") {
                +"reste "
                strong("amount") { +formatAmount(giver.left) }
            }
        }
        empProgress(giver.sent, giver.total, "Envoyé par ${giver.pseudo}", ProgressThickness.THIN)
        ul("giver__deliveries") {
            giver.deliveries.forEach { delivery ->
                val sent = computation.covered(delivery)
                val recipient = pseudos.getValue(delivery.toPlayerId)
                li(if (sent >= delivery.amount) "giver__delivery giver__delivery--done" else "giver__delivery") {
                    attributes["data-pseudo"] = recipient.lowercase()
                    val partial = sent > 0 && sent < delivery.amount
                    // What is left of a delivery partly sent: under the recipient on a phone, in the state column from a
                    // desktop width
                    span("giver__recipient") {
                        +"→ $recipient"
                        if (partial) span("giver__partial caption text-secondary") { +progressText(sent, delivery.amount)!! }
                    }
                    span("amount") { +formatAmount(delivery.amount) }
                    span("giver__state") {
                        if (sent >= delivery.amount) span("emp-row__done") {
                            +"✓"
                            span("giver__state-word") { +" envoyé" }
                        }
                        if (partial) span("giver__state-left small text-secondary") { +"reste ${formatAmount(delivery.amount - sent)}" }
                    }
                }
            }
        }
        remindersOf?.let { computationId ->
            form(href(Wages.Reminder(computationId = computationId, playerId = giver.playerId)), method = FormMethod.post, classes = "giver__reminder") {
                empCopyButton("Copier un rappel", BtnVariant.SECONDARY, BtnSize.COMPACT)
            }
        }
    }
}

// A giver who accumulates sends nothing and adds their balance to their debt
private val Wage.accumulates get() = netWage < 0 && settledAmount == 0L

private fun FlowContent.balances(
    computation: WageComputation,
    computationId: Long?,
    viewerId: Long?,
    chief: Boolean,
    detail: Boolean,
) = section("wages-section") {
    attributes["aria-labelledby"] = "balances"
    sectionHeader("balances", "Soldes", "+ reçoit · − envoie") {
        if (chief) detailToggle(computationId, detail)
    }
    div(if (detail) "emp-frame balances balances--detail" else "emp-frame balances") {
        table("emp-table") {
            thead {
                tr {
                    column("Joueur")
                    if (detail) {
                        listOf("TdC moyen", "Production propre", "Temps de ponte", "Part des besoins", "Part")
                            .forEach { column(it, number = true) }
                    }
                    column("Solde", number = true)
                    column("Dette", number = true)
                }
            }
            tbody {
                computation.wages.sortedBy { it.pseudo.lowercase() }.forEach { wage ->
                    val debt = computation.debts.getValue(wage.playerId)
                    tr(if (wage.playerId == viewerId) "emp-table__you" else null) {
                        attributes["data-pseudo"] = wage.pseudo.lowercase()
                        td {
                            +wage.pseudo
                            if (wage.playerId == viewerId) +" (toi)"
                            if (wage.accumulates) span("emp-badge badge-accumulates") { +"cumule" }
                        }
                        if (detail) {
                            td("number") { +formatAmount(wage.averageField.roundToLong()) }
                            td("number") { +formatAmount(wage.selfProduction) }
                            td("number") { +wage.layingTime.toString() }
                            td("number") { +formatPercent(computation.needShare(wage.playerId), decimals = 2) }
                            td("number") { +formatAmount(wage.grossWage) }
                        }
                        td(if (wage.netWage > 0) "number balance--positive" else "number") { +formatBalance(wage.netWage) }
                        if (debt > 0) td("number debt-amount") { abridged(debt) } else td("number text-secondary") { +"—" }
                    }
                }
            }
        }
    }
    noMatch()
}

private fun TR.column(title: String, number: Boolean = false) = th(classes = if (number) "number" else null) {
    attributes["scope"] = "col"
    +title
}

// Submitted as soon as it is ticked
private fun DIV.detailToggle(computationId: Long?, detail: Boolean) {
    form(action = "${href(Wages())}#balances", method = FormMethod.get, classes = "balances__toggle") {
        attributes["data-submit-on-change"] = ""
        computationId?.let { input(InputType.hidden, name = "id") { value = it.toString() } }
        label {
            input(InputType.checkBox, name = "detail") {
                value = "1"
                checked = detail
            }
            +"Détail complet"
            empBadge(BadgeVariant.LEADER, "Chef")
        }
    }
}

// The constants come from the calculation itself, the totals from the computation shown
private fun FlowContent.method(computation: WageComputation) = empDisclosure("Comment sont calculés les salaires ?", card = true, id = "method", cssClass = "method") {
    val toSend = computation.deliveries.sumOf { it.amount }
    div("emp-card method__content") {
        ol("method__steps") {
            methodStep(
                1,
                "La récolte",
                "${WOOD_PER_FIELD_PER_DAY.toInt()} bois par cm² de TdC et par jour, sur la moyenne des mesures horaires de la période.",
                "Alliance : ${formatAmount(computation.wages.sumOf { it.selfProduction })}",
            )
            methodStep(
                2,
                "Les colonies",
                "Une colonie reverse à son maître $COLONY_BASE_SHARE_PERCENT$NBSP% de sa production, " +
                    "plus $COLONY_SHARE_PERCENT_PER_APHID_FARM_LEVEL$NBSP% par niveau d'étable du maître.",
                if (computation.hasDetail) {
                    "Reversé aux maîtres : ${formatAmount(computation.wages.sumOf { maxOf(it.colonyTransfer ?: 0, 0) })}"
                } else {
                    DETAIL_UNAVAILABLE
                },
            )
            methodStep(
                3,
                "Les besoins",
                "Temps de ponte (couveuse + solarium + technique de ponte), rapporté au temps de recherche du prochain niveau de technique de ponte.",
                null,
            )
            methodStep(
                4,
                "Le partage",
                "La récolte est partagée au prorata des besoins : c'est la part. Solde = part − production propre.",
                "Soldes positifs : ${formatAmount(computation.wages.sumOf { maxOf(it.netWage, 0) })}",
            )
            methodStep(
                5,
                "Les envois",
                "Les soldes négatifs envoient aux positifs, les plus gros ensemble pour limiter les convois. " +
                    "Un livreur peut cumuler sa dette sur décision des chefs ; un receveur endetté rembourse en recevant moins.",
                "${plural(computation.deliveries.size, "envoi")} : ${formatAmount(toSend)}",
            )
        }
    }
}

private fun OL.methodStep(number: Int, title: String, text: String, total: String?) = li("method__step") {
    span("method__title") {
        span("emp-step") { +number.toString() }
        +title
    }
    span("text-secondary") { +text }
    total?.let { span("method__total") { +it } }
}

private const val PREVIOUS_ICON =
    """<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>"""

private const val NEXT_ICON =
    """<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>"""

private const val SEARCH_ICON =
    """<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>"""
