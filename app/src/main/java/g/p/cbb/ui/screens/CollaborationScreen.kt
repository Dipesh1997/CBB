package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import g.p.cbb.viewmodel.CbbViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborationScreen(viewModel: CbbViewModel) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    var inviteEmail by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Team Collaboration") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            if (userEmail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { 
                        Toast.makeText(context, "Contacting Google...", Toast.LENGTH_SHORT).show()
                        viewModel.signIn(context) 
                    }) {
                        Icon(Icons.Default.Login, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(userName ?: "User", fontWeight = FontWeight.Bold)
                            Text(userEmail ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.Default.Logout, null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Invite Collaborator", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Colleague's Gmail") },
                    leadingIcon = { Icon(Icons.Default.Email, null) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (inviteEmail.contains("@")) {
                            viewModel.inviteCollaborator(inviteEmail)
                            Toast.makeText(context, "Invite sent to $inviteEmail", Toast.LENGTH_SHORT).show()
                            inviteEmail = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Database")
                }
            }
        }
    }
}
