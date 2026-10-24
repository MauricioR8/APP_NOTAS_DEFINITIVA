package com.marco.zettel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marco.zettel.domain.model.NoteType

/** Barra de herramientas flotante. */
@Composable
fun Toolbar(vm: BoardViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Herramienta.MOVER).forEach { h ->
                FilterChip(
                    selected = vm.herramienta == h,
                    onClick = { vm.herramienta = h },
                    label = { Text(h.etiqueta) },
                )
            }

            var menu by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { menu = true }) { Text("+ Nueva") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Zettel permanente") },
                        onClick = { menu = false; vm.nuevaTarjeta(NoteType.PERMANENT) },
                    )
                    DropdownMenuItem(
                        text = { Text("Nota de literatura") },
                        onClick = { menu = false; vm.nuevaTarjeta(NoteType.LITERATURE) },
                    )
                    DropdownMenuItem(
                        text = { Text("Tablero (índice)") },
                        onClick = { menu = false; vm.nuevaTarjeta(NoteType.INDEX) },
                    )
                }
            }
            TextButton(onClick = { vm.camara.restablecerZoom(vm.vistaAncho, vm.vistaAlto) }) { Text("1:1") }
        }
    }
}
