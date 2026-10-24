package com.marco.zettel.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.marco.zettel.data.db.NoteEntity
import com.marco.zettel.data.db.PlacementEntity
import com.marco.zettel.data.db.StrokeEntity
import com.marco.zettel.domain.Geometry
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.domain.spatial.Quadtree
import com.marco.zettel.domain.spatial.WRect

/** Un trazo decodificado y listo para pintar. */
class StrokeData(var entity: StrokeEntity, val stroke: Stroke) {
    val id get() = entity.id

    /** Caja en el marco de coordenadas en que se dibujó, con margen por el grosor. */
    val bounds = WRect(entity.minX, entity.minY, entity.maxX, entity.maxY).inflate(entity.brushSize)

    private val tmp = StrokeInput()

    val numPuntos get() = stroke.inputs.size

    /** Escribe el punto [i] (desplazado por dx, dy) en [salida] = (x, y). */
    fun punto(i: Int, dx: Float, dy: Float, salida: FloatArray) {
        stroke.inputs.populate(i, tmp)
        salida[0] = tmp.x + dx
        salida[1] = tmp.y + dy
    }

    /** ¿Algún tramo del trazo pasa a menos de [radio] de (wx, wy)? */
    fun cercaDe(wx: Float, wy: Float, radio: Float, dx: Float, dy: Float): Boolean {
        val n = numPuntos
        if (n == 0) return false
        val a = FloatArray(2)
        val b = FloatArray(2)
        punto(0, dx, dy, a)
        if (n == 1) return Geometry.distanciaASegmento(wx, wy, a[0], a[1], a[0], a[1]) <= radio
        for (i in 1 until n) {
            punto(i, dx, dy, b)
            if (Geometry.distanciaASegmento(wx, wy, a[0], a[1], b[0], b[1]) <= radio) return true
            a[0] = b[0]
            a[1] = b[1]
        }
        return false
    }
}

/** Una nota colocada en el tablero (su ubicación + la nota + sus trazos). */
class CardData(
    val placementId: Long,
    note: NoteEntity,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var z: Int,
) {
    /** Estado observable: cambiar la nota invalida el dibujo y el panel. */
    var note by mutableStateOf(note)
    val strokes = ArrayList<StrokeData>()

    val tipo get() = NoteType.from(note.type)
    val rect get() = WRect(x, y, x + w, y + h)

    fun aUbicacion(boardId: String) = PlacementEntity(placementId, note.id, boardId, x, y, w, h, z)
}

/** Enlace entre dos notas, dibujado como conector cuando ambas están en el tablero. */
class LinkData(val id: Long, val origen: String, val destino: String, relacion: String) {
    var relacion by mutableStateOf(relacion)
    var enSeleccion by mutableStateOf(false)
}

/**
 * Contenido de un tablero en memoria, con un quadtree para consultar solo lo visible.
 * Se accede únicamente desde el hilo de UI.
 */
class BoardScene(val boardId: String, val board: NoteEntity) {
    val tarjetas = ArrayList<CardData>()
    val tarjetaPorNota = HashMap<String, CardData>()
    val trazosLibres = ArrayList<StrokeData>()
    val enlaces = ArrayList<LinkData>()

    var arbol: Quadtree<Any> = Quadtree.build(emptyList())
        private set

    /** Se incrementa en cada cambio; el dibujo lo lee para invalidarse. */
    var revision by mutableIntStateOf(0)
        private set

    fun redibujar() {
        revision++
    }

    fun reindexar() {
        val items = ArrayList<Pair<Any, WRect>>(tarjetas.size + trazosLibres.size)
        tarjetas.forEach { items.add(it to it.rect) }
        trazosLibres.forEach { items.add(it to it.bounds) }
        arbol = Quadtree.build(items)
    }

    private fun comprobarDesborde() {
        if (arbol.enDesborde > 256) reindexar()
    }

    fun agregarTarjeta(c: CardData) {
        tarjetas.add(c)
        tarjetaPorNota[c.note.id] = c
        arbol.insertar(c, c.rect)
        comprobarDesborde()
        redibujar()
    }

    fun quitarTarjeta(c: CardData) {
        tarjetas.remove(c)
        tarjetaPorNota.remove(c.note.id)
        arbol.quitar(c, c.rect)
        enlaces.removeAll { it.origen == c.note.id || it.destino == c.note.id }
        redibujar()
    }

    fun agregarTrazoLibre(s: StrokeData) {
        trazosLibres.add(s)
        arbol.insertar(s, s.bounds)
        comprobarDesborde()
        redibujar()
    }

    fun quitarTrazoLibre(s: StrokeData) {
        trazosLibres.remove(s)
        arbol.quitar(s, s.bounds)
        redibujar()
    }

    /** Mueve/redimensiona una tarjeta manteniendo el índice espacial al día. */
    fun geometria(c: CardData, x: Float, y: Float, w: Float, h: Float) {
        arbol.quitar(c, c.rect)
        c.x = x
        c.y = y
        c.w = w
        c.h = h
        arbol.insertar(c, c.rect)
        comprobarDesborde()
        redibujar()
    }

    /** Sube la tarjeta al frente; devuelve true si cambió su z. */
    fun alFrente(c: CardData): Boolean {
        val max = tarjetas.maxOfOrNull { it.z } ?: 0
        if (c.z == max && tarjetas.count { it.z == max } == 1) return false
        c.z = max + 1
        redibujar()
        return true
    }

    /** Tarjeta superior en el punto de mundo (wx, wy), o null. */
    fun tarjetaEn(wx: Float, wy: Float): CardData? {
        var mejor: CardData? = null
        for (it in arbol.consultar(WRect(wx, wy, wx, wy))) {
            if (it is CardData && it.rect.contains(wx, wy) && (mejor == null || it.z >= mejor.z)) mejor = it
        }
        return mejor
    }

    fun enlaceEn(wx: Float, wy: Float, tolerancia: Float): LinkData? {
        var mejor: LinkData? = null
        var mejorD = tolerancia
        for (l in enlaces) {
            val a = tarjetaPorNota[l.origen] ?: continue
            val b = tarjetaPorNota[l.destino] ?: continue
            val seg = SegmentoEnlace.entre(a, b)
            val d = Geometry.distanciaASegmento(wx, wy, seg.x1, seg.y1, seg.x2, seg.y2)
            if (d <= mejorD) {
                mejor = l
                mejorD = d
            }
        }
        return mejor
    }
}

/** Segmento de un conector, recortado a los bordes de las tarjetas. */
class SegmentoEnlace(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    companion object {
        fun entre(a: CardData, b: CardData): SegmentoEnlace {
            val ax = a.x + a.w / 2
            val ay = a.y + a.h / 2
            val bx = b.x + b.w / 2
            val by = b.y + b.h / 2
            val (x1, y1) = bordeHacia(a, bx, by)
            val (x2, y2) = bordeHacia(b, ax, ay)
            return SegmentoEnlace(x1, y1, x2, y2)
        }

        /** Punto donde el rayo desde el centro de [c] hacia (tx, ty) sale de la tarjeta. */
        fun bordeHacia(c: CardData, tx: Float, ty: Float): Pair<Float, Float> {
            val cx = c.x + c.w / 2
            val cy = c.y + c.h / 2
            val dx = tx - cx
            val dy = ty - cy
            if (dx == 0f && dy == 0f) return cx to cy
            val sx = if (dx != 0f) (c.w / 2) / kotlin.math.abs(dx) else Float.MAX_VALUE
            val sy = if (dy != 0f) (c.h / 2) / kotlin.math.abs(dy) else Float.MAX_VALUE
            val t = minOf(sx, sy)
            return (cx + dx * t) to (cy + dy * t)
        }
    }
}
