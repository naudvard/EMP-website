package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.web.assets.stylesheets
import fr.nathanaudvard.emp.web.resources.Assets
import fr.nathanaudvard.emp.web.resources.Debts
import fr.nathanaudvard.emp.web.resources.Home
import fr.nathanaudvard.emp.web.resources.Levels
import fr.nathanaudvard.emp.web.resources.MyDeliveries
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

// The current pages, until the three tabs Moi · Salaires · Alliance
enum class Tab(val label: String) {
    HOME("Accueil"),
    WAGES("Salaires"),
    LEVELS("Niveaux"),
    DELIVERIES("Envois"),
    DEBTS("Dettes"),
}

private val Tab.href
    get() = when (this) {
        Tab.HOME -> href(Home())
        Tab.WAGES -> href(Wages())
        Tab.LEVELS -> href(Levels())
        Tab.DELIVERIES -> href(MyDeliveries())
        Tab.DEBTS -> href(Debts())
    }

// Pages not migrated to the design system yet keep their former styles
fun HTML.layout(title: String, tab: Tab?, legacyStyle: Boolean = true, content: MAIN.() -> Unit) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("$title · EMP")
        // Before the stylesheets, so that a dark page is never drawn light first
        script { unsafe { +THEME_SCRIPT } }
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(rel = "stylesheet", href = FONT_URL)
        stylesheets.forEach { link(rel = "stylesheet", href = href(Assets(it.name))) }
        if (legacyStyle) style { unsafe { +STYLE } }
    }
    body {
        header("emp-header") {
            div("emp-header__inner") {
                a(href(Home()), classes = "emp-logo") {
                    unsafe { +ANT_ICON }
                    +"EMP"
                }
                nav("emp-nav") {
                    attributes["aria-label"] = "Navigation principale"
                    Tab.entries.forEach {
                        a(it.href, classes = "emp-tab") {
                            if (it == tab) attributes["aria-current"] = "page"
                            +it.label
                        }
                    }
                }
            }
        }
        main(if (legacyStyle) null else "emp-page") { content() }
    }
}

// Pages have no call: the format is the Resources plugin's, which is installed with its defaults
@PublishedApi
internal val resourcesFormat = ResourcesFormat()

inline fun <reified T : Any> href(resource: T): String = io.ktor.resources.href(resourcesFormat, resource)

fun FlowContent.deliveryProgress(sent: Long, amount: Long) {
    val text = progressText(sent, amount) ?: return
    span(if (sent >= amount) "progress done" else "progress") { +text }
}

private const val FONT_URL = "https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;600;700&display=swap"

private const val ANT_ICON =
    """<svg aria-hidden="true" width="26" height="26" viewBox="0 0 32 32" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="7" cy="16" r="3"/><ellipse cx="14" cy="16" rx="3.2" ry="2.4"/><ellipse cx="23" cy="16" rx="5" ry="3.6"/><path d="M5 13.5 2 9M6.5 13 6 8M12 14l-2-5M14 14v-5M16 14l2-5M12 18l-2 5M14 18v5M16 18l2 5"/></svg>"""

private const val THEME_SCRIPT = """
(function () {
    var dark = matchMedia("(prefers-color-scheme: dark)");
    function apply() { document.documentElement.setAttribute("data-theme", dark.matches ? "dark" : "light"); }
    apply();
    dark.addEventListener("change", apply);
})();
"""

// Pages not migrated to the design system yet: their former palette is mapped onto its tokens
private const val STYLE = """
:root, [data-theme] {
    --bg: var(--surface-page);
    --surface: var(--surface-card);
    --text: var(--text-primary);
    --muted: var(--text-secondary);
    --border: var(--border-subtle);
    --border-strong: var(--border-control);
    --accent: var(--action-primary);
    --accent-hover: var(--action-primary);
    --on-accent: var(--on-action-primary);
    --focus: var(--focus-ring);
    --receive: var(--success);
    --give: var(--warning);
    --error-bg: var(--danger-bg);
    --error: var(--danger);
}
* { box-sizing: border-box; }
main { max-width: 44rem; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
h1 { margin: 0 0 .35rem; font-size: 1.6rem; }
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

.choice { display: inline-flex; padding: .2rem; gap: .2rem; background: var(--bg); border: 1px solid var(--border-strong); border-radius: .5rem; }
.choice label { position: relative; padding: .3rem .75rem; border-radius: .35rem; color: var(--muted); cursor: pointer; white-space: nowrap; }
.choice label:hover { color: var(--text); }
.choice input { position: absolute; inset: 0; width: 100%; margin: 0; padding: 0; opacity: 0; cursor: pointer; }
.choice label:has(input:checked) { background: var(--surface); color: var(--text); font-weight: 600; box-shadow: 0 1px 2px rgb(0 0 0 / .12); }
.choice label:has(input[value=ACCUMULATE_DEBT]:checked) { color: var(--give); }
.choice label:has(input:focus-visible) { outline: 2px solid var(--focus); outline-offset: 1px; }
.settlements td { vertical-align: middle; }
.settlements td:first-child { width: 1%; }
.rank { display: inline-block; min-width: 2.2rem; padding: .1rem .45rem; border-radius: .35rem; background: var(--bg); border: 1px solid var(--border); text-align: center; font-weight: 600; font-variant-numeric: tabular-nums; }
.rank.none { border-color: transparent; background: none; color: var(--muted); font-weight: 400; }
.settlements button[type=submit] { display: block; min-width: 10rem; margin: 1rem 0 0 auto; }



table { width: 100%; border-collapse: separate; border-spacing: 0; background: var(--surface); border: 1px solid var(--border); border-radius: .5rem; }
th, td { text-align: left; padding: .5rem .75rem; border-bottom: 1px solid var(--border); }
th { position: sticky; top: 0; background: var(--surface); color: var(--muted); font-size: .82rem; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover td { background: var(--bg); }
.amount { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.receive { color: var(--receive); }
.give { color: var(--give); }
.who { display: flex; flex-wrap: wrap; align-items: center; gap: .6rem; margin-bottom: 1rem; }
.who label { font-weight: 500; }
.who input { width: auto; flex: 1 1 12rem; max-width: 18rem; }
.mine { display: flex; flex-direction: column; gap: 1rem; padding: 1.1rem 1.2rem; background: var(--surface); border: 1px solid var(--border); border-radius: .75rem; }
.mine .headline { margin: 0; font-size: 1.15rem; }
.mine .headline strong { display: block; font-size: 1.6rem; text-align: left; }
.mine ul { display: flex; flex-direction: column; gap: .5rem; margin: 0; padding: 0; list-style: none; }
.mine li { display: flex; justify-content: space-between; align-items: center; gap: .75rem; padding: .65rem .8rem; background: var(--bg); border-radius: .5rem; font-size: 1.05rem; }
.mine li .pseudo { font-weight: 600; min-width: 0; overflow-wrap: anywhere; }
.mine li .amount { font-weight: 600; }
.mine li .figures { display: flex; flex-direction: column; align-items: flex-end; text-align: right; }
.mine li.done .amount { color: var(--muted); }
.progress { color: var(--muted); font-size: .82rem; font-weight: 500; }
.progress.done { color: var(--receive); }
.mine .headline .progress { display: block; margin-top: .15rem; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.convoys { margin-top: 1.5rem; }
.convoys h2 { margin: 0 0 .75rem; font-size: 1.2rem; }
.convoy-form { display: flex; flex-direction: column; gap: .4rem; }
.convoy-form textarea { width: 100%; padding: .55rem .7rem; font: inherit; color: inherit; background: var(--surface); border: 1px solid var(--border-strong); border-radius: .5rem; resize: vertical; }
.convoy-form textarea:focus { border-color: var(--focus); }
.convoy-form button[type=submit] { align-self: flex-end; min-width: 10rem; }
.convoy-list { display: flex; flex-direction: column; gap: .4rem; margin: 1rem 0 0; padding: 0; list-style: none; }
.convoy-list li { display: flex; flex-wrap: wrap; align-items: center; gap: .25rem .75rem; padding: .5rem .8rem; background: var(--surface); border: 1px solid var(--border); border-radius: .5rem; }
.convoy-list .pseudo { font-weight: 600; flex: 1; min-width: 0; overflow-wrap: anywhere; }
.convoy-list .amount { font-weight: 600; }
.convoy-list .when { color: var(--muted); font-size: .85rem; }
.convoy-list form { margin: 0; }
.convoy-list button[type=submit] { padding: .2rem .5rem; background: none; color: var(--muted); border: 1px solid var(--border-strong); font-size: .85rem; font-weight: 500; }
.convoy-list button[type=submit]:hover { background: var(--error-bg); color: var(--error); }
.mine-foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem; padding-top: .75rem; border-top: 1px solid var(--border); color: var(--muted); font-size: .9rem; }
.debt { margin-left: .5rem; padding: .05rem .4rem; border-radius: .3rem; background: var(--warning-bg); color: var(--give); font-size: .78rem; font-weight: 600; white-space: nowrap; }

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
    .field-row { grid-template-columns: 1fr; }
    .card-form button[type=submit] { width: 100%; }
    .convoy-form button[type=submit] { align-self: stretch; }
    .settlements button[type=submit] { width: 100%; }
    .choice label { padding-inline: .45rem; font-size: .88rem; }
    .settlements :is(th, td) { padding-inline: .35rem; }
    th, td { padding: .5rem; }
}
"""
