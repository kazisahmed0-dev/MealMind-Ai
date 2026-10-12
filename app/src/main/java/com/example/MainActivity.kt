package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExerciseReminder
import com.example.reminder.ExerciseReminderReceiver
import com.example.reminder.ReminderScheduler
import com.example.ui.components.ActiveAlarmDialog
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FoodCatalogScreen
import com.example.ui.screens.HealthToolsScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.MealMindViewModel

data class NavItem(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MealMindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleReminderIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MealMindApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ExerciseReminderReceiver.EXTRA_SHOW_ALARM_DIALOG, false) == true) {
            val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0L)
            val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Exercise Alarm"
            val type = intent.getStringExtra(ReminderScheduler.EXTRA_EXERCISE_TYPE) ?: "Morning Workout"
            viewModel.showActiveAlarm(
                ExerciseReminder(
                    id = id,
                    title = title,
                    exerciseType = type,
                    hour = 0,
                    minute = 0
                )
            )
            viewModel.navigateTo(AppDestination.EXERCISE_REMINDERS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealMindApp(viewModel: MealMindViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsState()
    val saveMessage by viewModel.saveSuccessMessage.collectAsState()
    val activeAlarm by viewModel.activeAlarmTriggered.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage) {
        saveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSaveMessage()
        }
    }

    val navItems = listOf(
        NavItem(AppDestination.DASHBOARD, "Daily Log", Icons.Default.Today, "nav_dashboard"),
        NavItem(AppDestination.SCANNER, "AI Vision", Icons.Default.CameraAlt, "nav_scanner"),
        NavItem(AppDestination.FOOD_DATABASE, "Catalog", Icons.Default.RestaurantMenu, "nav_catalog"),
        NavItem(AppDestination.EXERCISE_REMINDERS, "Alarms", Icons.Default.Alarm, "nav_reminders"),
        NavItem(AppDestination.PROFILE_TOOLS, "Tools", Icons.Default.FitnessCenter, "nav_tools")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MealMind AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppDestination.AI_ASSISTANT) },
                        modifier = Modifier.testTag("top_bar_ai_assistant")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Nutrition AI Assistant",
                            tint = if (currentDestination == AppDestination.AI_ASSISTANT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentDestination == item.destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.navigateTo(item.destination) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { target ->
                when (target) {
                    AppDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                    AppDestination.SCANNER -> ScannerScreen(
                        viewModel = viewModel
                    )
                    AppDestination.FOOD_DATABASE -> FoodCatalogScreen(
                        viewModel = viewModel
                    )
                    AppDestination.AI_ASSISTANT -> AiAssistantScreen(
                        viewModel = viewModel
                    )
                    AppDestination.PROFILE_TOOLS -> HealthToolsScreen(
                        viewModel = viewModel
                    )
                    AppDestination.EXERCISE_REMINDERS -> {
                        val reminders by viewModel.reminders.collectAsState()
                        val history by viewModel.reminderHistory.collectAsState()
                        val nextUpcoming by viewModel.nextUpcomingReminder.collectAsState()
                        RemindersScreen(
                            reminders = reminders,
                            history = history,
                            nextUpcoming = nextUpcoming,
                            onSaveReminder = { viewModel.saveReminder(it) },
                            onToggleReminder = { viewModel.toggleReminder(it) },
                            onDeleteReminder = { viewModel.deleteReminder(it) },
                            onTestReminder = { viewModel.testReminder(it) },
                            onClearHistory = { viewModel.clearReminderHistory() }
                        )
                    }
                }
            }

            // Active Alarm Modal Dialog (Pops up on alarm trigger or when opened via notification)
            if (activeAlarm != null) {
                ActiveAlarmDialog(
                    reminder = activeAlarm!!,
                    onDone = { viewModel.markReminderDone(it) },
                    onSnooze = { id, min -> viewModel.snoozeReminder(id, min) },
                    onDismiss = { viewModel.dismissReminder(it) }
                )
            }
        }
    }
}

