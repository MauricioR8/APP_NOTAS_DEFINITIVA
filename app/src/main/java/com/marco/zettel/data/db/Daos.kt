package com.marco.zettel.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Fila de un enlace junto con la nota del otro extremo (para backlinks y salientes). */
data class LinkedNoteRow(
    val linkId: Long,
    val relationType: String,
    @Embedded(prefix = "n_") val note: NoteEntity,
)

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE id = :id")
    suspend fun count(id: String): Int

    @Query("SELECT * FROM notes WHERE id IN (SELECT note_id FROM placements WHERE board_id = :boardId)")
    suspend fun notesOfBoard(boardId: String): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    /** Bandeja de entrada: todas las notas fugaces pendientes de procesar. */
    @Query("SELECT * FROM notes WHERE type = 'FLEETING' ORDER BY created_at DESC")
    fun observeInbox(): Flow<List<NoteEntity>>

    /** Huérfanas: sin enlaces (entrantes ni salientes) y sin aparecer en ningún tablero. */
    @Query(
        """SELECT * FROM notes WHERE type != 'INDEX'
           AND id NOT IN (SELECT source_note_id FROM links)
           AND id NOT IN (SELECT target_note_id FROM links)
           AND id NOT IN (SELECT note_id FROM placements)
           ORDER BY created_at DESC""",
    )
    fun observeOrphans(): Flow<List<NoteEntity>>
}

@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    @Query("DELETE FROM strokes WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE strokes SET note_id = :noteId, origin_x = :originX, origin_y = :originY WHERE id = :id")
    suspend fun moveToNote(id: String, noteId: String, originX: Float, originY: Float)

    /** Trazos libres del tablero y los de las tarjetas colocadas en él. */
    @Query(
        """SELECT * FROM strokes WHERE note_id = :boardId
           OR note_id IN (SELECT note_id FROM placements WHERE board_id = :boardId)""",
    )
    suspend fun strokesOfBoard(boardId: String): List<StrokeEntity>
}

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkEntity): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE links SET relation_type = :relation WHERE id = :id")
    suspend fun setRelation(id: Long, relation: String)

    @Query("SELECT * FROM links WHERE source_note_id = :source AND target_note_id = :target")
    suspend fun find(source: String, target: String): LinkEntity?

    /** Enlaces entre notas que están colocadas en el tablero. */
    @Query(
        """SELECT * FROM links
           WHERE source_note_id IN (SELECT note_id FROM placements WHERE board_id = :boardId)
           AND target_note_id IN (SELECT note_id FROM placements WHERE board_id = :boardId)""",
    )
    suspend fun linksOfBoard(boardId: String): List<LinkEntity>

    /** Backlinks: notas que enlazan a [noteId]. */
    @Query(
        """SELECT l.id AS linkId, l.relation_type AS relationType,
                  n.id AS n_id, n.type AS n_type, n.title AS n_title,
                  n.text_content AS n_text_content, n.source AS n_source,
                  n.created_at AS n_created_at, n.updated_at AS n_updated_at
           FROM links l JOIN notes n ON n.id = l.source_note_id
           WHERE l.target_note_id = :noteId ORDER BY n.updated_at DESC""",
    )
    fun observeBacklinks(noteId: String): Flow<List<LinkedNoteRow>>

    /** Enlaces salientes de [noteId]. */
    @Query(
        """SELECT l.id AS linkId, l.relation_type AS relationType,
                  n.id AS n_id, n.type AS n_type, n.title AS n_title,
                  n.text_content AS n_text_content, n.source AS n_source,
                  n.created_at AS n_created_at, n.updated_at AS n_updated_at
           FROM links l JOIN notes n ON n.id = l.target_note_id
           WHERE l.source_note_id = :noteId ORDER BY n.updated_at DESC""",
    )
    fun observeOutgoing(noteId: String): Flow<List<LinkedNoteRow>>
}

@Dao
interface PlacementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(placement: PlacementEntity): Long

    @Query("SELECT * FROM placements WHERE board_id = :boardId ORDER BY z_index")
    suspend fun placementsOfBoard(boardId: String): List<PlacementEntity>

    @Query("SELECT * FROM placements WHERE note_id = :noteId AND board_id = :boardId")
    suspend fun find(noteId: String, boardId: String): PlacementEntity?

    @Query("UPDATE placements SET x = :x, y = :y, width = :w, height = :h, z_index = :z WHERE id = :id")
    suspend fun setGeometry(id: Long, x: Float, y: Float, w: Float, h: Float, z: Int)

    @Query("DELETE FROM placements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(z_index), 0) FROM placements WHERE board_id = :boardId")
    suspend fun maxZ(boardId: String): Int

    /** Tableros donde aparece la nota (transclusión). */
    @Query(
        """SELECT n.* FROM placements p JOIN notes n ON n.id = p.board_id
           WHERE p.note_id = :noteId ORDER BY n.title""",
    )
    fun observeBoardsContaining(noteId: String): Flow<List<NoteEntity>>
}
