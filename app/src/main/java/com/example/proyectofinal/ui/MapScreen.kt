package com.example.proyectofinal.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.example.proyectofinal.R
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// JSON con el estilo para el mapa oscuro.
private const val darkMapStyleJson = """
[
  { "elementType": "geometry", "stylers": [ { "color": "#242f3e" } ] },
  { "elementType": "labels.text.stroke", "stylers": [ { "color": "#242f3e" } ] },
  { "elementType": "labels.text.fill", "stylers": [ { "color": "#746855" } ] },
  { "featureType": "administrative.locality", "elementType": "labels.text.fill", "stylers": [ { "color": "#d59563" } ] },
  { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [ { "color": "#d59563" } ] },
  { "featureType": "poi.park", "elementType": "geometry", "stylers": [ { "color": "#263c3f" } ] },
  { "featureType": "poi.park", "elementType": "labels.text.fill", "stylers": [ { "color": "#6b9a76" } ] },
  { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#38414e" } ] },
  { "featureType": "road", "elementType": "geometry.stroke", "stylers": [ { "color": "#212a37" } ] },
  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [ { "color": "#9ca5b3" } ] },
  { "featureType": "road.highway", "elementType": "geometry", "stylers": [ { "color": "#746855" } ] },
  { "featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [ { "color": "#1f2835" } ] },
  { "featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [ { "color": "#f3d19c" } ] },
  { "featureType": "transit", "elementType": "geometry", "stylers": [ { "color": "#2f3948" } ] },
  { "featureType": "transit.station", "elementType": "labels.text.fill", "stylers": [ { "color": "#d59563" } ] },
  { "featureType": "water", "elementType": "geometry", "stylers": [ { "color": "#17263c" } ] },
  { "featureType": "water", "elementType": "labels.text.fill", "stylers": [ { "color": "#515c6d" } ] },
  { "featureType": "water", "elementType": "labels.text.stroke", "stylers": [ { "color": "#17263c" } ] }
]
"""

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val writePermissionState = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val cameraPositionState = rememberCameraPositionState()

    var recuerdos by remember { mutableStateOf<List<Recuerdo>>(emptyList()) }
    var filtro by remember { mutableStateOf("todos") }
    var recuerdosRelacionados by remember { mutableStateOf<List<Recuerdo>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    var lugarNombre by remember { mutableStateOf<String?>(null) }
    var notaEditada by remember { mutableStateOf("") }
    var mostrarDialogo by remember { mutableStateOf(false) }

    var isMapDark by remember { mutableStateOf(false) }

    val permisoConcedido = locationPermissionState.status.isGranted

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        locationPermissionState.launchPermissionRequest()
    }

    LaunchedEffect(filtro, permisoConcedido) {
        if (!permisoConcedido) {
            Toast.makeText(context, "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(it.latitude, it.longitude), 12f
                    )
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Error de permisos de ubicación", Toast.LENGTH_SHORT).show()
        }
        val db = Firebase.firestore
        val user = auth.currentUser
        val recuerdosPublicos = if (filtro != "privados") {
            db.collection("recuerdos_publicos").get().await()
                .documents.mapNotNull { it.toObject(Recuerdo::class.java)?.copy(id = it.id) }
        } else emptyList()
        val recuerdosPrivados = if (filtro != "publicos" && user != null) {
            db.collection("usuarios")
                .document(user.uid)
                .collection("recuerdos_privados")
                .get()
                .await()
                .documents.mapNotNull { it.toObject(Recuerdo::class.java)?.copy(id = it.id) }
        } else emptyList()
        recuerdos = recuerdosPublicos + recuerdosPrivados
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val mapProperties = MapProperties(
            mapStyleOptions = if (isMapDark) MapStyleOptions(darkMapStyleJson) else null
        )

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false),
            properties = mapProperties
        ) {
            recuerdos.forEach { recuerdo ->
                Marker(
                    state = MarkerState(position = LatLng(recuerdo.latitud, recuerdo.longitud)),
                    title = recuerdo.nota.take(25),
                    snippet = recuerdo.timestamp.toDate().toString(),
                    onClick = {
                        val relacionados = recuerdos.filter {
                            distanciaMetros(it.latitud, it.longitud, recuerdo.latitud, recuerdo.longitud) <= 500
                        }.sortedByDescending { it.timestamp }
                        selectedIndex = relacionados.indexOf(recuerdo).coerceAtLeast(0)
                        recuerdosRelacionados = relacionados
                        obtenerNombreLugar(context, recuerdo.latitud, recuerdo.longitud) { nombre ->
                            lugarNombre = nombre
                        }
                        mostrarDialogo = true
                        false
                    }
                )
            }
        }

        // MODIFICADO: Se añade un fondo a la fila de filtros para que resalten
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ElevatedFilterChip(
                    selected = filtro == "todos",
                    onClick = { filtro = "todos" },
                    label = { Text("Todos") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_filter_all), contentDescription = "Filtro Todos") }
                )
                ElevatedFilterChip(
                    selected = filtro == "publicos",
                    onClick = { filtro = "publicos" },
                    label = { Text("Públicos") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_public), contentDescription = "Filtro Públicos") }
                )
                ElevatedFilterChip(
                    selected = filtro == "privados",
                    onClick = { filtro = "privados" },
                    label = { Text("Privados") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_private), contentDescription = "Filtro Privados") }
                )
            }
        }

        FloatingActionButton(
            onClick = { isMapDark = !isMapDark },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                if (isMapDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Cambiar tema del mapa"
            )
        }
    }


    if (mostrarDialogo && recuerdosRelacionados.isNotEmpty()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        val recuerdoActual = recuerdosRelacionados.getOrNull(selectedIndex)
        val currentUser = auth.currentUser

        var showConfirmDeleteDialog by remember { mutableStateOf(false) }
        var verImagenCompleta by remember { mutableStateOf(false) }

        LaunchedEffect(selectedIndex) {
            notaEditada = recuerdosRelacionados.getOrNull(selectedIndex)?.nota ?: ""
            listState.animateScrollToItem(selectedIndex)
        }
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty()) {
                    val viewportCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                    val closestItem = visibleItems.minByOrNull {
                        val itemCenter = it.offset + it.size / 2
                        abs(itemCenter - viewportCenter)
                    }
                    if (closestItem != null && selectedIndex != closestItem.index) {
                        selectedIndex = closestItem.index
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("Detalles del Recuerdo") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            itemsIndexed(recuerdosRelacionados) { _, rec ->
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .fillMaxHeight()
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(rec.url_imagen),
                                        contentDescription = "Recuerdo, presiona para ver en pantalla completa",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { verImagenCompleta = true }
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Fullscreen,
                                        contentDescription = "Ver en pantalla completa",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            recuerdosRelacionados.forEachIndexed { index, _ ->
                                val isSelected = index == selectedIndex
                                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                Canvas(modifier = Modifier.size(8.dp).padding(horizontal = 2.dp)) {
                                    drawCircle(color = color)
                                }
                            }
                        }
                    }

                    recuerdoActual?.let { rec ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoRow(icon = Icons.Default.Person, text = rec.nombre_usuario)
                            InfoRow(icon = Icons.Default.CalendarToday, text = formatTimestamp(rec.timestamp))
                            lugarNombre?.let {
                                InfoRow(icon = Icons.Default.LocationOn, text = it)
                            }
                        }
                        OutlinedTextField(
                            value = notaEditada,
                            onValueChange = { notaEditada = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            label = { Text("Nota") },
                            readOnly = rec.userId != currentUser?.uid,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    compartirRecuerdo(context, rec.url_imagen, rec.nota)
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir")
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Compartir")
                            }

                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                    if (writePermissionState.status.isGranted) {
                                        descargarImagen(context, rec.url_imagen, rec.id)
                                    } else {
                                        writePermissionState.launchPermissionRequest()
                                    }
                                } else {
                                    descargarImagen(context, rec.url_imagen, rec.id)
                                }
                            }) {
                                Icon(Icons.Filled.Download, contentDescription = "Descargar")
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Descargar")
                            }

                            if (rec.userId == currentUser?.uid) {
                                TextButton(onClick = { showConfirmDeleteDialog = true }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Text("Cerrar")
                }
            },
            confirmButton = {
                if (recuerdoActual != null && currentUser != null && recuerdoActual.userId == currentUser.uid) {
                    TextButton(onClick = {
                        guardarEdicionRecuerdo(recuerdoActual, notaEditada) {
                            mostrarDialogo = false
                            Toast.makeText(context, "Nota actualizada", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Guardar")
                    }
                }
            }
        )

        if (showConfirmDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteDialog = false },
                title = { Text("Confirmar eliminación") },
                text = { Text("¿Estás seguro de que deseas eliminar este recuerdo? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            recuerdoActual?.let {
                                eliminarRecuerdo(it) {
                                    showConfirmDeleteDialog = false
                                    mostrarDialogo = false
                                    Toast.makeText(context, "Recuerdo eliminado", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (verImagenCompleta) {
            recuerdoActual?.let { rec ->
                Dialog(
                    onDismissRequest = { verImagenCompleta = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    var rotation by remember { mutableStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, gestureRotation ->
                                    scale *= zoom
                                    rotation += gestureRotation
                                    offset += pan
                                }
                            }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = rec.url_imagen),
                            contentDescription = "Imagen del recuerdo en pantalla completa",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                    rotationZ = rotation
                                )
                        )
                        IconButton(
                            onClick = { verImagenCompleta = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar vista de pantalla completa",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                                rotation = 0f
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restablecer imagen",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

data class Recuerdo(
    val id: String = "",
    val userId: String = "",
    val esPublico: Boolean = false,
    val nota: String = "",
    val url_imagen: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val nombre_usuario: String = "Anónimo"
)

fun descargarImagen(context: Context, url: String, id: String) {
    try {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)

        val nombreArchivo = "recuerdo_${id}.jpg"

        val request = DownloadManager.Request(uri).apply {
            setTitle("Descargando Recuerdo")
            setDescription(nombreArchivo)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "RecuerdosApp" + File.separator + nombreArchivo)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        downloadManager.enqueue(request)
        Toast.makeText(context, "Iniciando descarga...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al iniciar la descarga.", Toast.LENGTH_LONG).show()
        Log.e("DescargaImagen", "Error al descargar imagen: ${e.message}", e)
    }
}

suspend fun compartirRecuerdo(context: Context, imageUrl: String, texto: String) {
    val loader = context.imageLoader
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .build()

    try {
        val result = loader.execute(request).drawable
        if (result == null) {
            Toast.makeText(context, "No se pudo cargar la imagen para compartir.", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = (result as BitmapDrawable).bitmap

        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "shared_image.png")
        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        fileOutputStream.close()

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, context.contentResolver.getType(contentUri))
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, texto)
            putExtra(Intent.EXTRA_SUBJECT, "Mira este recuerdo")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Compartir recuerdo"))

    } catch (e: Exception) {
        Log.e("CompartirRecuerdo", "Error al compartir", e)
        Toast.makeText(context, "No se pudo compartir la imagen.", Toast.LENGTH_SHORT).show()
    }
}

fun obtenerNombreLugar(
    context: Context,
    lat: Double,
    lng: Double,
    onResult: (String?) -> Unit
) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val direcciones = geocoder.getFromLocation(lat, lng, 1)
        val lugar = direcciones?.firstOrNull()
        val nombre = lugar?.subLocality ?: lugar?.locality ?: lugar?.adminArea
        onResult(nombre)
    } catch (e: Exception) {
        onResult(null)
    }
}

fun guardarEdicionRecuerdo(
    recuerdo: Recuerdo,
    nuevaNota: String,
    onComplete: () -> Unit
) {
    if (recuerdo.id.isEmpty()) return
    val db = Firebase.firestore
    val coleccion = if (recuerdo.esPublico) {
        db.collection("recuerdos_publicos")
    } else {
        db.collection("usuarios").document(recuerdo.userId).collection("recuerdos_privados")
    }
    coleccion.document(recuerdo.id).update("nota", nuevaNota)
        .addOnSuccessListener { onComplete() }
}

fun eliminarRecuerdo(
    recuerdo: Recuerdo,
    onComplete: () -> Unit
) {
    if (recuerdo.id.isEmpty()) return
    val db = Firebase.firestore
    val coleccion = if (recuerdo.esPublico) {
        db.collection("recuerdos_publicos")
    } else {
        db.collection("usuarios").document(recuerdo.userId).collection("recuerdos_privados")
    }
    coleccion.document(recuerdo.id).delete()
        .addOnSuccessListener { onComplete() }
}

fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val loc1 = Location("").apply {
        latitude = lat1
        longitude = lon1
    }
    val loc2 = Location("").apply {
        latitude = lat2
        longitude = lon2
    }
    return loc1.distanceTo(loc2)
}