package g.p.cbb.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import g.p.cbb.data.entity.Customer
import g.p.cbb.repository.SettingsRepository
import g.p.cbb.ui.theme.Info
import g.p.cbb.utils.ImageUtils

@Composable
fun AddEditCustomerDialog(
    customerToEdit: Customer? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, profileImageUri: String?, isBadDebt: Boolean) -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }

    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(customerToEdit?.phone ?: "") }
    var address by remember { mutableStateOf(customerToEdit?.address ?: "") }
    var isBadDebt by remember { mutableStateOf(customerToEdit?.isBadDebt ?: false) }
    var profileImageUri by remember { mutableStateOf<String?>(customerToEdit?.profileImageUri) }
    var compressPhoto by remember { mutableStateOf(settingsRepository.getCompressProfilePhotos()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ImageUtils.saveCustomerProfilePhoto(context, uri, compressPhoto)
            profileImageUri = savedPath ?: uri.toString()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            try {
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        val contactId = if (idIndex >= 0) it.getString(idIndex) else ""
                        val contactName = if (nameIndex >= 0) it.getString(nameIndex) else ""
                        val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) else 0

                        if (contactName.isNotBlank()) {
                            name = contactName
                        }

                        if (hasPhone > 0 && contactId.isNotBlank()) {
                            val phoneCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pCursor ->
                                if (pCursor.moveToFirst()) {
                                    val phoneNumIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (phoneNumIndex >= 0) {
                                        val retrievedPhone = pCursor.getString(phoneNumIndex)
                                        if (!retrievedPhone.isNullOrBlank()) {
                                            phone = retrievedPhone
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        }
    }

    val launchContactPicker = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactPickerLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (customerToEdit == null) "Add New Customer" else "Edit Customer",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Photo Picker Section
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    CustomerAvatar(
                        name = if (name.isNotBlank()) name else "C",
                        profileImageUri = profileImageUri,
                        size = 80.dp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (profileImageUri == null) "Add Photo" else "Change Photo", fontSize = 12.sp)
                    }

                    if (profileImageUri != null) {
                        TextButton(
                            onClick = { profileImageUri = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }

                // Photo Compression Option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = compressPhoto,
                        onCheckedChange = { checked ->
                            compressPhoto = checked
                            settingsRepository.saveCompressProfilePhotos(checked)
                        }
                    )
                    Column {
                        Text(
                            text = "Compress Profile Photo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (compressPhoto) "Saves storage & fast sync (JPEG format)" else "Saves original uncompressed photo format",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedButton(
                    onClick = launchContactPicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick from Phone Book", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Full name of the customer for PDF statements",
                        fontSize = 11.sp,
                        color = Info
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it in "+- ()" }) {
                            phone = input
                        }
                    },
                    label = { Text("Phone") },
                    placeholder = { Text("+91 98765-43210") },
                    trailingIcon = {
                        IconButton(onClick = launchContactPicker) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = "Pick Contact",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Phone number for sharing bill details",
                        fontSize = 11.sp,
                        color = Info
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isBadDebt,
                        onCheckedChange = { isBadDebt = it }
                    )
                    Text(
                        text = "Flag as Bad Debt / Default Risk",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name, phone, address, profileImageUri, isBadDebt)
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (customerToEdit == null) "Save Customer" else "Update Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
