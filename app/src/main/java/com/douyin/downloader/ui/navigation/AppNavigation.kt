package com.douyin.downloader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.douyin.downloader.ui.downloads.DownloadCenterViewModel
import com.douyin.downloader.ui.downloads.DownloadsScreen
import com.douyin.downloader.ui.history.HistoryScreen
import com.douyin.downloader.ui.home.BatchScreen
import com.douyin.downloader.ui.home.HomeScreen
import com.douyin.downloader.ui.home.HomeViewModel
import com.douyin.downloader.ui.profile.ProfileScreen

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "解析", Icons.Default.Home),
    Batch("batch", "批量", Icons.Default.Add),
    Downloads("downloads", "下载", Icons.Default.DateRange),
    History("history", "历史", Icons.AutoMirrored.Filled.List),
    Profile("profile", "我的", Icons.Default.Person),
}

@Composable
fun AppNavigation(sharedUrl: String?) {
    val navController = rememberNavController()
    val viewModel: HomeViewModel = hiltViewModel()

    // ON_RESUME 时通知 ViewModel：用户可能从系统设置返回授权了
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onPermissionResumed()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
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
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
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
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
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
