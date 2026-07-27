package g.p.cbb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import g.p.cbb.ui.screens.ActivityHistoryScreen
import g.p.cbb.ui.screens.CustomerDetailScreen
import g.p.cbb.ui.screens.HomeScreen
import g.p.cbb.viewmodel.CbbViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

@Composable
fun CbbApp() {
    val navController = rememberNavController()
    val viewModel: CbbViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onCustomerClick = { customer ->
                    viewModel.selectCustomer(customer)
                    navController.navigate("detail")
                },
                onHistoryClick = {
                    navController.navigate("history")
                }
            )
        }
        composable("detail") {
            CustomerDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("history") {
            ActivityHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
