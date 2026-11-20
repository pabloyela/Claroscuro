package com.tua.claroscuro

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    var glView: CameraGLView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var surfaceTexture: SurfaceTexture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val shutterSound = MediaActionSound()
    private var toneGen: ToneGenerator? = null
    private var orientationListener: OrientationEventListener? = null

    // ---------- Estado que ve la pantalla ----------
    val permiso = mutableStateOf(false)
    val estilo = mutableStateOf(Estilo.CLASICO)
    val ajustes = mutableStateOf(Ajustes())
    val sonido = mutableStateOf(true)
    val cuadricula = mutableStateOf(false)
    val guardarColor = mutableStateOf(false)
    val teclaVolumen = mutableStateOf(true)
    val temporizador = mutableIntStateOf(0)
    val flash = mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)
    val frontal = mutableStateOf(false)
    val aspecto = mutableFloatStateOf(3f / 4f)
    val miniatura = mutableStateOf<Bitmap?>(null)
    val ultimaUri = mutableStateOf<Uri?>(null)
    val guardando = mutableIntStateOf(0)
    val disparoVolumen = mutableIntStateOf(0)
    val zoom = mutableFloatStateOf(1f)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permiso.value = ok
        if (ok) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ajustes", MODE_PRIVATE)
        loadPrefs()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
        toneGen = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }

        permiso.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            ClaroTheme {
                if (permiso.value) CameraScreen(this)
                else PermissionScreen { permLauncher.launch(Manifest.permission.CAMERA) }
            }
        }

        // Para que las fotos en horizontal se guarden derechas
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val r = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = r
            }
        }

        loadLastPhoto()
        if (permiso.value) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
        orientationListener?.enable()
    }

    override fun onPause() {
        orientationListener?.disable()
        glView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        shutterSound.release()
        toneGen?.release()
        cameraExecutor.shutdown()
    }

    // ---------- Preferencias ----------

    private fun loadPrefs() {
        estilo.value = Estilo.entries.firstOrNull { it.name == prefs.getString("estilo", "") } ?: Estilo.CLASICO
        ajustes.value = Ajustes(
            contraste = prefs.getFloat("contraste", 0f),
            brillo = prefs.getFloat("brillo", 0f),
            grano = prefs.getFloat("grano", estilo.value.grano),
            vineta = prefs.getFloat("vineta", estilo.value.vineta)
        )
        sonido.value = prefs.getBoolean("sonido", true)
        cuadricula.value = prefs.getBoolean("cuadricula", false)
        guardarColor.value = prefs.getBoolean("guardarColor", false)
        teclaVolumen.value = prefs.getBoolean("teclaVolumen", true)
        temporizador.intValue = prefs.getInt("temporizador", 0)
        flash.intValue = prefs.getInt("flash", ImageCapture.FLASH_MODE_OFF)
    }

    fun savePrefs() {
        val a = ajustes.value
        prefs.edit()
            .putString("estilo", estilo.value.name)
            .putFloat("contraste", a.contraste)
            .putFloat("brillo", a.brillo)
            .putFloat("grano", a.grano)
            .putFloat("vineta", a.vineta)
            .putBoolean("sonido", sonido.value)
            .putBoolean("cuadricula", cuadricula.value)
            .putBoolean("guardarColor", guardarColor.value)
            .putBoolean("teclaVolumen", teclaVolumen.value)
            .putInt("temporizador", temporizador.intValue)
            .putInt("flash", flash.intValue)
            .apply()
    }

    fun pushUniforms() {
        glView?.renderer?.uniforms = FxUniforms.from(estilo.value, ajustes.value)
    }

    // ---------- Cámara ----------

    /** Lo llama el visor cuando su superficie está lista. */
    fun onPreviewSurface(st: SurfaceTexture) {
        surfaceTexture = st
        pushUniforms()
        bindCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = try { future.get() } catch (e: Exception) { null }
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    fun bindCamera() {
        val provider = cameraProvider ?: return
        val st = surfaceTexture ?: return
        val main = ContextCompat.getMainExecutor(this)

        val previewSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(1440, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(previewSelector)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        preview.setSurfaceProvider(main) { request ->
            val res = request.resolution
            st.setDefaultBufferSize(res.width, res.height)
            request.setTransformationInfoListener(main) { info ->
                val rot = info.rotationDegrees
                glView?.renderer?.rotation = rot.toFloat()
                aspecto.floatValue = if (rot % 180 == 0) res.width.toFloat() / res.height
                else res.height.toFloat() / res.width
            }
            val surface = Surface(st)
            request.provideSurface(surface, main) { surface.release() }
        }

        // Unos 12 MP: buena calidad sin agotar la memoria al procesar
        val photoSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(photoSelector)
            .setFlashMode(flash.intValue)
            .build()

        val selector = if (frontal.value) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, capture)
            imageCapture = capture
            glView?.renderer?.mirror = frontal.value
            zoom.floatValue = 1f
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
            if (frontal.value) {
                frontal.value = false
                bindCamera()
            }
        }
    }

    fun switchCamera() {
        frontal.value = !frontal.value
        bindCamera()
    }

    fun setFlash(mode: Int) {
        flash.intValue = mode
        imageCapture?.flashMode = mode
        savePrefs()
    }

    /** Enfocar donde se tocó (coordenadas 0..1 del visor, y hacia abajo). */
    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        var u = x
        val v = y
        if (frontal.value) u = 1f - u
        val rot = (glView?.renderer?.rotation ?: 90f).roundToInt()
        val (bx, by) = when (rot) {
            90 -> v to (1f - u)
            180 -> (1f - u) to (1f - v)
            270 -> (1f - v) to u
            else -> u to v
        }
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(bx, by)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        try {
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            // algunos equipos no lo permiten
        }
    }

    fun zoomBy(scale: Float) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value ?: return
        val r = (zs.zoomRatio * scale).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(r)
        zoom.floatValue = r
    }

    fun beep() {
        if (sonido.value) toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    // ---------- Tomar y guardar la foto ----------

    fun takePhoto() {
        val ic = imageCapture ?: return
        val u = FxUniforms.from(estilo.value, ajustes.value) // el estilo del momento del disparo
        val saveColor = guardarColor.value
        if (sonido.value) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        guardando.intValue++
        ic.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val bmp = try {
                    image.toBitmap()
                } catch (e: Throwable) {
                    null
                } finally {
                    image.close()
                }
                lifecycleScope.launch {
                    try {
                        if (bmp != null) processAndSave(bmp, rot, u, saveColor)
                        else aviso("No se pudo leer la foto")
                    } catch (e: Throwable) {
                        aviso("No se pudo guardar la foto")
                    } finally {
                        guardando.intValue--
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    guardando.intValue--
                    aviso("No se pudo tomar la foto")
                }
            }
        })
    }

    private fun aviso(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private suspend fun processAndSave(src: Bitmap, rot: Int, u: FxUniforms, saveColor: Boolean) {
        val upright = withContext(Dispatchers.Default) { rotate(src, rot) }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val carpeta = "${Environment.DIRECTORY_PICTURES}/Claroscuro"
        if (saveColor) {
            withContext(Dispatchers.IO) { saveJpeg(upright, "BN_${stamp}_color.jpg", "$carpeta/Originales") }
        }
        val bw = Fx.apply(upright, u)
        upright.recycle()
        val uri = withContext(Dispatchers.IO) { saveJpeg(bw, "BN_$stamp.jpg", carpeta) }
        val thumb = withContext(Dispatchers.Default) {
            val tw = 240
            val th = (tw.toFloat() * bw.height / bw.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bw, tw, th, true)
        }
        bw.recycle()
        if (uri != null) {
            miniatura.value = thumb
            ultimaUri.value = uri
        } else {
            aviso("No se pudo guardar la foto")
        }
    }

    private fun rotate(src: Bitmap, deg: Int): Bitmap {
        if (deg % 360 == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun saveJpeg(bmp: Bitmap, name: String, relPath: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    /** Muestra la miniatura de la última foto tomada con la app. */
    private fun loadLastPhoto() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    contentResolver.query(
                        base,
                        arrayOf(MediaStore.Images.Media._ID),
                        "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                        arrayOf("${Environment.DIRECTORY_PICTURES}/Claroscuro/"),
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val uri = android.content.ContentUris.withAppendedId(base, c.getLong(0))
                            val thumb = contentResolver.loadThumbnail(uri, Size(240, 320), null)
                            uri to thumb
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) {
                ultimaUri.value = result.first
                miniatura.value = result.second
            }
        }
    }

    fun openLastPhoto() {
        val uri = ultimaUri.value ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            aviso("No hay app para ver fotos")
        }
    }

    // ---------- Botones de volumen como disparador ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (permiso.value && teclaVolumen.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if ((event?.repeatCount ?: 0) == 0) disparoVolumen.intValue++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (permiso.value && teclaVolumen.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) return true
        return super.onKeyUp(keyCode, event)
    }
}
