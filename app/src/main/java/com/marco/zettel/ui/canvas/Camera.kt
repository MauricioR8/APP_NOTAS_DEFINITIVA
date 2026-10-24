package com.marco.zettel.ui.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Matrix

/**
 * Cámara del canvas infinito. Convención: pantalla_px = mundo * [scale] + (ox, oy).
 * Con scale = densidad, 1 unidad de mundo = 1 dp. Solo se lee en la fase de dibujo,
 * así pan/zoom no provoca recomposición.
 */
@Stable
class Camera {
    var scale by mutableFloatStateOf(1f)
        private set
    var ox by mutableFloatStateOf(0f)
        private set
    var oy by mutableFloatStateOf(0f)
        private set

    var iniciada = false
        private set
    private var densidad = 1f

    /**
     * Matriz píxel->mundo para Jetpack Ink. Es SIEMPRE la misma instancia (se muta en sitio)
     * para que mover la cámara no reinicie el procesamiento del lápiz a mitad de un trazo.
     */
    val matrizPuntero = Matrix()

    fun iniciar(densidad: Float, ancho: Int, alto: Int) {
        this.densidad = densidad
        scale = densidad
        ox = ancho * 0.08f
        oy = alto * 0.14f
        iniciada = true
        sincronizar()
    }

    fun mundoX(px: Float) = (px - ox) / scale
    fun mundoY(py: Float) = (py - oy) / scale

    fun desplazar(dx: Float, dy: Float) {
        ox += dx
        oy += dy
        sincronizar()
    }

    /** Zoom manteniendo fijo el punto de pantalla (cx, cy). */
    fun zoomEn(cx: Float, cy: Float, factor: Float) {
        val nueva = (scale * factor).coerceIn(densidad * ZOOM_MIN, densidad * ZOOM_MAX)
        val real = nueva / scale
        ox = cx - (cx - ox) * real
        oy = cy - (cy - oy) * real
        scale = nueva
        sincronizar()
    }

    fun transformar(cx: Float, cy: Float, panX: Float, panY: Float, zoom: Float) {
        if (zoom != 1f) zoomEn(cx, cy, zoom)
        desplazar(panX, panY)
    }

    /** Centra el punto de mundo (wx, wy) en la vista. */
    fun centrarEn(wx: Float, wy: Float, ancho: Int, alto: Int) {
        ox = ancho / 2f - wx * scale
        oy = alto / 2f - wy * scale
        sincronizar()
    }

    fun restablecerZoom(ancho: Int, alto: Int) {
        val wx = mundoX(ancho / 2f)
        val wy = mundoY(alto / 2f)
        scale = densidad
        centrarEn(wx, wy, ancho, alto)
    }

    private fun sincronizar() {
        val v = matrizPuntero.values
        val inv = 1f / scale
        v[Matrix.ScaleX] = inv
        v[Matrix.ScaleY] = inv
        v[Matrix.TranslateX] = -ox * inv
        v[Matrix.TranslateY] = -oy * inv
    }

    companion object {
        const val ZOOM_MIN = 0.08f
        const val ZOOM_MAX = 8f
    }
}
