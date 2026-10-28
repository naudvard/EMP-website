package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.web.resources.Debts
import fr.nathanaudvard.emp.web.resources.Levels
import fr.nathanaudvard.emp.web.resources.Wages
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

fun HTML.homePage(allianceTag: String, latest: WageComputation?, memberCount: Int) = layout("Accueil", Tab.HOME) {
    h1 { +"Alliance $allianceTag" }
    p("meta") { +"Salaires et niveaux des membres de l'alliance." }
    div("cards") {
        card(href(Wages()), "Salaires") {
            if (latest == null) {
                p { +"Aucun calcul de salaires pour l'instant." }
            } else {
                p { +"Dernier calcul le ${formatDateTime(latest.computedAt)}" }
                p("meta") {
                    +"${latest.deliveries.size} livraisons · ${latest.wages.size} joueurs · répartition ${latest.distribution.label}"
                }
            }
        }
        card(href(Levels()), "Mes niveaux") {
            p { +"Renseigne tes niveaux de couveuse, solarium, technique de ponte et étable." }
            p("meta") {
                +if (memberCount == 0) "Aucun membre synchronisé" else "$memberCount membres dans l'alliance"
            }
        }
        card(href(Debts()), "Dettes") {
            p { +"Choisis pour chaque joueur s'il livre ce qu'il doit ou s'il cumule de la dette." }
        }
    }
}

// The title's link is stretched over the whole card by CSS
private fun FlowContent.card(link: String, title: String, content: FlowContent.() -> Unit) {
    section("card") {
        h2 { a(link) { +title } }
        content()
        span("card-action") { attributes["aria-hidden"] = "true"; +"Ouvrir →" }
    }
}
