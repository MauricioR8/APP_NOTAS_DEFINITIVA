package com.marco.zettel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.ZettelApp

class MainActivity : ComponentActivity() {

    private val vm: BoardViewModel by viewModels {
        viewModelFactory { initializer { BoardViewModel((application as ZettelApplication).repo) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZettelApp(vm) }
    }

    override fun onStop() {
        // Asegura que las ediciones de texto pendientes llegan a Room.
        vm.vaciarPendientes()
        super.onStop()
    }
}
