package fr.nathanaudvard.emp.web.pages

import fr.nathanaudvard.emp.domain.model.WageComputation
import kotlin.time.Instant

// Texts copied into the game: private messages and the alliance forum. Plain text with plain spaces, the game's
// pages possibly not keeping the narrow ones

private fun String.plain() = replace(DIGIT_GROUP_SEPARATOR, " ").replace(NBSP, " ")

private fun WageComputation.pseudo(playerId: Long) = wages.single { it.playerId == playerId }.pseudo

// What the giver has left to send of the computation, recipient by recipient. Null when nothing is left
fun reminderText(computation: WageComputation, giverId: Long, nextComputation: Instant, meUrl: String): String? {
    val left = computation.deliveries.filter { it.fromPlayerId == giverId }
        .map { it to it.amount - computation.covered(it) }
        .filter { (_, left) -> left > 0 }
        .sortedByDescending { (_, left) -> left }
        .ifEmpty { return null }
    return buildString {
        appendLine("Salut ${computation.pseudo(giverId)},")
        appendLine()
        appendLine("Il te reste à envoyer pour le calcul du ${formatDateTime(computation.computedAt)} :")
        left.forEach { (delivery, amount) -> appendLine("- ${formatAmount(amount)} à ${computation.pseudo(delivery.toPlayerId)}") }
        appendLine()
        appendLine("Avant le prochain calcul, ${formatDateTime(nextComputation)}.")
        append("Le détail, et « J'ai envoyé » une fois fait, sur ta page : $meUrl")
    }.plain()
}

// The deliveries only, giver by giver: never the levels, nor what the wages come from
fun announcementText(computation: WageComputation): String = buildString {
    appendLine("Salaires du calcul du ${formatDateTime(computation.computedAt)}")
    appendLine()
    if (computation.deliveries.isEmpty()) {
        append("Aucun envoi pour ce calcul.")
        return@buildString
    }
    val givers = computation.deliveries.groupBy { it.fromPlayerId }.entries
        .sortedBy { computation.pseudo(it.key).lowercase() }
    givers.forEachIndexed { index, (giverId, deliveries) ->
        appendLine("${computation.pseudo(giverId)} envoie :")
        deliveries.sortedByDescending { it.amount }.forEach {
            appendLine("- ${formatAmount(it.amount)} à ${computation.pseudo(it.toPlayerId)}")
        }
        if (index < givers.lastIndex) appendLine()
    }
}.trimEnd().plain()
