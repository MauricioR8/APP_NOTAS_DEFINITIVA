package com.marco.zettel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Ruta de tableros abiertos; tocar un nivel anterior vuelve a él. */
@Composable
fun Breadcrumb(vm: BoardViewModel, modifier: Modifier = Modifier) {
    val ruta = vm.ruta
    var renombrando by remember { mutableStateOf(false) }

    Surface(modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ruta.forEachIndexed { i, m ->
                val ultimo = i == ruta.lastIndex
                if (ultimo) {
                    Text(m.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { renombrando = true }) { Text("Renombrar") }
                } else {
                    TextButton(onClick = { vm.irARuta(i) }) { Text(m.titulo) }
                    Text("›", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (renombrando) {
        var texto by remember { mutableStateOf(ruta.last().titulo.takeIf { it != "Sin título" } ?: "") }
        AlertDialog(
            onDismissRequest = { renombrando = false },
            title = { Text("Nombre del tablero") },
            text = { OutlinedTextField(texto, { texto = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.renombrarTableroActual(texto)
                    renombrando = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renombrando = false }) { Text("Cancelar") } },
        )
    }
}
