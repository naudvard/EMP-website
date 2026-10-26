package fr.nathanaudvard.emp.service

// What the game says once a convoy is launched, as
// "Vous allez livrer 0 nourritures et 200 000 materiaux à Alcaz dans 10H 46m 4s - Retour le 27 oct. à 08h39 - Annuler"
data class ConvoyMessage(val recipient: String, val materials: Long)

// Thousands may be separated by any space, non-breaking ones included when copied from the game
private const val NUMBER = """(\d[\d\s  .]*)"""

private val CONVOY = Regex(
    """livrer\s+$NUMBER\s+nourritures?\s+et\s+$NUMBER\s+mat[ée]riaux\s+[àa]\s+(\S+)\s+dans""",
    RegexOption.IGNORE_CASE,
)

// Every convoy of the text, several lines being pasted at once. Convoys of food only carry no wage
fun parseConvoyMessages(text: String): List<ConvoyMessage> =
    CONVOY.findAll(text).mapNotNull { match ->
        val (_, materials, recipient) = match.destructured
        materials.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0 }?.let { ConvoyMessage(recipient, it) }
    }.toList()
