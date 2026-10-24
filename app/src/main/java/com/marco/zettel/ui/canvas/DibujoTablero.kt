package com.marco.zettel.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.marco.zettel.domain.model.NoteType
import com.marco.zettel.domain.spatial.WRect
import com.marco.zettel.ui.BoardViewModel
import com.marco.zettel.ui.Herramienta
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object Paleta {
    val Papel = Color(0xFFF6F3EA)
    val Punto = Color(0xFFCFC9B8)
    val Seleccion = Color(0xFF1F6FEB)
    val Enlace = Color(0xFF6B7280)
    val Lazo = Color(0xFF7C3AED)

    fun fondo(t: NoteType) = when (t) {
        NoteType.FLEETING -> Color(0xFFFFF3B0)
        NoteType.LITERATURE -> Color(0xFFD7E8FF)
        NoteType.PERMANENT -> Color(0xFFFFFFFF)
        NoteType.INDEX -> Color(0xFFD9F2DA)
    }

    fun borde(t: NoteType) = when (t) {
        NoteType.FLEETING -> Color(0xFFD9C560)
        NoteType.LITERATURE -> Color(0xFF8FB4E8)
        NoteType.PERMANENT -> Color(0xFFB8B2A0)
        NoteType.INDEX -> Color(0xFF7DBB80)
    }
}

/** Objetos reutilizables entre fotogramas para no generar basura al dibujar. */
internal class RecursosDibujo(
    val medidor: TextMeasurer,
    val renderer: CanvasStrokeRenderer,
    val densidad: Float,
) {
    val matrizTrazo = android.graphics.Matrix()
    val pinturaPunto = android.graphics.Paint().apply {
        color = 0xFFCFC9B8.toInt()
        strokeCap = android.graphics.Paint.Cap.ROUND
        isAntiAlias = true
    }
    var buffer = FloatArray(0)

    /** Las tarjetas se miden en unidades de mundo: densidad 1 evita escalar el texto dos veces. */
    val d1 = Density(1f, 1f)
    val estiloTitulo = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1B1B), lineHeight = 20.sp)
    val estiloCuerpo = TextStyle(fontSize = 13.sp, color = Color(0xFF333333), lineHeight = 17.sp)
    val estiloMeta = TextStyle(fontSize = 9.sp, color = Color(0xFF8A8577))
    val estiloRelacion = TextStyle(fontSize = 11.sp, color = Color(0xFF374151))
}

internal fun DrawScope.dibujarTablero(vm: BoardViewModel, e: BoardScene, cam: Camera, r: RecursosDibujo) {
    e.revision // leer el contador para que cualquier cambio invalide este dibujo
    val s = cam.scale
    val ox = cam.ox
    val oy = cam.oy

    drawRect(Paleta.Papel)
    cuadricula(s, ox, oy, r)

    val vista = WRect(-ox / s, -oy / s, (size.width - ox) / s, (size.height - oy) / s)
    val tarjetas = ArrayList<CardData>()
    val trazos = ArrayList<StrokeData>()
    for (item in e.arbol.consultar(vista)) {
        when (item) {
            is CardData -> tarjetas.add(item)
            is StrokeData -> trazos.add(item)
        }
    }
    tarjetas.sortBy { it.z }
    val sel = vm.tarjetaSel
    val herramienta = vm.herramienta

    withTransform({
        translate(ox, oy)
        scale(s, s, Offset.Zero)
    }) {
        dibujarEnlaces(e, vista, s, r)
        for (c in tarjetas) dibujarTarjeta(c, c === sel, s, r)
        if (sel != null && herramienta == Herramienta.MOVER) {
            val lado = 14f / s * r.densidad
            drawRect(
                Paleta.Seleccion,
                Offset(sel.x + sel.w - lado / 2, sel.y + sel.h - lado / 2),
                Size(lado, lado),
            )
        }
        for (t in trazos) dibujarTrazo(t, s, r)
    }
}

private fun DrawScope.cuadricula(s: Float, ox: Float, oy: Float, r: RecursosDibujo) {
    var paso = 40f
    while (paso * s < 18f * r.densidad) paso *= 2f
    val x0 = floor(-ox / s / paso) * paso
    val y0 = floor(-oy / s / paso) * paso
    val columnas = ceil(size.width / s / paso).toInt() + 2
    val filas = ceil(size.height / s / paso).toInt() + 2
    val n = columnas * filas * 2
    if (r.buffer.size < n) r.buffer = FloatArray(n)
    var i = 0
    for (f in 0 until filas) {
        for (c in 0 until columnas) {
            r.buffer[i++] = (x0 + c * paso) * s + ox
            r.buffer[i++] = (y0 + f * paso) * s + oy
        }
    }
    r.pinturaPunto.strokeWidth = 2.2f * r.densidad
    drawIntoCanvas { it.nativeCanvas.drawPoints(r.buffer, 0, i, r.pinturaPunto) }
}

private fun DrawScope.dibujarTrazo(t: StrokeData, s: Float, r: RecursosDibujo) {
    r.matrizTrazo.setScale(s, s)
    drawIntoCanvas { r.renderer.draw(it.nativeCanvas, t.stroke, r.matrizTrazo) }
}

private fun DrawScope.dibujarTarjeta(c: CardData, seleccionada: Boolean, s: Float, r: RecursosDibujo) {
    val nota = c.note
    val tipo = c.tipo
    val tl = Offset(c.x, c.y)
    val sz = Size(c.w, c.h)
    val esquina = CornerRadius(10f, 10f)
    drawRoundRect(Paleta.fondo(tipo), tl, sz, esquina, Fill)
    drawRoundRect(
        if (seleccionada) Paleta.Seleccion else Paleta.borde(tipo),
        tl,
        sz,
        esquina,
        style = Stroke(width = if (seleccionada) maxOf(3f, 2.5f / s * r.densidad) else maxOf(1.2f, 1.2f / s * r.densidad)),
    )

    val anchoPantallaDp = c.w * s / r.densidad
    if (anchoPantallaDp >= 48f) {
        val pad = 10f
        val ancho = (c.w - 2 * pad).toInt().coerceAtLeast(1)
        var y = c.y + pad
        val prefijo = if (tipo == NoteType.INDEX) "▸ " else ""
        if (nota.title.isNotBlank()) {
            val lt = r.medidor.measure(
                text = prefijo + nota.title,
                style = r.estiloTitulo,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = Constraints(maxWidth = ancho),
                density = r.d1,
            )
            drawText(lt, topLeft = Offset(c.x + pad, y))
            y += lt.size.height + 4f
        }
        if (nota.textContent.isNotBlank()) {
            val lineas = ((c.y + c.h - 20f - y) / 17f).toInt()
            if (lineas >= 1) {
                val lt = r.medidor.measure(
                    text = nota.textContent,
                    style = r.estiloCuerpo,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = lineas,
                    constraints = Constraints(maxWidth = ancho),
                    density = r.d1,
                )
                drawText(lt, topLeft = Offset(c.x + pad, y))
            }
        }
        if (anchoPantallaDp >= 90f) {
            val meta = r.medidor.measure(
                text = "${tipo.etiqueta} · ${nota.id}",
                style = r.estiloMeta,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = ancho),
                density = r.d1,
            )
            drawText(meta, topLeft = Offset(c.x + pad, c.y + c.h - meta.size.height - 5f))
        }
    }

    if (c.strokes.isNotEmpty()) {
        clipRect(c.x, c.y, c.x + c.w, c.y + c.h) {
            for (t in c.strokes) {
                translate(c.x - t.entity.originX, c.y - t.entity.originY) { dibujarTrazo(t, s, r) }
            }
        }
    }
}

private fun DrawScope.dibujarEnlaces(e: BoardScene, vista: WRect, s: Float, r: RecursosDibujo) {
    val ancho = maxOf(2f, 2f / s * r.densidad)
    for (l in e.enlaces) {
        val a = e.tarjetaPorNota[l.origen] ?: continue
        val b = e.tarjetaPorNota[l.destino] ?: continue
        if (!a.rect.union(b.rect).intersects(vista)) continue
        val seg = SegmentoEnlace.entre(a, b)
        val color = if (l.enSeleccion) Paleta.Seleccion else Paleta.Enlace
        val grosor = if (l.enSeleccion) ancho * 1.8f else ancho
        flecha(seg.x1, seg.y1, seg.x2, seg.y2, color, grosor, s, r)
        if (l.relacion.isNotEmpty()) {
            val lt = r.medidor.measure(
                text = l.relacion,
                style = r.estiloRelacion,
                maxLines = 1,
                density = r.d1,
            )
            val mx = (seg.x1 + seg.x2) / 2 - lt.size.width / 2f
            val my = (seg.y1 + seg.y2) / 2 - lt.size.height / 2f
            drawRoundRect(
                Paleta.Papel,
                Offset(mx - 4f, my - 2f),
                Size(lt.size.width + 8f, lt.size.height + 4f),
                CornerRadius(6f, 6f),
            )
            drawText(lt, topLeft = Offset(mx, my))
        }
    }
}

/** Línea con punta de flecha en (x2, y2). */
internal fun DrawScope.flecha(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, grosor: Float, s: Float, r: RecursosDibujo) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = grosor)
    val ang = atan2(y2 - y1, x2 - x1)
    val largo = maxOf(14f, 12f / s * r.densidad)
    val abre = 0.45f
    val p = Path().apply {
        moveTo(x2, y2)
        lineTo(x2 - largo * cos(ang - abre), y2 - largo * sin(ang - abre))
        lineTo(x2 - largo * cos(ang + abre), y2 - largo * sin(ang + abre))
        close()
    }
    drawPath(p, color)
}
