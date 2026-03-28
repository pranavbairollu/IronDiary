package com.example.irondiary.ui.navigation

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.irondiary.data.Resource
import com.example.irondiary.ui.academics.AcademicsScreen
import com.example.irondiary.ui.academics.LogStudySessionDialog
import com.example.irondiary.ui.academics.TaskDialog
import com.example.irondiary.ui.auth.AuthScreen
import com.example.irondiary.ui.auth.SplashScreen
import com.example.irondiary.ui.calendar.CalendarScreen
import com.example.irondiary.ui.graph.WeightGraphScreen
import com.example.irondiary.viewmodel.AuthUiState
import com.example.irondiary.viewmodel.AuthViewModel
import com.example.irondiary.viewmodel.AuthViewModelFactory
import com.example.irondiary.viewmodel.MainViewModel
import com.example.irondiary.viewmodel.MainViewModelFactory
import com.example.irondiary.ui.components.SettingsDialog
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val application = LocalContext.current.applicationContext as Application
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(application))
    val uiState by authViewModel.uiState.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Idle) {
            navController.navigate("auth") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    AnimatedContent(
        targetState = uiState is AuthUiState.Checking,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
        },
        label = "SplashScreenTransition"
    ) { isChecking ->
        if (isChecking) {
            SplashScreen()
        } else {
            NavHost(
                navController = navController,
                startDestination = if (uiState is AuthUiState.Success) "main" else "auth"
            ) {
                composable("auth") {
                    AuthScreen(onSignIn = {
                        navController.navigate("main") {
                            popUpTo("auth") { inclusive = true }
                        }
                    })
                }
                composable("main") {
                    TopLevelNav()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelNav() {
    val navController = rememberNavController()
    var showLogChoiceDialog by remember { mutableStateOf(false) }
    var showLogStudySessionDialog by remember { mutableStateOf(false) }
    var showLogTaskDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))
    val saveStatus by mainViewModel.saveStatus.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(saveStatus) {
        if (saveStatus is Resource.Error) {
            Toast.makeText(context, (saveStatus as Resource.Error).message ?: "Error saving", Toast.LENGTH_LONG).show()
            mainViewModel.resetSaveStatus()
        } else if (saveStatus is Resource.Success) {
            mainViewModel.resetSaveStatus()
            showLogStudySessionDialog = false
            showLogTaskDialog = false
        }
    }

    if (showLogChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showLogChoiceDialog = false },
            title = { Text("What do you want to log?") },
            text = {
                Column {
                    TextButton(onClick = {
                        showLogChoiceDialog = false
                        showLogStudySessionDialog = true
                    }) {
                        Text("Study Session")
                    }
                    TextButton(onClick = {
                        showLogChoiceDialog = false
                        showLogTaskDialog = true
                    }) {
                        Text("Task")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLogChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogStudySessionDialog) {
        LogStudySessionDialog(
            onDismiss = { showLogStudySessionDialog = false },
            onConfirm = { subject, duration ->
                mainViewModel.addStudySession(subject, duration)
            },
            isLoading = saveStatus is Resource.Loading
        )
    }

    if (showLogTaskDialog) {
        TaskDialog(
            onDismiss = { showLogTaskDialog = false },
            onConfirm = { description ->
                mainViewModel.addTask(description)
            }
            // If TaskDialog doesn't support isLoading yet, we rely on the dialog staying open
            // until LaunchedEffect closes it upon Success. Adding isLoading parameter to TaskDialog
            // would require editing it as well. We'll pass it if applicable later.
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            mainViewModel = mainViewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IronDiary") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { screen.icon() },
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
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            if (currentDestination?.route == Screen.Academics.route) {
                FloatingActionButton(onClick = { showLogChoiceDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Log Data")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Academics.route) { AcademicsScreen(mainViewModel) }
            composable(Screen.Graph.route) { WeightGraphScreen() }
            composable(Screen.Templates.route) { com.example.irondiary.ui.templates.TemplatesScreen() }
        }
    }
}
