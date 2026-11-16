package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.device.DeviceUsageRepository
import com.example.data.repository.ParentalRepository
import com.example.ui.KidGuardViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.components.PinPadDialog
import com.example.ui.screens.AppRulesScreen
import com.example.ui.screens.FamilySettingsScreen
import com.example.ui.screens.KidModeScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ScheduleRewardsScreen
import com.example.ui.theme.KidGuardTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_OVERVIEW = "overview"
private const val ROUTE_APPS = "apps"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_FAMILY = "family"

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TopDestinations = listOf(
    TopDestination(ROUTE_OVERVIEW, R.string.nav_overview, Icons.Rounded.Home, Icons.Outlined.Home),
    TopDestination(ROUTE_APPS, R.string.nav_apps, Icons.Rounded.Apps, Icons.Outlined.Apps),
    TopDestination(ROUTE_SCHEDULE, R.string.nav_schedule, Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    TopDestination(ROUTE_FAMILY, R.string.nav_family, Icons.Rounded.FamilyRestroom, Icons.Outlined.FamilyRestroom),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidGuardTheme {
                KidGuardRoot()
            }
        }
    }
}

@Composable
fun KidGuardRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var checkingAutoSignIn by remember { mutableStateOf(currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                checkingAutoSignIn = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    checkingAutoSignIn = false
                },
                onUnauthenticated = {
                    checkingAutoSignIn = false
                },
                scope = scope,
            )
        }
    }

    val user = currentUser
    when {
        checkingAutoSignIn -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                CircularProgressIndicator()
            }
        }
        user == null -> {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
        else -> {
            val uid = user.uid
            val displayName = user.displayName ?: "Parent"
            val email = user.email
            androidx.compose.runtime.key(uid) {
                val viewModel: KidGuardViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) as Application
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            KidGuardViewModel(
                                repository = ParentalRepository(db),
                                deviceUsageRepository = DeviceUsageRepository(app),
                                currentUserId = uid,
                                displayName = displayName,
                            )
                        }
                    }
                )
                KidGuardAuthenticatedHost(
                    viewModel = viewModel,
                    userEmail = email,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = {
                                currentUser = null
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun KidGuardAuthenticatedHost(
    viewModel: KidGuardViewModel,
    userEmail: String?,
    onSignOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingKidModeChildId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val settings = uiState.familySettings
    val kidModeChild = uiState.kidModeChild

    if (settings?.kidModeActive == true && kidModeChild != null) {
        KidModeScreen(
            uiState = uiState,
            child = kidModeChild,
            hasParentPin = settings.hasParentPin,
            onRequestQuestApproval = viewModel::requestQuestApproval,
            onLaunchApp = viewModel::launchApp,
            onVerifyAndExitKidMode = viewModel::exitKidMode,
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val enterKidModeWithPinCheck: (String) -> Unit = { childId ->
        if (settings?.hasParentPin == true) {
            viewModel.enterKidMode(childId)
        } else {
            pendingKidModeChildId = childId
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TopDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TopDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_OVERVIEW,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_OVERVIEW) {
                        OverviewScreen(
                            uiState = uiState,
                            onSelectChild = viewModel::selectChild,
                            onAddChild = viewModel::addChild,
                            onTogglePause = viewModel::togglePause,
                            onGrantBonus = viewModel::grantQuickBonus,
                            onResetDailyUsage = viewModel::resetDailyUsage,
                            onSyncDeviceUsage = viewModel::syncDeviceUsageToChild,
                            onRefreshTelemetry = viewModel::refreshDeviceTelemetry,
                            onApproveQuest = viewModel::approveQuest,
                            onDeclineQuest = viewModel::declineQuest,
                            onEnterKidMode = { child -> enterKidModeWithPinCheck(child.id) },
                        )
                    }
                    composable(ROUTE_APPS) {
                        AppRulesScreen(
                            uiState = uiState,
                            onSaveAppRule = viewModel::saveAppRule,
                            onDeleteAppRule = viewModel::removeAppRule,
                        )
                    }
                    composable(ROUTE_SCHEDULE) {
                        ScheduleRewardsScreen(
                            uiState = uiState,
                            onSaveScheduleAndSafety = viewModel::saveScheduleAndSafety,
                            onCreateQuest = viewModel::createQuest,
                            onApproveQuest = viewModel::approveQuest,
                            onDeleteQuest = viewModel::deleteQuest,
                        )
                    }
                    composable(ROUTE_FAMILY) {
                        FamilySettingsScreen(
                            uiState = uiState,
                            userEmail = userEmail,
                            onAddChild = viewModel::addChild,
                            onUpdateChild = viewModel::updateChildInfo,
                            onDeleteChild = viewModel::deleteChild,
                            onSetParentPin = viewModel::setParentPin,
                            onEnterKidMode = { child -> enterKidModeWithPinCheck(child.id) },
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }

    val childForPin = pendingKidModeChildId
    if (childForPin != null) {
        PinPadDialog(
            title = "Create Parent PIN First",
            subtitle = "Set a 4-digit Parent PIN before entering Kid Mode so only you can unlock the parent dashboard.",
            onDismiss = { pendingKidModeChildId = null },
            onPinComplete = { pin ->
                viewModel.setParentPin(pin)
                viewModel.enterKidMode(childForPin)
                pendingKidModeChildId = null
            }
        )
    }
}
