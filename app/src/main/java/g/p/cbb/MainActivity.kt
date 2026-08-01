package g.p.cbb

import android.Manifest
import android.accounts.AccountManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.*
import g.p.cbb.utils.SyncWorker
import java.util.concurrent.TimeUnit
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import g.p.cbb.ui.screens.*
import g.p.cbb.viewmodel.CbbViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CbbApp()
                }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.History)
    object Backup : Screen("backup", "Backup", Icons.Default.Settings)
    object Collab : Screen("collab", "Team", Icons.Default.Person)
}

@Composable
fun CbbApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: CbbViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val unreadCount by viewModel.unreadHistoryCount.collectAsState(initial = 0)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.syncNow()
        } else {
            android.widget.Toast.makeText(context, "Account permission is required for Cloud Sync", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncNow()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncNow()
        }
    }

    LaunchedEffect(viewModel.syncEvents) {
        viewModel.syncEvents.collect { event ->
            when (event) {
                is CbbViewModel.SyncEvent.PickAccount -> {
                    val intent = AccountManager.newChooseAccountIntent(
                        null, null, arrayOf("com.google"), null, null, null, null
                    )
                    pickerLauncher.launch(intent)
                }
                is CbbViewModel.SyncEvent.RequestAuthorization -> {
                    authLauncher.launch(event.intent)
                }
                is CbbViewModel.SyncEvent.Error -> {
                    val message = event.message
                    if (message.contains("name must not be empty", ignoreCase = true)) {
                        // Request permission if it might be the cause
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                        } else {
                            android.widget.Toast.makeText(context, "Google account not found on device. Please sign in to settings.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                is CbbViewModel.SyncEvent.Success -> {
                    android.widget.Toast.makeText(context, "Sync Successful", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Schedule Cloud Sync
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cloud_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }

    val items = listOf(
        Screen.Home,
        Screen.History,
        Screen.Collab,
        Screen.Backup
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    val isHomeSelected = screen == Screen.Home && (currentDestination?.route == Screen.Home.route || currentDestination?.route == "detail")
                    val isSelected = if (screen == Screen.Home) isHomeSelected else currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = { 
                            BadgedBox(
                                badge = {
                                    if (screen == Screen.History && unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(screen.icon, contentDescription = null)
                            }
                        },
                        label = { Text(screen.label) },
                        selected = isSelected,
                        onClick = {
                            if (screen == Screen.Home && currentDestination?.route == "detail") {
                                navController.popBackStack(Screen.Home.route, inclusive = false)
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onCustomerClick = { customer ->
                        viewModel.selectCustomer(customer)
                        navController.navigate("detail")
                    }
                )
            }
            composable(Screen.History.route) {
                ActivityHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Collab.route) {
                CollaborationScreen(viewModel = viewModel)
            }
            composable(Screen.Backup.route) {
                BackupScreen(viewModel = viewModel)
            }
            composable("detail") {
                CustomerDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
