package com.marco.zettel.ui.inbox

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.marco.zettel.data.db.NoteEntity
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.ui.BoardViewModel

/**
 * Bandeja de entrada: captura rápida de notas fugaces y su procesamiento posterior.
 * La segunda pestaña lista las notas huérfanas (sin enlaces y sin tablero).
 */
@Composable
fun InboxPanel(vm: BoardViewModel, modifier: Modifier = Modifier) {
    val bandeja by vm.bandeja.collectAsState()
    val huerfanas by vm.huerfanas.collectAsState()
    var pestana by remember { mutableStateOf(0) }
    var captura by remember { mutableStateOf("") }

    Surface(modifier.width(360.dp).fillMaxHeight(), tonalElevation = 3.dp, shadowElevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bandeja de entrada", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.bandejaAbierta = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar bandeja")
                }
            }

            OutlinedTextField(
                value = captura,
                onValueChange = { captura = it },
                label = { Text("Captura rápida (nota fugaz)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(
                onClick = {
                    vm.capturaRapida(captura)
                    captura = ""
                },
                enabled = captura.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar en la bandeja") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(pestana == 0, { pestana = 0 }, label = { Text("Por procesar (${bandeja.size})") })
                FilterChip(pestana == 1, { pestana = 1 }, label = { Text("Huérfanas (${huerfanas.size})") })
            }

            val lista = if (pestana == 0) bandeja else huerfanas
            if (lista.isEmpty()) {
                Text(
                    if (pestana == 0) "Nada pendiente. Las notas fugaces nuevas aparecen aquí." else "No hay notas huérfanas.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lista, key = { it.id }) { nota -> FilaNota(vm, nota, procesable = pestana == 0) }
            }
        }
    }
}

@Composable
private fun FilaNota(vm: BoardViewModel, nota: NoteEntity, procesable: Boolean) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val titulo = nota.title.ifBlank { nota.textContent.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Sin contenido" }
            Text(titulo, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            Text("${NoteType.from(nota.type).etiqueta} · ${nota.id}", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.colocarNota(nota) }) { Text("Al tablero") }
                if (procesable) {
                    TextButton(onClick = { vm.cambiarTipoNota(nota, NoteType.PERMANENT) }) { Text("→ Permanente") }
                    TextButton(onClick = { vm.cambiarTipoNota(nota, NoteType.LITERATURE) }) { Text("→ Literatura") }
                }
                TextButton(onClick = { vm.eliminarNotaPorId(nota) }) { Text("Eliminar") }
            }
        }
    }
}
