package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.helper.COLONY_BASE_SHARE_PERCENT
import fr.nathanaudvard.emp.domain.helper.DEVICE_CODE_DURATION
import fr.nathanaudvard.emp.domain.helper.WOOD_PER_FIELD_PER_DAY
import fr.nathanaudvard.emp.domain.model.Convoy
import fr.nathanaudvard.emp.domain.model.ConvoyPaste
import fr.nathanaudvard.emp.domain.model.ConvoySource
import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.LevelChange
import fr.nathanaudvard.emp.domain.model.MyOverview
import fr.nathanaudvard.emp.domain.model.PlayerLevel
import fr.nathanaudvard.emp.domain.model.PlayerLevelsState
import fr.nathanaudvard.emp.domain.model.SavedComputation
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.domain.model.Wage
import fr.nathanaudvard.emp.domain.model.WageComputation
import fr.nathanaudvard.emp.domain.model.WageDelivery
import fr.nathanaudvard.emp.web.resources.Access
import fr.nathanaudvard.emp.web.resources.Me
import fr.nathanaudvard.emp.web.resources.Wages
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.OL
import kotlinx.html.SECTION
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The sections the posts of the page change, which htmx swaps. Sending is the main column, whose cards come and go
enum class MeSection(val id: String) {
    SENDING("sending"),
    PREVIOUS("previous"),
    SEND("send"),
    RECEIVE("receive"),
    STATE("state"),
    LEVELS("levels"),
    LEVELS_SHORTCUT("levels-shortcut"),
    CALCULATION("calculation"),
    CONVOYS("convoys"),
    DEVICE("device"),
}

object MeFields {
    const val LEVEL = "level"
    const val TYPED = "typed"
    const val MESSAGE = "message"
    const val ID = "id"
}

// The text just pasted and what became of it
class PasteForm(val text: String, val result: ConvoyPaste)

// Where the forms of a player's cards post, and who they speak to: the viewer on their own page, a chief on the page
// of a member, acting in their name
class PlayerForms(
    val own: Boolean,
    val level: (PlayerLevel) -> String,
    val confirmLevels: String,
    val undoLevelChange: (Long) -> String,
    val declare: (computationId: Long, to: Long) -> String,
    val deleteConvoys: String,
)

private val MY_FORMS = PlayerForms(
    own = true,
    level = { href(Me.Level(kind = it.name)) },
    confirmLevels = href(Me.ConfirmLevels()),
    undoLevelChange = { href(Me.UndoLevelChange(id = it)) },
    declare = { computationId, to -> href(Me.Declare(computationId = computationId, to = to)) },
    deleteConvoys = href(Me.Convoys.Delete()),
)

// The computations made before the detail was stored
const val DETAIL_UNAVAILABLE = "Détail indisponible pour ce calcul."

// Levels not saved nor confirmed for this long are asked about
val LEVELS_STALE_AFTER = 7.days

// What the player has to do first: send, receive, or nothing, for a reason
internal enum class State { SEND, RECEIVE, ACCUMULATE, NOTHING, NO_COMPUTATION, NEW_MEMBER }

// The latest computation from a player's side. On their page, only the viewer's wage and levels are ever shown
class Mine(val playerId: Long, val saved: SavedComputation) {
    val computation = saved.computation
    val wage: Wage? = computation.wages.find { it.playerId == playerId }
    val sends = computation.deliveries.filter { it.fromPlayerId == playerId }.sortedByDescending { it.amount }
    val receives = computation.deliveries.filter { it.toPlayerId == playerId }.sortedByDescending { it.amount }
    val convoys = computation.convoys.filter { it.fromPlayerId == playerId }.reversed()
}

// The convoys of the computation before recorded since the latest one: sent from the previous card, still undone there
fun Mine.lateConvoys(previous: SavedComputation): List<Convoy> =
    previous.computation.convoys.filter { it.fromPlayerId == playerId && it.sentAt >= computation.computedAt }.reversed()

// What the player still has to send of the computation before, as long as they are in the latest one, with the
// deliveries sent since the latest computation, which can still be undone
fun previousLeft(mine: Mine?, previous: SavedComputation?): Pair<SavedComputation, List<WageDelivery>>? =
    previous?.takeIf { mine?.wage != null }?.let { saved ->
        val late = mine!!.lateConvoys(saved)
        saved to saved.computation.deliveries.filter { delivery ->
            delivery.fromPlayerId == mine.playerId &&
                (saved.computation.covered(delivery) < delivery.amount || late.any { it.toPlayerId == delivery.toPlayerId })
        }
    }?.takeIf { it.second.isNotEmpty() }

// The viewer's page worked out once, for the whole page as for the sections a post sends back
class MyPage(val viewer: Viewer, val overview: MyOverview, val now: Instant) {
    val mine = overview.latest?.let { Mine(viewer.playerId, it) }
    val previous = previousLeft(mine, overview.previous)
    val late = previous?.let { mine!!.lateConvoys(it.first) }.orEmpty()
    internal val state = when {
        mine == null -> State.NO_COMPUTATION
        mine.wage == null -> State.NEW_MEMBER
        mine.sends.isNotEmpty() -> State.SEND
        mine.receives.isNotEmpty() -> State.RECEIVE
        mine.wage.netWage < 0 && mine.wage.settledAmount == 0L -> State.ACCUMULATE
        else -> State.NOTHING
    }

    // The game's messages are pasted in the first sending card
    val pasteIn = when {
        state == State.SEND -> MeSection.SEND
        previous != null -> MeSection.PREVIOUS
        else -> null
    }
}

// Mobile: one column, what to do first. Desktop: sending and convoys on the left, levels and calculation on the right
fun HTML.mePage(page: MyPage) =
    layout(page.viewer.pseudo, Tab.ME, page.viewer) {
        val mine = page.mine
        val overview = page.overview
        div("me-header") {
            div("me-header__title") {
                h1("page-title") { +page.viewer.pseudo }
                // Without a computation, the state card says when the first one comes
                if (mine != null) {
                    p("me-header__meta text-secondary") {
                        +"Calcul du ${formatDateTime(mine.computation.computedAt)}"
                        if (overview.newComputation && mine.wage != null) empBadge(BadgeVariant.SOLID, "nouveau")
                        span { +"· prochain ${formatDue(overview.nextComputation, page.now)}" }
                    }
                }
            }
            levelsShortcut(overview, page.now)
        }
        div("me") {
            sendingColumn(page)
            div("me__column me__column--side") {
                myLevelsCard(page)
                mine?.wage?.let { calculationCard(mine, it) }
                deviceBlock(null)
            }
        }
    }

fun FlowContent.sendingColumn(page: MyPage, paste: PasteForm? = null) = div("me__column me__column--main") {
    id = MeSection.SENDING.id
    val mine = page.mine
    val now = page.now
    page.previous?.let { (saved, left) ->
        val pasteHere = page.pasteIn == MeSection.PREVIOUS
        previousCard(saved, left, now, MY_FORMS, pasteHere, paste.takeIf { pasteHere })
    }
    when (page.state) {
        State.SEND -> sendCard(mine!!, now, MY_FORMS, paste, deadline = page.overview.nextComputation)
        State.RECEIVE -> receiveCard(mine!!)
        else -> stateCard(page.state, page.viewer, mine, page.overview.nextComputation, now)
    }
    if (mine != null && (mine.sends.isNotEmpty() || mine.convoys.isNotEmpty() || page.late.isNotEmpty())) {
        convoysCard(mine, MY_FORMS, page.late, page.previous?.first, now)
    }
}

// The levels card and its shortcut at the top of the page, which the level posts change together
fun FlowContent.myLevels(page: MyPage) {
    levelsShortcut(page.overview, page.now)
    myLevelsCard(page)
}

private fun FlowContent.myLevelsCard(page: MyPage) =
    levelsCard(page.overview.levels, page.overview.change, page.now, MY_FORMS, first = page.state != State.SEND && page.state != State.RECEIVE)

// The code shown in place of the button, for the other device to type on the access screen
fun FlowContent.deviceBlock(code: DeviceCode?) = div("me-device") {
    id = MeSection.DEVICE.id
    if (code == null) {
        form(action = href(Access.Device()), method = FormMethod.post) {
            live(MeSection.DEVICE)
            empBtn(BtnVariant.LINK) { +"Connecter un autre appareil" }
        }
        return@div
    }
    empCard(cssClass = "device-code") {
        attributes["role"] = "status"
        span("text-secondary") { +"Saisis ce code sur l'autre appareil" }
        strong("device-code__value") { +code.display() }
        span("text-secondary") { +"Valable ${DEVICE_CODE_DURATION.inWholeMinutes} minutes, une seule fois." }
    }
}

private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count > 1) many else one}"

fun SECTION.titled(section: MeSection) {
    id = section.id
    attributes["aria-labelledby"] = "${section.id}-title"
}

// Phone only: the levels at a glance, at the top of the page
private fun FlowContent.levelsShortcut(overview: MyOverview, now: Instant) =
    a("#${MeSection.LEVELS.id}", classes = "me-shortcut") {
        id = MeSection.LEVELS_SHORTCUT.id
        span("me-shortcut__title") { +"Mes niveaux" }
        span("me-shortcut__levels") { +PlayerLevel.entries.joinToString(" · ") { overview.levels.levels[it].toString() } }
        span("me-shortcut__date small text-secondary") { +levelsDate(overview.levels.updatedAt, now) }
        unsafe { +CHEVRON_DOWN_ICON }
    }

private fun levelsDate(updatedAt: Instant?, now: Instant) = updatedAt?.let { formatRelative(it, now) } ?: "jamais"

// What the convoys cover of a delivery, and the latest of them
private class Line(computation: WageComputation, val delivery: WageDelivery, val pseudo: String) {
    val sent = computation.covered(delivery)
    val left = delivery.amount - sent
    val latest: Convoy? = computation.convoys.lastOrNull {
        it.fromPlayerId == delivery.fromPlayerId && it.toPlayerId == delivery.toPlayerId
    }
}

private fun WageComputation.pseudo(playerId: Long) = wages.single { it.playerId == playerId }.pseudo

// The deadline is the next computation, for the player's own page
fun FlowContent.sendCard(mine: Mine, now: Instant, forms: PlayerForms, paste: PasteForm?, deadline: Instant? = null) =
    empCard(cssClass = "me-send") {
        titled(MeSection.SEND)
        val computation = mine.computation
        val lines = mine.sends.map { Line(computation, it, computation.pseudo(it.toPlayerId)) }
        val total = lines.sumOf { it.delivery.amount }
        val sent = lines.sumOf { it.sent }
        div("me-send__head") {
            div("me-send__summary") {
                // On a member's page, a chief reads what is left and of which computation
                if (!forms.own) {
                    div("me-card-head") {
                        h2("card-title") {
                            id = "${MeSection.SEND.id}-title"
                            if (sent >= total) +"✓ Tout envoyé" else +"Envoie ${formatAmount(total - sent)}"
                        }
                        span("small text-secondary") { +"calcul du ${formatDay(computation.computedAt)}" }
                    }
                    return@div
                }
                h2("me-card-caption") {
                    id = "${MeSection.SEND.id}-title"
                    if (sent >= total) {
                        +"✓ Tout envoyé pour ce calcul"
                    } else {
                        +"À envoyer"
                        deadline?.let { +" ${formatDeadline(it, now)}" }
                    }
                }
                strong("figure amount") {
                    +formatAmount(total - sent)
                    span("me-figure-unit") {
                        +" bois"
                        span("me-figure-unit__wide") { +" sur ${formatAmount(total)}, à ${plural(lines.size, "joueur")}" }
                    }
                }
            }
            div("me-progress") {
                // Rounded down: 100 % only once everything is sent
                span("me-progress__percent small text-secondary") { +"${formatPercent(floor(sent * 100.0 / total) / 100)} envoyé" }
                div("me-progress__bar") {
                    empProgress(sent, total, "Envoyé")
                    span("me-progress__total small text-secondary amount") { +"sur ${formatAmount(total)}" }
                }
            }
        }
        deliveriesTable(lines, mine.saved.id, now, forms, previous = null)
        // The game's messages are only pasted by the player
        if (forms.own) pasteBlock(MeSection.SEND, paste, now, forms)
    }

fun FlowContent.previousCard(
    saved: SavedComputation,
    left: List<WageDelivery>,
    now: Instant,
    forms: PlayerForms,
    withPaste: Boolean,
    paste: PasteForm?,
) = empCard(CardVariant.WARNING, "me-previous") {
    titled(MeSection.PREVIOUS)
    val computation = saved.computation
    val name = computationName(computation.computedAt, now)
    val lines = left.map { Line(computation, it, computation.pseudo(it.toPlayerId)) }
    val done = lines.all { it.left <= 0 }
    h2("card-title") {
        id = "${MeSection.PREVIOUS.id}-title"
        +(if (done) "✓ Tout envoyé du $name" else "Encore à envoyer du $name")
    }
    deliveriesTable(lines, saved.id, now, forms, previous = name)
    if (!done) p("small text-secondary") { +"Reste du calcul précédent : à envoyer en plus du calcul du jour." }
    if (withPaste) pasteBlock(MeSection.PREVIOUS, paste, now, forms)
}

private fun computationName(computedAt: Instant, now: Instant) =
    if (formatRelativeDay(computedAt, now) == "hier") "calcul d'hier" else "calcul du ${formatDay(computedAt)}"

// A table on a desktop, rows of two lines on a phone. Previous names the computation before the latest one, whose
// card it is
private fun FlowContent.deliveriesTable(lines: List<Line>, computationId: Long, now: Instant, forms: PlayerForms, previous: String?) =
    table("me-deliveries") {
        thead {
            tr {
                th { attributes["scope"] = "col"; +"Destinataire" }
                th(classes = "number") { attributes["scope"] = "col"; +"Montant" }
                th(classes = "me-deliveries__state") { attributes["scope"] = "col"; +"État" }
            }
        }
        tbody { lines.forEach { deliveryRow(it, computationId, now, forms, previous) } }
    }

private fun TBODY.deliveryRow(line: Line, computationId: Long, now: Instant, forms: PlayerForms, previous: String?) {
    val done = line.left <= 0
    val back = if (previous != null) MeSection.PREVIOUS else MeSection.SEND
    tr(if (done) "me-deliveries__row me-deliveries__row--done" else "me-deliveries__row") {
        td("me-deliveries__pseudo") {
            span("me-deliveries__arrow") { +"→ " }
            +line.pseudo
        }
        td("me-deliveries__amount number") {
            span("amount") { +formatAmount(if (done) line.delivery.amount else line.left) }
            if (!done && line.sent > 0) span("caption text-secondary") { +"${formatAmount(line.sent)} déjà envoyés" }
        }
        td("me-deliveries__state") {
            val focus = "${back.id}-${computationId}-${line.delivery.toPlayerId}"
            if (!done) {
                form(action = forms.declare(computationId, line.delivery.toPlayerId), method = FormMethod.post) {
                    live(MeSection.SENDING)
                    val label = if (forms.own) "J'ai envoyé" else "Marquer envoyé"
                    empBtn(BtnVariant.SECONDARY, BtnSize.COMPACT) {
                        id = focus
                        attributes["aria-label"] = "$label à ${line.pseudo}" + previous?.let { ", $it" }.orEmpty()
                        +label
                    }
                }
                return@td
            }
            // On a phone the amount column is gone: the line says what was sent
            span("emp-row__done") {
                +"✓ "
                span("me-deliveries__done-amount") { +"${formatAmount(line.delivery.amount)} " }
                span("me-deliveries__done-word") { +"envoyé" }
                line.latest?.let { +" ${formatSince(it.sentAt, now)}" }
            }
            // A declared convoy is undone at once; a pasted one is removed from the convoys
            line.latest?.takeIf { it.source == ConvoySource.DECLARED }?.let { convoy ->
                deleteForm(listOf(convoy.id), forms) {
                    empBtn(BtnVariant.LINK, BtnSize.COMPACT) {
                        id = focus
                        attributes["aria-label"] = "Annuler l'envoi à ${line.pseudo}"
                        +"Annuler"
                    }
                }
            }
        }
    }
}

private fun FORM.live(vararg sections: MeSection) = live(*sections.map { it.id }.toTypedArray())

private fun FlowContent.deleteForm(ids: List<Long>, forms: PlayerForms, button: FlowContent.() -> Unit) =
    form(action = forms.deleteConvoys, method = FormMethod.post, classes = "me-inline-form") {
        live(MeSection.SENDING)
        ids.forEach { input(InputType.hidden, name = MeFields.ID) { value = it.toString() } }
        button()
    }

// The clipboard button on a phone, the field on a desktop or when the clipboard cannot be read. The messages live
// outside the form, which holds none
private fun FlowContent.pasteBlock(section: MeSection, paste: PasteForm?, now: Instant, forms: PlayerForms) = div("paste-block") {
    paste?.let { pasteMessages(it, section, now, forms) }
    form(action = href(Me.Convoys()), method = FormMethod.post, classes = if (paste?.result?.nothing == true) "paste paste--manual" else "paste") {
        live(MeSection.SENDING)
        empBtn(BtnVariant.SECONDARY, type = ButtonType.button) {
            this.classes += "paste__clipboard"
            attributes["data-paste"] = ""
            unsafe { +CLIPBOARD_ICON }
            +"Coller les messages du jeu"
        }
        p("paste__hint small text-secondary") {
            +"Envoi partiel : copie la page des convois du jeu, puis touche ce bouton."
        }
        div("paste__field") {
            val field = "${section.id}-message"
            // The script shows the short words when the clipboard could not be read
            label("paste__label") {
                htmlFor = field
                attributes["data-manual"] = "Colle le message ici"
                +"Envoi partiel ou plusieurs convois : colle le message du jeu"
            }
            textArea(rows = "2", classes = "emp-textarea") {
                id = field
                name = MeFields.MESSAGE
                attributes["data-submit-on-paste"] = ""
                attributes["data-manual"] = "Appui long puis « Coller »"
                placeholder = "Vous allez livrer 0 nourritures et 2 750 216 materiaux à Crematogaster dans 10H 46m 4s - Retour le 28 oct. à 08h39 - Annuler"
                if (paste?.result?.nothing == true) +paste.text
            }
            p("small text-secondary") {
                +"Enregistré dès le collage. Toute la page des convois peut être collée : seuls les convois de matériaux prévus sont gardés, les doublons ignorés."
            }
        }
    }
}

private fun FlowContent.pasteMessages(paste: PasteForm, section: MeSection, now: Instant, forms: PlayerForms) {
    val result = paste.result
    if (result.nothing) {
        empMessage(MessageVariant.DANGER) {
            strong { +"Aucun convoi de matériaux dans ce texte" }
            span { +"Il faut la ligne « Vous allez livrer … matériaux à … » de la page des convois." }
        }
        return
    }
    if (result.recorded.isNotEmpty()) {
        val count = result.recorded.size
        empMessage(MessageVariant.SUCCESS) {
            strong { +"${plural(count, "convoi")} ${if (count > 1) "enregistrés" else "enregistré"}" }
            result.recorded.forEach { convoy ->
                span { +("${formatAmount(convoy.amount)} → ${convoy.recipient}" + convoy.arrivesAt?.let { ", ${formatArrival(it)}" }.orEmpty()) }
            }
            deleteForm(result.recorded.map { it.id }, forms) {
                empBtn(BtnVariant.LINK, BtnSize.COMPACT) { +(if (count > 1) "Annuler ces $count convois" else "Annuler ce convoi") }
            }
        }
    }
    if (result.refused.isNotEmpty()) {
        val count = result.refused.size
        empMessage(MessageVariant.DANGER) {
            strong { +"${plural(count, "convoi")} non ${if (count > 1) "enregistrés" else "enregistré"}" }
            result.refused.forEach {
                span {
                    +"${formatAmount(it.amount)} → ${it.recipient} : aucun envoi prévu vers ${it.recipient} dans ${if (forms.own) "ton" else "son"} calcul. "
                    +(if (forms.own) "Vérifie le destinataire dans le jeu, ou préviens un chef." else "Vérifie le destinataire dans le jeu.")
                }
            }
        }
    }
    if (result.duplicates.isNotEmpty()) {
        empMessage(MessageVariant.INFO) {
            strong { +(if (result.recorded.isEmpty()) "Rien de nouveau" else "Déjà déclarés") }
            result.duplicates.forEach {
                span { +"${formatAmount(it.amount)} → ${it.recipient} est déjà déclaré (${formatDayTime(it.recordedAt, now)})." }
            }
            if (result.recorded.isEmpty()) span { +"Rien n'a été ajouté." }
        }
    }
}

private fun FlowContent.receiveCard(mine: Mine) = empCard(cssClass = "me-receive") {
    titled(MeSection.RECEIVE)
    val computation = mine.computation
    val lines = mine.receives.map { Line(computation, it, computation.pseudo(it.fromPlayerId)) }
    val total = lines.sumOf { it.delivery.amount }
    val received = lines.sumOf { it.sent }
    div("me-send__summary") {
        h2("me-card-caption") {
            id = "${MeSection.RECEIVE.id}-title"
            +"Tu reçois"
        }
        div("me-receive__figure") {
            strong("figure amount balance--positive") { +formatAmount(total) }
            span("text-secondary") { +"bois de ${plural(lines.size, "joueur")}" }
        }
        div("me-progress__bar") {
            empProgress(received, total, "Reçu")
            span("small text-secondary amount") {
                +(if (received >= total) "✓ tout envoyé" else "reste ${formatAmount(total - received)}")
            }
        }
    }
    ul("me-list") {
        lines.forEach { line ->
            li("emp-row") {
                div("emp-row__text") {
                    span(if (line.left <= 0) "emp-row__title text-secondary" else "emp-row__title") { +line.pseudo }
                    span(if (line.left <= 0) "amount text-secondary" else "amount") { +formatAmount(line.delivery.amount) }
                }
                when {
                    line.sent == 0L -> span("small text-secondary") { +"pas encore envoyé" }
                    line.left > 0 -> span("small text-secondary me-receive__state") {
                        +"${formatAmount(line.sent)} envoyés, reste ${formatAmount(line.left)}"
                    }
                    else -> span("me-receive__state me-receive__state--sent") {
                        span("me-receive__sent") {
                            unsafe { +CHECK_ICON }
                            +"Envoyé"
                        }
                        line.latest?.arrivesAt?.let { span("caption text-secondary") { +formatArrival(it) } }
                    }
                }
            }
        }
    }
    p("small text-secondary") { +"Rien à faire de ton côté : chaque livreur enregistre ses convois." }
}

private fun FlowContent.stateCard(state: State, viewer: Viewer, mine: Mine?, next: Instant, now: Instant) = empCard(cssClass = "me-state") {
    titled(MeSection.STATE)
    val title: String
    val text: DIV.() -> Unit
    when (state) {
        State.ACCUMULATE -> {
            val wage = mine!!.wage!!
            title = "Aujourd'hui, rien à envoyer"
            text = {
                p {
                    +"Les chefs ont choisi que tu gardes ta part : "
                    strong("amount") { +formatAmount(-wage.netWage) }
                    +" s'ajoutent à ta dette."
                }
                empMessage(MessageVariant.WARNING, inline = true) {
                    span("me-state__label") { +"Dette" }
                    strong("amount") { +formatAmount(mine.computation.debts.getValue(viewer.playerId)) }
                }
                p("small text-secondary") { +"Elle se rembourse les jours où tu envoies, ou en recevant moins." }
            }
        }
        State.NO_COMPUTATION -> {
            title = "Premier calcul ${formatDue(next, now)}"
            text = { p("text-secondary") { +"En attendant, remplis tes niveaux : ils comptent dès ce calcul." } }
        }
        State.NEW_MEMBER -> {
            title = "Bienvenue, ${viewer.pseudo}"
            text = {
                p("text-secondary") {
                    // "27 oct." ends the sentence with its own dot, "27 mai" does not
                    val day = formatDay(mine!!.computation.computedAt)
                    +"Tu n'étais pas encore dans le calcul du $day${if (day.endsWith('.')) "" else "."} "
                    val today = formatDue(next, now).startsWith("aujourd'hui")
                    +"Tu seras dans ${if (today) "celui de ${formatTime(next)}" else "le prochain"} : remplis tes niveaux d'ici là."
                }
            }
        }
        else -> {
            title = "Rien à faire aujourd'hui"
            text = {
                p("text-secondary") {
                    +(if (mine?.wage?.netWage == 0L) "Ta production couvre exactement ta part." else "Ni envoi ni réception pour toi dans ce calcul.")
                }
            }
        }
    }
    h2("card-title") {
        id = "${MeSection.STATE.id}-title"
        +title
    }
    div("me-state__text") { text() }
}

// First when there is nothing to send or receive. UpdatedBy names who last saved the levels, for a chief
fun FlowContent.levelsCard(
    state: PlayerLevelsState,
    change: LevelChange?,
    now: Instant,
    forms: PlayerForms,
    first: Boolean = false,
    updatedBy: String? = null,
) = empCard(cssClass = if (first) "me-levels me-levels--first" else "me-levels") {
    titled(MeSection.LEVELS)
    val stale = state.updatedAt == null || now - state.updatedAt > LEVELS_STALE_AFTER
    // Only a change still in place can be undone
    val undoable = change?.takeIf { state.levels[it.kind] == it.to }
    div("me-card-head") {
        h2("card-title") {
            id = "${MeSection.LEVELS.id}-title"
            +(if (forms.own) "Mes niveaux" else "Niveaux")
            if (!forms.own) empBadge(BadgeVariant.LEADER, "Chef")
        }
        span(if (stale) "small me-levels__date me-levels__date--stale" else "small text-secondary me-levels__date") {
            +(state.updatedAt?.let { "mis à jour ${updatedBy?.let { "par $it " }.orEmpty()}${formatRelative(it, now)}" } ?: "jamais mis à jour")
        }
    }
    if (stale && state.updatedAt != null) {
        empMessage(MessageVariant.WARNING, inline = true) {
            span("me-levels__question") { +"Toujours à jour ?" }
            form(action = forms.confirmLevels, method = FormMethod.post, classes = "me-inline-form") {
                live(MeSection.LEVELS, MeSection.LEVELS_SHORTCUT)
                empBtn(BtnVariant.SECONDARY, BtnSize.COMPACT) {
                    id = "levels-confirm"
                    +"Oui, rien n'a changé"
                }
            }
        }
    }
    div("me-levels__grid") {
        PlayerLevel.entries.forEach { kind ->
            val field = "level-${kind.name.lowercase().replace('_', '-')}"
            form(
                action = forms.level(kind),
                method = FormMethod.post,
                classes = if (undoable?.kind == kind) "emp-level emp-level--changed" else "emp-level",
            ) {
                live(MeSection.LEVELS, MeSection.LEVELS_SHORTCUT)
                // The default button: a typed level is sent alone when Enter is pressed
                button(type = ButtonType.submit, classes = "visually-hidden") {
                    attributes["tabindex"] = "-1"
                    attributes["aria-hidden"] = "true"
                    +"Enregistrer"
                }
                label("emp-level__name") {
                    htmlFor = field
                    +kind.label
                }
                empStepper(MeFields.LEVEL, MeFields.TYPED, field, state.levels[kind], kind.label, PlayerLevel.MAX_LEVEL.toLong())
            }
        }
    }
    undoable?.let {
        empMessage(MessageVariant.SUCCESS, inline = true) {
            span("me-levels__saved") { +"✓ ${it.kind.label} ${it.from} → ${it.to} enregistré" }
            form(action = forms.undoLevelChange(it.id), method = FormMethod.post, classes = "me-inline-form") {
                live(MeSection.LEVELS, MeSection.LEVELS_SHORTCUT)
                empBtn(BtnVariant.LINK, BtnSize.COMPACT) {
                    attributes["aria-label"] = "Annuler : remettre ${it.kind.label} au niveau ${it.from}"
                    +"Annuler"
                }
            }
        }
    }
    p("small text-secondary") {
        +"Enregistré à chaque changement. Visible par ${if (forms.own) "toi" else "le membre"} et les chefs seulement."
    }
}

private fun FlowContent.calculationCard(mine: Mine, wage: Wage) = empCard(cssClass = "me-calculation") {
    titled(MeSection.CALCULATION)
    val computation = mine.computation
    val debt = computation.debts.getValue(wage.playerId)
    h2("card-title") {
        id = "${MeSection.CALCULATION.id}-title"
        +"Mon calcul"
    }
    div("me-tiles") {
        div("emp-tile") {
            span("caption text-secondary") { +"Solde du jour" }
            strong(if (wage.netWage > 0) "me-tile__figure amount balance--positive" else "me-tile__figure amount") {
                +formatBalance(wage.netWage)
            }
        }
        div("emp-tile") {
            span("caption text-secondary") { +"Dette" }
            strong(if (debt > 0) "me-tile__figure amount debt-amount" else "me-tile__figure") {
                +(if (debt > 0) formatAmount(debt) else "Aucune")
            }
        }
    }
    p("text-secondary") { +settlementText(wage) }
    if (computation.hasDetail) {
        empDisclosure("D'où vient ce chiffre ?", id = "calculation-detail", cssClass = "me-detail") {
            ol("me-steps") { detailSteps(mine, wage) }
        }
    } else {
        p("small text-secondary") { +DETAIL_UNAVAILABLE }
    }
    a("${href(Wages())}#method", classes = "me-method-link") { +"Méthode et totaux de l'alliance →" }
}

private fun settlementText(wage: Wage): String = when {
    wage.netWage < 0 && wage.settledAmount == 0L ->
        "Solde négatif : les chefs ont choisi que tu gardes ta part, elle s'ajoute à ta dette."
    wage.netWage < 0 -> "Solde négatif : tu envoies ta part (choix des chefs, au lieu de la cumuler en dette)."
    wage.netWage > 0 && (wage.settledAmount ?: wage.netWage) < wage.netWage ->
        "Solde positif : tu reçois ta part, moins ce qui rembourse ta dette."
    wage.netWage > 0 -> "Solde positif : tu reçois ta part."
    else -> "Solde nul : ta production couvre exactement ta part."
}

// Each step's figure follows from the ones before: the production plus what goes to or comes from the colonies is the
// own production, the share minus the own production the balance, which the debts and the sharing turn into what is
// actually sent or received
private fun OL.detailSteps(mine: Mine, wage: Wage) {
    val computation = mine.computation
    val base = wage.baseProduction ?: return
    val transfer = wage.colonyTransfer ?: 0
    var number = 0
    fun step(title: String, figure: String, figureClass: String? = null, text: String) = li("me-step") {
        span("emp-step") {
            attributes["aria-hidden"] = "true"
            +(++number).toString()
        }
        div("me-step__body") {
            div("me-step__head") {
                strong { +title }
                span(listOfNotNull("me-step__figure amount", figureClass).joinToString(" ")) { +figure }
            }
            span("small text-secondary") { +text }
        }
    }

    val days = (computation.computedAt - computation.since) / 1.days
    step(
        "Ta production",
        formatAmount(base),
        text = "TdC moyen ${formatAmount(wage.averageField.roundToLong())} cm² × ${WOOD_PER_FIELD_PER_DAY.toInt()} bois par jour × " +
            "${formatDays(days)} (${plural(computation.hours, "mesure")}, du ${formatDateTime(computation.since)} " +
            "au ${formatDateTime(computation.computedAt)})",
    )
    val colonies = computation.colonies(wage.playerId)
    val master = wage.masterPlayerId?.let { id -> computation.wages.find { it.playerId == id } }
    when {
        transfer > 0 && colonies.isNotEmpty() -> {
            val names = colonies.map { it.pseudo }.let { if (it.size > 1) "${it.dropLast(1).joinToString(", ")} et ${it.last()}" else it.single() }
            val percent = colonies.firstNotNullOfOrNull { it.colonyPercent() }
            step(
                if (colonies.size > 1) "Tes colonies" else "Ta colonie",
                formatBalance(transfer),
                text = "$names te ${if (colonies.size > 1) "reversent" else "reverse"}" +
                    (percent?.let { " $it$NBSP% ($COLONY_BASE_SHARE_PERCENT$NBSP% + ton étable niv. ${it - COLONY_BASE_SHARE_PERCENT})" } ?: "") +
                    ". Production propre : ${formatAmount(wage.selfProduction)}",
            )
        }
        transfer < 0 && master != null -> step(
            "Part de ton maître",
            formatBalance(transfer),
            text = "Tu es une colonie ${ofPseudo(master.pseudo)} : " +
                (wage.colonyPercent()?.let { "$it$NBSP% de ta production lui reviennent" } ?: "une part de ta production lui revient") +
                ". Production propre : ${formatAmount(wage.selfProduction)}",
        )
    }
    val share = computation.needShare(wage.playerId)
    val nextTechnique = wage.layingTechniqueLevel() + 1
    step(
        "Ton besoin",
        "${formatPercent(share, decimals = 2)} de l'alliance",
        text = "Temps de ponte ${wage.layingTime} (couveuse + solarium + technique de ponte), rapporté au temps de recherche de " +
            "ta prochaine technique de ponte (niv. $nextTechnique : ${formatSpan(wage.buildTimeSeconds.seconds)}). " +
            "Plus le temps de ponte est haut et la recherche courte, plus le besoin pèse.",
    )
    step(
        "Ta part de la récolte",
        formatAmount(wage.grossWage),
        text = "${formatPercent(share, decimals = 2)} × ${formatAmount(computation.wages.sumOf { it.selfProduction })} bois récoltés par l'alliance",
    )
    step(
        "Solde",
        formatBalance(wage.netWage),
        figureClass = if (wage.netWage > 0) "balance--positive" else null,
        text = "Ta part − ta production propre : " + when {
            wage.netWage > 0 -> "positif, tu reçois"
            wage.netWage < 0 -> "négatif, tu envoies"
            else -> "nul"
        },
    )
    settledStep(mine, wage)?.let { (title, amount, text) ->
        step(title, formatAmount(amount), if (wage.netWage > 0) "balance--positive" else null, text)
    }
}

// What is actually sent or received, when it differs from the balance: each difference says where it comes from
private fun settledStep(mine: Mine, wage: Wage): Triple<String, Long, String>? {
    val settled = wage.settledAmount ?: return null
    if (wage.netWage < 0) {
        val actual = mine.sends.sumOf { it.amount }
        if (actual == -wage.netWage) return null
        if (settled == 0L) {
            return Triple("À envoyer", 0, "Les chefs ont choisi que tu gardes ta part : ${formatAmount(-wage.netWage)} s'ajoutent à ta dette.")
        }
        val reasons = listOfNotNull(
            (wage.netWage - settled).takeIf { it > 0 }?.let { "${formatBalance(it)} : tu rembourses ta dette en envoyant plus." },
            (actual + settled).takeIf { it < 0 }?.let { "${formatBalance(it)} : les receveurs en ont besoin de moins, le reste demeure dans ton grenier." },
        )
        return Triple("À envoyer", actual, reasons.joinToString(" "))
    }
    if (wage.netWage > 0) {
        val actual = mine.receives.sumOf { it.amount }
        if (actual == wage.netWage) return null
        val reasons = listOfNotNull(
            (settled - wage.netWage).takeIf { it < 0 }?.let { "${formatBalance(it)} : tu rembourses ta dette en recevant moins." },
            (actual - settled).takeIf { it < 0 }?.let {
                "${formatBalance(it)} : un livreur cumule sa dette aujourd'hui, le manque est réparti entre les receveurs. " +
                    "Quand il remboursera, ce bois ira aux receveurs du jour."
            },
            (actual - settled).takeIf { it > 0 }?.let { "${formatBalance(it)} : un livreur rembourse sa dette, ce bois est réparti entre les receveurs du jour." },
        )
        return Triple("À recevoir", actual, reasons.joinToString(" "))
    }
    return null
}

// What a colony gives its master, in percent of its production: the master's aphid farm decides it
private fun Wage.colonyPercent(): Int? {
    val base = baseProduction?.takeIf { it > 0 } ?: return null
    val given = -(colonyTransfer ?: return null)
    return (given * 100.0 / base).roundToInt().takeIf { given > 0 }
}

// The level the computation used, found back from its architecture reduction 0.9^level
private fun Wage.layingTechniqueLevel(): Int = (ln(reductionFactor) / ln(0.9)).roundToInt()

// A convoy a chief recorded for the player keeps the chief's name. Late are the convoys sent since for the computation
// before, which are listed after the latest ones
fun FlowContent.convoysCard(
    mine: Mine,
    forms: PlayerForms,
    late: List<Convoy> = emptyList(),
    previous: SavedComputation? = null,
    now: Instant? = null,
) = empCard(cssClass = "me-convoys") {
    titled(MeSection.CONVOYS)
    h2("card-title") {
        id = "${MeSection.CONVOYS.id}-title"
        +(if (forms.own) "Mes convois du calcul" else "Ses convois du calcul")
    }
    if (mine.convoys.isEmpty()) p("text-secondary small") { +"Aucun convoi enregistré pour ce calcul." }
    val rows = mine.convoys.map { it to mine.computation } + late.map { it to previous!!.computation }
    if (rows.isEmpty()) return@empCard
    val previousName = previous?.let { saved -> now?.let { computationName(saved.computation.computedAt, it) } }
    ul("me-list") {
        rows.forEach { (convoy, computation) ->
            val recipient = computation.pseudo(convoy.toPlayerId)
            val chief = convoy.declaredBy?.takeIf { it != convoy.fromPlayerId }
                ?.let { by -> computation.wages.find { it.playerId == by }?.pseudo ?: "un chef" }
            li("emp-row") {
                div("emp-row__text") {
                    span("me-convoy__title") {
                        +"→ $recipient · "
                        span("amount") { +formatAmount(convoy.amount) }
                    }
                    span("small text-secondary") {
                        +(if (convoy.source == ConvoySource.DECLARED) "déclaré " else "collé ")
                        chief?.let { +"par $it " }
                        +formatDateTime(convoy.sentAt)
                        convoy.arrivesAt?.let { +" · ${formatArrival(it)}" }
                        if (computation !== mine.computation) previousName?.let { +" · $it" }
                    }
                }
                deleteForm(listOf(convoy.id), forms) {
                    empBtn(BtnVariant.SUBTLE, BtnSize.COMPACT) {
                        id = "convoy-${convoy.id}"
                        attributes["aria-label"] = "Supprimer le convoi de ${formatAmount(convoy.amount)} à $recipient"
                        +"Supprimer"
                    }
                }
            }
        }
    }
}

private const val CHEVRON_DOWN_ICON =
    """<svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>"""

private const val CLIPBOARD_ICON =
    """<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/></svg>"""

private const val CHECK_ICON =
    """<svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>"""
