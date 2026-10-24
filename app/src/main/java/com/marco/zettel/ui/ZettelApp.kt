package com.marco.zettel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.marco.zettel.ui.inbox.InboxPanel
import com.marco.zettel.ui.panel.PanelEnlace
import com.marco.zettel.ui.panel.SidePanel

@Composable
fun ZettelApp(vm: BoardViewModel) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F6FEB))) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                BoardCanvas(vm)
                Toolbar(vm, Modifier.align(Alignment.TopCenter).padding(8.dp))
                Breadcrumb(vm, Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 76.dp).widthIn(max = 460.dp))
                if (vm.bandejaAbierta) InboxPanel(vm, Modifier.align(Alignment.CenterStart))

                // Atrás: primero cierra selección/lazo, luego sube un nivel en la ruta de tableros.
                BackHandler(enabled = vm.bandejaAbierta || vm.tarjetaSel != null || vm.enlaceSel != null || vm.lazoSel.isNotEmpty() || vm.ruta.size > 1) {
                    when {
                        vm.bandejaAbierta -> vm.bandejaAbierta = false
                        vm.lazoSel.isNotEmpty() -> vm.limpiarLazo()
                        vm.tarjetaSel != null || vm.enlaceSel != null -> vm.deseleccionar()
                        else -> vm.subirNivel()
                    }
                }
                if (vm.lazoSel.isNotEmpty()) {
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(24.dp),
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.lazoAZettel() }) { Text("Convertir en zettel (${vm.lazoSel.size} trazos)") }
                            TextButton(onClick = { vm.limpiarLazo() }) { Text("Cancelar") }
                        }
                    }
                }
                vm.enlaceSel?.let { PanelEnlace(vm, it, Modifier.align(Alignment.CenterEnd)) }
                vm.tarjetaSel?.let { SidePanel(vm, it, Modifier.align(Alignment.CenterEnd)) }
            }
        }
    }
}
