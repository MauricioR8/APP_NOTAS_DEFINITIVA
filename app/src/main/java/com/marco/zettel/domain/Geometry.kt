package com.marco.zettel.domain

import kotlin.math.hypot

/** Utilidades geométricas puras (sin dependencias de Android, probables en JVM). */
object Geometry {

    /** Punto dentro de polígono (regla par-impar). El polígono son los [n] primeros vértices. */
    fun puntoEnPoligono(px: Float, py: Float, xs: FloatArray, ys: FloatArray, n: Int = xs.size): Boolean {
        var dentro = false
        var j = n - 1
        for (i in 0 until n) {
            val cruza = (ys[i] > py) != (ys[j] > py) &&
                px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]
            if (cruza) dentro = !dentro
            j = i
        }
        return dentro
    }

    /** Distancia de un punto a un segmento. */
    fun distanciaASegmento(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        if (largo2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
