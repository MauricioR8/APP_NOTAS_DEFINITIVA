package com.marco.zettel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una nota (zettel). El [id] tiene formato yyyyMMddHHmmss y es permanente.
 * Los tableros son notas de tipo INDEX.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    /** Nombre de [com.marco.zettel.domain.model.NoteType]. */
    val type: String,
    val title: String,
    @ColumnInfo(name = "text_content") val textContent: String,
    /** Fuente bibliográfica (solo notas de literatura). */
    val source: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Un trazo manuscrito. Se guarda el lote de entradas de Jetpack Ink comprimido (BLOB)
 * más los datos del pincel. Las coordenadas son del "mundo" del tablero donde se dibujó;
 * ([originX], [originY]) es la esquina de la tarjeta en ese momento, de modo que el trazo
 * se pinta en coordenadas locales de la nota: posición_tarjeta + (punto - origen).
 * Para trazos libres de un tablero, noteId = id del tablero y el origen es (0, 0).
 */
@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id")],
)
data class StrokeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "brush_family") val brushFamily: String,
    @ColumnInfo(name = "brush_color") val brushColor: Int,
    @ColumnInfo(name = "brush_size") val brushSize: Float,
    @ColumnInfo(name = "brush_epsilon") val brushEpsilon: Float,
    val inputs: ByteArray,
    @ColumnInfo(name = "origin_x") val originX: Float,
    @ColumnInfo(name = "origin_y") val originY: Float,
    @ColumnInfo(name = "min_x") val minX: Float,
    @ColumnInfo(name = "min_y") val minY: Float,
    @ColumnInfo(name = "max_x") val maxX: Float,
    @ColumnInfo(name = "max_y") val maxY: Float,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Enlace dirigido entre dos notas. [relationType] vacío = sin etiqueta. */
@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("source_note_id"),
        Index("target_note_id"),
        Index(value = ["source_note_id", "target_note_id"], unique = true),
    ],
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_note_id") val sourceNoteId: String,
    @ColumnInfo(name = "target_note_id") val targetNoteId: String,
    @ColumnInfo(name = "relation_type") val relationType: String = "",
)

/** Posición de una nota en un tablero. Una nota puede tener varios placements (transclusión). */
@Entity(
    tableName = "placements",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["board_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("note_id"),
        Index("board_id"),
        Index(value = ["note_id", "board_id"], unique = true),
    ],
)
data class PlacementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "board_id") val boardId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    @ColumnInfo(name = "z_index") val zIndex: Int,
)
