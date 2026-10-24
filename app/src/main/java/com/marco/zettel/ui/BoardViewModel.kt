package com.marco.zettel.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marco.zettel.data.db.NoteEntity
import com.marco.zettel.data.repo.BoardSnapshot
import com.marco.zettel.data.repo.StrokeCodec
import com.marco.zettel.data.repo.ZettelRepository
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.domain.model.ROOT_BOARD_ID
import com.marco.zettel.domain.spatial.WRect
import com.marco.zettel.ui.canvas.BoardScene
import com.marco.zettel.ui.canvas.Camera
import com.marco.zettel.ui.canvas.CardData
import com.marco.zettel.ui.canvas.LinkData
import com.marco.zettel.ui.canvas.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Herramientas del lápiz. El dedo siempre hace pan/zoom/toque. */
enum class Herramienta(val etiqueta: String) {
    PLUMA("Pluma"),
    BORRADOR("Borrador"),
    LAZO("Lazo"),
    MOVER("Mover"),
    CONECTAR("Conectar"),
}

/**
 * Estado del tablero abierto. La escena vive en memoria y es la fuente de verdad en pantalla;
 * las escrituras a Room se encolan en un único canal para conservar el orden sin bloquear la UI.
 */
class BoardViewModel(private val repo: ZettelRepository) : ViewModel() {

    var escena by mutableStateOf<BoardScene?>(null)
        private set
    var camara by mutableStateOf(Camera())
        private set
    var herramienta by mutableStateOf(Herramienta.PLUMA)

    // Pincel actual (la tinta es lo primero: la app arranca con la pluma activa).
    var familiaPluma by mutableStateOf(StrokeCodec.PLUMA)
    var colorPluma by mutableIntStateOf(COLORES_TINTA[0])
    var grosorPluma by mutableFloatStateOf(2.5f)

    var tarjetaSel by mutableStateOf<CardData?>(null)
        private set

    var vistaAncho = 0
        private set
    var vistaAlto = 0
        private set

    private val camaras = HashMap<String, Camera>()

    // ---- Escritura serializada a Room ------------------------------------------------------
    private val escritor = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private fun persistir(tarea: suspend () -> Unit) {
        escritor.trySend(tarea)
    }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (tarea in escritor) {
                try {
                    tarea()
                } catch (e: Exception) {
                    Log.e("Zettel", "Fallo al guardar", e)
                }
            }
        }
        viewModelScope.launch {
            repo.asegurarRaiz()
            cargar(ROOT_BOARD_ID)
        }
    }

    // ---- Carga de tableros -----------------------------------------------------------------
    private suspend fun cargar(id: String): Boolean {
        val snap = repo.cargarTablero(id) ?: return false
        val nueva = withContext(Dispatchers.Default) { construirEscena(snap) }
        deseleccionar()
        camara = camaras.getOrPut(id) { Camera() }
        escena = nueva
        return true
    }

    private fun construirEscena(snap: BoardSnapshot): BoardScene {
        val e = BoardScene(snap.board.id, snap.board)
        val trazos = snap.strokes.mapNotNull {
            try {
                StrokeData(it, StrokeCodec.deEntidad(it))
            } catch (ex: Exception) {
                Log.e("Zettel", "Trazo ilegible ${it.id}", ex)
                null
            }
        }.groupBy { it.entity.noteId }
        for (p in snap.placements) {
            val nota = snap.notes[p.noteId] ?: continue
            val c = CardData(p.id, nota, p.x, p.y, p.width, p.height, p.zIndex)
            // Los trazos de una nota INDEX son los trazos libres de su propio tablero: no van en la tarjeta.
            if (c.tipo != NoteType.INDEX) trazos[nota.id]?.let { c.strokes.addAll(it) }
            e.tarjetas.add(c)
            e.tarjetaPorNota[nota.id] = c
        }
        trazos[snap.board.id]?.let { e.trazosLibres.addAll(it) }
        for (l in snap.links) {
            if (e.tarjetaPorNota.containsKey(l.sourceNoteId) && e.tarjetaPorNota.containsKey(l.targetNoteId)) {
                e.enlaces.add(LinkData(l.id, l.sourceNoteId, l.targetNoteId, l.relationType))
            }
        }
        e.reindexar()
        return e
    }

    fun tamanoVista(ancho: Int, alto: Int) {
        vistaAncho = ancho
        vistaAlto = alto
    }

    /** Centro de la vista actual en coordenadas de mundo. */
    private fun centroVista(): Pair<Float, Float> =
        camara.mundoX(vistaAncho / 2f) to camara.mundoY(vistaAlto / 2f)

    // ---- Selección -------------------------------------------------------------------------
    fun seleccionar(c: CardData) {
        tarjetaSel = c
        val e = escena ?: return
        if (e.alFrente(c)) persistir { repo.geometria(c.aUbicacion(e.boardId)) }
    }

    fun deseleccionar() {
        tarjetaSel = null
    }

    // ---- Tarjetas --------------------------------------------------------------------------
    fun nuevaTarjeta(tipo: NoteType, wx: Float? = null, wy: Float? = null) {
        val e = escena ?: return
        val (cx, cy) = if (wx != null && wy != null) wx to wy else centroVista()
        viewModelScope.launch {
            val nota = repo.crearNota(tipo)
            val w = ANCHO_TARJETA
            val h = ALTO_TARJETA
            val p = repo.colocar(nota.id, e.boardId, cx - w / 2, cy - h / 2, w, h)
            if (escena !== e) return@launch
            val c = CardData(p.id, nota, p.x, p.y, p.width, p.height, p.zIndex)
            e.agregarTarjeta(c)
            seleccionar(c)
        }
    }

    // Las ediciones de texto se agrupan: se guarda 500 ms después de la última tecla.
    private val pendientes = LinkedHashMap<String, NoteEntity>()
    private var jobGuardado: Job? = null

    fun editarNota(
        c: CardData,
        titulo: String? = null,
        texto: String? = null,
        fuente: String? = null,
        tipo: NoteType? = null,
    ) {
        val n = c.note.copy(
            title = titulo ?: c.note.title,
            textContent = texto ?: c.note.textContent,
            source = fuente ?: c.note.source,
            type = tipo?.name ?: c.note.type,
        )
        c.note = n
        pendientes[n.id] = n
        jobGuardado?.cancel()
        jobGuardado = viewModelScope.launch {
            delay(500)
            vaciarPendientes()
        }
        escena?.redibujar()
    }

    /** Envía a Room las ediciones pendientes (también se llama al salir de la app). */
    fun vaciarPendientes() {
        val copia = pendientes.values.toList()
        pendientes.clear()
        copia.forEach { n -> persistir { repo.guardarContenido(n) } }
    }

    fun quitarDelTablero(c: CardData) {
        val e = escena ?: return
        e.quitarTarjeta(c)
        if (tarjetaSel === c) deseleccionar()
        persistir { repo.quitarUbicacion(c.placementId) }
    }

    /** Elimina la nota por completo (de todos los tableros, con sus enlaces y trazos). */
    fun eliminarNota(c: CardData) {
        val e = escena ?: return
        e.quitarTarjeta(c)
        if (tarjetaSel === c) deseleccionar()
        pendientes.remove(c.note.id)
        persistir { repo.eliminarNota(c.note.id) }
    }

    // ---- Toque con el dedo -----------------------------------------------------------------
    fun toqueTactil(wx: Float, wy: Float, doble: Boolean) {
        val e = escena ?: return
        val c = e.tarjetaEn(wx, wy)
        if (c != null) {
            seleccionar(c)
        } else {
            deseleccionar()
            if (doble) nuevaTarjeta(NoteType.PERMANENT, wx, wy)
        }
    }

    // ---- Herramienta MOVER con el lápiz ----------------------------------------------------
    private enum class Modo { NADA, MOVER, REDIMENSIONAR, PANEAR }

    private var modo = Modo.NADA
    private var objetivo: CardData? = null
    private var offX = 0f
    private var offY = 0f
    private var ultPx = 0f
    private var ultPy = 0f

    fun herramientaAbajo(h: Herramienta, wx: Float, wy: Float, px: Float, py: Float) {
        val e = escena ?: return
        ultPx = px
        ultPy = py
        modo = Modo.NADA
        when (h) {
            Herramienta.MOVER -> {
                val sel = tarjetaSel
                val tol = TOLERANCIA_PX / camara.scale
                if (sel != null && kotlin.math.abs(wx - (sel.x + sel.w)) < tol && kotlin.math.abs(wy - (sel.y + sel.h)) < tol) {
                    modo = Modo.REDIMENSIONAR
                    objetivo = sel
                    return
                }
                val c = e.tarjetaEn(wx, wy)
                if (c != null) {
                    seleccionar(c)
                    modo = Modo.MOVER
                    objetivo = c
                    offX = wx - c.x
                    offY = wy - c.y
                } else {
                    deseleccionar()
                    modo = Modo.PANEAR
                }
            }
            Herramienta.BORRADOR -> borrarEn(wx, wy)
            Herramienta.LAZO -> {
                limpiarLazo()
                anadirPuntoLazo(wx, wy)
            }
            else -> Unit
        }
    }

    fun herramientaMover(h: Herramienta, wx: Float, wy: Float, px: Float, py: Float) {
        val e = escena ?: return
        if (h == Herramienta.BORRADOR) {
            borrarEn(wx, wy)
            return
        }
        if (h == Herramienta.LAZO) {
            anadirPuntoLazo(wx, wy)
            e.redibujar()
            return
        }
        when (modo) {
            Modo.MOVER -> objetivo?.let { e.geometria(it, wx - offX, wy - offY, it.w, it.h) }
            Modo.REDIMENSIONAR -> objetivo?.let {
                e.geometria(it, it.x, it.y, (wx - it.x).coerceAtLeast(MIN_ANCHO), (wy - it.y).coerceAtLeast(MIN_ALTO))
            }
            Modo.PANEAR -> camara.desplazar(px - ultPx, py - ultPy)
            Modo.NADA -> Unit
        }
        ultPx = px
        ultPy = py
    }

    fun herramientaArriba(h: Herramienta, wx: Float, wy: Float) {
        val e = escena
        val c = objetivo
        if (e != null && c != null && (modo == Modo.MOVER || modo == Modo.REDIMENSIONAR)) {
            persistir { repo.geometria(c.aUbicacion(e.boardId)) }
        }
        modo = Modo.NADA
        objetivo = null
        if (h == Herramienta.LAZO) cerrarLazo()
        if (h == Herramienta.BORRADOR && borrados.isNotEmpty()) {
            val ids = borrados.toList()
            borrados.clear()
            persistir { repo.eliminarTrazos(ids) }
        }
    }

    // ---- Tinta -----------------------------------------------------------------------------

    /**
     * Jetpack Ink entrega los trazos terminados en el hilo de UI; hay que añadirlos a la escena
     * en esa misma pasada para que no parpadeen. Si el trazo empieza dentro de una tarjeta (que no
     * sea un tablero) pertenece a esa nota; si no, es un trazo libre del tablero.
     */
    fun trazosTerminados(lista: List<Stroke>, familia: String) {
        val e = escena ?: return
        for (st in lista) {
            if (st.inputs.size == 0) continue
            val primero = st.inputs[0]
            val tarjeta = e.tarjetaEn(primero.x, primero.y)?.takeIf { it.tipo != NoteType.INDEX }
            val entidad = StrokeCodec.aEntidad(
                stroke = st,
                familia = familia,
                noteId = tarjeta?.note?.id ?: e.boardId,
                originX = tarjeta?.x ?: 0f,
                originY = tarjeta?.y ?: 0f,
            )
            val datos = StrokeData(entidad, st)
            if (tarjeta != null) tarjeta.strokes.add(datos) else e.agregarTrazoLibre(datos)
            persistir { repo.guardarTrazo(entidad) }
        }
        e.redibujar()
    }

    // ---- Lazo ------------------------------------------------------------------------------
    // El trazo del lazo se guarda en arrays simples (no es estado) y se redibuja con escena.redibujar().
    internal var lazoXs = FloatArray(256)
    internal var lazoYs = FloatArray(256)
    internal var lazoN = 0

    /** Trazos libres encerrados por el último lazo. */
    var lazoSel by mutableStateOf<List<StrokeData>>(emptyList())
        private set

    fun elegirHerramienta(h: Herramienta) {
        if (h != Herramienta.LAZO) limpiarLazo()
        herramienta = h
    }

    fun limpiarLazo() {
        lazoN = 0
        if (lazoSel.isNotEmpty()) lazoSel = emptyList()
        escena?.redibujar()
    }

    private fun anadirPuntoLazo(wx: Float, wy: Float) {
        if (lazoN == lazoXs.size) {
            lazoXs = lazoXs.copyOf(lazoN * 2)
            lazoYs = lazoYs.copyOf(lazoN * 2)
        }
        lazoXs[lazoN] = wx
        lazoYs[lazoN] = wy
        lazoN++
    }

    /** Al soltar el lápiz: selecciona los trazos libres con más de la mitad de sus puntos dentro. */
    private fun cerrarLazo() {
        val e = escena ?: return
        if (lazoN < 3) {
            limpiarLazo()
            return
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until lazoN) {
            minX = minOf(minX, lazoXs[i]); maxX = maxOf(maxX, lazoXs[i])
            minY = minOf(minY, lazoYs[i]); maxY = maxOf(maxY, lazoYs[i])
        }
        val zona = WRect(minX, minY, maxX, maxY)
        val p = FloatArray(2)
        val elegidos = ArrayList<StrokeData>()
        for (item in e.arbol.consultar(zona)) {
            if (item !is StrokeData || !item.bounds.intersects(zona)) continue
            var dentro = 0
            val n = item.numPuntos
            for (i in 0 until n) {
                item.punto(i, 0f, 0f, p)
                if (com.marco.zettel.domain.Geometry.puntoEnPoligono(p[0], p[1], lazoXs, lazoYs, lazoN)) dentro++
            }
            if (n > 0 && dentro * 2 >= n) elegidos.add(item)
        }
        lazoN = 0
        lazoSel = elegidos
        e.redibujar()
    }

    /** Convierte los trazos seleccionados con el lazo en un zettel nuevo (fugaz) sobre el tablero. */
    fun lazoAZettel() {
        val e = escena ?: return
        val sel = lazoSel
        if (sel.isEmpty()) return
        var caja = sel.first().bounds
        sel.forEach { caja = caja.union(it.bounds) }
        val margen = 16f
        val x = caja.left - margen
        val y = caja.top - margen
        val w = maxOf(caja.width + 2 * margen, MIN_ANCHO)
        val h = maxOf(caja.height + 2 * margen, MIN_ALTO)
        limpiarLazo()
        viewModelScope.launch {
            val nota = repo.crearNota(NoteType.FLEETING)
            val ub = repo.colocar(nota.id, e.boardId, x, y, w, h)
            if (escena !== e) return@launch
            val c = CardData(ub.id, nota, ub.x, ub.y, ub.width, ub.height, ub.zIndex)
            for (t in sel) {
                e.quitarTrazoLibre(t)
                // El trazo conserva sus coordenadas; solo cambia de dueño y de origen.
                t.entity = t.entity.copy(noteId = nota.id, originX = ub.x, originY = ub.y)
                c.strokes.add(t)
            }
            e.agregarTarjeta(c)
            persistir { repo.moverTrazosANota(sel.map { it.id }, nota.id, ub.x, ub.y) }
            seleccionar(c)
        }
    }

    // ---- Borrador --------------------------------------------------------------------------
    private val borrados = ArrayList<String>()

    private fun borrarEn(wx: Float, wy: Float) {
        val e = escena ?: return
        val r = RADIO_BORRADOR_PX / camara.scale
        val zona = WRect(wx - r, wy - r, wx + r, wy + r)
        var cambio = false
        for (item in e.arbol.consultar(zona)) {
            when (item) {
                is StrokeData -> if (item.bounds.intersects(zona) &&
                    item.cercaDe(wx, wy, r + item.entity.brushSize / 2, 0f, 0f)
                ) {
                    e.quitarTrazoLibre(item)
                    borrados.add(item.id)
                    cambio = true
                }
                is CardData -> if (item.rect.contains(wx, wy)) {
                    val it2 = item.strokes.iterator()
                    while (it2.hasNext()) {
                        val t = it2.next()
                        val dx = item.x - t.entity.originX
                        val dy = item.y - t.entity.originY
                        if (t.cercaDe(wx, wy, r + t.entity.brushSize / 2, dx, dy)) {
                            it2.remove()
                            borrados.add(t.id)
                            cambio = true
                        }
                    }
                }
            }
        }
        if (cambio) e.redibujar()
    }

    override fun onCleared() {
        vaciarPendientes()
        super.onCleared()
    }

    companion object {
        const val ANCHO_TARJETA = 260f
        const val ALTO_TARJETA = 170f
        const val MIN_ANCHO = 110f
        const val MIN_ALTO = 70f
        const val TOLERANCIA_PX = 40f
        const val RADIO_BORRADOR_PX = 18f

        /** Colores de tinta (ARGB): negro, azul, rojo, verde. */
        val COLORES_TINTA = intArrayOf(0xFF1A1A1A.toInt(), 0xFF1F5FD0.toInt(), 0xFFD0342C.toInt(), 0xFF1E8E3E.toInt())
        val GROSORES_TINTA = floatArrayOf(1.5f, 2.5f, 5f)
    }
}
