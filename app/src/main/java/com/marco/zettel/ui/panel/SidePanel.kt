package com.marco.zettel.ui.panel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marco.zettel.data.db.LinkedNoteRow
import com.marco.zettel.data.db.NoteEntity
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.canvas.CardData

/** Panel lateral de la tarjeta seleccionada: edición de la nota. */
@Composable
fun SidePanel(vm: BoardViewModel, carta: CardData, modifier: Modifier = Modifier) {
    val nota = carta.note
    var titulo by remember(nota.id) { mutableStateOf(nota.title) }
    var texto by remember(nota.id) { mutableStateOf(nota.textContent) }
    var fuente by remember(nota.id) { mutableStateOf(nota.source ?: "") }
    var confirmarBorrado by remember(nota.id) { mutableStateOf(false) }

    Surface(
        modifier = modifier.width(360.dp).fillMaxHeight(),
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Zettel", style = MaterialTheme.typography.labelMedium)
                    Text(nota.id, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { vm.deseleccionar() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar panel")
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteType.entries.forEach { tipo ->
                    FilterChip(
                        selected = carta.tipo == tipo,
                        onClick = { vm.editarNota(carta, tipo = tipo) },
                        label = { Text(tipo.etiqueta) },
                    )
                }
            }

            OutlinedTextField(
                value = titulo,
                onValueChange = {
                    titulo = it
                    vm.editarNota(carta, titulo = it)
                },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = texto,
                onValueChange = {
                    texto = it
                    vm.editarNota(carta, texto = it)
                },
                label = { Text("Contenido") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            if (carta.tipo == NoteType.LITERATURE) {
                OutlinedTextField(
                    value = fuente,
                    onValueChange = {
                        fuente = it
                        vm.editarNota(carta, fuente = it)
                    },
                    label = { Text("Fuente") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (carta.tipo == NoteType.INDEX) {
                Button(onClick = { vm.abrirTablero(nota.id, nota.title) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir tablero")
                }
            }

            HorizontalDivider()
            val entrantes by vm.backlinks.collectAsState()
            val salientes by vm.salientes.collectAsState()
            val tableros by vm.tablerosConNota.collectAsState()
            SeccionEnlaces("Backlinks (${entrantes.size})", "Ninguna nota enlaza a esta todavía.", entrantes) { vm.irANota(it) }
            SeccionEnlaces("Enlaces salientes (${salientes.size})", "Dibuja un conector con la herramienta Conectar.", salientes) { vm.irANota(it) }

            Text("Aparece en ${tableros.size} tablero(s)", style = MaterialTheme.typography.titleSmall)
            tableros.forEach { t ->
                TextButton(onClick = { vm.abrirTablero(t.id, t.title) }) {
                    Text(t.title.ifBlank { t.id })
                }
            }
            HorizontalDivider()

            OutlinedButton(onClick = { vm.quitarDelTablero(carta) }, modifier = Modifier.fillMaxWidth()) {
                Text("Quitar de este tablero")
            }
            Button(onClick = { confirmarBorrado = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Eliminar nota")
            }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Eliminar la nota?") },
            text = { Text("Se borrará de todos los tableros, junto con sus trazos y enlaces. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = false
                    vm.eliminarNota(carta)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

/** Lista de notas relacionadas (backlinks o salientes); al tocar una se navega a ella. */
@Composable
private fun SeccionEnlaces(
    titulo: String,
    vacio: String,
    filas: List<LinkedNoteRow>,
    alTocar: (NoteEntity) -> Unit,
) {
    Text(titulo, style = MaterialTheme.typography.titleSmall)
    if (filas.isEmpty()) {
        Text(vacio, style = MaterialTheme.typography.bodySmall)
    }
    filas.forEach { f ->
        Surface(
            Modifier.fillMaxWidth().clickable { alTocar(f.note) },
            tonalElevation = 2.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(f.note.title.ifBlank { f.note.textContent.lineSequence().firstOrNull().orEmpty().ifBlank { "Sin título" } }, maxLines = 1)
                val relacion = if (f.relationType.isEmpty()) "" else " · ${f.relationType}"
                Text("${NoteType.from(f.note.type).etiqueta} · ${f.note.id}$relacion", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
