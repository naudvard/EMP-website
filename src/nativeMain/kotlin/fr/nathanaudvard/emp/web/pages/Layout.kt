package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.web.assets.script as sharedScript
import fr.nathanaudvard.emp.web.assets.stylesheets
import fr.nathanaudvard.emp.web.resources.Alliance
import fr.nathanaudvard.emp.web.resources.Assets
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.resources.Wages
import io.ktor.resources.serialization.ResourcesFormat
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
import kotlinx.html.title
import kotlinx.html.unsafe

enum class Tab(val label: String) {
    ME("Moi"),
    WAGES("Salaires"),
    ALLIANCE("Alliance"),
}

private val Tab.href
    get() = when (this) {
        Tab.ME -> href(Me())
        Tab.WAGES -> href(Wages())
        Tab.ALLIANCE -> href(Alliance())
    }

// Without navigation, the banner has no tabs. The viewer is named at its right from a desktop width
fun HTML.layout(
    title: String,
    tab: Tab?,
    viewer: Viewer? = null,
    navigation: Boolean = true,
    content: MAIN.() -> Unit,
) {
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
        script(src = href(Assets(sharedScript.name))) { defer = true }
    }
    body {
        header("emp-header") {
            div("emp-header__inner") {
                a(href(Me()), classes = "emp-logo") {
                    unsafe { +ANT_ICON }
                    +"EMP"
                }
                if (navigation) {
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
                viewer?.let { span("emp-header__user") { +"${it.pseudo} · ${if (it.chief) "chef" else "membre"}" } }
            }
        }
        main("emp-page") { content() }
    }
}

// Pages have no call: the format is the Resources plugin's, which is installed with its defaults
@PublishedApi
internal val resourcesFormat = ResourcesFormat()

inline fun <reified T : Any> href(resource: T): String = io.ktor.resources.href(resourcesFormat, resource)

private const val FONT_URL = "https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;600;700&display=swap"

private const val ANT_ICON =
    """<svg aria-hidden="true" width="26" height="26" viewBox="0 0 32 32" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="7" cy="16" r="3"/><ellipse cx="14" cy="16" rx="3.2" ry="2.4"/><ellipse cx="23" cy="16" rx="5" ry="3.6"/><path d="M5 13.5 2 9M6.5 13 6 8M12 14l-2-5M14 14v-5M16 14l2-5M12 18l-2 5M14 18v5M16 18l2 5"/></svg>"""

private const val THEME_SCRIPT = """
(function () {
    var dark = matchMedia("(prefers-color-scheme: dark)");
    function apply() { document.documentElement.setAttribute("data-theme", dark.matches ? "dark" : "light"); }
    // Styles what only works with the shared script, which comes deferred
    document.documentElement.classList.add("js");
    apply();
    dark.addEventListener("change", apply);
})();
"""

