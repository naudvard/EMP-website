package fr.nathanaudvard.emp.web.pages

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

fun HTML.layout(title: String, content: MAIN.() -> Unit) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title(title)
        style { unsafe { +STYLE } }
    }
    body {
        main {
            nav {
                a("/wages") { +"Salaires" }
                a("/update-levels") { +"Mes niveaux" }
            }
            content()
        }
    }
}

private val gameTimeZone = TimeZone.of("Europe/Paris")

fun formatDateTime(instant: Instant): String = with(instant.toLocalDateTime(gameTimeZone)) {
    fun Int.pad() = toString().padStart(2, '0')
    "${day.pad()}/${month.ordinal.plus(1).pad()}/$year à ${hour.pad()}:${minute.pad()}"
}

fun formatAmount(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(" ").reversed()

private const val STYLE = """
:root { color-scheme: light dark; font-family: system-ui, sans-serif; }
body { margin: 0; padding: 2rem 1rem; }
main { max-width: 40rem; margin: 0 auto; }
nav { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
form { max-width: 26rem; }
.field { display: flex; flex-direction: column; gap: .35rem; margin-bottom: 1rem; }
input, select, button { font: inherit; padding: .5rem .6rem; }
button { cursor: pointer; }
.notice { padding: .6rem .8rem; border-radius: .4rem; background: rgb(128 128 128 / .12); }
.success { background: rgb(46 160 67 / .15); }
.error { background: rgb(209 36 47 / .15); }
.field-error { margin: 0; font-size: .9rem; color: rgb(209 36 47); }
.meta { opacity: .7; }
table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
th, td { text-align: left; padding: .45rem .5rem; border-bottom: 1px solid rgb(128 128 128 / .3); }
.amount { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.tabs { display: flex; gap: .25rem; margin-top: 1rem; border-bottom: 1px solid rgb(128 128 128 / .3); }
.tabs button { border: none; border-bottom: 2px solid transparent; background: none; color: inherit; padding: .5rem .8rem; }
.tabs button[aria-selected=true] { border-bottom-color: currentColor; font-weight: 600; }
"""
