package com.ssafy.marusys.navigation

import android.app.Application
import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ssafy.marusys.presentation.screen.AudioScreen
import com.ssafy.marusys.presentation.screen.HomeScreen
import com.ssafy.marusys.presentation.screen.PermissionsScreen
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    context: Context,
) {
    val application = context as Application
    val homeViewModel = HomeViewModel()
    NavHost(
        navController = navController,
//        startDestination = "permissions_fragment"
        startDestination = "home"
    ) {
        // permissions page
        composable(
            "permissions_fragment",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            PermissionsScreen(viewModel = homeViewModel
            )
        }
        // home page
        composable(
            "home",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            HomeScreen(
                navController = navController,
                context = context,
            )
        }

    }
}
