package com.marco.zettel.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Genera ids de nota con formato yyyyMMddHHmmss. Si dos notas se crean en el mismo
 * segundo, se avanza de segundo en segundo hasta encontrar uno libre.
 */
object IdGenerator {
    private val formato = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun format(momento: LocalDateTime): String = momento.format(formato)

    /** Secuencia infinita de ids candidatos a partir de [inicio], un segundo cada uno. */
    fun candidatos(inicio: LocalDateTime): Sequence<String> =
        generateSequence(inicio) { it.plusSeconds(1) }.map(::format)

    /** Primer id candidato que [existe] reporte como libre. */
    suspend fun siguienteLibre(
        inicio: LocalDateTime = LocalDateTime.now(),
        existe: suspend (String) -> Boolean,
    ): String {
        for (id in candidatos(inicio)) {
            if (!existe(id)) return id
        }
        error("inalcanzable")
    }
}
