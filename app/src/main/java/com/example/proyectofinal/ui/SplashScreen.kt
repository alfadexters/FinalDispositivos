package com.example.proyectofinal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.proyectofinal.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 2000) // Duración de la animación del logo
    )

    // La lógica para navegar no cambia
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(3000) // Tiempo total en milisegundos que se muestra el splash

        navController.popBackStack() // Limpia el historial para no volver al splash
        if (auth.currentUser != null) {
            navController.navigate("home")
        } else {
            navController.navigate("login")
        }
    }

    // --- Diseño del Splash Screen con tus imágenes PNG ---
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // Centra todo el contenido del Box
    ) {
        // 1. Imagen de fondo que ocupa toda la pantalla
        Image(
            painter = painterResource(id = R.drawable.background_screen_1),
            contentDescription = "Fondo de pantalla de bienvenida",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop // Escala la imagen para que cubra todo sin deformarse
        )

        // 2. Logo superpuesto en el centro, con animación de aparición
        Image(
            painter = painterResource(id = R.drawable.logo_nombre_app_2),
            contentDescription = "Logo de la App",
            modifier = Modifier
                .width(250.dp) // Ajusta el ancho del logo como prefieras
                .alpha(alphaAnim.value) // Aplica la animación de aparición solo al logo
        )
    }
}