package com.marco.zettel.domain.spatial

/** Rectángulo en coordenadas de mundo (puro Kotlin, para poder probarlo en JVM). */
data class WRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun intersects(o: WRect) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    fun contains(o: WRect) = left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun inflate(m: Float) = WRect(left - m, top - m, right + m, bottom + m)
    fun union(o: WRect) = WRect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

/**
 * Índice espacial quadtree para renderizar solo lo visible.
 * Los elementos fuera de [bounds] van a una lista de desborde que siempre se revisa;
 * el llamador reconstruye con [build] cuando el desborde crece.
 */
class Quadtree<T : Any>(
    private val bounds: WRect,
    private val capacidad: Int = 8,
    private val profundidadMax: Int = 10,
) {
    private class Entrada<T>(val item: T, val rect: WRect)

    private inner class Nodo(val area: WRect, val profundidad: Int) {
        val entradas = ArrayList<Entrada<T>>(4)
        var hijos: Array<Nodo>? = null

        fun insertar(e: Entrada<T>) {
            val h = hijos
            if (h != null) {
                val destino = h.firstOrNull { it.area.contains(e.rect) }
                if (destino != null) destino.insertar(e) else entradas.add(e)
                return
            }
            entradas.add(e)
            if (entradas.size > capacidad && profundidad < profundidadMax) dividir()
        }

        private fun dividir() {
            val mx = (area.left + area.right) / 2
            val my = (area.top + area.bottom) / 2
            val p = profundidad + 1
            val nuevos = arrayOf(
                Nodo(WRect(area.left, area.top, mx, my), p),
                Nodo(WRect(mx, area.top, area.right, my), p),
                Nodo(WRect(area.left, my, mx, area.bottom), p),
                Nodo(WRect(mx, my, area.right, area.bottom), p),
            )
            hijos = nuevos
            val previas = ArrayList(entradas)
            entradas.clear()
            for (e in previas) {
                val destino = nuevos.firstOrNull { it.area.contains(e.rect) }
                if (destino != null) destino.insertar(e) else entradas.add(e)
            }
        }

        fun consultar(r: WRect, salida: MutableList<T>) {
            for (e in entradas) if (e.rect.intersects(r)) salida.add(e.item)
            hijos?.forEach { if (it.area.intersects(r)) it.consultar(r, salida) }
        }

        fun quitar(item: T, r: WRect): Boolean {
            val idx = entradas.indexOfFirst { it.item === item }
            if (idx >= 0) {
                entradas.removeAt(idx)
                return true
            }
            hijos?.forEach { if (it.area.intersects(r) && it.quitar(item, r)) return true }
            return false
        }
    }

    private val raiz = Nodo(bounds, 0)
    private val desborde = ArrayList<Entrada<T>>()

    var tamano = 0
        private set

    /** Cantidad de elementos en la lista de desborde (señal para reconstruir). */
    val enDesborde get() = desborde.size

    fun insertar(item: T, rect: WRect) {
        val e = Entrada(item, rect)
        if (bounds.contains(rect)) raiz.insertar(e) else desborde.add(e)
        tamano++
    }

    fun quitar(item: T, rect: WRect): Boolean {
        val ok = raiz.quitar(item, rect) || desborde.removeAll { it.item === item }
        if (ok) tamano--
        return ok
    }

    /** Todos los elementos cuyo rectángulo intersecta [r]. */
    fun consultar(r: WRect, salida: MutableList<T> = ArrayList()): MutableList<T> {
        for (e in desborde) if (e.rect.intersects(r)) salida.add(e.item)
        raiz.consultar(r, salida)
        return salida
    }

    companion object {
        /** Construye un quadtree que abarca todos los elementos con un margen amplio. */
        fun <T : Any> build(elementos: List<Pair<T, WRect>>, margen: Float = 4000f): Quadtree<T> {
            val area = elementos.map { it.second }.reduceOrNull { a, b -> a.union(b) }
                ?.inflate(margen) ?: WRect(-margen, -margen, margen, margen)
            val q = Quadtree<T>(area)
            for ((item, r) in elementos) q.insertar(item, r)
            return q
        }
    }
}
