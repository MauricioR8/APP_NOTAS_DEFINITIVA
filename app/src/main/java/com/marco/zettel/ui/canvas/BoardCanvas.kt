package com.marco.zettel.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.Herramienta
import kotlin.math.abs

/** Último toque con el dedo, para detectar el doble toque. */
private class EstadoToque(var t: Long = -10_000L, var x: Float = 0f, var y: Float = 0f)

/**
 * Lienzo infinito. Reparto de entradas:
 *  - dedo: pan + zoom con dos dedos, toque para seleccionar, doble toque para crear/abrir.
 *  - lápiz: según la herramienta activa (pluma dibuja con Jetpack Ink; el resto, ver [gestoLapiz]).
 */
@Composable
fun BoardCanvas(vm: BoardViewModel, modifier: Modifier = Modifier) {
    val escena = vm.escena
    val cam = vm.camara
    val densidad = LocalDensity.current.density
    val medidor = rememberTextMeasurer(cacheSize = 256)
    val renderer = remember { CanvasStrokeRenderer.create() }
    val recursos = remember(medidor, renderer, densidad) { RecursosDibujo(medidor, renderer, densidad) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                vm.tamanoVista(it.width, it.height)
                if (!cam.iniciada) cam.iniciar(densidad, it.width, it.height)
            }
            .pointerInput(vm, cam) {
                val toque = EstadoToque()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    when {
                        down.type == PointerType.Touch -> gestoTactil(down, vm, cam, toque)
                        vm.herramienta != Herramienta.PLUMA -> gestoLapiz(down, vm.herramienta, vm, cam)
                        // PLUMA con lápiz/ratón: lo procesa la capa de tinta (hija de este Box).
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (escena != null && cam.iniciada) dibujarTablero(vm, escena, cam, recursos)
        }
    }
}

/** Pan y zoom con el dedo; un toque corto sin movimiento selecciona. Consume todo para que no deje tinta. */
private suspend fun AwaitPointerEventScope.gestoTactil(
    down: PointerInputChange,
    vm: BoardViewModel,
    cam: Camera,
    toque: EstadoToque,
) {
    down.consume()
    val umbral = viewConfiguration.touchSlop
    var superoUmbral = false
    var panAcum = Offset.Zero
    var zoomAcum = 1f
    var maxDedos = 1
    do {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val dedos = ev.changes.count { it.pressed }
        if (dedos > maxDedos) maxDedos = dedos
        val zoom = ev.calculateZoom()
        val pan = ev.calculatePan()
        val centro = ev.calculateCentroid(useCurrent = true)
        if (!superoUmbral) {
            panAcum += pan
            zoomAcum *= zoom
            val tamano = ev.calculateCentroidSize(useCurrent = false)
            if (abs(1 - zoomAcum) * tamano > umbral || panAcum.getDistance() > umbral) superoUmbral = true
        }
        if (superoUmbral && centro.isSpecified) cam.transformar(centro.x, centro.y, pan.x, pan.y, zoom)
        ev.changes.forEach { it.consume() }
    } while (ev.changes.any { it.pressed })

    if (!superoUmbral && maxDedos == 1) {
        val p = down.position
        val doble = down.uptimeMillis - toque.t < 320 && (p - Offset(toque.x, toque.y)).getDistance() < 60f
        toque.t = if (doble) -10_000L else down.uptimeMillis
        toque.x = p.x
        toque.y = p.y
        vm.toqueTactil(cam.mundoX(p.x), cam.mundoY(p.y), doble)
    }
}

/** Gesto de lápiz (o ratón) para las herramientas que no son la pluma. */
private suspend fun AwaitPointerEventScope.gestoLapiz(
    down: PointerInputChange,
    herramienta: Herramienta,
    vm: BoardViewModel,
    cam: Camera,
) {
    down.consume()
    vm.herramientaAbajo(herramienta, cam.mundoX(down.position.x), cam.mundoY(down.position.y), down.position.x, down.position.y)
    var ultima = down.position
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
        ultima = ch.position
        ch.consume()
        if (!ch.pressed) break
        vm.herramientaMover(herramienta, cam.mundoX(ultima.x), cam.mundoY(ultima.y), ultima.x, ultima.y)
    }
    vm.herramientaArriba(herramienta, cam.mundoX(ultima.x), cam.mundoY(ultima.y))
}
