package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.AppRule
import com.example.data.model.ChildProfile
import com.example.data.model.RewardQuest
import com.example.ui.KidGuardUiState
import com.example.ui.components.ChildAvatarBadge
import com.example.ui.components.PinPadDialog
import com.example.ui.components.ScreenTimeGauge
import com.example.ui.components.formatDurationMinutes
import com.example.ui.components.isCurrentTimeInWindow
import com.example.ui.components.isWeekendToday
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenContainer

@Composable
fun KidModeScreen(
    uiState: KidGuardUiState,
    child: ChildProfile,
    hasParentPin: Boolean,
    onRequestQuestApproval: (childId: String, questId: String) -> Unit,
    onLaunchApp: (packageName: String) -> Boolean,
    onVerifyAndExitKidMode: (pin: String) -> Boolean,
) {
    var showUnlockDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var blockedReasonDialog by remember { mutableStateOf<String?>(null) }

    val isWeekend = remember { isWeekendToday() }
    val inBedtime = child.bedtimeEnabled &&
        isCurrentTimeInWindow(child.bedtimeStartMinutes, child.bedtimeEndMinutes)
    val remaining = child.remainingMinutes(isWeekend)
    val isLockedOut = child.isPaused || inBedtime || remaining <= 0L

    val allowedDeviceApps = remember(uiState.deviceSnapshot.installedApps, uiState.appRules) {
        val rulesByPkg = uiState.appRules.associateBy { it.packageName }
        uiState.deviceSnapshot.installedApps.filter { app ->
            val rule = rulesByPkg[app.packageName]
            rule == null || rule.status != AppRule.STATUS_BLOCKED
        }.take(16)
    }

    BackHandler {
        if (hasParentPin) {
            pinError = null
            showUnlockDialog = true
        } else {
            onVerifyAndExitKidMode("")
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChildAvatarBadge(avatarKey = child.avatarKey, size = 46.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hi, ${child.name}!",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "KidGuard Safe Space",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (hasParentPin) {
                                pinError = null
                                showUnlockDialog = true
                            } else {
                                onVerifyAndExitKidMode("")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("exit_kid_mode_button")
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Parent Exit")
                    }
                }
            }

            item {
                if (isLockedOut) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_bedtime_moon),
                                contentDescription = "Screen time paused illustration",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Icon(
                                imageVector = if (inBedtime) Icons.Rounded.Bedtime else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    child.isPaused -> "Screen Time Paused"
                                    inBedtime -> "Bedtime Wind-Down"
                                    else -> "Time's Up for Today!"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Take a break, read a book, or complete a Bonus Quest below to ask your parent for extra minutes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            ScreenTimeGauge(
                                usedMinutes = child.usedMinutesToday,
                                totalAllowanceMinutes = child.totalAllowanceMinutes(isWeekend),
                                isPausedOrBlocked = false
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatDurationMinutes(remaining)} remaining",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap an approved app below or complete a quest to earn bonus minutes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.quests.isNotEmpty()) {
                item {
                    Text(
                        text = "Earn Bonus Time",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(uiState.quests, key = { it.id }) { quest ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = if (quest.status == RewardQuest.STATUS_APPROVED) Icons.Rounded.CheckCircle else Icons.Rounded.Stars,
                                contentDescription = null,
                                tint = if (quest.status == RewardQuest.STATUS_APPROVED) SafeGreen else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(quest.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "+${quest.rewardMinutes}m reward",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            when (quest.status) {
                                RewardQuest.STATUS_OPEN -> {
                                    FilledTonalButton(
                                        onClick = { onRequestQuestApproval(child.id, quest.id) }
                                    ) {
                                        Text("I Did It!")
                                    }
                                }
                                RewardQuest.STATUS_PENDING_APPROVAL -> {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.HourglassEmpty, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Asked Parent", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                else -> {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = SafeGreenContainer
                                    ) {
                                        Text(
                                            text = "Rewarded",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SafeGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (allowedDeviceApps.isNotEmpty()) {
                item {
                    Text(
                        text = "Approved Apps",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(allowedDeviceApps, key = { it.packageName }) { app ->
                    val rule = uiState.appRules.firstOrNull { it.packageName == app.packageName }
                    val appLimitExceeded = rule?.status == AppRule.STATUS_LIMITED &&
                        rule.dailyLimitMinutes > 0L &&
                        app.usageMinutesToday >= rule.dailyLimitMinutes

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                when {
                                    isLockedOut -> blockedReasonDialog = "Screen time is paused or in bedtime right now."
                                    appLimitExceeded -> blockedReasonDialog = "${app.appName} has reached its daily limit of ${rule?.dailyLimitMinutes}m."
                                    else -> {
                                        val ok = onLaunchApp(app.packageName)
                                        if (!ok) {
                                            blockedReasonDialog = "${app.appName} could not be launched on this device."
                                        }
                                    }
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            val bmp = remember(app.packageName) {
                                app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "${app.appName} icon",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (rule?.status == AppRule.STATUS_LIMITED) {
                                        "Daily limit: ${rule.dailyLimitMinutes}m"
                                    } else {
                                        app.category
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isLockedOut || appLimitExceeded) "Locked" else "Open",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isLockedOut || appLimitExceeded) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    if (showUnlockDialog) {
        PinPadDialog(
            title = "Parent Unlock",
            subtitle = "Enter your 4-digit Parent PIN to return to the Parent Dashboard.",
            errorText = pinError,
            onDismiss = { showUnlockDialog = false },
            onPinComplete = { pin ->
                if (onVerifyAndExitKidMode(pin)) {
                    showUnlockDialog = false
                } else {
                    pinError = "Incorrect PIN. Please try again."
                }
            }
        )
    }

    if (blockedReasonDialog != null) {
        AlertDialog(
            onDismissRequest = { blockedReasonDialog = null },
            title = { Text("App Paused") },
            text = { Text(blockedReasonDialog.orEmpty()) },
            confirmButton = {
                Button(onClick = { blockedReasonDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}
