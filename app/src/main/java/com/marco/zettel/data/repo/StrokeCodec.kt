package com.marco.zettel.data.repo

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.marco.zettel.data.db.StrokeEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Conversión entre [Stroke] de Jetpack Ink y la fila de Room ([StrokeEntity]). */
object StrokeCodec {
    const val PLUMA = "pen"
    const val MARCADOR = "marker"

    fun familia(codigo: String): BrushFamily = when (codigo) {
        MARCADOR -> StockBrushes.marker()
        else -> StockBrushes.pressurePen()
    }

    fun aEntidad(
        stroke: Stroke,
        familia: String,
        noteId: String,
        originX: Float,
        originY: Float,
        id: String = UUID.randomUUID().toString(),
    ): StrokeEntity {
        val salida = ByteArrayOutputStream()
        stroke.inputs.encode(salida)
        val caja = stroke.shape.computeBoundingBox()
        return StrokeEntity(
            id = id,
            noteId = noteId,
            brushFamily = familia,
            brushColor = stroke.brush.colorIntArgb,
            brushSize = stroke.brush.size,
            brushEpsilon = stroke.brush.epsilon,
            inputs = salida.toByteArray(),
            originX = originX,
            originY = originY,
            minX = caja?.xMin ?: 0f,
            minY = caja?.yMin ?: 0f,
            maxX = caja?.xMax ?: 0f,
            maxY = caja?.yMax ?: 0f,
            createdAt = System.currentTimeMillis(),
        )
    }

    fun deEntidad(e: StrokeEntity): Stroke {
        val brush = Brush.createWithColorIntArgb(familia(e.brushFamily), e.brushColor, e.brushSize, e.brushEpsilon)
        val inputs = StrokeInputBatch.decode(ByteArrayInputStream(e.inputs))
        return Stroke(brush, inputs)
    }
}
