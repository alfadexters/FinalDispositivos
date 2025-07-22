package com.example.proyectofinal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen(
    navController: NavHostController,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val user = auth.currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "¡Bienvenido!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        // Muestra el nombre del usuario si está disponible
        user?.displayName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Sección de Información del Usuario con Iconos ---
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth(0.8f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Icono de Correo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Correo: ${user?.email ?: "No disponible"}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Icono de UID",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("UID: ${user?.uid ?: "No disponible"}")
            }
        }


        Spacer(modifier = Modifier.height(48.dp))

        // --- Sección de Botones de Acción con Iconos ---
        Button(
            onClick = { navController.navigate("create") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Crear Recuerdo")
            Spacer(Modifier.width(8.dp))
            Text("Crear Recuerdo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("map") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Map, contentDescription = "Ver Mapa")
            Spacer(Modifier.width(8.dp))
            Text("Ver Mapa de Recuerdos")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de cerrar sesión con un estilo diferente para distinguirlo
        OutlinedButton(
            onClick = {
                auth.signOut()
                // También cerramos sesión de Google para que vuelva a preguntar la cuenta
                // val googleSignInClient = GoogleSignIn.getClient(context, gso) // Necesitarías GSO aquí
                // googleSignInClient.signOut()
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Logout, contentDescription = "Cerrar Sesión")
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}