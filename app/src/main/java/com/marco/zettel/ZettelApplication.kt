package com.marco.zettel

import android.app.Application
import com.marco.zettel.data.db.AppDatabase
import com.marco.zettel.data.repo.ZettelRepository

/** Contenedor de dependencias manual (sin Hilt, para mantener la compilación rápida). */
class ZettelApplication : Application() {
    val db by lazy { AppDatabase.create(this) }
    val repo by lazy { ZettelRepository(db) }
}
