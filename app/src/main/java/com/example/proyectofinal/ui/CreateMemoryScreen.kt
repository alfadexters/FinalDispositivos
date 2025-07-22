package com.example.proyectofinal.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.example.proyectofinal.R
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

@Composable
fun CreateMemoryScreen(
    navController: NavHostController,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val context = LocalContext.current
    val activity = context as Activity
    val db = Firebase.firestore
    val user = auth.currentUser ?: return

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var note by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val cloudinary = remember {
        Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", "dntjc74uy",
                "api_key", "619748843429275",
                "api_secret", "4CmP7K1ZTzqIY9nrix_oE0FecsQ"
            )
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap = bitmap
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener {
                    location = it ?: run {
                        Toast.makeText(context, "Ubicación no disponible, intente de nuevo.", Toast.LENGTH_SHORT).show()
                        null
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Error obteniendo ubicación", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // La ubicación se solicita una vez al entrar a la pantalla
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val isFormValid = imageBitmap != null && location != null && note.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Nuevo Recuerdo", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Tomar foto")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Foto")
        }

        imageBitmap?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Escribe una nota") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    val ciudad = location?.let { obtenerCiudadDesdeUbicacion(context, it) }
                    val notaMejorada = mejorarNotaConGemini(note, ciudad)
                    if (notaMejorada != null) {
                        note = notaMejorada
                        Toast.makeText(context, "Nota mejorada con IA", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error con Gemini", Toast.LENGTH_SHORT).show()
                    }
                    isLoading = false
                }
            },
            enabled = note.isNotBlank() && !isLoading
        ) {
            Text("Mejorar con IA")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Privacidad: ")
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isPublic,
                onClick = { isPublic = true },
                label = { Text("Pública") },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_public), contentDescription = "Icono Público") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !isPublic,
                onClick = { isPublic = false },
                label = { Text("Privada") },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_private), contentDescription = "Icono Privado") }
            )
        }

        Button(
            onClick = {
                isLoading = true
                val stream = ByteArrayOutputStream()
                imageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val byteArray = stream.toByteArray()

                Thread {
                    try {
                        val uploadResult = cloudinary.uploader().upload(byteArray, ObjectUtils.emptyMap())
                        val imageUrl = uploadResult["secure_url"]?.toString() ?: ""

                        val data = hashMapOf(
                            "userId" to user.uid,
                            "esPublico" to isPublic,
                            "nota" to note,
                            "url_imagen" to imageUrl,
                            "latitud" to location!!.latitude,
                            "longitud" to location!!.longitude,
                            "timestamp" to Timestamp.now(),
                            "nombre_usuario" to (user.displayName ?: "Anónimo")
                        )

                        val collection = if (isPublic) {
                            db.collection("recuerdos_publicos")
                        } else {
                            db.collection("usuarios").document(user.uid).collection("recuerdos_privados")
                        }
                        collection.add(data)

                        activity.runOnUiThread {
                            isLoading = false
                            Toast.makeText(context, "Recuerdo guardado", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } catch (e: Exception) {
                        Log.e("Cloudinary", "Error al subir imagen", e)
                        activity.runOnUiThread {
                            isLoading = false
                            Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isFormValid
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Guardar Recuerdo")
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isLoading) "Guardando..." else "Guardar")
        }

        // --- BOTÓN DE UBICACIÓN ELIMINADO ---
    }
}

fun obtenerCiudadDesdeUbicacion(context: Context, location: Location): String? {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val direcciones = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        direcciones?.firstOrNull()?.locality
    } catch (e: Exception) {
        null
    }
}

suspend fun mejorarNotaConGemini(textoOriginal: String, ciudad: String?): String? {
    // IMPORTANTE: Reemplaza "TU_API_KEY_AQUI" con tu clave real de API de Gemini
    val apiKey = "AIzaSyB-6-5qKUcBl-p0NWGaNcYUfJyZzlKk39o"
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    val ubicacionTexto = ciudad?.let { "Lugar aproximado: $it" } ?: ""

    val prompt = """
        Mejora ligeramente esta nota para que sea más clara y un poco más descriptiva.
        Puedes incluir una pequeña referencia al lugar si encaja naturalmente.
        No seas poético ni emocional, dame una sola respuesta que exprese todo lo que digo en la nota.
        
        $ubicacionTexto
        
        Nota del usuario:
        $textoOriginal
    """.trimIndent()

    val json = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        }))
    }

    return withContext(Dispatchers.IO) {
        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-goog-api-key", apiKey)
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            val response = StringBuilder()
            BufferedReader(conn.inputStream.reader()).useLines { lines ->
                lines.forEach { response.append(it) }
            }

            val jsonResponse = JSONObject(response.toString())
            return@withContext jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}