package com.marco.zettel.data.repo

import androidx.room.withTransaction
import com.marco.zettel.data.db.AppDatabase
import com.marco.zettel.data.db.LinkEntity
import com.marco.zettel.data.db.LinkedNoteRow
import com.marco.zettel.data.db.NoteEntity
import com.marco.zettel.data.db.PlacementEntity
import com.marco.zettel.data.db.StrokeEntity
import com.marco.zettel.domain.IdGenerator
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.domain.model.ROOT_BOARD_ID
import kotlinx.coroutines.flow.Flow

/** Todo lo necesario para pintar un tablero. */
class BoardSnapshot(
    val board: NoteEntity,
    val placements: List<PlacementEntity>,
    val notes: Map<String, NoteEntity>,
    val strokes: List<StrokeEntity>,
    val links: List<LinkEntity>,
)

/** Único punto de acceso a los datos; todas las escrituras compuestas son transaccionales. */
class ZettelRepository(private val db: AppDatabase) {
    private val notas = db.noteDao()
    private val trazos = db.strokeDao()
    private val enlaces = db.linkDao()
    private val ubicaciones = db.placementDao()

    /** Crea el tablero raíz la primera vez. */
    suspend fun asegurarRaiz() {
        if (notas.count(ROOT_BOARD_ID) == 0) {
            val ahora = System.currentTimeMillis()
            notas.insert(NoteEntity(ROOT_BOARD_ID, NoteType.INDEX.name, "Inicio", "", null, ahora, ahora))
        }
    }

    suspend fun cargarTablero(boardId: String): BoardSnapshot? = db.withTransaction {
        val board = notas.get(boardId) ?: return@withTransaction null
        BoardSnapshot(
            board = board,
            placements = ubicaciones.placementsOfBoard(boardId),
            notes = notas.notesOfBoard(boardId).associateBy { it.id },
            strokes = trazos.strokesOfBoard(boardId),
            links = enlaces.linksOfBoard(boardId),
        )
    }

    suspend fun obtenerNota(id: String): NoteEntity? = notas.get(id)

    /** Crea una nota con id único permanente. */
    suspend fun crearNota(
        tipo: NoteType,
        titulo: String = "",
        texto: String = "",
        fuente: String? = null,
    ): NoteEntity = db.withTransaction {
        val id = IdGenerator.siguienteLibre { notas.count(it) > 0 }
        val ahora = System.currentTimeMillis()
        val nota = NoteEntity(id, tipo.name, titulo, texto, fuente, ahora, ahora)
        notas.insert(nota)
        nota
    }

    /** Coloca la nota en el tablero; si ya estaba, devuelve la ubicación existente. */
    suspend fun colocar(noteId: String, boardId: String, x: Float, y: Float, w: Float, h: Float): PlacementEntity =
        db.withTransaction {
            ubicaciones.find(noteId, boardId)?.let { return@withTransaction it }
            val z = ubicaciones.maxZ(boardId) + 1
            val p = PlacementEntity(noteId = noteId, boardId = boardId, x = x, y = y, width = w, height = h, zIndex = z)
            p.copy(id = ubicaciones.insert(p))
        }

    suspend fun geometria(p: PlacementEntity) =
        ubicaciones.setGeometry(p.id, p.x, p.y, p.width, p.height, p.zIndex)

    suspend fun quitarUbicacion(id: Long) = ubicaciones.delete(id)

    suspend fun guardarContenido(nota: NoteEntity) =
        notas.update(nota.copy(updatedAt = System.currentTimeMillis()))

    suspend fun eliminarNota(id: String) = notas.delete(id)

    suspend fun guardarTrazo(t: StrokeEntity) = trazos.insert(t)

    suspend fun eliminarTrazos(ids: List<String>) {
        if (ids.isNotEmpty()) trazos.delete(ids)
    }

    suspend fun moverTrazosANota(ids: List<String>, noteId: String, originX: Float, originY: Float) =
        db.withTransaction { ids.forEach { trazos.moveToNote(it, noteId, originX, originY) } }

    /** Crea un enlace real entre dos notas. Devuelve null si ya existía o es la misma nota. */
    suspend fun crearEnlace(origen: String, destino: String, relacion: String = ""): LinkEntity? {
        if (origen == destino) return null
        return db.withTransaction {
            if (enlaces.find(origen, destino) != null) return@withTransaction null
            val l = LinkEntity(sourceNoteId = origen, targetNoteId = destino, relationType = relacion)
            l.copy(id = enlaces.insert(l))
        }
    }

    suspend fun buscarEnlace(origen: String, destino: String) = enlaces.find(origen, destino)

    suspend fun relacionDeEnlace(id: Long, relacion: String) = enlaces.setRelation(id, relacion)

    suspend fun eliminarEnlace(id: Long) = enlaces.delete(id)

    fun bandeja(): Flow<List<NoteEntity>> = notas.observeInbox()
    fun huerfanas(): Flow<List<NoteEntity>> = notas.observeOrphans()
    fun backlinks(noteId: String): Flow<List<LinkedNoteRow>> = enlaces.observeBacklinks(noteId)
    fun salientes(noteId: String): Flow<List<LinkedNoteRow>> = enlaces.observeOutgoing(noteId)
    fun tablerosConNota(noteId: String): Flow<List<NoteEntity>> = ubicaciones.observeBoardsContaining(noteId)
}
