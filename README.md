# Zettel Notas

App de notas visual para tablet Android (Galaxy Tab S9+ con S Pen): canvas infinito
pensado para el lápiz, tarjetas-zettel, tableros anidados y enlaces bidireccionales
(Zettelkasten). 100 % offline.

Stack: Kotlin nativo, Jetpack Compose, Jetpack Ink (tinta de baja latencia), Room. MVVM con
capas `data / domain / ui`.

## Instalar el APK
Cada push a `main` compila con GitHub Actions y publica el APK en
**Releases → "APK más reciente"** (`zettel-notas.apk`). Descárgalo en la tablet e instálalo
(habilita "instalar apps desconocidas" para tu navegador). Está firmado con la llave debug.

## Cómo se usa
- **Dedo**: pan y zoom (dos dedos), toque = seleccionar, doble toque en vacío = nuevo zettel,
  doble toque en un tablero (tarjeta verde) = abrirlo.
- **S Pen** según la herramienta:
  - *Pluma*: escribe. Si el trazo empieza dentro de una tarjeta pertenece a esa nota; si no,
    es un trazo libre del tablero. El extremo borrador del S Pen borra siempre.
  - *Borrador*, *Lazo* (selecciona trazos → "Convertir en zettel"), *Mover* (mover/redimensionar
    tarjetas con la esquina inferior derecha), *Conectar* (arrastra de una tarjeta a otra para
    crear un enlace real; se elige la relación en el panel).
- **Panel lateral** de la tarjeta: tipo (fugaz / literatura / permanente / índice), título,
  contenido, backlinks, enlaces salientes y tableros donde aparece.
- **Transclusión**: tocar un backlink de una nota que no está en el tablero la trae aquí; es la
  misma nota, editarla actualiza todos los tableros.
- **Bandeja**: captura rápida de notas fugaces, "Al tablero", promover a permanente/literatura,
  y pestaña de notas huérfanas.

## Arquitectura
- `data/db`: entidades Room (`notes`, `strokes`, `links`, `placements`) y DAOs.
- `data/repo`: `ZettelRepository` (transacciones) y `StrokeCodec` (Ink ⇄ BLOB).
- `domain`: `Quadtree`, `IdGenerator` (yyyyMMddHHmmss único), geometría pura. Con tests JVM.
- `ui`: `BoardViewModel` (escena en memoria + escritura serializada a Room), canvas
  (`Camera`, `DibujoTablero`, `CapaTinta`), paneles.

El id de una nota es permanente. Los tableros son notas de tipo índice; el tablero raíz tiene el
id fijo `00000000000000`.

## Fases posteriores (no implementadas)
Fase 2: manuscrito a texto (ML Kit), búsqueda global, `[[` para enlazar, vista de grafo,
anotación de PDFs, atajos del botón del S Pen. Fase 3: sincronización con CRDTs y exportación
a Markdown.
