package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChildProfile
import com.example.data.model.RewardQuest
import com.example.ui.KidGuardUiState
import com.example.ui.components.formatDurationMinutes
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleRewardsScreen(
    uiState: KidGuardUiState,
    onSaveScheduleAndSafety: (ChildProfile) -> Unit,
    onCreateQuest: (childId: String, title: String, rewardMinutes: Long) -> Unit,
    onApproveQuest: (ChildProfile, RewardQuest) -> Unit,
    onDeleteQuest: (childId: String, questId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedChild = uiState.selectedChild
    var showAddQuestDialog by remember { mutableStateOf(false) }

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
                    text = "Schedules & Quests",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (selectedChild != null) {
                        "Healthy routines and rewards for ${selectedChild.name}"
                    } else {
                        "Create a child profile first to manage schedules"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedChild != null) {
                item(key = "schedule_${selectedChild.id}") {
                    ScheduleAndSafetyEditorCard(
                        child = selectedChild,
                        onSave = onSaveScheduleAndSafety
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
                            Image(
                                painter = painterResource(id = R.drawable.img_kid_quest_castle),
                                contentDescription = "Reward quests illustration",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Positive Bonus Quests",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Kids earn extra minutes by completing offline habits.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { showAddQuestDialog = true },
                                    modifier = Modifier.testTag("add_quest_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quest")
                                }
                            }
                        }
                    }
                }

                if (uiState.quests.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No quests yet. Tap + Quest to add chores like reading or homework for bonus screen time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.quests, key = { it.id }) { quest ->
                        QuestManagementRow(
                            quest = quest,
                            onApprove = { onApproveQuest(selectedChild, quest) },
                            onDelete = { onDeleteQuest(selectedChild.id, quest.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddQuestDialog && selectedChild != null) {
        AddQuestDialog(
            onDismiss = { showAddQuestDialog = false },
            onCreate = { title, minutes ->
                onCreateQuest(selectedChild.id, title, minutes)
                showAddQuestDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleAndSafetyEditorCard(
    child: ChildProfile,
    onSave: (ChildProfile) -> Unit,
) {
    var dailyLimit by remember(child.id, child.dailyLimitMinutes) {
        mutableIntStateOf(child.dailyLimitMinutes.toInt())
    }
    var weekendLimit by remember(child.id, child.weekendLimitMinutes) {
        mutableIntStateOf(child.weekendLimitMinutes.toInt())
    }
    var bedtimeEnabled by remember(child.id, child.bedtimeEnabled) {
        mutableStateOf(child.bedtimeEnabled)
    }
    var bedtimeStart by remember(child.id, child.bedtimeStartMinutes) {
        mutableIntStateOf(child.bedtimeStartMinutes.toInt())
    }
    var bedtimeEnd by remember(child.id, child.bedtimeEndMinutes) {
        mutableIntStateOf(child.bedtimeEndMinutes.toInt())
    }
    var studyEnabled by remember(child.id, child.studyFocusEnabled) {
        mutableStateOf(child.studyFocusEnabled)
    }
    var contentRating by remember(child.id, child.contentRating) {
        mutableStateOf(child.contentRating)
    }
    var safeSearch by remember(child.id, child.safeSearchEnabled) {
        mutableStateOf(child.safeSearchEnabled)
    }
    var blockPurchases by remember(child.id, child.blockPurchases) {
        mutableStateOf(child.blockPurchases)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Daily Screen Allowance", style = MaterialTheme.typography.titleMedium)

                Text(
                    text = "Weekdays: ${formatDurationMinutes(dailyLimit.toLong())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = dailyLimit.toFloat(),
                    onValueChange = { dailyLimit = ((it / 15f).roundToInt() * 15).coerceIn(15, 480) },
                    valueRange = 15f..480f,
                    steps = 30
                )

                Text(
                    text = "Weekends: ${formatDurationMinutes(weekendLimit.toLong())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = weekendLimit.toFloat(),
                    onValueChange = { weekendLimit = ((it / 15f).roundToInt() * 15).coerceIn(15, 480) },
                    valueRange = 15f..480f,
                    steps = 30
                )
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_bedtime_moon),
                    contentDescription = "Bedtime schedule banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Bedtime Downtime", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${formatMinutesOfDay(bedtimeStart.toLong())} – ${formatMinutesOfDay(bedtimeEnd.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = bedtimeEnabled,
                        onCheckedChange = { bedtimeEnabled = it },
                        modifier = Modifier.testTag("bedtime_switch")
                    )
                }

                if (bedtimeEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { bedtimeStart = (bedtimeStart + 30).let { if (it > 23 * 60 + 30) 19 * 60 else it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start: ${formatMinutesOfDay(bedtimeStart.toLong())}")
                        }
                        OutlinedButton(
                            onClick = { bedtimeEnd = (bedtimeEnd + 30).let { if (it > 9 * 60 + 30) 6 * 60 else it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wake: ${formatMinutesOfDay(bedtimeEnd.toLong())}")
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("School Focus Window", style = MaterialTheme.typography.titleMedium)
                            Text("8:30 AM – 3:00 PM weekdays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = studyEnabled,
                        onCheckedChange = { studyEnabled = it }
                    )
                }
            }
        }

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
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Content & Store Guardrails", style = MaterialTheme.typography.titleMedium)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "kids_4_7" to "Ages 4–7",
                        "preteen_8_12" to "Ages 8–12",
                        "teen_13_plus" to "Ages 13+",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = contentRating == key,
                            onClick = { contentRating = key },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SafeSearch & Web Filtering", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = safeSearch, onCheckedChange = { safeSearch = it })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Require Approval for Purchases", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = blockPurchases, onCheckedChange = { blockPurchases = it })
                }

                Button(
                    onClick = {
                        onSave(
                            child.copy(
                                dailyLimitMinutes = dailyLimit.toLong(),
                                weekendLimitMinutes = weekendLimit.toLong(),
                                bedtimeEnabled = bedtimeEnabled,
                                bedtimeStartMinutes = bedtimeStart.toLong(),
                                bedtimeEndMinutes = bedtimeEnd.toLong(),
                                studyFocusEnabled = studyEnabled,
                                contentRating = contentRating,
                                safeSearchEnabled = safeSearch,
                                blockPurchases = blockPurchases,
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_schedule_button")
                ) {
                    Text("Save Schedule & Safety Rules")
                }
            }
        }
    }
}

@Composable
private fun QuestManagementRow(
    quest: RewardQuest,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
) {
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (quest.status == RewardQuest.STATUS_APPROVED) SafeGreenContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (quest.status == RewardQuest.STATUS_APPROVED) Icons.Rounded.CheckCircle else Icons.Rounded.Stars,
                        contentDescription = null,
                        tint = if (quest.status == RewardQuest.STATUS_APPROVED) SafeGreen else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(quest.title, style = MaterialTheme.typography.titleSmall)
                val statusLabel = when (quest.status) {
                    RewardQuest.STATUS_PENDING_APPROVAL -> "Waiting for your approval"
                    RewardQuest.STATUS_APPROVED -> "Completed & rewarded"
                    else -> "Available in Kid Mode"
                }
                Text(
                    text = "+${quest.rewardMinutes}m • $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (quest.status != RewardQuest.STATUS_APPROVED) {
                TextButton(onClick = onApprove) {
                    Text("Grant")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete quest",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddQuestDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, rewardMinutes: Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var rewardMinutes by remember { mutableIntStateOf(20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Bonus Quest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = { Text("Habit or Chore (e.g. Read 20 mins)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quest_title_input")
                )
                Text(
                    text = "Bonus Reward: +${rewardMinutes}m",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = rewardMinutes.toFloat(),
                    onValueChange = { rewardMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 90) },
                    valueRange = 5f..90f,
                    steps = 16
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.trim(), rewardMinutes.toLong()) },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("create_quest_confirm_button")
            ) {
                Text("Add Quest")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
