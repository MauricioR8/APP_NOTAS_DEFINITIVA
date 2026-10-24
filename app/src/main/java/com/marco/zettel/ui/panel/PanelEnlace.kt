package com.marco.zettel.ui.panel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marco.zettel.domain.model.RELACIONES
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.canvas.LinkData

/** Panel del enlace seleccionado: tipo de relación y borrado. */
@Composable
fun PanelEnlace(vm: BoardViewModel, enlace: LinkData, modifier: Modifier = Modifier) {
    val escena = vm.escena
    val origen = escena?.tarjetaPorNota?.get(enlace.origen)?.note
    val destino = escena?.tarjetaPorNota?.get(enlace.destino)?.note
    fun nombre(titulo: String?, id: String) = if (titulo.isNullOrBlank()) id else "$titulo ($id)"

    Surface(modifier.width(360.dp).fillMaxHeight(), tonalElevation = 3.dp, shadowElevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Enlace", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.deseleccionar() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar panel")
                }
            }
            Text("Desde: ${nombre(origen?.title, enlace.origen)}")
            Text("Hacia: ${nombre(destino?.title, enlace.destino)}")
            Text("Relación", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RELACIONES.forEach { rel ->
                    FilterChip(
                        selected = enlace.relacion == rel,
                        onClick = { vm.relacionDeEnlace(enlace, rel) },
                        label = { Text(rel.ifEmpty { "sin etiqueta" }) },
                    )
                }
            }
            Button(onClick = { vm.eliminarEnlace(enlace) }, modifier = Modifier.fillMaxWidth()) {
                Text("Eliminar enlace")
            }
        }
    }
}
