package com.srinandahr.splitornosplit.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.Settlement
import com.srinandahr.splitornosplit.data.key
import com.srinandahr.splitornosplit.ui.theme.LedgerColors
import kotlin.math.abs
import kotlin.math.max

// ---- Balances ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesScreen(state: UiState, onSettleUp: () -> Unit) {
    val project = state.active ?: return
    val maxAbs = max(state.balances.maxOfOrNull { abs(it.balance) } ?: 1.0, 0.01)
    val settlements = state.settlements

    Scaffold(
        topBar = { TopAppBar(title = { Text("Balances") }) },
        floatingActionButton = {
            if (settlements.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onSettleUp,
                    icon = { Icon(Icons.Outlined.Handshake, contentDescription = null) },
                    text = { Text("Settle up") },
                    containerColor = LedgerColors.lent,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.balances.isEmpty()) {
                item {
                    Text(
                        "No balances yet. Add an expense to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Spelling out who pays whom turns a column of signed numbers into instructions.
            if (settlements.isNotEmpty()) {
                item(key = "settle-header") {
                    Text(
                        "To settle up",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(settlements, key = { "s-${it.fromId}-${it.toId}" }) { s ->
                    val mine = s.fromId == project.myMemberId || s.toId == project.myMemberId
                    Text(
                        settlementLine(s, project.myMemberId, project.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mine) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "settle-divider") { HorizontalDivider() }
            }
            items(state.balances.sortedByDescending { it.balance }, key = { it.memberId }) { b ->
                val isMe = b.memberId == project.myMemberId
                val tint = when {
                    b.balance > 0.005 -> LedgerColors.lent
                    b.balance < -0.005 -> LedgerColors.borrowed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(b.memberName, isMe)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (isMe) "${b.memberName} (you)" else b.memberName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                balanceLabel(b.balance, project.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tint,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // A proportional bar makes who is furthest out of pocket obvious at a glance.
                        val fraction by animateFloatAsState(
                            (abs(b.balance) / maxAbs).toFloat().coerceIn(0f, 1f),
                            label = "balanceBar",
                        )
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = tint,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            balanceBreakdown(b, project.currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, highlighted: Boolean) {
    val bg by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "avatarBg",
    )
    Box(
        modifier = Modifier.size(38.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Groups -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    state: UiState,
    onSelect: (Project) -> Unit,
    onAddGroup: () -> Unit,
    onRemove: (Project) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                actions = {
                    IconButton(onClick = onAddGroup) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add group")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.projects, key = { it.key }) { project ->
                val isActive = project.key == state.active?.key
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(project) }
                        .animateItem(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    // An outline marks the active group rather than a filled brand colour,
                    // which drowned the secondary text and read as a warning.
                    border = if (isActive) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${project.members.size} people · ${project.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isActive) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { pendingRemoval = project }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${project.name}")
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAddGroup, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Create or join a group")
                }
            }
        }
    }

    pendingRemoval?.let { project ->
        ConfirmDialog(
            title = "Remove ${project.name}?",
            body = "This only removes it from this phone. The group and its expenses stay on " +
                "the server, and you can rejoin with the same link.",
            confirmLabel = "Remove",
            onConfirm = { pendingRemoval = null; onRemove(project) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

// ---- Settings ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onSetPaused: (Boolean) -> Unit,
    onChangeMember: () -> Unit,
    onShare: () -> Unit,
    onAddMember: (String) -> Unit,
    onReset: () -> Unit,
) {
    val project = state.active
    var confirmReset by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.paused) "Detection paused" else "Watching for bank SMS",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.paused) MaterialTheme.colorScheme.onSurfaceVariant
                            else LedgerColors.lent,
                        )
                        Text(
                            if (state.paused) "No split prompts will appear."
                            else "You'll get a Split prompt when money leaves your account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = !state.paused, onCheckedChange = { onSetPaused(!it) })
                }
            }

            if (project != null) {
                SettingRow(
                    icon = Icons.Outlined.Person,
                    title = "You are ${state.myName ?: "not set"}",
                    subtitle = "Expenses from this phone are recorded as paid by this member",
                    onClick = onChangeMember,
                )
                HorizontalDivider()
                SettingRow(
                    icon = Icons.Outlined.Add,
                    title = "Add a member",
                    subtitle = "${project.members.size} people in ${project.name}",
                    onClick = { showAddMember = true },
                )
                HorizontalDivider()
                SettingRow(
                    icon = Icons.Outlined.Share,
                    title = "Invite to ${project.name}",
                    subtitle = "Share a QR code or link",
                    onClick = onShare,
                )
                HorizontalDivider()
                SettingRow(
                    icon = Icons.Outlined.Dns,
                    title = "Server",
                    subtitle = project.instanceUrl,
                    onClick = null,
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmReset = true }) {
                Text("Reset app", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showAddMember && project != null) {
        AddMemberDialog(
            groupName = project.name,
            onAdd = onAddMember,
            onDismiss = { showAddMember = false },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset the app?",
            body = "Deletes every group's credentials from this phone. If you have not saved " +
                "your join links elsewhere, you will not be able to get back in.",
            confirmLabel = "Reset",
            onConfirm = { confirmReset = false; onReset() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
