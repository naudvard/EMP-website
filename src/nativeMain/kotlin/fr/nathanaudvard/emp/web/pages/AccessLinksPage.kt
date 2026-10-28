package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.AllianceMember
import fr.nathanaudvard.emp.domain.model.MemberAccess
import fr.nathanaudvard.emp.web.resources.Access
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.textArea
import kotlin.time.Instant

const val ACCESS_MESSAGE_SUBJECT = "Ton accès EMP"

// The body of the private message, its subject being typed apart in the game
fun accessMessage(link: String) = """
    |Voici ton lien personnel pour le site de l'alliance :
    |$link
    |
    |Ouvre-le une fois sur ton téléphone : il s'en souviendra pendant un an.
    |Ensuite, « Forum externe » dans le menu de Fourmizzz t'amène directement sur ta page.
""".trimMargin()

// Until Alliance and the member's page take it over. Copied is the message just made, shown to be copied by hand
// when the script could not copy it; replaced the pseudo of the player whose link was just replaced
fun HTML.accessLinksPage(
    accesses: List<MemberAccess>,
    now: Instant,
    replaced: String? = null,
    copied: Pair<AllianceMember, String>? = null,
) = layout("Accès des membres", tab = null, legacyStyle = false) {
    div("access-links__header") {
        h1("page-title") { +"Accès des membres" }
        empBadge(BadgeVariant.LEADER, "Chef")
    }
    p("text-secondary") {
        +"Copie le message d'un membre et envoie-le-lui en message privé dans Fourmizzz, avec l'objet « $ACCESS_MESSAGE_SUBJECT »."
        +" Chaque copie renouvelle son lien : seul le dernier message copié ouvre une session, ses appareils déjà connectés le restent."
    }
    replaced?.let {
        empMessage(MessageVariant.SUCCESS) {
            +"Lien de $it remplacé : ses appareils sont déconnectés. Copie son nouveau message pour lui rendre l'accès."
        }
    }
    copied?.let { (member, message) ->
        empCard(CardVariant.ACCENT) {
            label {
                htmlFor = "access-message"
                +"Message pour ${member.pseudo}, objet « $ACCESS_MESSAGE_SUBJECT »"
            }
            textArea(rows = "6", classes = "emp-textarea") {
                id = "access-message"
                readonly = true
                autoFocus = true
                attributes["onfocus"] = "this.select()"
                +message
            }
            empCopyButton("Copier", BtnVariant.SECONDARY, target = "access-message")
        }
    }
    section("emp-card access-links") {
        attributes["aria-label"] = "Membres"
        accesses.forEach { access ->
            val member = access.member
            div("emp-row access-links__row") {
                div("emp-row__text") {
                    span("emp-row__title") {
                        +member.pseudo
                        if (access.chief) empBadge(BadgeVariant.LEADER, "Chef")
                    }
                    span("text-secondary") { +accessState(access, now) }
                }
                div("access-links__actions") {
                    form(href(Access.Links.Message(playerId = member.id)), method = FormMethod.post) {
                        empCopyButton("Copier le MP d'accès", BtnVariant.SECONDARY, BtnSize.SMALL)
                    }
                    if (access.sentAt != null) {
                        empDisclosure("Remplacer le lien…", cssClass = "access-links__replace") {
                            form(href(Access.Links.Replace(playerId = member.id)), method = FormMethod.post) {
                                p("text-secondary") { +"L'ancien lien et ses appareils seront déconnectés." }
                                empBtn(BtnVariant.SECONDARY, BtnSize.SMALL) { +"Remplacer le lien de ${member.pseudo}" }
                            }
                        }
                    }
                }
            }
        }
    }
    copyScript()
}

// "Lien envoyé le 20 oct. · dernière visite il y a 5 j"
private fun accessState(access: MemberAccess, now: Instant) = listOfNotNull(
    access.sentAt?.let { "lien envoyé le ${formatDay(it)}" },
    access.lastSeenAt?.let { "dernière visite ${formatRelative(it, now)}" } ?: "jamais connecté",
).joinToString(" · ").replaceFirstChar { it.uppercase() }
