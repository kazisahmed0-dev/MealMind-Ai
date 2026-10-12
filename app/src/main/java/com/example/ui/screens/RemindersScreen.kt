package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ExerciseReminder
import com.example.data.model.ReminderHistoryItem
import com.example.reminder.ReminderTimeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminders: List<ExerciseReminder>,
    history: List<ReminderHistoryItem>,
    nextUpcoming: Pair<ExerciseReminder, String>?,
    onSaveReminder: (ExerciseReminder) -> Unit,
    onToggleReminder: (ExerciseReminder) -> Unit,
    onDeleteReminder: (ExerciseReminder) -> Unit,
    onTestReminder: (ExerciseReminder) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab state: 0 = Reminders, 1 = History
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog states
    var showEditDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<ExerciseReminder?>(null) }
    var reminderToDelete by remember { mutableStateOf<ExerciseReminder?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    // Filter for Reminders: "All", "Active", "Disabled"
    var reminderFilter by remember { mutableStateOf("All") }

    // Notification permission check for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        scope.launch {
            if (granted) {
                snackbarHostState.showSnackbar("Notification permission granted! Exercise alerts will fire on time.")
            } else {
                snackbarHostState.showSnackbar("Notifications denied. Reminders cannot trigger alerts without permission.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        reminderToEdit = null
                        showEditDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Reminder") },
                    text = { Text("New Reminder", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("fab_add_reminder"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("reminders_screen")
        ) {
            // Screen Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exercise Reminders",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Daily movement, cardio & workout schedule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                reminderToEdit = null
                                showEditDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_quick_add_reminder")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Permission Warning Banner if missing
                    if (!hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notification_permission_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notifications are disabled",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Enable notifications so your phone alerts you when it's time to workout.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_enable_notifications")
                                ) {
                                    Text("Enable", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Next Upcoming Alarm Spotlight Card
                    if (nextUpcoming != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("next_upcoming_spotlight")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (upcomingRem, relativeTime) = nextUpcoming
                                val emoji = ReminderTimeUtils.getExerciseEmoji(upcomingRem.exerciseType)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Next: ${upcomingRem.title}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$relativeTime • ${upcomingRem.exerciseType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tab Navigation: Reminders vs History
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    "Alarms (${reminders.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_reminders_list")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "History Log (${history.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_reminders_history")
                        )
                    }
                }
            }

            // Tab 0: Reminders List
            if (selectedTab == 0) {
                // Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf("All", "Active", "Disabled").forEach { filterOption ->
                        FilterChip(
                            selected = reminderFilter == filterOption,
                            onClick = { reminderFilter = filterOption },
                            label = { Text(filterOption) },
                            modifier = Modifier.testTag("filter_chip_$filterOption")
                        )
                    }
                }

                val filteredReminders = remember(reminders, reminderFilter) {
                    when (reminderFilter) {
                        "Active" -> reminders.filter { it.isEnabled }
                        "Disabled" -> reminders.filter { !it.isEnabled }
                        else -> reminders
                    }
                }

                if (filteredReminders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (reminders.isEmpty()) "No Exercise Alarms Yet" else "No matching reminders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Set up your morning workout, daily walk, or evening stretch alarms with sound & repeat options.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    reminderToEdit = null
                                    showEditDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("btn_empty_create_reminder")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Alarm")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredReminders, key = { it.id }) { reminder ->
                            ReminderItemCard(
                                reminder = reminder,
                                onToggle = { onToggleReminder(reminder) },
                                onEdit = {
                                    reminderToEdit = reminder
                                    showEditDialog = true
                                },
                                onDelete = { reminderToDelete = reminder },
                                onTest = {
                                    onTestReminder(reminder)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Triggering test alarm for ${reminder.title}...")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Tab 1: Reminder History Log
            if (selectedTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Activity & Trigger History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (history.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearHistoryConfirm = true },
                                modifier = Modifier.testTag("btn_clear_history")
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Alarm History Recorded",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Completed, snoozed, and dismissed exercise alarms will be logged here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(history, key = { it.id }) { item ->
                                HistoryItemCard(item = item)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showEditDialog) {
        AddEditReminderDialog(
            initialReminder = reminderToEdit,
            onDismiss = {
                showEditDialog = false
                reminderToEdit = null
            },
            onSave = { savedReminder ->
                onSaveReminder(savedReminder)
                showEditDialog = false
                reminderToEdit = null
                scope.launch {
                    val verb = if (savedReminder.id == 0L) "Created" else "Updated"
                    snackbarHostState.showSnackbar("$verb exercise alarm: ${savedReminder.title}")
                }
            }
        )
    }

    // Confirm Delete Dialog
    if (reminderToDelete != null) {
        val rem = reminderToDelete!!
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Delete Reminder?") },
            text = {
                Text("Are you sure you want to delete '${rem.title}'? This will cancel all upcoming scheduled alarms for this workout.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReminder(rem)
                        reminderToDelete = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Deleted reminder: ${rem.title}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_reminder")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { reminderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Clear History Dialog
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear Alarm History?") },
            text = { Text("This will erase all past alarm trigger, completion, and snooze logs. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryConfirm = false
                        scope.launch { snackbarHostState.showSnackbar("Alarm history cleared.") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_clear_history")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReminderItemCard(
    reminder: ExerciseReminder,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    val emoji = ReminderTimeUtils.getExerciseEmoji(reminder.exerciseType)
    val timeFormatted = ReminderTimeUtils.formatTime12Hour(reminder.hour, reminder.minute)
    val repeatFormatted = ReminderTimeUtils.formatDaysOfWeek(
        repeatType = reminder.repeatType,
        daysOfWeek = reminder.daysOfWeek,
        specificDate = reminder.specificDate
    )
    val relativeNext = ReminderTimeUtils.formatRelativeNextTrigger(reminder)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (reminder.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Emoji, Name, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = if (reminder.isEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = reminder.exerciseType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Accessible Status & Switch
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = reminder.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("reminder_switch_${reminder.id}")
                    )
                    Text(
                        text = if (reminder.isEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (reminder.isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle: Big Time & Repeat Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (reminder.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = repeatFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Next trigger badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (reminder.isEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = relativeNext,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reminder.isEnabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sound Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sound: ${reminder.notificationSound}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions: Edit, Delete, Test Alarm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_test_reminder_${reminder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Alarm", fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("btn_edit_reminder_${reminder.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Reminder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("btn_delete_reminder_${reminder.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Reminder",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: ReminderHistoryItem) {
    val statusColor = when (item.status) {
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        "SNOOZED" -> MaterialTheme.colorScheme.tertiary
        "DISMISSED" -> MaterialTheme.colorScheme.outline
        "TRIGGERED" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val emoji = ReminderTimeUtils.getExerciseEmoji(item.exerciseType)
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${item.exerciseType} • ${item.scheduledDate} at ${item.scheduledTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = item.status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun AddEditReminderDialog(
    initialReminder: ExerciseReminder?,
    onDismiss: () -> Unit,
    onSave: (ExerciseReminder) -> Unit
) {
    var title by remember { mutableStateOf(initialReminder?.title ?: "Morning Exercise") }
    var exerciseType by remember { mutableStateOf(initialReminder?.exerciseType ?: "Morning Workout") }
    var minute by remember { mutableIntStateOf(initialReminder?.minute ?: 0) }
    var isAm by remember { mutableStateOf(initialReminder?.let { it.hour < 12 } ?: true) }
    var hour12 by remember {
        mutableIntStateOf(
            initialReminder?.let {
                when {
                    it.hour == 0 -> 12
                    it.hour > 12 -> it.hour - 12
                    else -> it.hour
                }
            } ?: 7
        )
    }

    var repeatType by remember { mutableStateOf(initialReminder?.repeatType ?: "EVERY_DAY") }
    var selectedDays by remember {
        mutableStateOf(
            initialReminder?.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
                ?: setOf(1, 2, 3, 4, 5, 6, 7)
        )
    }
    var specificDate by remember {
        mutableStateOf(
            initialReminder?.specificDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }
    var notificationSound by remember { mutableStateOf(initialReminder?.notificationSound ?: "Default Chime") }
    var isEnabled by remember { mutableStateOf(initialReminder?.isEnabled ?: true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exerciseTypeOptions = listOf(
        "Morning Workout",
        "Jogging / Running",
        "Brisk Walk",
        "Yoga & Stretching",
        "Strength Training",
        "Cardio / HIIT",
        "Cycling",
        "Swimming",
        "Evening Walk",
        "Custom"
    )

    val soundOptions = listOf("Default Chime", "Gentle Bell", "Energetic Tone", "Silent")

    val dayNames = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialReminder == null) "Create Exercise Alarm" else "Edit Exercise Alarm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("dialog_add_edit_reminder"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Reminder Name") },
                    placeholder = { Text("e.g. Morning Exercise") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reminder_name")
                )

                // Quick Presets Chips
                Column {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Morning Workout", "Evening Walk", "Core & Stretch", "Cardio Blast", "Post-Meal Stroll").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    title = preset
                                    if (preset.contains("Walk")) exerciseType = "Brisk Walk"
                                    if (preset.contains("Stretch")) exerciseType = "Yoga & Stretching"
                                    if (preset.contains("Cardio")) exerciseType = "Cardio / HIIT"
                                }
                            ) {
                                Text(
                                    text = preset,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Exercise Type Selector
                Column {
                    Text(
                        text = "Exercise Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        exerciseTypeOptions.forEach { type ->
                            FilterChip(
                                selected = exerciseType == type,
                                onClick = { exerciseType = type },
                                label = {
                                    Text("${ReminderTimeUtils.getExerciseEmoji(type)} $type")
                                },
                                modifier = Modifier.testTag("chip_exercise_type_$type")
                            )
                        }
                    }
                }

                // Time Selector (Hour, Minute, AM/PM)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Scheduled Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Hour selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Hour",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            hour12 = if (hour12 <= 1) 12 else hour12 - 1
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", hour12),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            hour12 = if (hour12 >= 12) 1 else hour12 + 1
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                text = ":",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // Minute selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Minute",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            minute = if (minute <= 0) 55 else minute - 5
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%02d", minute),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            minute = if (minute >= 55) 0 else minute + 5
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // AM / PM Toggle
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Row(modifier = Modifier.padding(2.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clickable { isAm = true }
                                    ) {
                                        Text(
                                            text = "AM",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAm) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (!isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clickable { isAm = false }
                                    ) {
                                        Text(
                                            text = "PM",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isAm) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Repeat Options
                Column {
                    Text(
                        text = "Repeat Option",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "EVERY_DAY" to "Every Day",
                            "SPECIFIC_DAYS" to "Specific Days",
                            "ONCE" to "One-Time"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = repeatType == type,
                                onClick = { repeatType = type },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_repeat_$type")
                            )
                        }
                    }

                    // Specific Days of Week picker
                    AnimatedVisibility(visible = repeatType == "SPECIFIC_DAYS") {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Select active days:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                dayNames.forEach { (dayNum, dayLabel) ->
                                    val isSelected = selectedDays.contains(dayNum)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable {
                                                selectedDays = if (isSelected) {
                                                    selectedDays - dayNum
                                                } else {
                                                    selectedDays + dayNum
                                                }
                                            },
                                        tonalElevation = if (isSelected) 2.dp else 0.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = dayLabel.take(1),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { selectedDays = setOf(1, 2, 3, 4, 5) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Weekdays", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { selectedDays = setOf(6, 7) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Weekends", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { selectedDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("All Days", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // One-Time Date Picker
                    AnimatedVisibility(visible = repeatType == "ONCE") {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Reminder Date (YYYY-MM-DD):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = specificDate,
                                onValueChange = { specificDate = it },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_one_time_date"),
                                placeholder = { Text("2026-09-15") }
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val cal = Calendar.getInstance()
                                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val todayStr = dateFormat.format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                                val tomorrowStr = dateFormat.format(cal.time)

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { specificDate = todayStr }
                                ) {
                                    Text("Today", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { specificDate = tomorrowStr }
                                ) {
                                    Text("Tomorrow", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Notification Sound
                Column {
                    Text(
                        text = "Notification Sound",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        soundOptions.forEach { sound ->
                            FilterChip(
                                selected = notificationSound == sound,
                                onClick = { notificationSound = sound },
                                label = { Text(sound) },
                                modifier = Modifier.testTag("chip_sound_$sound")
                            )
                        }
                    }
                }

                // Enable/Disable Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Enable Alarm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isEnabled) "Active and scheduled" else "Paused (will not trigger)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("switch_dialog_enabled")
                    )
                }

                // Error Banner
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Please enter a reminder name."
                        return@Button
                    }

                    if (repeatType == "SPECIFIC_DAYS" && selectedDays.isEmpty()) {
                        errorMessage = "Please select at least one day of the week."
                        return@Button
                    }

                    // Convert 12-hour to 24-hour
                    val calculated24Hour = when {
                        isAm && hour12 == 12 -> 0
                        !isAm && hour12 < 12 -> hour12 + 12
                        else -> hour12
                    }

                    val daysString = if (repeatType == "SPECIFIC_DAYS") {
                        selectedDays.sorted().joinToString(",")
                    } else {
                        "1,2,3,4,5,6,7"
                    }

                    val reminderToSave = ExerciseReminder(
                        id = initialReminder?.id ?: 0L,
                        title = title.trim(),
                        exerciseType = exerciseType,
                        hour = calculated24Hour,
                        minute = minute,
                        repeatType = repeatType,
                        daysOfWeek = daysString,
                        specificDate = if (repeatType == "ONCE") specificDate else null,
                        isEnabled = isEnabled,
                        notificationSound = notificationSound,
                        status = if (isEnabled) "ACTIVE" else "DISABLED"
                    )

                    onSave(reminderToSave)
                },
                modifier = Modifier.testTag("btn_dialog_save_reminder")
            ) {
                Text("Save Alarm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
