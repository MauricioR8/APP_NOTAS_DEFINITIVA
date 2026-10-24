package com.marco.zettel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marco.zettel.ui.canvas.BoardCanvas
import com.marco.zettel.ui.panel.SidePanel

@Composable
fun ZettelApp(vm: BoardViewModel) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F6FEB))) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                BoardCanvas(vm)
                Toolbar(vm, Modifier.align(Alignment.TopCenter).padding(8.dp))
                vm.tarjetaSel?.let { SidePanel(vm, it, Modifier.align(Alignment.CenterEnd)) }
            }
        }
    }
}
