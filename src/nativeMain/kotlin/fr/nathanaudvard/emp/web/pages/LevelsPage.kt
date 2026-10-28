package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.web.forms.LevelsForm
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select

fun HTML.levelsPage(
    action: String,
    members: List<AllianceMember>,
    values: LevelsForm,
    errors: Map<String, String> = emptyMap(),
    saved: String? = null,
) = layout("Mes niveaux", Tab.LEVELS) {
    h1 { +"Mes niveaux" }
    p("meta") { +"Ces niveaux servent au calcul des salaires de l'alliance." }
    if (saved != null) {
        p("notice success") { +"Niveau de $saved enregistré." }
    }
    if (members.isEmpty()) {
        p("notice error") { +"Aucun membre connu : l'export des joueurs n'a pas encore été synchronisé." }
    }
    form(action = action, method = FormMethod.post, classes = "card-form") {
        field(LevelsForm.PSEUDO, "Pseudo", errors) {
            input(InputType.text, name = LevelsForm.PSEUDO) {
                id = LevelsForm.PSEUDO
                value = values.pseudo
                placeholder = "Rechercher un pseudo"
                required = true
                attributes["list"] = "members"
                attributes["autocomplete"] = "off"
            }
            dataList {
                id = "members"
                members.forEach { option { value = it.pseudo } }
            }
        }
        // Side by side on wide screens: the element and its level go together
        div("field-row") {
            field(LevelsForm.KIND, "Élément", errors) {
                select {
                    id = LevelsForm.KIND
                    name = LevelsForm.KIND
                    required = true
                    // After a save, the pseudo is kept: the next level starts from the element
                    if (saved != null) autoFocus = true
                    option {
                        value = ""
                        +"Choisir…"
                    }
                    PlayerLevel.entries.forEach { kind ->
                        option {
                            value = kind.name
                            selected = kind.name == values.kind
                            +kind.label
                        }
                    }
                }
            }
            field(LevelsForm.LEVEL, "Niveau", errors, hint = "Entre 0 et ${PlayerLevel.MAX_LEVEL}") {
                input(InputType.number, name = LevelsForm.LEVEL) {
                    id = LevelsForm.LEVEL
                    value = values.level
                    min = "0"
                    max = PlayerLevel.MAX_LEVEL.toString()
                    step = "1"
                    attributes["inputmode"] = "numeric"
                    required = true
                }
            }
        }
        button(type = ButtonType.submit) { +"Enregistrer" }
    }
}

private fun FlowContent.field(
    name: String,
    text: String,
    errors: Map<String, String>,
    hint: String? = null,
    control: DIV.() -> Unit
) {
    div("field") {
        label {
            htmlFor = name
            +text
        }
        control()
        hint?.let { p("hint") { +it } }
        errors[name]?.let { p("field-error") { +it } }
    }
}
