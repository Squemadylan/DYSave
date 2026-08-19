package com.douyin.downloader.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.douyin.downloader.R
import com.douyin.downloader.ui.components.YuanNavItem
import com.douyin.downloader.ui.components.YuanNavigationBar
import com.douyin.downloader.ui.downloads.DownloadCenterViewModel
import com.douyin.downloader.ui.downloads.DownloadsScreen
import com.douyin.downloader.ui.history.HistoryScreen
import com.douyin.downloader.ui.home.BatchScreen
import com.douyin.downloader.ui.home.HomeScreen
import com.douyin.downloader.ui.home.HomeViewModel
import com.douyin.downloader.ui.profile.ProfileScreen

enum class Screen(
    val route: String,
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, Icons.Default.Home),
    Batch("batch", R.string.nav_batch, Icons.Default.Add),
    Downloads("downloads", R.string.nav_downloads, Icons.Default.Download),
    History("history", R.string.nav_history, Icons.AutoMirrored.Filled.List),
    Profile("profile", R.string.nav_profile, Icons.Default.Person),
}

@Composable
fun AppNavigation(sharedUrl: String?) {
    val navController = rememberNavController()
    val viewModel: HomeViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onPermissionResumed()
    }

    val navItems = listOf(
        YuanNavItem(Screen.Home.route, "", Screen.Home.icon),
        YuanNavItem(Screen.Batch.route, "", Screen.Batch.icon),
        YuanNavItem(Screen.Downloads.route, "", Screen.Downloads.icon),
        YuanNavItem(Screen.History.route, "", Screen.History.icon),
        YuanNavItem(Screen.Profile.route, "", Screen.Profile.icon),
    )
    val itemsWithLabel = navItems.map { item ->
        val label = when (item.key) {
            Screen.Home.route -> stringResource(Screen.Home.labelRes)
            Screen.Batch.route -> stringResource(Screen.Batch.labelRes)
            Screen.Downloads.route -> stringResource(Screen.Downloads.labelRes)
            Screen.History.route -> stringResource(Screen.History.labelRes)
            Screen.Profile.route -> stringResource(Screen.Profile.labelRes)
            else -> ""
        }
        item.copy(label = label)
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                YuanNavigationBar(
                    currentKey = currentRoute,
                    onSelect = { item ->
                        navController.navigate(item.key) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    items = itemsWithLabel,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    sharedUrl = sharedUrl,
                    viewModel = viewModel,
                    onNavigateToDownloads = {
                        navController.navigate(Screen.Downloads.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBatch = {
                        navController.navigate(Screen.Batch.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Screen.Batch.route) {
                BatchScreen(viewModel = viewModel)
            }
            composable(Screen.Downloads.route) {
                val downloadVm: DownloadCenterViewModel = hiltViewModel()
                DownloadsScreen(viewModel = downloadVm)
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}