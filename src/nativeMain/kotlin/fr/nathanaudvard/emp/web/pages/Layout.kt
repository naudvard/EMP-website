package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.web.resources.Home
import fr.nathanaudvard.emp.web.resources.Levels
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.lang
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlin.time.Instant

fun HTML.layout(title: String, content: MAIN.() -> Unit) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("$title · EMP")
        style { unsafe { +STYLE } }
    }
    body {
        header {
            nav {
                // The page whose label is the title is the current one
                listOf(href(Home()) to "Accueil", href(Wages()) to "Salaires", href(Levels()) to "Mes niveaux").forEach { (link, label) ->
                    a(link) {
                        if (label == title) attributes["aria-current"] = "page"
                        +label
                    }
                }
            }
        }
        main { content() }
    }
}

// Pages have no call: the format is the Resources plugin's, which is installed with its defaults
@PublishedApi
internal val resourcesFormat = ResourcesFormat()

inline fun <reified T : Any> href(resource: T): String = io.ktor.resources.href(resourcesFormat, resource)

val gameTimeZone = TimeZone.of("Europe/Paris")

fun formatDateTime(instant: Instant): String = with(instant.toLocalDateTime(gameTimeZone)) {
    fun Int.pad() = toString().padStart(2, '0')
    "${day.pad()}/${month.ordinal.plus(1).pad()}/$year à ${hour.pad()}:${minute.pad()}"
}

fun formatAmount(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(" ").reversed()

private const val STYLE = """
:root {
    color-scheme: light dark;
    font-family: system-ui, sans-serif;
    line-height: 1.45;
    --bg: #f5f6f8;
    --surface: #fff;
    --text: #1c2025;
    --muted: #5d6773;
    --border: #dfe3e8;
    --border-strong: #c3c9d1;
    --accent: #2e6b4f;
    --accent-hover: #255a42;
    --on-accent: #fff;
    --focus: #3b82f6;
    --receive: #1a7f37;
    --give: #b4530b;
    --success-bg: #e6f4ea;
    --warning-bg: #fdf3dd;
    --error-bg: #fdeaea;
    --error: #c42b2b;
}
@media (prefers-color-scheme: dark) {
    :root {
        --bg: #111418;
        --surface: #1a1e24;
        --text: #e5e8ec;
        --muted: #9aa4b0;
        --border: #2b3139;
        --border-strong: #3d4550;
        --accent: #5cb98a;
        --accent-hover: #74c89c;
        --on-accent: #0f1a14;
        --focus: #7aa7ff;
        --receive: #5cc27e;
        --give: #f0a05a;
        --success-bg: #183324;
        --warning-bg: #3a2f14;
        --error-bg: #3d1b1b;
        --error: #ff8a80;
    }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); }
header { background: var(--surface); border-bottom: 1px solid var(--border); }
nav, main { max-width: 44rem; margin: 0 auto; padding: 0 1rem; }
nav { display: flex; gap: .25rem; padding-block: .6rem; }
nav a { padding: .4rem .75rem; border-radius: .5rem; color: var(--muted); text-decoration: none; font-weight: 500; }
nav a:hover { background: var(--bg); color: var(--text); }
nav a[aria-current=page] { background: var(--bg); color: var(--text); font-weight: 600; }
main { padding-block: 1.5rem 3rem; }
h1 { margin: 0 0 .35rem; font-size: 1.6rem; }
a { color: var(--accent); }
:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.meta { margin: 0 0 1rem; color: var(--muted); font-size: .92rem; }
.notice { margin: 0 0 1rem; padding: .7rem .9rem; border-radius: .5rem; background: var(--surface); border: 1px solid var(--border); }
.success { background: var(--success-bg); border-color: transparent; }
.warning { background: var(--warning-bg); border-color: transparent; }
.error { background: var(--error-bg); border-color: transparent; }

.card-form { padding: 1.25rem; background: var(--surface); border: 1px solid var(--border); border-radius: .75rem; }
.field { display: flex; flex-direction: column; gap: .35rem; margin-bottom: 1rem; }
label { font-weight: 500; }
.hint { margin: 0; font-size: .85rem; color: var(--muted); }
input, select, button { font: inherit; color: inherit; padding: .55rem .7rem; border-radius: .5rem; }
input, select { width: 100%; background: var(--bg); border: 1px solid var(--border-strong); }
input:focus, select:focus { border-color: var(--focus); }
.field:has(.field-error) :is(input, select) { border-color: var(--error); }
.field:has(.field-error) .hint { display: none; }
.field-error { margin: 0; font-size: .9rem; color: var(--error); }
button { cursor: pointer; }
.field-row { display: grid; grid-template-columns: 2fr 1fr; gap: 0 1rem; }
button[type=submit] { border: none; background: var(--accent); color: var(--on-accent); font-weight: 600; }
button[type=submit]:hover { background: var(--accent-hover); }
.card-form button[type=submit] { display: block; min-width: 10rem; margin-left: auto; }

.pager { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: 1.25rem; padding: .5rem; background: var(--surface); border: 1px solid var(--border); border-radius: .5rem; }
.step { padding: .4rem .6rem; border-radius: .4rem; text-decoration: none; font-weight: 500; white-space: nowrap; }
a.step:hover { background: var(--bg); }
.step[aria-disabled] { color: var(--muted); opacity: .5; }
.jump { display: flex; align-items: center; gap: .5rem; }
.jump label { color: var(--muted); font-weight: 400; white-space: nowrap; }
.jump input { width: auto; padding-block: .35rem; }
.jump button { padding-block: .35rem; }

.toolbar { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; gap: .75rem; border-bottom: 1px solid var(--border); }
.toolbar input { width: auto; flex: 0 1 15rem; margin-bottom: .5rem; }
.tabs { display: flex; gap: .25rem; }
.tabs button { border: none; border-bottom: 2px solid transparent; border-radius: 0; background: none; color: var(--muted); padding: .6rem .9rem; font-weight: 500; }
.tabs button:hover { color: var(--text); }
.tabs button[aria-selected=true] { border-bottom-color: var(--accent); color: var(--text); font-weight: 600; }
[role=tabpanel] { padding-top: 1rem; }

table { width: 100%; border-collapse: separate; border-spacing: 0; background: var(--surface); border: 1px solid var(--border); border-radius: .5rem; }
th, td { text-align: left; padding: .5rem .75rem; border-bottom: 1px solid var(--border); }
th { position: sticky; top: 0; background: var(--surface); color: var(--muted); font-size: .82rem; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover td { background: var(--bg); }
.amount { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.group-start:not(:first-child) td { border-top: 1px solid var(--border-strong); }
#livraisons tbody tr:not(.group-start) td:first-child { color: var(--muted); }
#bilan td:nth-child(2) { color: var(--receive); }
#bilan td:nth-child(3) { color: var(--give); }

.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 1rem; margin-top: 1.5rem; }
.card { position: relative; display: flex; flex-direction: column; gap: .4rem; padding: 1.25rem; background: var(--surface); border: 1px solid var(--border); border-radius: .75rem; transition: border-color .15s, transform .15s; }
.card:hover, .card:focus-within { border-color: var(--accent); transform: translateY(-1px); }
.card h2 { margin: 0 0 .25rem; font-size: 1.15rem; }
.card h2 a { color: inherit; text-decoration: none; }
.card h2 a::after { content: ""; position: absolute; inset: 0; border-radius: inherit; }
.card h2 a:focus-visible { outline: none; }
.card:has(a:focus-visible) { outline: 2px solid var(--focus); outline-offset: 2px; }
.card p { margin: 0; }
.card-action { margin-top: auto; padding-top: .75rem; color: var(--accent); font-weight: 600; }

@media (max-width: 30rem) {
    main { padding-top: 1rem; }
    .toolbar input { flex-basis: 100%; order: -1; margin-top: .25rem; }
    .field-row { grid-template-columns: 1fr; }
    .card-form button[type=submit] { width: 100%; }
    .pager { flex-wrap: wrap; }
    .jump { order: -1; flex-basis: 100%; }
    .jump input { flex: 1; }
    th, td { padding: .5rem; }
}
"""
