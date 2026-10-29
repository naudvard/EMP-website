package fr.nathanaudvard.emp.domain.model

import kotlin.time.Instant

// What became of the convoys of a pasted text. Nothing at all when it held no convoy of materials
data class ConvoyPaste(
    val recorded: List<RecordedConvoy> = emptyList(),
    val duplicates: List<DuplicateConvoy> = emptyList(),
    val refused: List<RefusedConvoy> = emptyList(),
) {
    val nothing: Boolean get() = recorded.isEmpty() && duplicates.isEmpty() && refused.isEmpty()
}

data class RecordedConvoy(val id: Long, val recipient: String, val amount: Long, val arrivesAt: Instant?)

// Recorded before, at recordedAt
data class DuplicateConvoy(val recipient: String, val amount: Long, val recordedAt: Instant)

// Towards a player no delivery of the sender is planned to
data class RefusedConvoy(val recipient: String, val amount: Long)
