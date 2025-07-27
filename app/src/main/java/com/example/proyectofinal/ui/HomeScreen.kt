package com.example.proyectofinal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.proyectofinal.R
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen(
    navController: NavHostController,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Logo ---
        Image(
            painter = painterResource(id = R.drawable.logo_nombre_app_2),
            contentDescription = "Logo de la aplicación",
            modifier = Modifier.width(250.dp)
        )

        // --- Imagen Central ---
        Image(
            painter = painterResource(id = R.drawable.fto_screen),
            contentDescription = "Imagen decorativa",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(80.dp))

        // --- Sección Inferior: Botones de Acción ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            OutlinedButton(
                onClick = {
                    auth.signOut()
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
}