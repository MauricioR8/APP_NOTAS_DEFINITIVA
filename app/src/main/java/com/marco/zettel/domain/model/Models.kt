package com.marco.zettel.domain.model

/** Tipos de zettel. Los tableros son notas de tipo [INDEX]. */
enum class NoteType(val etiqueta: String) {
    FLEETING("Fugaz"),
    LITERATURE("Literatura"),
    PERMANENT("Permanente"),
    INDEX("Índice");

    companion object {
        fun from(name: String): NoteType = entries.firstOrNull { it.name == name } ?: FLEETING
    }
}

/** Relaciones sugeridas para etiquetar un enlace (cadena vacía = sin etiqueta). */
val RELACIONES = listOf("", "apoya", "contradice", "ejemplo", "amplía", "origen")

/** Id fijo del tablero raíz (nota INDEX creada en el primer arranque). */
const val ROOT_BOARD_ID = "00000000000000"
