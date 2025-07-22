package com.example.proyectofinal.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.example.proyectofinal.ui.CreateMemoryScreen
import com.example.proyectofinal.ui.LoginScreen
import com.example.proyectofinal.ui.HomeScreen
import com.example.proyectofinal.ui.MapScreen
import com.example.proyectofinal.ui.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(navController)
        }
        composable("login") {
            LoginScreen(navController)
        }
        composable("home") {
            HomeScreen(navController)
        }
        composable("create") {
            CreateMemoryScreen(navController)
        }
        composable("map") {
            MapScreen()
        }



    }
}
