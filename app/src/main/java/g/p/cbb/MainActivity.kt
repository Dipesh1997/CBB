package g.p.cbb

import android.Manifest
import android.accounts.AccountManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.work.*
import dagger.hilt.android.AndroidEntryPoint
import g.p.cbb.ui.screens.*
import g.p.cbb.ui.theme.CBBTheme
import g.p.cbb.utils.SyncWorker
import g.p.cbb.viewmodel.CbbViewModel
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CbbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleOAuthRedirect(intent)
        enableEdgeToEdge()
        setContent {
            CBBTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CbbApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val urlString = data.toString()
        if (urlString.contains("access_token=")) {
            val tokenPart = urlString.substringAfter("access_token=", "").substringBefore("&")
            val accessToken = android.net.Uri.decode(tokenPart)
            if (accessToken.isNotEmpty()) {
                viewModel.saveOAuthToken(accessToken)
                android.widget.Toast.makeText(this, "Google Sign-In Authorized!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.Default.Receipt)
    object History : Screen("history", "Audit Log", Icons.Default.History)
}

@Composable
fun CbbApp(viewModel: CbbViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val unreadCount by viewModel.unreadHistoryCount.collectAsState(initial = 0)
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()

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

    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
        }
    }

    if (syncErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { syncErrorMessage = null },
            title = { Text("Cloud Sync Diagnostic") },
            text = { Text(syncErrorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { syncErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
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
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                        } else {
                            syncErrorMessage = "Google account not found on device. Please sign in to Google in your device settings."
                        }
                    } else {
                        syncErrorMessage = message
                    }
                }
                is CbbViewModel.SyncEvent.Success -> {
                    android.widget.Toast.makeText(context, "Sync Successful", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Schedule Cloud Sync Worker
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
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
        Screen.Transactions,
        Screen.History
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
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionHistoryScreen(
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
            composable("detail") {
                selectedCustomer?.let { cust ->
                    CustomerDetailScreen(
                        viewModel = viewModel,
                        customer = cust,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
