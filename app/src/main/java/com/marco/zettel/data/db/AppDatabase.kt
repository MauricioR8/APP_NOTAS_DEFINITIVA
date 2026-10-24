package com.marco.zettel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, StrokeEntity::class, LinkEntity::class, PlacementEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun strokeDao(): StrokeDao
    abstract fun linkDao(): LinkDao
    abstract fun placementDao(): PlacementDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zettel.db")
                .build()
    }
}
