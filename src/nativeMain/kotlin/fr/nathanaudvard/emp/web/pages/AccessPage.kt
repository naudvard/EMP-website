package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.SignIn
import fr.nathanaudvard.emp.service.DEVICE_CODE_DURATION
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.AccessNotice
import fr.nathanaudvard.emp.web.resources.Home
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.OL
import kotlinx.html.SPAN
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong

const val DEVICE_CODE_FIELD = "code"

// The screen without a session: no tab leads anywhere from it. The notice says why the player is on it, the error
// why the code typed opened nothing
fun HTML.accessPage(
    allianceTag: String,
    chiefs: List<String>,
    notice: String? = null,
    code: String = "",
    codeError: String? = null,
) = layout("Accès membre", tab = null, legacyStyle = false, navigation = false) {
    div("access") {
        div("access__intro") {
            h1("page-title") { +"Accès membre" }
            p("access__lead text-secondary") {
                +"Les salaires de l'alliance $allianceTag, tes livraisons et tes niveaux. Une seule connexion par téléphone, avec le lien personnel reçu dans Fourmizzz."
            }
        }
        notice?.let { empMessage(MessageVariant.DANGER) { +it } }
        ol("emp-card access__steps") {
            step(1) { +"Dans Fourmizzz, ouvre ta messagerie." }
            step(2) {
                +"Ouvre le message "
                strong { +"« $ACCESS_MESSAGE_SUBJECT »" }
                +" envoyé par un chef."
            }
            step(3) { +"Touche le lien. Ce navigateur restera reconnu, et « Forum externe » t'amènera directement ici." }
        }
        form(action = href(Access.Code()), method = FormMethod.post, classes = "emp-card access__code") {
            label {
                htmlFor = DEVICE_CODE_FIELD
                +"Déjà connecté sur un autre appareil ?"
            }
            p("access__hint text-secondary") {
                +"Sur l'appareil déjà connecté, touche « Connecter un autre appareil » sur l'accueil, puis saisis le code affiché."
            }
            div("access__code-row") {
                input(InputType.text, name = DEVICE_CODE_FIELD, classes = "emp-field access__code-field") {
                    id = DEVICE_CODE_FIELD
                    value = code
                    placeholder = "Code à 6 caractères"
                    required = true
                    attributes["autocomplete"] = "one-time-code"
                    attributes["autocapitalize"] = "characters"
                    attributes["spellcheck"] = "false"
                    if (codeError != null) {
                        attributes["aria-invalid"] = "true"
                        attributes["aria-describedby"] = "code-error"
                    }
                }
                empBtn(BtnVariant.PRIMARY) { +"Valider" }
            }
            codeError?.let {
                empMessage(MessageVariant.DANGER) {
                    id = "code-error"
                    +it
                }
            }
        }
        p("access__chiefs text-secondary") {
            +"Pas de message ni d'autre appareil ? "
            if (chiefs.isEmpty()) {
                +"Écris à un chef de l'alliance dans Fourmizzz."
            } else {
                +"Écris à un chef dans Fourmizzz : "
                chiefs.forEachIndexed { index, chief ->
                    if (index > 0) +(if (index == chiefs.lastIndex) " ou " else ", ")
                    strong { +chief }
                }
                +"."
            }
        }
    }
}

private fun OL.step(number: Int, text: SPAN.() -> Unit) = li("access__step") {
    span("emp-step emp-step--large") {
        attributes["aria-hidden"] = "true"
        +number.toString()
    }
    span(block = text)
}

fun AccessNotice.message(allianceTag: String) = when (this) {
    AccessNotice.LEFT -> leftMessage(allianceTag)
}

private fun leftMessage(allianceTag: String) =
    "Tu ne fais plus partie de l'alliance $allianceTag d'après le dernier export du jeu : les pages te sont fermées."

// Null once a session is opened
fun SignIn.message(allianceTag: String): String? = when (this) {
    is SignIn.Opened -> null
    SignIn.InvalidLink -> "Ce lien n'ouvre pas d'accès : il a été remplacé, ou mal copié. Demande un nouveau message « $ACCESS_MESSAGE_SUBJECT » à un chef."
    SignIn.Left -> leftMessage(allianceTag)
    SignIn.UnknownCode -> "Code inconnu : vérifie les 6 caractères affichés sur l'autre appareil."
    SignIn.ExpiredCode -> "Code expiré ou déjà utilisé. Affiche un nouveau code sur l'autre appareil."
    SignIn.TooManyAttempts -> "Trop d'essais. Réessaie dans quelques minutes."
}

// "K7Q-4MX": read and typed in two halves
fun DeviceCode.display() = code.chunked(3).joinToString("-")

fun HTML.devicePage(code: DeviceCode) = layout("Connecter un autre appareil", tab = null, legacyStyle = false) {
    div("access") {
        h1("page-title") { +"Connecter un autre appareil" }
        empCard(cssClass = "device-code") {
            span("text-secondary") { +"Saisis ce code sur l'autre appareil" }
            strong("device-code__value") { +code.display() }
            span("text-secondary") { +"Valable ${DEVICE_CODE_DURATION.inWholeMinutes} minutes, une seule fois." }
        }
        p("text-secondary") { +"Sur l'autre appareil, ouvre le site : l'écran Accès demande ce code." }
        a(href(Home())) { +"Retour à l'accueil" }
    }
}
