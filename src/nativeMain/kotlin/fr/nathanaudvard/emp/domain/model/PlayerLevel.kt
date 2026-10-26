package fr.nathanaudvard.emp.domain.model

enum class PlayerLevel(val label: String) {
    INCUBATOR("Couveuse"),
    SOLARIUM("Solarium"),
    LAYING_TECHNIQUE("Technique de ponte"),
    APHID_FARM("Étable à pucerons"),
    ;

    companion object {
        // Well above real levels, low enough that laying needs stay within Long
        const val MAX_LEVEL = 40
    }
}
