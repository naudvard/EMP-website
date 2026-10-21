package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDistribution
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe

private enum class Tab(val id: String, val label: String) {
    DELIVERIES("livraisons", "Livraisons"),
    SUMMARY("bilan", "Bilan"),
}

fun HTML.wagesPage(computation: WageComputation?) = layout("Salaires") {
    h1 { +"Salaires" }
    if (computation == null) {
        p("notice") { +"Aucun calcul de salaires pour l'instant." }
        return@layout
    }

    val distribution = when (computation.distribution) {
        WageDistribution.MINIMAL -> "minimale"
        WageDistribution.PROPORTIONAL -> "proportionnelle"
    }
    p("meta") {
        +"Calculé le ${formatDateTime(computation.computedAt)} · ${computation.hours} h de mesures sur 24 · répartition $distribution"
    }

    input(InputType.search) {
        id = "filter"
        placeholder = "Filtrer par pseudo"
        attributes["autocomplete"] = "off"
    }
    div("tabs") {
        attributes["role"] = "tablist"
        Tab.entries.forEach { tab ->
            button(type = ButtonType.button) {
                id = "tab-${tab.id}"
                attributes["role"] = "tab"
                attributes["aria-controls"] = tab.id
                attributes["aria-selected"] = (tab == Tab.DELIVERIES).toString()
                +tab.label
            }
        }
    }
    panel(Tab.DELIVERIES) { deliveries(computation) }
    panel(Tab.SUMMARY) { summary(computation) }
    script { unsafe { +SCRIPT } }
}

private fun FlowContent.panel(tab: Tab, content: FlowContent.() -> Unit) {
    section {
        id = tab.id
        attributes["role"] = "tabpanel"
        attributes["aria-labelledby"] = "tab-${tab.id}"
        if (tab != Tab.DELIVERIES) attributes["hidden"] = "hidden"
        content()
    }
}

private fun FlowContent.deliveries(computation: WageComputation) {
    if (computation.deliveries.isEmpty()) {
        p("notice") { +"Aucune livraison à faire." }
        return
    }
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val deliveries = computation.deliveries
        .map { Triple(pseudos.getValue(it.fromPlayerId), pseudos.getValue(it.toPlayerId), it.amount) }
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
            deliveries.forEach { (from, to, amount) ->
                tr {
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
document.getElementById('filter').addEventListener('input', event => {
    const query = event.target.value.trim().toLowerCase()
    for (const row of document.querySelectorAll('tbody tr')) {
        row.hidden = query !== '' && !row.textContent.toLowerCase().includes(query)
    }
})

const tabs = document.querySelectorAll('[role=tab]')
const select = id => {
    for (const tab of tabs) {
        const selected = tab.getAttribute('aria-controls') === id
        tab.setAttribute('aria-selected', selected)
        document.getElementById(tab.getAttribute('aria-controls')).hidden = !selected
    }
}
for (const tab of tabs) {
    tab.addEventListener('click', () => {
        const id = tab.getAttribute('aria-controls')
        select(id)
        history.replaceState(null, '', '#' + id)
    })
}
if (document.getElementById(location.hash.slice(1))?.getAttribute('role') === 'tabpanel') select(location.hash.slice(1))
"""
