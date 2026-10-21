package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.LayingTimeFactor
import fr.nathanaudvard.emp.web.forms.LayingTimeForm
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

fun HTML.layingTimePage(
    action: String,
    members: List<AllianceMember>,
    values: LayingTimeForm,
    errors: Map<String, String> = emptyMap(),
    saved: String? = null,
) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("Mon TDP")
        style { unsafe { +STYLE } }
    }
    body {
        main {
            h1 { +"Mon TDP" }
            if (saved != null) {
                p("notice success") { +"TDP de $saved enregistré." }
            }
            if (members.isEmpty()) {
                p("notice error") { +"Aucun membre connu : l'export des joueurs n'a pas encore été synchronisé." }
            }
            form(action = action, method = FormMethod.post) {
                field(LayingTimeForm.PSEUDO, "Pseudo", errors) {
                    input(InputType.text, name = LayingTimeForm.PSEUDO) {
                        id = LayingTimeForm.PSEUDO
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
                field(LayingTimeForm.FACTOR, "Élément", errors) {
                    select {
                        id = LayingTimeForm.FACTOR
                        name = LayingTimeForm.FACTOR
                        required = true
                        option {
                            value = ""
                            +"Choisir…"
                        }
                        LayingTimeFactor.entries.forEach { factor ->
                            option {
                                value = factor.name
                                selected = factor.name == values.factor
                                +factor.label
                            }
                        }
                    }
                }
                field(LayingTimeForm.LEVEL, "Niveau", errors) {
                    input(InputType.number, name = LayingTimeForm.LEVEL) {
                        id = LayingTimeForm.LEVEL
                        value = values.level
                        min = "0"
                        step = "1"
                        required = true
                    }
                }
                button(type = ButtonType.submit) { +"Enregistrer" }
            }
        }
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

private const val STYLE = """
:root { color-scheme: light dark; font-family: system-ui, sans-serif; }
body { margin: 0; padding: 2rem 1rem; }
main { max-width: 26rem; margin: 0 auto; }
.field { display: flex; flex-direction: column; gap: .35rem; margin-bottom: 1rem; }
input, select, button { font: inherit; padding: .5rem .6rem; }
button { cursor: pointer; }
.notice { padding: .6rem .8rem; border-radius: .4rem; }
.success { background: rgb(46 160 67 / .15); }
.error { background: rgb(209 36 47 / .15); }
.field-error { margin: 0; font-size: .9rem; color: rgb(209 36 47); }
"""
