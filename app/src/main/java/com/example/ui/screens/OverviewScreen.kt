package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.device.DeviceAppInfo
import com.example.data.model.ChildProfile
import com.example.data.model.RewardQuest
import com.example.ui.KidGuardUiState
import com.example.ui.components.AddEditChildDialog
import com.example.ui.components.ChildAvatarBadge
import com.example.ui.components.ScreenTimeGauge
import com.example.ui.components.formatDurationMinutes
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.components.isCurrentTimeInWindow
import com.example.ui.components.isWeekendToday
import com.example.ui.theme.BedtimeIndigo
import com.example.ui.theme.BedtimeIndigoContainer
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenContainer

@Composable
fun OverviewScreen(
    uiState: KidGuardUiState,
    onSelectChild: (String) -> Unit,
    onAddChild: (String, Long, String, Long, Long) -> Unit,
    onTogglePause: (ChildProfile) -> Unit,
    onGrantBonus: (ChildProfile, Long) -> Unit,
    onResetDailyUsage: (ChildProfile) -> Unit,
    onSyncDeviceUsage: (ChildProfile) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onApproveQuest: (ChildProfile, RewardQuest) -> Unit,
    onDeclineQuest: (String, RewardQuest) -> Unit,
    onEnterKidMode: (ChildProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showAddChildDialog by remember { mutableStateOf(false) }

    val selectedChild = uiState.selectedChild
    val isWeekend = remember { isWeekendToday() }
    val pendingQuests = remember(uiState.quests) {
        uiState.quests.filter { it.status == RewardQuest.STATUS_PENDING_APPROVAL }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Family Shield",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Welcome back, ${uiState.familySettings?.displayName ?: "Parent"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddChildDialog = true },
                        modifier = Modifier.testTag("overview_add_child_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add child profile",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Child")
                    }
                }
            }

            if (uiState.children.isEmpty()) {
                item {
                    EmptyFamilyCard(onAddChildClick = { showAddChildDialog = true })
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.children.forEach { child ->
                            FilterChip(
                                selected = child.id == selectedChild?.id,
                                onClick = { onSelectChild(child.id) },
                                label = {
                                    Text(
                                        text = "${child.name} (${child.age})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                leadingIcon = {
                                    ChildAvatarBadge(avatarKey = child.avatarKey, size = 24.dp)
                                },
                                modifier = Modifier.testTag("child_chip_${child.name.lowercase()}")
                            )
                        }
                    }
                }

                if (selectedChild != null) {
                    item {
                        ChildHeroSummaryCard(
                            child = selectedChild,
                            isWeekend = isWeekend,
                            onTogglePause = { onTogglePause(selectedChild) },
                            onAddBonus15 = { onGrantBonus(selectedChild, 15L) },
                            onResetToday = { onResetDailyUsage(selectedChild) },
                            onEnterKidMode = { onEnterKidMode(selectedChild) }
                        )
                    }

                    if (pendingQuests.isNotEmpty()) {
                        item {
                            PendingApprovalsCard(
                                child = selectedChild,
                                quests = pendingQuests,
                                onApprove = { q -> onApproveQuest(selectedChild, q) },
                                onDecline = { q -> onDeclineQuest(selectedChild.id, q) }
                            )
                        }
                    }

                    item {
                        DeviceTelemetryCard(
                            hasUsageAccess = uiState.deviceSnapshot.hasUsageAccess,
                            totalDeviceMinutes = uiState.deviceSnapshot.totalMinutesToday,
                            onOpenUsageSettings = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            onRefresh = onRefreshTelemetry,
                            onSyncToChild = { onSyncDeviceUsage(selectedChild) }
                        )
                    }

                    val activeApps = uiState.deviceSnapshot.installedApps
                        .filter { it.usageMinutesToday > 0L }
                        .take(5)
                    if (activeApps.isNotEmpty()) {
                        item {
                            Text(
                                text = "Most Used on This Device Today",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(activeApps, key = { it.packageName }) { app ->
                            DeviceUsageRow(app = app)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddChildDialog) {
        AddEditChildDialog(
            onDismiss = { showAddChildDialog = false },
            onConfirm = { name, age, avatar, daily, weekend ->
                onAddChild(name, age, avatar, daily, weekend)
                showAddChildDialog = false
            }
        )
    }
}

@Composable
private fun EmptyFamilyCard(onAddChildClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_family_shield),
                contentDescription = "Create your first child profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Set up your first child profile",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create a profile to set daily screen time caps, bedtime downtime, app guardrails, and positive reward quests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddChildClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_state_add_child_button")
            ) {
                Icon(Icons.Rounded.ChildCare, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Child Profile")
            }
        }
    }
}

@Composable
private fun ChildHeroSummaryCard(
    child: ChildProfile,
    isWeekend: Boolean,
    onTogglePause: () -> Unit,
    onAddBonus15: () -> Unit,
    onResetToday: () -> Unit,
    onEnterKidMode: () -> Unit,
) {
    val totalAllowance = child.totalAllowanceMinutes(isWeekend)
    val inBedtime = child.bedtimeEnabled &&
        isCurrentTimeInWindow(child.bedtimeStartMinutes, child.bedtimeEndMinutes)
    val inStudyFocus = child.studyFocusEnabled &&
        isCurrentTimeInWindow(child.studyStartMinutes, child.studyEndMinutes)

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChildAvatarBadge(avatarKey = child.avatarKey, size = 48.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = child.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isWeekend) "Weekend limit active" else "Weekday limit active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        child.isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                        inBedtime -> BedtimeIndigoContainer
                        else -> SafeGreenContainer
                    }
                ) {
                    Text(
                        text = when {
                            child.isPaused -> "Paused"
                            inBedtime -> "Bedtime"
                            inStudyFocus -> "Study Mode"
                            else -> "Active"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            child.isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                            inBedtime -> BedtimeIndigo
                            else -> SafeGreen
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ScreenTimeGauge(
                    usedMinutes = child.usedMinutesToday,
                    totalAllowanceMinutes = totalAllowance,
                    isPausedOrBlocked = child.isPaused || inBedtime
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatLine(
                        label = "Used today",
                        value = formatDurationMinutes(child.usedMinutesToday)
                    )
                    StatLine(
                        label = "Daily allowance",
                        value = formatDurationMinutes(totalAllowance)
                    )
                    if (child.bonusMinutesToday > 0L) {
                        StatLine(
                            label = "Bonus earned",
                            value = "+${child.bonusMinutesToday}m"
                        )
                    }
                    if (child.bedtimeEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatMinutesOfDay(child.bedtimeStartMinutes)} – ${formatMinutesOfDay(child.bedtimeEndMinutes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onTogglePause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (child.isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        contentColor = if (child.isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("toggle_pause_button")
                ) {
                    Icon(
                        imageVector = if (child.isPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (child.isPaused) "Resume" else "Pause Now")
                }

                FilledTonalButton(
                    onClick = onAddBonus15,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_bonus_15_button")
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+15m Bonus")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onEnterKidMode,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("enter_kid_mode_button")
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kid Mode")
                }

                OutlinedButton(
                    onClick = onResetToday,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_today_button")
                ) {
                    Text("Reset Timer")
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PendingApprovalsCard(
    child: ChildProfile,
    quests: List<RewardQuest>,
    onApprove: (RewardQuest) -> Unit,
    onDecline: (RewardQuest) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Bonus Requests from ${child.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            quests.forEach { quest ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quest.title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Reward: +${quest.rewardMinutes}m screen time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            IconButton(onClick = { onDecline(quest) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Decline quest",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            IconButton(onClick = { onApprove(quest) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Approve quest",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTelemetryCard(
    hasUsageAccess: Boolean,
    totalDeviceMinutes: Long,
    onOpenUsageSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSyncToChild: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Device Screen Telemetry",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (hasUsageAccess) {
                                "Active today: ${formatDurationMinutes(totalDeviceMinutes)}"
                            } else {
                                "Usage Access permission needed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh device stats"
                    )
                }
            }

            if (hasUsageAccess) {
                FilledTonalButton(
                    onClick = onSyncToChild,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Device Usage (${formatDurationMinutes(totalDeviceMinutes)}) to Profile")
                }
            } else {
                Text(
                    text = "Grant Android Usage Access in Settings so KidGuard can read real foreground app time on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenUsageSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_usage_access_button")
                ) {
                    Text("Open Usage Access Settings")
                }
            }
        }
    }
}

@Composable
private fun DeviceUsageRow(app: DeviceAppInfo) {
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
            val bitmap = remember(app.packageName) {
                app.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.School, contentDescription = null)
                    }
                }
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
                    text = app.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(formatDurationMinutes(app.usageMinutesToday)) }
            )
        }
    }
}
