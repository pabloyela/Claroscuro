package com.tua.claroscuro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val Suave = Color(0x99FFFFFF)
private val Margen = 20.dp // lejos del borde curvo

@Composable
fun ClaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Color(0xFF151515),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Claroscuro necesita permiso para usar la cámara.",
            color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAsk,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) { Text("Dar permiso", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Si ya lo negaste, actívalo en Ajustes › Apps › Claroscuro › Permisos.",
            color = Suave, fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CameraScreen(a: MainActivity) {
    val estilo = a.estilo.value
    val ajustes = a.ajustes.value
    SideEffect { a.pushUniforms() }

    val scope = rememberCoroutineScope()
    var cuenta by remember { mutableIntStateOf(0) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var panel by remember { mutableStateOf(false) }
    var foco by remember { mutableStateOf<Offset?>(null) }
    val parpadeo = remember { Animatable(0f) }
    val giro by animateFloatAsState(a.giroUi.intValue.toFloat(), label = "giro")

    fun disparar() {
        if (timerJob?.isActive == true && cuenta > 0) { // tocar otra vez cancela la cuenta regresiva
            timerJob?.cancel()
            cuenta = 0
            return
        }
        if (a.modoVideo.value && a.grabando.value) { // detener la grabación al instante
            a.toggleRecording()
            return
        }
        val t = a.temporizador.intValue
        timerJob = scope.launch {
            for (s in t downTo 1) {
                cuenta = s
                a.beep()
                delay(1000)
            }
            cuenta = 0
            if (a.modoVideo.value) {
                a.toggleRecording()
            } else {
                a.takePhoto()
                parpadeo.snapTo(0.85f)
                parpadeo.animateTo(0f, tween(260))
            }
        }
    }

    val volumen = a.disparoVolumen.intValue
    LaunchedEffect(volumen) { if (volumen > 0) disparar() }
    LaunchedEffect(foco) {
        if (foco != null) {
            delay(1200)
            foco = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ---------- Arriba: ajustes rápidos, todos a la vista ----------
        Row(Modifier.fillMaxWidth().padding(horizontal = Margen, vertical = 6.dp)) {
            val fl = a.flash.intValue
            Rapido(
                when (fl) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                    else -> Icons.Rounded.FlashOff
                },
                when (fl) {
                    ImageCapture.FLASH_MODE_ON -> "Flash"
                    ImageCapture.FLASH_MODE_AUTO -> "Auto"
                    else -> "Flash"
                },
                fl != ImageCapture.FLASH_MODE_OFF,
                giro
            ) {
                a.setFlash(
                    when (fl) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                )
            }
            val t = a.temporizador.intValue
            Rapido(
                if (t == 0) Icons.Rounded.TimerOff else Icons.Rounded.Timer,
                if (t == 0) "Timer" else "$t s",
                t != 0,
                giro
            ) {
                a.temporizador.intValue = when (t) {
                    0 -> 3
                    3 -> 5
                    5 -> 10
                    else -> 0
                }
                a.savePrefs()
            }
            val s = a.sonido.value
            Rapido(
                if (s) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                if (s) "Sonido" else "Silencio",
                s,
                giro
            ) {
                a.sonido.value = !s
                a.savePrefs()
            }
            val g = a.cuadricula.value
            Rapido(if (g) Icons.Rounded.GridOn else Icons.Rounded.GridOff, "Rejilla", g, giro) {
                a.cuadricula.value = !g
                a.savePrefs()
            }
            val c = a.guardarColor.value
            Rapido(Icons.Rounded.Palette, "Color", c, giro) {
                a.guardarColor.value = !c
                a.savePrefs()
            }
            val v = a.teclaVolumen.value
            Rapido(Icons.Rounded.Smartphone, "Tecla vol", v, giro) {
                a.teclaVolumen.value = !v
                a.savePrefs()
            }
            val fe = a.fecha.value
            Rapido(Icons.Rounded.DateRange, "Fecha", fe, giro) {
                a.fecha.value = !fe
                a.savePrefs()
            }
        }

        // ---------- Visor (ocupa el espacio libre y se ajusta dentro) ----------
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.aspectRatio(a.aspecto.floatValue).background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    CameraGLView(ctx).also { v ->
                        a.glView = v
                        v.renderer.onSurfaceReady = { st -> a.onPreviewSurface(st) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Gestos: tocar para enfocar, pellizcar para zoom
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { o ->
                            foco = o
                            a.focusAt(o.x / size.width, o.y / size.height)
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) a.zoomBy(zoomChange)
                        }
                    }
            )

            if (!a.modoVideo.value) {
                Text(
                    if (a.formato169.value) "16:9" else "4:3",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .rotate(giro)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000))
                        .clickable { a.toggleFormato() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (a.cuadricula.value) {
                Canvas(Modifier.fillMaxSize()) {
                    val col = Color(0x66FFFFFF)
                    val w = size.width
                    val h = size.height
                    for (i in 1..2) {
                        drawLine(col, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 2f)
                        drawLine(col, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 2f)
                    }
                }
            }

            val f = foco
            if (f != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White, radius = 70f, center = f, style = Stroke(width = 4f))
                }
            }

            val z = a.zoom.floatValue
            if (z > 1.05f) {
                Text(
                    String.format(Locale.US, "%.1fx", z),
                    color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (a.grabando.value) {
                val ms = a.tiempoGrabacion.longValue
                val seg = ms / 1000
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xB3000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFE53935)))
                    Text(
                        String.format(Locale.US, "  %02d:%02d", seg / 60, seg % 60),
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            val prog = a.procesando.value
            if (prog != null && !panel) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xB3000000)).padding(horizontal = Margen, vertical = 10.dp)
                ) {
                    Text(
                        "Convirtiendo video a B/N… ${(prog * 100).roundToInt()}%",
                        color = Color.White, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { prog },
                        color = Color.White,
                        trackColor = Color(0x44FFFFFF),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (cuenta > 0) {
                Text(
                    "$cuenta", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).rotate(giro)
                )
            }

            // Parpadeo negro al disparar
            if (parpadeo.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = parpadeo.value)))
            }

            // Panel de deslizadores
            Desvanecer(visible = panel, modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xB3000000))
                        .padding(horizontal = Margen, vertical = 8.dp)
                ) {
                    AjusteSlider("Contraste", ajustes.contraste, -1f..1f) {
                        a.ajustes.value = ajustes.copy(contraste = it)
                    }
                    AjusteSlider("Brillo", ajustes.brillo, -1f..1f) {
                        a.ajustes.value = ajustes.copy(brillo = it)
                    }
                    AjusteSlider("Grano", ajustes.grano, 0f..1f) {
                        a.ajustes.value = ajustes.copy(grano = it)
                    }
                    AjusteSlider("Viñeta", ajustes.vineta, 0f..1f) {
                        a.ajustes.value = ajustes.copy(vineta = it)
                    }
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            a.ajustes.value = Ajustes.de(estilo)
                            a.savePrefs()
                        }) { Text("Restablecer", color = Color.White, fontSize = 15.sp) }
                    }
                }
            }
        }

        }

        // ---------- Estilos ----------
        LazyRow(
            contentPadding = PaddingValues(horizontal = Margen),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            item {
                Chip(
                    texto = "Ajustes",
                    icono = Icons.Rounded.Tune,
                    activo = panel
                ) {
                    panel = !panel
                    if (!panel) a.savePrefs()
                }
            }
            items(Estilo.entries) { e ->
                Chip(texto = e.nombre, icono = null, activo = e == estilo) {
                    a.estilo.value = e
                    a.ajustes.value = Ajustes.de(e, a.ajustes.value)
                    a.savePrefs()
                }
            }
        }

        // ---------- Foto / Video ----------
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ModoTab("FOTO", !a.modoVideo.value, enabled = !a.grabando.value) { a.setModo(false) }
            Spacer(Modifier.width(18.dp))
            ModoTab("VIDEO", a.modoVideo.value, enabled = !a.grabando.value) { a.setModo(true) }
        }

        // ---------- Disparador ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Margen + 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Última foto
            Box(
                Modifier.size(60.dp).rotate(giro).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222222)).border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                    .clickable { a.openLastPhoto() },
                contentAlignment = Alignment.Center
            ) {
                val m = a.miniatura.value
                if (m != null) {
                    Image(
                        m.asImageBitmap(), "Última foto",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                }
                if (a.guardando.intValue > 0 || a.procesando.value != null) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Botón grande de disparo
            Box(
                Modifier.size(86.dp).clip(CircleShape).border(5.dp, Color.White, CircleShape)
                    .clickable { disparar() },
                contentAlignment = Alignment.Center
            ) {
                val rojo = Color(0xFFE53935)
                when {
                    a.grabando.value -> Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(rojo)
                    )
                    a.modoVideo.value -> Box(Modifier.size(66.dp).clip(CircleShape).background(rojo))
                    else -> Box(
                        Modifier.size(66.dp).clip(CircleShape)
                            .background(if (cuenta > 0) rojo else Color.White)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Cambiar cámara (no durante la grabación)
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(if (a.grabando.value) Color(0xFF111111) else Color(0xFF222222))
                    .clickable(enabled = !a.grabando.value) { a.switchCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Cameraswitch, "Cambiar cámara", tint = Color.White,
                    modifier = Modifier.size(30.dp).rotate(giro)
                )
            }
        }
    }
}

/** Aparece/desaparece suavemente (fuera de Column para que Compose use la versión general). */
@Composable
private fun Desvanecer(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}

@Composable
private fun ModoTab(texto: String, activo: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        color = if (activo) Color.White else Suave,
        fontSize = 15.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun RowScope.Rapido(
    icon: ImageVector,
    label: String,
    activo: Boolean,
    giro: Float = 0f,
    onClick: () -> Unit
) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (activo) Color.White else Suave, modifier = Modifier.size(26.dp).rotate(giro))
        Text(
            label, fontSize = 11.sp, maxLines = 1,
            color = if (activo) Color.White else Suave,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun Chip(texto: String, icono: ImageVector?, activo: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (activo) Color.White else Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) {
            Icon(
                icono, null, tint = if (activo) Color.Black else Color.White,
                modifier = Modifier.size(20.dp).padding(end = 0.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontSize = 15.sp, color = if (activo) Color.Black else Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AjusteSlider(
    nombre: String,
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = valor,
            onValueChange = onChange,
            valueRange = rango,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0x55FFFFFF)
            )
        )
        val pct = (valor * 100).roundToInt()
        Text(
            if (rango.start < 0f && pct > 0) "+$pct" else "$pct",
            color = Color.White, fontSize = 13.sp, textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}
