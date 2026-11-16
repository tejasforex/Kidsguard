package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ChildProfile
import com.example.ui.KidGuardUiState
import com.example.ui.components.AddEditChildDialog
import com.example.ui.components.ChildAvatarBadge
import com.example.ui.components.PinPadDialog
import com.example.ui.components.formatDurationMinutes

@Composable
fun FamilySettingsScreen(
    uiState: KidGuardUiState,
    userEmail: String?,
    onAddChild: (String, Long, String, Long, Long) -> Unit,
    onUpdateChild: (String, String, Long, String, Long, Long) -> Unit,
    onDeleteChild: (String) -> Unit,
    onSetParentPin: (String) -> Unit,
    onEnterKidMode: (ChildProfile) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<ChildProfile?>(null) }
    var deletingChild by remember { mutableStateOf<ChildProfile?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Family & Security",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.familySettings?.displayName ?: "Parent Account",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = userEmail ?: "Signed in with Google",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { showPinDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("manage_pin_button")
                            ) {
                                Icon(Icons.Rounded.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (uiState.familySettings?.hasParentPin == true) "Change PIN" else "Set Parent PIN"
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Child Profiles (${uiState.children.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            items(uiState.children, key = { it.id }) { child ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChildAvatarBadge(avatarKey = child.avatarKey, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${child.name}, age ${child.age}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Weekday ${formatDurationMinutes(child.dailyLimitMinutes)} • Weekend ${formatDurationMinutes(child.weekendLimitMinutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingChild = child }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${child.name}")
                            }
                            IconButton(onClick = { deletingChild = child }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete ${child.name}",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Button(
                            onClick = { onEnterKidMode(child) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Launch ${child.name}'s Kid Mode")
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Android Usage Access", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (uiState.deviceSnapshot.hasUsageAccess) {
                                "Granted: live app usage telemetry is enabled on this device."
                            } else {
                                "Not granted yet: open Usage Access Settings to enable live screen time tracking."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage Usage Access")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddDialog) {
        AddEditChildDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, age, avatar, daily, weekend ->
                onAddChild(name, age, avatar, daily, weekend)
                showAddDialog = false
            }
        )
    }

    val editTarget = editingChild
    if (editTarget != null) {
        AddEditChildDialog(
            initialChild = editTarget,
            onDismiss = { editingChild = null },
            onConfirm = { name, age, avatar, daily, weekend ->
                onUpdateChild(editTarget.id, name, age, avatar, daily, weekend)
                editingChild = null
            }
        )
    }

    val deleteTarget = deletingChild
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deletingChild = null },
            title = { Text("Remove ${deleteTarget.name}?") },
            text = { Text("This will remove ${deleteTarget.name}'s profile from your family dashboard.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChild(deleteTarget.id)
                        deletingChild = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deletingChild = null }) { Text("Cancel") }
            }
        )
    }

    if (showPinDialog) {
        PinPadDialog(
            title = "Set Parent PIN",
            subtitle = "Enter a 4-digit PIN required to exit Kid Mode.",
            onDismiss = { showPinDialog = false },
            onPinComplete = { pin ->
                onSetParentPin(pin)
                showPinDialog = false
            }
        )
    }
}
