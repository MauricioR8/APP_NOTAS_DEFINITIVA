package com.marco.zettel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.collectAsState
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
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Herramienta.PLUMA, Herramienta.BORRADOR, Herramienta.LAZO, Herramienta.MOVER, Herramienta.CONECTAR).forEach { h ->
                FilterChip(
                    selected = vm.herramienta == h,
                    onClick = { vm.elegirHerramienta(h) },
                    label = { Text(h.etiqueta) },
                )
            }

            if (vm.herramienta == Herramienta.PLUMA) {
                BoardViewModel.COLORES_TINTA.forEach { c ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(c), CircleShape)
                            .border(if (vm.colorPluma == c) 3.dp else 1.dp, if (vm.colorPluma == c) Color(0xFF1F6FEB) else Color.Gray, CircleShape)
                            .clickable { vm.colorPluma = c },
                    )
                }
                BoardViewModel.GROSORES_TINTA.forEachIndexed { i, g ->
                    FilterChip(
                        selected = vm.grosorPluma == g,
                        onClick = { vm.grosorPluma = g },
                        label = { Text(listOf("Fino", "Medio", "Grueso")[i]) },
                    )
                }
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
            val pendientes by vm.bandeja.collectAsState()
            FilledTonalButton(onClick = { vm.bandejaAbierta = !vm.bandejaAbierta }) {
                Text("Bandeja (${pendientes.size})")
            }
            TextButton(onClick = { vm.camara.restablecerZoom(vm.vistaAncho, vm.vistaAlto) }) { Text("1:1") }
        }
    }
}
