package com.marco.zettel.ui.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import com.marco.zettel.data.repo.StrokeCodec
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.Herramienta

/**
 * Capa de escritura de baja latencia (Jetpack Ink: renderizado con front buffer + predicción de
 * movimiento). Solo dibuja con la herramienta PLUMA; con las demás el pincel es null y no hace nada.
 *
 * La matriz píxel->mundo es la misma instancia mutada en sitio por [Camera], así que mover la
 * cámara no recompone este bloque ni reinicia un trazo en curso.
 */
@Composable
fun CapaTinta(vm: BoardViewModel, cam: Camera) {
    val familia = vm.familiaPluma
    val color = vm.colorPluma
    val grosor = vm.grosorPluma
    val pincel = remember(familia, color, grosor) {
        Brush.createWithColorIntArgb(StrokeCodec.familia(familia), color, grosor, EPSILON)
    }
    InProgressStrokes(
        defaultBrush = if (vm.herramienta == Herramienta.PLUMA) pincel else null,
        pointerEventToWorldTransform = cam.matrizPuntero,
        onStrokesFinished = { vm.trazosTerminados(it, familia) },
    )
}

/** Fidelidad del trazo en unidades de mundo (1 unidad = 1 dp a zoom 1). */
private const val EPSILON = 0.1f
