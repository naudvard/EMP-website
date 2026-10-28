package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.service.COLONY_BASE_SHARE_PERCENT
import fr.nathanaudvard.emp.service.COLONY_SHARE_PERCENT_PER_APHID_FARM_LEVEL
import fr.nathanaudvard.emp.service.WOOD_PER_FIELD_PER_DAY
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
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.noScript
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Instant

// The searched date is the one asked for in the URL, when no computation id is. The viewer comes first in the
// deliveries and is marked in the balances; the full detail is only ever rendered for a chief
fun HTML.wagesPage(
    history: WageHistory,
    searchedDate: String? = null,
    viewerId: Long? = null,
    chief: Boolean = false,
    detail: Boolean = false,
) = layout("Salaires", Tab.WAGES, legacyStyle = false) {
    val computation = history.selected
    if (history.bounds.latestId == null || computation == null) {
        h1("page-title") { +"Salaires" }
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
        selector(history, computation)
        p("wages-header__period text-secondary") {
            +"Récolte du ${formatDateTime(computation.since)} au ${formatDateTime(computation.computedAt)}"
            +" · ${plural(computation.hours, "mesure horaire", "mesures horaires")}"
            +" · répartition ${computation.distribution.label}"
        }
    }
    if (history.selectedId != history.bounds.latestId) {
        empMessage(MessageVariant.INFO, inline = true) {
            +"Ancien calcul ·"
            a(href(Wages())) { +"voir le dernier" }
        }
    }
    tiles(computation)
    filter()
    deliveries(computation, viewerId)
    balances(computation, history.selectedId, viewerId, chief, detail && chief)
    method(computation)
    script { unsafe { +SCRIPT } }
}

val WageDistribution.label
    get() = when (this) {
        WageDistribution.MINIMAL -> "minimale"
        WageDistribution.PROPORTIONAL -> "proportionnelle"
    }

private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count > 1) many else one}"

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

// Steps to the neighbour computations, or jumps to a date: the history is never listed whole
private fun FlowContent.selector(history: WageHistory, computation: WageComputation) {
    nav("emp-picker wages-picker") {
        attributes["aria-label"] = "Choisir un calcul"
        step(history.olderId, "Calcul précédent", "Pas de calcul plus ancien", PREVIOUS_ICON)
        details("wages-date") {
            summary("wages-date__button") {
                +formatDateTime(computation.computedAt, separator = " · ")
                if (history.selectedId == history.bounds.latestId) empBadge(BadgeVariant.SOLID, "dernier")
            }
            div("emp-card wages-date__choice") { dateForm(history, computation.computedAt.gameDate()) }
        }
        step(history.newerId, "Calcul suivant", "Pas de calcul plus récent", NEXT_ICON)
    }
}

private fun FlowContent.step(id: Long?, label: String, disabledLabel: String, icon: String) {
    if (id != null) {
        a(href(Wages(id)), classes = "wages-picker__step") {
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

// What the convoys sent towards a delivery cover, never more than the delivery
private fun WageComputation.covered(delivery: WageDelivery) = min(sent(delivery.fromPlayerId, delivery.toPlayerId), delivery.amount)

private fun FlowContent.tiles(computation: WageComputation) {
    val toSend = computation.deliveries.sumOf { it.amount }
    val sent = computation.deliveries.sumOf { computation.covered(it) }
    val giversLeft = computation.deliveries.filter { computation.covered(it) < it.amount }.distinctBy { it.fromPlayerId }
    div("wages-tiles") {
        tile("Récolte") { abridged(computation.wages.sumOf { it.selfProduction }, "wages-tile__figure") }
        tile("À envoyer · ${plural(computation.deliveries.size, "envoi")}") {
            abridged(toSend, "wages-tile__figure")
        }
        tile("Envoyé") {
            if (toSend == 0L) {
                span("wages-tile__figure") { +"—" }
            } else {
                // Rounded down: 100 % only once everything is sent
                span("wages-tile__figure") { +formatPercent(floor(sent * 100.0 / toSend) / 100) }
                empProgress(sent, toSend, "Envoyé", ProgressThickness.THIN)
            }
        }
        tile("Reste · ${plural(giversLeft.size, "livreur")}") { abridged(toSend - sent, "wages-tile__figure") }
    }
}

private fun FlowContent.tile(label: String, figure: DIV.() -> Unit) = div("emp-tile") {
    span("caption text-secondary") { +label }
    figure()
}

// Hidden until the script shows it: the rest of the page needs no JavaScript
private fun FlowContent.filter() = div("wages-filter") {
    attributes["hidden"] = "hidden"
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

// The viewer first, then the largest amount left; those who sent everything folded at the bottom
private fun FlowContent.deliveries(computation: WageComputation, viewerId: Long?) {
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val givers = computation.deliveries.groupBy { it.fromPlayerId }.map { (playerId, deliveries) ->
        Giver(playerId, pseudos.getValue(playerId), deliveries.sortedByDescending { it.amount }, deliveries.sumOf { computation.covered(it) })
    }.sortedWith(
        compareByDescending<Giver> { it.playerId == viewerId }
            .thenBy { it.left == 0L }
            .thenByDescending { if (it.left == 0L) it.total else it.left }
            .thenBy { it.pseudo.lowercase() }
    )
    section("wages-section") {
        attributes["aria-labelledby"] = "deliveries"
        sectionHeader(
            "deliveries",
            "Envois",
            if (givers.any { it.playerId == viewerId }) "toi, puis le plus gros reste" else "le plus gros reste d'abord",
        )
        if (givers.isEmpty()) {
            empMessage(MessageVariant.INFO) { +"Aucun envoi à faire." }
            return@section
        }
        div("givers") {
            givers.forEach { giver(computation, pseudos, it, it.playerId == viewerId) }
        }
        noMatch()
    }
}

private fun FlowContent.giver(computation: WageComputation, pseudos: Map<Long, String>, giver: Giver, viewer: Boolean) {
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
                    span("giver__recipient") {
                        +"→ $recipient"
                        if (sent > 0 && sent < delivery.amount) span("caption text-secondary") { +progressText(sent, delivery.amount)!! }
                    }
                    span("amount") { +formatAmount(delivery.amount) }
                    span("giver__state") {
                        if (sent >= delivery.amount) span("emp-row__done") {
                            +"✓"
                            span("giver__state-word") { +" envoyé" }
                        }
                    }
                }
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

// Submitted as soon as it is ticked, with a button for when scripts do not run
private fun DIV.detailToggle(computationId: Long?, detail: Boolean) {
    form(action = "${href(Wages())}#balances", method = FormMethod.get, classes = "balances__toggle") {
        computationId?.let { input(InputType.hidden, name = "id") { value = it.toString() } }
        label {
            input(InputType.checkBox, name = "detail") {
                value = "1"
                checked = detail
                attributes["onchange"] = "this.form.submit()"
            }
            +"Détail complet"
            empBadge(BadgeVariant.LEADER, "Chef")
        }
        noScript { empBtn(BtnVariant.SUBTLE, BtnSize.SMALL) { +"Afficher" } }
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
                    null
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

private const val SCRIPT = """
const filter = document.getElementById('filter')
filter.parentElement.hidden = false
const matches = (element, query) => element.dataset.pseudo.includes(query)
filter.addEventListener('input', () => {
    const query = filter.value.trim().toLowerCase()
    // A giver's card shows all their deliveries when the giver matches, only the matching recipients otherwise
    for (const card of document.querySelectorAll('.giver')) {
        const giverMatches = matches(card, query)
        const lines = card.querySelectorAll('.giver__delivery')
        for (const line of lines) line.hidden = !giverMatches && !matches(line, query)
        card.hidden = !giverMatches && [...lines].every(line => line.hidden)
    }
    for (const row of document.querySelectorAll('.balances tbody tr')) row.hidden = !matches(row, query)
    for (const section of document.querySelectorAll('.wages-section')) {
        const items = [...section.querySelectorAll('.giver, .balances tbody tr')]
        const empty = items.length > 0 && items.every(item => item.hidden)
        const none = section.querySelector('.wages-no-match')
        if (none) none.hidden = !empty
        const table = section.querySelector('.balances')
        if (table) table.hidden = empty
    }
})
filter.addEventListener('keydown', event => {
    if (event.key === 'Escape' && filter.value !== '') {
        filter.value = ''
        filter.dispatchEvent(new Event('input'))
    }
})

// A folded card on a phone, an open one from a desktop width
if (matchMedia('(min-width: 48rem)').matches) document.getElementById('method').open = true
"""
