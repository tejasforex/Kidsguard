package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppRule
import com.example.ui.KidGuardUiState
import com.example.ui.components.formatDurationMinutes
import com.example.ui.theme.BlockedRed
import com.example.ui.theme.BlockedRedContainer
import com.example.ui.theme.LimitAmber
import com.example.ui.theme.LimitAmberContainer
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenContainer
import kotlin.math.roundToInt

private data class MergedAppEntry(
    val packageName: String,
    val appName: String,
    val category: String,
    val usageMinutesToday: Long,
    val icon: Drawable?,
    val rule: AppRule?,
) {
    val effectiveStatus: String
        get() = rule?.status ?: AppRule.STATUS_ALLOWED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    uiState: KidGuardUiState,
    onSaveAppRule: (
        childId: String,
        existingRuleId: String?,
        packageName: String,
        appName: String,
        category: String,
        status: String,
        dailyLimitMinutes: Long,
        usedMinutesToday: Long,
    ) -> Unit,
    onDeleteAppRule: (childId: String, ruleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedChild = uiState.selectedChild
    var query by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("all") }
    var editingEntry by remember { mutableStateOf<MergedAppEntry?>(null) }
    var showCustomRuleDialog by remember { mutableStateOf(false) }

    val mergedApps = remember(uiState.deviceSnapshot.installedApps, uiState.appRules) {
        val rulesByPkg = uiState.appRules.associateBy { it.packageName }
        val devicePackages = HashSet<String>()
        val combined = mutableListOf<MergedAppEntry>()

        for (app in uiState.deviceSnapshot.installedApps) {
            devicePackages.add(app.packageName)
            val rule = rulesByPkg[app.packageName]
            combined.add(
                MergedAppEntry(
                    packageName = app.packageName,
                    appName = app.appName,
                    category = app.category,
                    usageMinutesToday = maxOf(app.usageMinutesToday, rule?.usedMinutesToday ?: 0L),
                    icon = app.icon,
                    rule = rule,
                )
            )
        }
        for (rule in uiState.appRules) {
            if (!devicePackages.contains(rule.packageName)) {
                combined.add(
                    MergedAppEntry(
                        packageName = rule.packageName,
                        appName = rule.appName,
                        category = rule.category,
                        usageMinutesToday = rule.usedMinutesToday,
                        icon = null,
                        rule = rule,
                    )
                )
            }
        }
        combined
    }

    val filteredApps = remember(mergedApps, query, filterStatus) {
        mergedApps.filter { entry ->
            val matchesQuery = query.isBlank() ||
                entry.appName.contains(query, ignoreCase = true) ||
                entry.category.contains(query, ignoreCase = true)
            val matchesFilter = filterStatus == "all" || entry.effectiveStatus == filterStatus
            matchesQuery && matchesFilter
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Guardrails",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = if (selectedChild != null) {
                                "Rules for ${selectedChild.name}"
                            } else {
                                "Add a child profile first to configure app rules"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedChild != null) {
                        FilledTonalButton(
                            onClick = { showCustomRuleDialog = true },
                            modifier = Modifier.testTag("add_custom_app_rule_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Custom Rule")
                        }
                    }
                }
            }

            if (selectedChild != null) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search installed or managed apps") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_input")
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "all" to "All (${mergedApps.size})",
                            AppRule.STATUS_ALLOWED to "Allowed",
                            AppRule.STATUS_LIMITED to "Limited",
                            AppRule.STATUS_BLOCKED to "Blocked",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = filterStatus == key,
                                onClick = { filterStatus = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching apps found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Try clearing filters or add a custom app rule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { entry ->
                        AppRuleRow(
                            entry = entry,
                            onClick = { editingEntry = entry }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    val target = editingEntry
    if (target != null && selectedChild != null) {
        AppRuleEditorSheet(
            entry = target,
            onDismiss = { editingEntry = null },
            onSave = { status, limitMinutes ->
                onSaveAppRule(
                    selectedChild.id,
                    target.rule?.id,
                    target.packageName,
                    target.appName,
                    target.category,
                    status,
                    limitMinutes,
                    target.usageMinutesToday,
                )
                editingEntry = null
            },
            onResetDefault = if (target.rule != null) {
                {
                    onDeleteAppRule(selectedChild.id, target.rule.id)
                    editingEntry = null
                }
            } else null
        )
    }

    if (showCustomRuleDialog && selectedChild != null) {
        AddCustomAppRuleDialog(
            onDismiss = { showCustomRuleDialog = false },
            onCreate = { appName, category, status, limit ->
                val pkg = "custom." + appName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")
                onSaveAppRule(
                    selectedChild.id,
                    null,
                    pkg,
                    appName.trim(),
                    category,
                    status,
                    limit,
                    0L,
                )
                showCustomRuleDialog = false
            }
        )
    }
}

@Composable
private fun AppRuleRow(
    entry: MergedAppEntry,
    onClick: () -> Unit,
) {
    val status = entry.effectiveStatus
    val (badgeLabel, badgeTextColor, badgeBg) = when (status) {
        AppRule.STATUS_BLOCKED -> Triple("Blocked", BlockedRed, BlockedRedContainer)
        AppRule.STATUS_LIMITED -> Triple(
            "${entry.rule?.dailyLimitMinutes ?: 30L}m / day",
            LimitAmber,
            LimitAmberContainer
        )
        else -> Triple("Allowed", SafeGreen, SafeGreenContainer)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("app_rule_row_${entry.appName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            val bitmap = remember(entry.packageName) {
                entry.icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "${entry.appName} icon",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val usageText = if (entry.usageMinutesToday > 0L) {
                    "${entry.category} • Used ${formatDurationMinutes(entry.usageMinutesToday)}"
                } else {
                    entry.category
                }
                Text(
                    text = usageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeBg
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRuleEditorSheet(
    entry: MergedAppEntry,
    onDismiss: () -> Unit,
    onSave: (status: String, limitMinutes: Long) -> Unit,
    onResetDefault: (() -> Unit)?,
) {
    var status by remember { mutableStateOf(entry.effectiveStatus) }
    var limitMinutes by remember {
        mutableIntStateOf((entry.rule?.dailyLimitMinutes ?: 30L).coerceAtLeast(15L).toInt())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = entry.appName,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${entry.category} • ${entry.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = status == AppRule.STATUS_ALLOWED,
                    onClick = { status = AppRule.STATUS_ALLOWED },
                    label = { Text("Allowed") },
                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = status == AppRule.STATUS_LIMITED,
                    onClick = { status = AppRule.STATUS_LIMITED },
                    label = { Text("Limit") },
                    leadingIcon = { Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = status == AppRule.STATUS_BLOCKED,
                    onClick = { status = AppRule.STATUS_BLOCKED },
                    label = { Text("Blocked") },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (status == AppRule.STATUS_LIMITED) {
                Column {
                    Text(
                        text = "Daily App Limit: ${formatDurationMinutes(limitMinutes.toLong())}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = limitMinutes.toFloat(),
                        onValueChange = { limitMinutes = ((it / 15f).roundToInt() * 15).coerceIn(15, 240) },
                        valueRange = 15f..240f,
                        steps = 14
                    )
                }
            }

            Button(
                onClick = {
                    val savedLimit = if (status == AppRule.STATUS_LIMITED) limitMinutes.toLong() else 0L
                    onSave(status, savedLimit)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_app_rule_button")
            ) {
                Text("Save Rule")
            }

            if (onResetDefault != null) {
                TextButton(
                    onClick = onResetDefault,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Default")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddCustomAppRuleDialog(
    onDismiss: () -> Unit,
    onCreate: (appName: String, category: String, status: String, limit: Long) -> Unit,
) {
    var appName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Games") }
    var status by remember { mutableStateOf(AppRule.STATUS_LIMITED) }
    var limit by remember { mutableIntStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom App Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { if (it.length <= 60) appName = it },
                    label = { Text("App Name (e.g. YouTube Kids)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Games", "Video", "Social", "Learning").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == AppRule.STATUS_LIMITED,
                        onClick = { status = AppRule.STATUS_LIMITED },
                        label = { Text("Limited") }
                    )
                    FilterChip(
                        selected = status == AppRule.STATUS_BLOCKED,
                        onClick = { status = AppRule.STATUS_BLOCKED },
                        label = { Text("Blocked") }
                    )
                }
                if (status == AppRule.STATUS_LIMITED) {
                    Text("Limit: ${limit}m / day", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = limit.toFloat(),
                        onValueChange = { limit = ((it / 15f).roundToInt() * 15).coerceIn(15, 180) },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(appName, category, status, if (status == AppRule.STATUS_LIMITED) limit.toLong() else 0L) },
                enabled = appName.trim().isNotEmpty()
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
