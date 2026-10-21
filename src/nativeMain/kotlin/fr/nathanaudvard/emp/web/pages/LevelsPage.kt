package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.web.forms.LevelsForm
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
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
) = layout("Mes niveaux") {
    h1 { +"Mes niveaux" }
    if (saved != null) {
        p("notice success") { +"Niveau de $saved enregistré." }
    }
    if (members.isEmpty()) {
        p("notice error") { +"Aucun membre connu : l'export des joueurs n'a pas encore été synchronisé." }
    }
    form(action = action, method = FormMethod.post) {
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
        field(LevelsForm.KIND, "Élément", errors) {
            select {
                id = LevelsForm.KIND
                name = LevelsForm.KIND
                required = true
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
        field(LevelsForm.LEVEL, "Niveau", errors) {
            input(InputType.number, name = LevelsForm.LEVEL) {
                id = LevelsForm.LEVEL
                value = values.level
                min = "0"
                step = "1"
                required = true
            }
        }
        button(type = ButtonType.submit) { +"Enregistrer" }
    }
}

private fun FORM.field(name: String, text: String, errors: Map<String, String>, control: DIV.() -> Unit) {
    div("field") {
        label {
            htmlFor = name
            +text
        }
        control()
        errors[name]?.let { p("field-error") { +it } }
    }
}
