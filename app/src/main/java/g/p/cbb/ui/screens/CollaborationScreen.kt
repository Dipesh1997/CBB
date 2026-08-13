package g.p.cbb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.viewmodel.CbbViewModel

import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborationScreen(viewModel: CbbViewModel) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userProfilePic by viewModel.userProfilePic.collectAsState()
    val currentSheetId by viewModel.currentSpreadsheetId.collectAsState()

    var inviteEmail by remember { mutableStateOf("") }
    var joinSheetId by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Team Collaboration & Database Invite", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userEmail == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sign in to Collaborate",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connect your Google Account to invite partners, sync database records, and manage store cloud backups.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Primary Visible Google Sign-In Button
                        Button(
                            onClick = {
                                viewModel.signIn(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google Account Icon"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Fallback Account Picker Button
                        OutlinedButton(
                            onClick = {
                                viewModel.openAccountPicker(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Google Account on Device",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                // User Account Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!userProfilePic.isNullOrEmpty()) {
                            AsyncImage(
                                model = userProfilePic,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userName ?: "User", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(userEmail ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = Color.Red)
                        }
                    }
                }

                // 1. My Admin Database Invite Code & Share Link Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("My Admin Database Invite Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Text(
                            text = "Share this Invite Code or Link with your team members so they can join your database as full admins without needing your login.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentSheetId ?: "Generating...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        currentSheetId?.let { id ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Database Invite Code", id))
                                            Toast.makeText(context, "Invite Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                currentSheetId?.let { id ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Udaari Admin Database Invite")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join my Udaari Customer Ledger Admin Database!\n\nInvite Code: $id\nInvite Link: https://g.p.cbb/join?sheetId=$id"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Database Invite Code via"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Invite Link & Code", fontSize = 12.sp)
                        }
                    }
                }

                // 2. Grant Gmail Access to Collaborator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF2E7D32))
                            Text("Grant Editor Permission to Gmail", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Text(
                            text = "Enter colleague's Gmail address to grant them editor rights on your Google Sheet database.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inviteEmail,
                            onValueChange = { inviteEmail = it },
                            label = { Text("Colleague's Gmail Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (inviteEmail.contains("@")) {
                                    viewModel.inviteCollaborator(inviteEmail)
                                    Toast.makeText(context, "Editor permission granted to $inviteEmail on Google Drive!", Toast.LENGTH_LONG).show()
                                    inviteEmail = ""
                                } else {
                                    Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = inviteEmail.contains("@"),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Full Access", fontSize = 12.sp)
                        }
                    }
                }

                // 3. Join / Connect to an Admin's Database Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF0288D1))
                            Text("Join Admin's Database", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Text(
                            text = "Paste an Admin's Invite Code / Sheet ID below to connect directly to their database with full admin controls.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = joinSheetId,
                            onValueChange = { joinSheetId = it },
                            label = { Text("Admin's Invite Code / Sheet ID") },
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (joinSheetId.isNotBlank()) {
                                    isJoining = true
                                    viewModel.joinAdminDatabase(joinSheetId) { success ->
                                        isJoining = false
                                        if (success) {
                                            Toast.makeText(context, "Connected to Admin Database! Data synced.", Toast.LENGTH_LONG).show()
                                            joinSheetId = ""
                                        } else {
                                            Toast.makeText(context, "Failed to connect to spreadsheet ID", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = joinSheetId.isNotBlank() && !isJoining,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            if (isJoining) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting & Syncing...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect & Sync Database", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
