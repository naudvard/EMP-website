package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.web.forms.ConvoyForm
import fr.nathanaudvard.emp.web.resources.MyDeliveries
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.b
import kotlinx.html.button
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.textArea
import kotlinx.html.ul
import kotlinx.html.unsafe

// Only the latest computation: a player wants to know what is left to do, and the convoys they launched towards it.
// The form and its error are those of convoys that could not be recorded, recorded the number of those just recorded
fun HTML.myDeliveriesPage(
    latest: WageComputation?,
    pseudo: String?,
    form: ConvoyForm = ConvoyForm(),
    error: String? = null,
    recorded: Int? = null,
) = layout("Mes livraisons") {
    h1 { +"Mes livraisons" }
    if (latest == null) {
        p("notice") { +"Aucun calcul de salaires pour l'instant." }
        return@layout
    }
    p("meta") { +"D'après le dernier calcul, le ${formatDateTime(latest.computedAt)}." }

    form(action = href(MyDeliveries()), method = FormMethod.get, classes = "who") {
        label {
            htmlFor = "pseudo"
            +"Je suis"
        }
        input(InputType.text, name = "pseudo") {
            id = "pseudo"
            pseudo?.let { value = it }
            placeholder = "Ton pseudo"
            required = true
            attributes["list"] = "players"
            attributes["autocomplete"] = "off"
        }
        dataList {
            id = "players"
            latest.wages.sortedBy { it.pseudo.lowercase() }.forEach { option { value = it.pseudo } }
        }
        button(type = ButtonType.submit) { +"Voir" }
    }

    if (pseudo == null) {
        // The player chosen last time is shown directly
        script { unsafe { +SCRIPT } }
        return@layout
    }
    val wage = latest.wage(pseudo)
    if (wage == null) {
        p("notice error") { +"$pseudo n'est pas dans le dernier calcul." }
    } else {
        mine(latest, wage)
        convoys(latest, wage, form, error, recorded)
        script { unsafe { +"try { localStorage.setItem('pseudo', ${jsString(wage.pseudo)}) } catch {}" } }
    }
}

private fun FlowContent.mine(computation: WageComputation, wage: Wage) {
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val sent = computation.deliveries.filter { it.fromPlayerId == wage.playerId }.sortedByDescending { it.amount }
    val received = computation.deliveries.filter { it.toPlayerId == wage.playerId }.sortedByDescending { it.amount }
    val debt = computation.debts.getValue(wage.playerId)

    section("mine") {
        if (sent.isEmpty() && received.isEmpty()) {
            p("headline") { +"Rien à envoyer ni à recevoir." }
        }
        if (sent.isNotEmpty()) {
            transfers(computation, "Tu envoies", "à", "give", "→", sent.map { pseudos.getValue(it.toPlayerId) to it })
        }
        if (received.isNotEmpty()) {
            transfers(computation, "Tu reçois", "de", "receive", "←", received.map { pseudos.getValue(it.fromPlayerId) to it })
        }
        div("mine-foot") {
            span {
                +"Solde du calcul : "
                b(if (wage.netWage >= 0) "receive" else "give") { +formatBalance(wage.netWage) }
            }
            span {
                +"Dette restante : "
                b { +(if (debt > 0) formatAmount(debt) else "—") }
            }
        }
    }
}

private fun FlowContent.transfers(
    computation: WageComputation,
    verb: String,
    preposition: String,
    side: String,
    arrow: String,
    deliveries: List<Pair<String, WageDelivery>>,
) {
    val players = if (deliveries.size > 1) "${deliveries.size} joueurs" else "1 joueur"
    val sent = deliveries.associate { (_, delivery) -> delivery to computation.sent(delivery.fromPlayerId, delivery.toPlayerId) }
    p("headline") {
        +verb
        strong("amount $side") { +"${formatAmount(deliveries.sumOf { it.second.amount })} bois" }
        +"$preposition $players"
        deliveryProgress(sent.values.sum(), deliveries.sumOf { it.second.amount })
    }
    ul {
        deliveries.forEach { (other, delivery) ->
            li(if (sent.getValue(delivery) >= delivery.amount) "done" else null) {
                span("pseudo") { +"$arrow $other" }
                span("figures") {
                    span("amount $side") { +formatAmount(delivery.amount) }
                    deliveryProgress(sent.getValue(delivery), delivery.amount)
                }
            }
        }
    }
}

// Id of the convoys section, where the page is opened once convoys are recorded or removed
const val CONVOYS = "convoys"

// The convoys the player launched, the latest first, kept as they were recorded until the player removes one
private fun FlowContent.convoys(
    computation: WageComputation,
    wage: Wage,
    form: ConvoyForm,
    error: String?,
    recorded: Int?,
) {
    val pseudos = computation.wages.associate { it.playerId to it.pseudo }
    val convoys = computation.convoys.filter { it.fromPlayerId == wage.playerId }.reversed()
    section("convoys") {
        id = CONVOYS
        h2 { +"Mes convois" }
        recorded?.takeIf { it > 0 }?.let {
            p("notice success") { +(if (it > 1) "$it convois enregistrés." else "Convoi enregistré.") }
        }
        error?.let { p("notice error") { +it } }
        form(action = href(MyDeliveries(wage.pseudo)), method = FormMethod.post, classes = "convoy-form") {
            label {
                htmlFor = "message"
                +"Message du jeu après l'envoi"
            }
            textArea(rows = "3") {
                id = "message"
                name = ConvoyForm.MESSAGE
                required = true
                placeholder = "Vous allez livrer 0 nourritures et 200 000 materiaux à Alcaz dans 10H 46m 4s - Retour le 27 oct. à 08h39 - Annuler"
                +form.message
            }
            p("hint") { +"Plusieurs lignes peuvent être collées à la fois. Chaque convoi s'ajoute aux précédents." }
            button(type = ButtonType.submit) { +"Enregistrer" }
        }
        if (convoys.isNotEmpty()) {
            ul("convoy-list") {
                convoys.forEach { convoy ->
                    val recipient = pseudos.getValue(convoy.toPlayerId)
                    li {
                        span("pseudo") { +"→ $recipient" }
                        span("amount give") { +formatAmount(convoy.amount) }
                        span("when") { +formatDateTime(convoy.sentAt) }
                        form(
                            action = href(MyDeliveries.DeleteConvoy(MyDeliveries(wage.pseudo), convoy.id)),
                            method = FormMethod.post,
                        ) {
                            button(type = ButtonType.submit) {
                                attributes["aria-label"] = "Supprimer le convoi de ${formatAmount(convoy.amount)} à $recipient"
                                +"Supprimer"
                            }
                        }
                    }
                }
            }
        }
    }
}

// A JavaScript string literal of the text: JSON escapes are valid in it, and "</" cannot close the script
private fun jsString(text: String) = buildString {
    append('"')
    text.forEach { char ->
        when {
            char == '"' || char == '\\' || char == '/' -> append('\\').append(char)
            char < ' ' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private const val SCRIPT = """
try {
    const pseudo = localStorage.getItem('pseudo')
    if (pseudo) location.replace('?pseudo=' + encodeURIComponent(pseudo))
} catch {}
"""
