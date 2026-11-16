package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CrueltyFree
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ChildProfile
import java.util.Calendar
import kotlin.math.roundToInt

data class AvatarOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
)

val ChildAvatarOptions = listOf(
    AvatarOption("fox", "Fox", Icons.Rounded.Pets, Color(0xFFB3541E), Color(0xFFFFE4D3)),
    AvatarOption("owl", "Owl", Icons.Rounded.Face, Color(0xFF156B60), Color(0xFFC8EFE7)),
    AvatarOption("panda", "Panda", Icons.Rounded.CrueltyFree, Color(0xFF395144), Color(0xFFDCEAE0)),
    AvatarOption("rocket", "Rocket", Icons.Rounded.RocketLaunch, Color(0xFF28559A), Color(0xFFD9E6FF)),
    AvatarOption("dino", "Dino", Icons.Rounded.SmartToy, Color(0xFF5D6D1E), Color(0xFFEAF2C2)),
    AvatarOption("unicorn", "Star", Icons.Rounded.AutoAwesome, Color(0xFF8C3B6E), Color(0xFFFCDDF0)),
)

fun avatarForKey(key: String): AvatarOption {
    return ChildAvatarOptions.firstOrNull { it.key == key } ?: ChildAvatarOptions.first()
}

@Composable
fun ChildAvatarBadge(
    avatarKey: String,
    size: Dp = 44.dp,
) {
    val avatar = avatarForKey(avatarKey)
    Surface(
        color = avatar.container,
        shape = CircleShape,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = avatar.icon,
                contentDescription = "${avatar.label} avatar",
                tint = avatar.tint,
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

fun formatDurationMinutes(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0L)
    val h = safe / 60L
    val m = safe % 60L
    return when {
        h > 0L && m > 0L -> "${h}h ${m}m"
        h > 0L -> "${h}h"
        else -> "${m}m"
    }
}

fun formatMinutesOfDay(minutesFromMidnight: Long): String {
    val clamped = ((minutesFromMidnight % 1440L) + 1440L) % 1440L
    val hour24 = (clamped / 60L).toInt()
    val minute = (clamped % 60L).toInt()
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%d:%02d %s".format(hour12, minute, amPm)
}

fun isWeekendToday(): Boolean {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return day == Calendar.SATURDAY || day == Calendar.SUNDAY
}

fun isCurrentTimeInWindow(startMinutes: Long, endMinutes: Long): Boolean {
    val cal = Calendar.getInstance()
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60L + cal.get(Calendar.MINUTE)
    return if (startMinutes <= endMinutes) {
        nowMinutes in startMinutes until endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
}

@Composable
fun ScreenTimeGauge(
    usedMinutes: Long,
    totalAllowanceMinutes: Long,
    isPausedOrBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val rawProgress = if (totalAllowanceMinutes <= 0L) 1f else {
        (usedMinutes.toFloat() / totalAllowanceMinutes.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 550),
        label = "screenTimeGauge"
    )
    val remaining = (totalAllowanceMinutes - usedMinutes).coerceAtLeast(0L)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when {
        isPausedOrBlocked -> MaterialTheme.colorScheme.tertiary
        rawProgress >= 0.9f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(144.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 13.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatDurationMinutes(remaining),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "left today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditChildDialog(
    initialChild: ChildProfile? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, age: Long, avatarKey: String, dailyMinutes: Long, weekendMinutes: Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialChild?.name ?: "") }
    var age by remember { mutableIntStateOf((initialChild?.age ?: 8L).toInt()) }
    var avatarKey by remember { mutableStateOf(initialChild?.avatarKey ?: "fox") }
    var dailyMinutes by remember { mutableIntStateOf((initialChild?.dailyLimitMinutes ?: 120L).toInt()) }
    var weekendMinutes by remember { mutableIntStateOf((initialChild?.weekendLimitMinutes ?: 180L).toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialChild == null) "Add Child Profile" else "Edit ${initialChild.name}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Child's Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_name_input")
                )

                Column {
                    Text(
                        text = "Age: $age years",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = age.toFloat(),
                        onValueChange = { age = it.roundToInt().coerceIn(3, 17) },
                        valueRange = 3f..17f,
                        steps = 13,
                    )
                }

                Column {
                    Text(
                        text = "Avatar",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChildAvatarOptions.forEach { opt ->
                            FilterChip(
                                selected = avatarKey == opt.key,
                                onClick = { avatarKey = opt.key },
                                label = { Text(opt.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = opt.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Weekday Limit: ${formatDurationMinutes(dailyMinutes.toLong())}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = dailyMinutes.toFloat(),
                        onValueChange = { dailyMinutes = (it / 15f).roundToInt() * 15 },
                        valueRange = 30f..360f,
                        steps = 21
                    )
                }

                Column {
                    Text(
                        text = "Weekend Limit: ${formatDurationMinutes(weekendMinutes.toLong())}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = weekendMinutes.toFloat(),
                        onValueChange = { weekendMinutes = (it / 15f).roundToInt() * 15 },
                        valueRange = 30f..420f,
                        steps = 25
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        age.toLong(),
                        avatarKey,
                        dailyMinutes.toLong(),
                        weekendMinutes.toLong()
                    )
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_child_button")
            ) {
                Text(if (initialChild == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PinPadDialog(
    title: String,
    subtitle: String,
    errorText: String? = null,
    onDismiss: () -> Unit,
    onPinComplete: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_pad_dialog")
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < pin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .clickable {
                                            when (key) {
                                                "C" -> pin = ""
                                                "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                else -> {
                                                    if (pin.length < 4) {
                                                        val next = pin + key
                                                        pin = next
                                                        if (next.length == 4) {
                                                            onPinComplete(next)
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("pin_key_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                contentDescription = "Delete digit",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
