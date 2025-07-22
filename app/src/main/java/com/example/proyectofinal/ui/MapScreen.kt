package com.example.proyectofinal.ui

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.proyectofinal.R
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val cameraPositionState = rememberCameraPositionState()

    var recuerdos by remember { mutableStateOf<List<Recuerdo>>(emptyList()) }
    var filtro by remember { mutableStateOf("todos") }
    var recuerdosRelacionados by remember { mutableStateOf<List<Recuerdo>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    var lugarNombre by remember { mutableStateOf<String?>(null) }
    var notaEditada by remember { mutableStateOf("") }
    var mostrarDialogo by remember { mutableStateOf(false) }

    val permisoConcedido = locationPermissionState.status.isGranted

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
                .documents.mapNotNull { it.toObject(Recuerdo::class.java) }
        } else emptyList()
        val recuerdosPrivados = if (filtro != "publicos" && user != null) {
            db.collection("usuarios")
                .document(user.uid)
                .collection("recuerdos_privados")
                .get()
                .await()
                .documents.mapNotNull { it.toObject(Recuerdo::class.java) }
        } else emptyList()
        recuerdos = recuerdosPublicos + recuerdosPrivados
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = filtro == "todos",
                onClick = { filtro = "todos" },
                label = { Text("Todos") },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_filter_all), contentDescription = "Filtro Todos") }
            )
            FilterChip(
                selected = filtro == "publicos",
                onClick = { filtro = "publicos" },
                label = { Text("Públicos") },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_public), contentDescription = "Filtro Públicos") }
            )
            FilterChip(
                selected = filtro == "privados",
                onClick = { filtro = "privados" },
                label = { Text("Privados") },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_private), contentDescription = "Filtro Privados") }
            )
        }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
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
    }

    if (mostrarDialogo && recuerdosRelacionados.isNotEmpty()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        val recuerdoActual = recuerdosRelacionados.getOrNull(selectedIndex)
        val currentUser = auth.currentUser

        // --- INICIO CAMBIO 1: Nuevo estado para controlar el diálogo de confirmación ---
        var showConfirmDeleteDialog by remember { mutableStateOf(false) }

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
                                Image(
                                    painter = rememberAsyncImagePainter(rec.url_imagen),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .fillMaxHeight()
                                )
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
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            label = { Text("Nota") },
                            readOnly = rec.userId != currentUser?.uid,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cerrar")
                }
            },
            confirmButton = {
                if (recuerdoActual != null && currentUser != null && recuerdoActual.userId == currentUser.uid) {
                    Row {
                        TextButton(onClick = {
                            guardarEdicionRecuerdo(recuerdoActual, notaEditada) {
                                mostrarDialogo = false
                                Toast.makeText(context, "Nota actualizada", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Guardar",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Guardar")
                        }
                        // --- INICIO CAMBIO 2: El botón de eliminar ahora muestra el diálogo de confirmación ---
                        TextButton(onClick = { showConfirmDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Eliminar",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Eliminar")
                        }
                    }
                }
            }
        )

        // --- INICIO CAMBIO 3: El nuevo diálogo de confirmación ---
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
                                    mostrarDialogo = false // Cierra también el diálogo principal
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
    val userId: String = "",
    val esPublico: Boolean = false,
    val nota: String = "",
    val url_imagen: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val nombre_usuario: String = "Anónimo"
)

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
    val db = Firebase.firestore
    val coleccion = if (recuerdo.esPublico) {
        db.collection("recuerdos_publicos")
    } else {
        if (recuerdo.userId.isEmpty()) return
        db.collection("usuarios").document(recuerdo.userId).collection("recuerdos_privados")
    }
    coleccion.whereEqualTo("timestamp", recuerdo.timestamp)
        .get()
        .addOnSuccessListener { docs ->
            docs.firstOrNull()?.reference?.update("nota", nuevaNota)?.addOnSuccessListener {
                onComplete()
            }
        }
}

fun eliminarRecuerdo(
    recuerdo: Recuerdo,
    onComplete: () -> Unit
) {
    val db = Firebase.firestore
    val coleccion = if (recuerdo.esPublico) {
        db.collection("recuerdos_publicos")
    } else {
        if (recuerdo.userId.isEmpty()) return
        db.collection("usuarios").document(recuerdo.userId).collection("recuerdos_privados")
    }
    coleccion.whereEqualTo("timestamp", recuerdo.timestamp)
        .get()
        .addOnSuccessListener { docs ->
            docs.firstOrNull()?.reference?.delete()?.addOnSuccessListener {
                onComplete()
            }
        }
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