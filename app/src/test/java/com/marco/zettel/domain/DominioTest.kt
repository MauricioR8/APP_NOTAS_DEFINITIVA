package com.marco.zettel.domain

import com.marco.zettel.domain.spatial.Quadtree
import com.marco.zettel.domain.spatial.WRect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class DominioTest {

    @Test
    fun idTieneFormatoYSeIncrementaSiHayColision() = runBlocking {
        val t = LocalDateTime.of(2026, 10, 24, 11, 37, 59)
        assertEquals("20261024113759", IdGenerator.format(t))
        val ocupados = setOf("20261024113759", "20261024113800")
        val id = IdGenerator.siguienteLibre(t) { it in ocupados }
        assertEquals("20261024113801", id)
    }

    @Test
    fun quadtreeCoincideConFuerzaBruta() {
        val rnd = Random(42)
        val items = (0 until 2000).map {
            val x = rnd.nextFloat() * 10000 - 5000
            val y = rnd.nextFloat() * 10000 - 5000
            it to WRect(x, y, x + rnd.nextFloat() * 200, y + rnd.nextFloat() * 200)
        }
        val q = Quadtree.build(items, margen = 100f)
        repeat(200) {
            val x = rnd.nextFloat() * 10000 - 5000
            val y = rnd.nextFloat() * 10000 - 5000
            val vista = WRect(x, y, x + 1500, y + 1000)
            val esperado = items.filter { it.second.intersects(vista) }.map { it.first }.toSet()
            val obtenido = q.consultar(vista).toSet()
            assertEquals(esperado, obtenido)
        }
    }

    @Test
    fun quadtreeInsertaFueraDeLimitesYQuita() {
        val q = Quadtree<String>(WRect(0f, 0f, 100f, 100f))
        q.insertar("dentro", WRect(10f, 10f, 20f, 20f))
        q.insertar("fuera", WRect(500f, 500f, 510f, 510f))
        assertEquals(2, q.tamano)
        assertEquals(listOf("fuera"), q.consultar(WRect(400f, 400f, 600f, 600f)))
        assertTrue(q.quitar("fuera", WRect(500f, 500f, 510f, 510f)))
        assertTrue(q.consultar(WRect(400f, 400f, 600f, 600f)).isEmpty())
        assertTrue(q.quitar("dentro", WRect(10f, 10f, 20f, 20f)))
        assertFalse(q.quitar("dentro", WRect(10f, 10f, 20f, 20f)))
        assertEquals(0, q.tamano)
    }

    @Test
    fun puntoEnPoligono() {
        val xs = floatArrayOf(0f, 10f, 10f, 0f)
        val ys = floatArrayOf(0f, 0f, 10f, 10f)
        assertTrue(Geometry.puntoEnPoligono(5f, 5f, xs, ys))
        assertFalse(Geometry.puntoEnPoligono(15f, 5f, xs, ys))
    }

    @Test
    fun distanciaASegmento() {
        assertEquals(5f, Geometry.distanciaASegmento(5f, 5f, 0f, 0f, 10f, 0f), 0.001f)
        assertEquals(5f, Geometry.distanciaASegmento(-3f, 4f, 0f, 0f, 10f, 0f), 0.001f)
    }
}
