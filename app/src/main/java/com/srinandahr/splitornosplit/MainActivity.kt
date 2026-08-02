package com.srinandahr.splitornosplit

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.srinandahr.splitornosplit.ui.AddExpenseScreen
import com.srinandahr.splitornosplit.ui.AppViewModel
import com.srinandahr.splitornosplit.ui.BalancesScreen
import com.srinandahr.splitornosplit.ui.CreateProjectScreen
import com.srinandahr.splitornosplit.ui.ExpensesScreen
import com.srinandahr.splitornosplit.ui.GroupsScreen
import com.srinandahr.splitornosplit.ui.JoinLink
import com.srinandahr.splitornosplit.ui.JoinProjectScreen
import com.srinandahr.splitornosplit.ui.PickMemberScreen
import com.srinandahr.splitornosplit.ui.Screen
import com.srinandahr.splitornosplit.ui.SettingsScreen
import com.srinandahr.splitornosplit.ui.SetupScreen
import com.srinandahr.splitornosplit.ui.ShareDialog
import com.srinandahr.splitornosplit.ui.Tab
import com.srinandahr.splitornosplit.ui.UiState
import com.srinandahr.splitornosplit.ui.theme.SplitOrNoSplitTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val pendingLink: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingLink.value = intent?.dataString
        setContent {
            SplitOrNoSplitTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(pendingLink)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink.value = intent.dataString
    }
}

@Composable
private fun AppRoot(pendingLink: MutableState<String?>) {
    val vm: AppViewModel = viewModel()
    val state = vm.state
    val snackbars = remember { SnackbarHostState() }
    var showShare by remember { mutableStateOf(false) }

    // SMS + notification permissions. READ_SMS was requested in v1 but never declared in
    // the manifest, so it silently no-oped; the broadcast approach doesn't need it.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    // A join link may arrive from a QR scan in another app, or a shared invite.
    LaunchedEffect(pendingLink.value) {
        val raw = pendingLink.value ?: return@LaunchedEffect
        pendingLink.value = null
        JoinLink.parse(raw)?.let { (instance, id, code) ->
            vm.handleJoinLink(instance, id, code)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbars.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    // Splits can be detected or actioned from the notification shade while the app is in the
    // background, so re-read the pending list every time we come back to the foreground.
    LifecycleResumeEffect(Unit) {
        vm.refreshPending()
        onPauseOrDispose { }
    }

    // Without this the system back button finishes the activity from every screen, so backing
    // out of "Add expense" dropped the user on the launcher instead of the expense list.
    val backHandled = when (state.screen) {
        Screen.HOME -> state.tab != Tab.EXPENSES
        Screen.SETUP -> state.active != null
        else -> true
    }
    BackHandler(enabled = backHandled) {
        if (state.screen != Screen.HOME) vm.back() else vm.selectTab(Tab.EXPENSES)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                // Full-screen destinations slide up over the shell; going back fades out.
                (fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 10 })
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeShell(state, vm) { showShare = true }

                Screen.SETUP -> SetupScreen(
                    onCreate = { vm.goTo(Screen.CREATE) },
                    onJoin = { vm.goTo(Screen.JOIN) },
                    canGoBack = state.active != null,
                    onBack = vm::back,
                )

                Screen.CREATE -> CreateProjectScreen(
                    busy = state.busy,
                    onBack = vm::back,
                    onCreate = { instance, name, email, currency, members ->
                        vm.createProject(instance, name, email, currency, members)
                    },
                )

                Screen.JOIN -> JoinProjectScreen(
                    busy = state.busy,
                    onBack = vm::back,
                    onJoin = vm::joinProject,
                )

                Screen.PICK_MEMBER -> PickMemberScreen(
                    members = (state.pendingProject ?: state.active)?.members.orEmpty(),
                    onBack = vm::back,
                    onPick = vm::confirmMember,
                )

                Screen.ADD_EXPENSE -> {
                    val project = state.active
                    if (project == null) vm.back()
                    else AddExpenseScreen(
                        project = project,
                        busy = state.busy,
                        onBack = vm::back,
                        onSave = vm::addExpense,
                    )
                }
            }
        }

        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }

    if (showShare) {
        state.active?.let { ShareDialog(it) { showShare = false } }
    }

    if (state.showLegacyNotice) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Splitwise support has ended") },
            text = {
                Text(
                    "Splitwise now requires a paid subscription for API access, so this app has " +
                        "moved to I Hate Money — free, open source, and no account needed.\n\n" +
                        "Your old Splitwise expenses stay in Splitwise. Create or join a group " +
                        "here to carry on splitting.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = vm::dismissLegacyNotice) { Text("Set up a group") }
            },
        )
    }
}

private data class TabSpec(
    val tab: Tab,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val TABS = listOf(
    TabSpec(
        Tab.EXPENSES,
        "Expenses",
        Icons.AutoMirrored.Filled.ReceiptLong,
        Icons.AutoMirrored.Outlined.ReceiptLong,
    ),
    TabSpec(Tab.BALANCES, "Balances", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    TabSpec(Tab.GROUPS, "Groups", Icons.Filled.Groups, Icons.Outlined.Groups),
    TabSpec(Tab.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * The tabbed shell. NavigationBar is a sibling of the content rather than a Scaffold slot,
 * so each tab can own its own Scaffold (top bar, FAB) without fighting over insets.
 */
@Composable
private fun HomeShell(state: UiState, vm: AppViewModel, onShare: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (fadeIn(tween(180)) + slideInHorizontally(tween(240)) { offset * it / 8 })
                        .togetherWith(
                            fadeOut(tween(140)) +
                                slideOutHorizontally(tween(240)) { -offset * it / 8 },
                        )
                },
                label = "tab",
            ) { tab ->
                when (tab) {
                    Tab.EXPENSES -> ExpensesScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onShare = onShare,
                        onAddExpense = { vm.goTo(Screen.ADD_EXPENSE) },
                        onOpenBalances = { vm.selectTab(Tab.BALANCES) },
                        onAddMember = vm::addMember,
                        onSplitPending = vm::splitPending,
                        onDismissPending = vm::dismissPending,
                        onClearAllPending = vm::clearAllPending,
                    )

                    Tab.BALANCES -> BalancesScreen(state)

                    Tab.GROUPS -> GroupsScreen(
                        state = state,
                        onSelect = vm::setActive,
                        onAddGroup = { vm.goTo(Screen.SETUP) },
                        onRemove = vm::removeProject,
                    )

                    Tab.SETTINGS -> SettingsScreen(
                        state = state,
                        onSetPaused = vm::setPaused,
                        onChangeMember = vm::changeMyMember,
                        onShare = onShare,
                        onAddMember = vm::addMember,
                        onReset = vm::resetEverything,
                    )
                }
            }
        }

        NavigationBar {
            TABS.forEach { spec ->
                val selected = state.tab == spec.tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { vm.selectTab(spec.tab) },
                    icon = {
                        Icon(
                            if (selected) spec.selectedIcon else spec.icon,
                            contentDescription = spec.label,
                        )
                    },
                    label = { Text(spec.label) },
                )
            }
        }
    }
}
