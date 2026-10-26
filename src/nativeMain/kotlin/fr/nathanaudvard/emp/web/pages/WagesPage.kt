package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDistribution
import fr.nathanaudvard.emp.domain.model.WageHistory
import fr.nathanaudvard.emp.web.resources.Wages
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlin.time.Instant

private enum class Tab(val id: String, val label: String) {
    DELIVERIES("livraisons", "Livraisons"),
    SUMMARY("bilan", "Bilan"),
}

// The searched date is the one asked for in the URL, when no computation id is
fun HTML.wagesPage(history: WageHistory, searchedDate: String? = null) = layout("Salaires") {
    h1 { +"Salaires" }
    val computation = history.selected
    when {
        history.bounds.latestId == null -> {
            p("notice") { +"Aucun calcul de salaires pour l'instant." }
            return@layout
        }

        computation == null -> {
            p("notice error") {
                +(if (searchedDate != null) "Aucun calcul à cette date ou avant. " else "Ce calcul n'existe pas. ")
                a(href(Wages())) { +"Voir le dernier calcul" }
            }
            navigator(history, searchedDate)
            return@layout
        }

        history.selectedId != history.bounds.latestId -> p("notice warning") {
            +"Ancien calcul. "
            a(href(Wages())) { +"Voir le dernier calcul" }
        }
    }

    p("meta") {
        +"Calculé le ${formatDateTime(computation.computedAt)} · ${computation.hours} h de mesures sur 24 · répartition ${computation.distribution.label}"
    }
    navigator(history, computation.computedAt.gameDate())

    div("toolbar") {
        div("tabs") {
            attributes["role"] = "tablist"
            Tab.entries.forEach { tab ->
                button(type = ButtonType.button) {
                    id = "tab-${tab.id}"
                    attributes["role"] = "tab"
                    attributes["aria-controls"] = tab.id
                    attributes["aria-selected"] = (tab == Tab.DELIVERIES).toString()
                    if (tab != Tab.DELIVERIES) attributes["tabindex"] = "-1"
                    +tab.label
                }
            }
        }
        input(InputType.search) {
            id = "filter"
            placeholder = "Filtrer par pseudo"
            attributes["aria-label"] = "Filtrer par pseudo"
            attributes["autocomplete"] = "off"
        }
    }
    panel(Tab.DELIVERIES) { deliveries(computation) }
    panel(Tab.SUMMARY) { summary(computation) }
    script { unsafe { +SCRIPT } }
}

val WageDistribution.label
    get() = when (this) {
        WageDistribution.MINIMAL -> "minimale"
        WageDistribution.PROPORTIONAL -> "proportionnelle"
    }

private fun Instant.gameDate() = toLocalDateTime(gameTimeZone).date.toString()

// Steps to the neighbour computations, or jumps to a date: the history is never listed whole
private fun FlowContent.navigator(history: WageHistory, date: String?) {
    nav("pager") {
        attributes["aria-label"] = "Historique des calculs"
        step(history.olderId, "← Plus ancien")
        form(action = href(Wages()), method = FormMethod.get, classes = "jump") {
            label {
                htmlFor = "date"
                +"Calcul du"
            }
            input(InputType.date, name = "date") {
                id = "date"
                required = true
                date?.let { value = it }
                history.bounds.firstComputedAt?.let { min = it.gameDate() }
                history.bounds.latestComputedAt?.let { max = it.gameDate() }
            }
            button(type = ButtonType.submit) { +"Voir" }
        }
        step(history.newerId, "Plus récent →")
    }
}

private fun FlowContent.step(id: Long?, text: String) {
    if (id != null) {
        a(href(Wages(id)), classes = "step") { +text }
    } else {
        span("step") {
            attributes["aria-disabled"] = "true"
            +text
        }
    }
}

private fun FlowContent.panel(tab: Tab, content: FlowContent.() -> Unit) {
    section {
        id = tab.id
        attributes["role"] = "tabpanel"
        attributes["aria-labelledby"] = "tab-${tab.id}"
        if (tab != Tab.DELIVERIES) attributes["hidden"] = "hidden"
        content()
        p("notice no-match") {
            attributes["hidden"] = "hidden"
            +"Aucun pseudo ne correspond au filtre."
        }
    }
}

private fun FlowContent.deliveries(computation: WageComputation) {
    if (computation.deliveries.isEmpty()) {
        p("notice") { +"Aucune livraison à faire." }
        return
    }
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val deliveries = computation.deliveries
        .map {
            Triple(
                pseudos.getValue(it.fromPlayerId),
                pseudos.getValue(it.toPlayerId),
                it.amount
            )
        }
        .sortedWith(compareBy<Triple<String, String, Long>> { it.first.lowercase() }.thenByDescending { it.third })

    table {
        thead {
            tr {
                th { +"Donne" }
                th { +"Reçoit" }
                th(classes = "amount") { +"Bois" }
            }
        }
        tbody {
            deliveries.forEachIndexed { index, (from, to, amount) ->
                // Deliveries are sorted by giver: each giver's first one starts a group
                tr(if (index == 0 || deliveries[index - 1].first != from) "group-start" else null) {
                    td { +from }
                    td { +to }
                    td("amount") { +formatAmount(amount) }
                }
            }
        }
    }
}

private fun FlowContent.summary(computation: WageComputation) {
    table {
        thead {
            tr {
                th { +"Pseudo" }
                th(classes = "amount") { +"Reçoit" }
                th(classes = "amount") { +"Donne" }
            }
        }
        tbody {
            computation.wages.sortedBy { it.pseudo.lowercase() }.forEach { wage ->
                tr {
                    td { +wage.pseudo }
                    td("amount") { +(if (wage.netWage > 0) formatAmount(wage.netWage) else "—") }
                    td("amount") { +(if (wage.netWage < 0) formatAmount(-wage.netWage) else "—") }
                }
            }
        }
    }
}

private const val SCRIPT = """
const filter = document.getElementById('filter')
filter.addEventListener('input', () => {
    const query = filter.value.trim().toLowerCase()
    for (const panel of document.querySelectorAll('[role=tabpanel]')) {
        const rows = panel.querySelectorAll('tbody tr')
        for (const row of rows) {
            row.hidden = query !== '' && !row.textContent.toLowerCase().includes(query)
        }
        const empty = rows.length > 0 && [...rows].every(row => row.hidden)
        panel.querySelector('.no-match').hidden = !empty
        const table = panel.querySelector('table')
        if (table) table.hidden = empty
    }
})
filter.addEventListener('keydown', event => {
    if (event.key === 'Escape' && filter.value !== '') {
        filter.value = ''
        filter.dispatchEvent(new Event('input'))
    }
})

const tabs = document.querySelectorAll('[role=tab]')
const select = id => {
    for (const tab of tabs) {
        const selected = tab.getAttribute('aria-controls') === id
        tab.setAttribute('aria-selected', selected)
        tab.tabIndex = selected ? 0 : -1
        document.getElementById(tab.getAttribute('aria-controls')).hidden = !selected
    }
}
for (const tab of tabs) {
    tab.addEventListener('click', () => {
        const id = tab.getAttribute('aria-controls')
        select(id)
        history.replaceState(null, '', '#' + id)
    })
    // Arrow keys move between tabs, as in a native tab list
    tab.addEventListener('keydown', event => {
        const index = [...tabs].indexOf(tab)
        const next = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: tabs.length - 1 }[event.key]
        if (next === undefined) return
        event.preventDefault()
        const target = tabs[(next + tabs.length) % tabs.length]
        target.focus()
        target.click()
    })
}
if (document.getElementById(location.hash.slice(1))?.getAttribute('role') === 'tabpanel') select(location.hash.slice(1))
"""
