package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.web.resources.Debts
import fr.nathanaudvard.emp.web.resources.Home
import fr.nathanaudvard.emp.web.resources.Levels
import fr.nathanaudvard.emp.web.resources.MyDeliveries
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.FlowContent
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
import kotlinx.html.span
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
                listOf(
                    href(Home()) to "Accueil",
                    href(Wages()) to "Salaires",
                    href(Levels()) to "Mes niveaux",
                    href(MyDeliveries()) to "Mes livraisons",
                    href(Debts()) to "Dettes",
                ).forEach { (link, label) ->
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

// Signed, with a dash for zero: positive is received, negative is given
fun formatBalance(balance: Long): String = when {
    balance > 0 -> "+${formatAmount(balance)}"
    balance < 0 -> "−${formatAmount(-balance)}"
    else -> "—"
}

// What the convoys sent towards a delivery cover, null until one was sent
fun progressText(sent: Long, amount: Long): String? = when {
    sent <= 0 -> null
    sent >= amount -> "✓ livré"
    else -> "${formatAmount(sent)} livrés, reste ${formatAmount(amount - sent)}"
}

fun FlowContent.deliveryProgress(sent: Long, amount: Long) {
    val text = progressText(sent, amount) ?: return
    span(if (sent >= amount) "progress done" else "progress") { +text }
}

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
header nav { overflow-x: auto; }
header nav a { flex: none; white-space: nowrap; }
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
.givers { display: grid; grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); gap: .75rem; align-items: start; }
.giver { background: var(--surface); border: 1px solid var(--border); border-radius: .5rem; }
.giver-head { display: flex; justify-content: space-between; align-items: baseline; gap: .5rem; padding: .6rem .85rem; border-bottom: 1px solid var(--border); }
.giver-head .pseudo { font-weight: 600; }
.giver-head .amount { font-size: .9rem; }
.giver ul { margin: 0; padding: .35rem 0; list-style: none; }
.giver li { display: flex; gap: .5rem; padding: .3rem .85rem; }
.giver li::before { content: "→"; color: var(--muted); }
.giver li .pseudo { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.receive { color: var(--receive); }
.give { color: var(--give); }
.balances .balance-bar { width: 40%; }
.balance-bar { position: relative; }
td.balance-bar::before { content: ""; position: absolute; left: 50%; top: .4rem; bottom: .4rem; width: 1px; background: var(--border-strong); }
.bar { position: absolute; top: 50%; height: .7rem; margin-top: -.35rem; border-radius: .2rem; }
.bar.receive { left: 50%; background: var(--receive); }
.bar.give { right: 50%; background: var(--give); }
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
.giver li.partial { background: linear-gradient(to right, var(--success-bg) var(--sent), transparent var(--sent)); }
.giver li.done { color: var(--muted); }
.giver li.done::before { content: "✓"; color: var(--receive); }
.giver-head:is(.partial, .done) { background: linear-gradient(var(--receive), var(--receive)) left bottom / var(--sent, 100%) 3px no-repeat; }
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
    .toolbar input { flex-basis: 100%; order: -1; margin-top: .25rem; }
    .field-row { grid-template-columns: 1fr; }
    .card-form button[type=submit] { width: 100%; }
    .convoy-form button[type=submit] { align-self: stretch; }
    .settlements button[type=submit] { width: 100%; }
    .choice label { padding-inline: .45rem; font-size: .88rem; }
    .settlements :is(th, td) { padding-inline: .35rem; }
    .pager { flex-wrap: wrap; }
    .jump { order: -1; flex-basis: 100%; }
    .jump input { flex: 1; }
    th, td { padding: .5rem; }
    .balance-bar { display: none; }
}
"""
