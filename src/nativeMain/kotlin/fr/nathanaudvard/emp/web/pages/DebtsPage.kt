package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.WageSettlement
import fr.nathanaudvard.emp.web.forms.DebtsForm
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun HTML.debtsPage(
    action: String,
    members: List<AllianceMember>,
    settlements: Map<Long, WageSettlement>,
    debts: Map<Long, Long>,
    error: String? = null,
    saved: Boolean = false,
) = layout("Dettes") {
    h1 { +"Dettes" }
    p("meta") { +"Pour chaque joueur, choisis s'il livre ce qu'il doit ou s'il le cumule en dette." }
    if (saved) {
        p("notice success") { +"Choix enregistrés." }
    }
    error?.let { p("notice error") { +it } }
    if (members.isEmpty()) {
        p("notice error") { +"Aucun membre connu : l'export des joueurs n'a pas encore été synchronisé." }
        return@layout
    }
    form(action = action, method = FormMethod.post, classes = "settlements") {
        table {
            thead {
                tr {
                    th { +"Rang" }
                    th { +"Joueur" }
                    th { +"Salaire dû" }
                    th(classes = "amount") { +"Dette" }
                }
            }
            tbody {
                members.forEach { member ->
                    val current = settlements[member.id] ?: WageSettlement.DELIVER
                    tr {
                        td {
                            val rank = member.chainRank
                            if (rank == null) span("rank none") { +"—" } else span("rank") { +rank }
                        }
                        td { +member.pseudo }
                        td {
                            div("choice") {
                                attributes["role"] = "radiogroup"
                                attributes["aria-label"] = "Salaire dû par ${member.pseudo}"
                                WageSettlement.entries.forEach { settlement ->
                                    label {
                                        input(InputType.radio, name = DebtsForm.fieldName(member)) {
                                            value = settlement.name
                                            checked = settlement == current
                                        }
                                        span { +settlement.label }
                                    }
                                }
                            }
                        }
                        val debt = debts[member.id] ?: 0
                        td("amount") { +(if (debt > 0) formatAmount(debt) else "—") }
                    }
                }
            }
        }
        button(type = ButtonType.submit) { +"Enregistrer" }
    }
}

val WageSettlement.label
    get() = when (this) {
        WageSettlement.DELIVER -> "Livrer"
        WageSettlement.ACCUMULATE_DEBT -> "Cumuler la dette"
    }
