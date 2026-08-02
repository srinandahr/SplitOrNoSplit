package com.srinandahr.splitornosplit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.PendingSplit
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.ui.theme.LedgerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenBalances: () -> Unit,
    onAddMember: (String) -> Unit,
    onSplitPending: (PendingSplit) -> Unit,
    onDismissPending: (PendingSplit) -> Unit,
    onClearAllPending: () -> Unit,
) {
    val project = state.active ?: return
    var showAddMember by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val listState = rememberLazyListState()

    // Collapse the FAB to an icon once the user starts scrolling, so it stops covering rows.
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80 }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(project.name, maxLines = 1)
                        Text(
                            "${project.members.size} people",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                },
                actions = {
                    HeaderIcon(Icons.Outlined.Refresh, "Refresh", onRefresh)
                    HeaderIcon(Icons.Outlined.Share, "Share group", onShare)
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add expense") },
                containerColor = LedgerColors.lent,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "summary") {
                SummaryHeader(state, project)
            }

            item(key = "chips") {
                ActionChipRow(
                    onOpenBalances = onOpenBalances,
                    onShare = onShare,
                    onAddMember = { showAddMember = true },
                )
            }

            // Detected debits the user has not decided on. Shown in-app so a swiped-away
            // notification is still recoverable for 24 hours.
            if (state.pending.isNotEmpty()) {
                item(key = "pending-header") {
                    PendingHeader(onClearAll = { confirmClearAll = true })
                }
                items(state.pending, key = { "pending-${it.id}" }) { split ->
                    PendingRow(
                        split = split,
                        currency = project.currency,
                        onSplit = { onSplitPending(split) },
                        onSkip = { onDismissPending(split) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "busy") {
                AnimatedVisibility(
                    visible = state.busy,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (state.bills.isEmpty() && !state.busy) {
                item(key = "empty") { EmptyExpenses() }
            }

            groupByMonth(state.bills).forEach { (month, bills) ->
                item(key = "month-$month") { MonthHeader(month) }
                items(bills, key = { it.id }) { bill ->
                    ExpenseRow(
                        bill = bill,
                        project = project,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showAddMember) {
        AddMemberDialog(
            groupName = project.name,
            onAdd = onAddMember,
            onDismiss = { showAddMember = false },
        )
    }

    if (confirmClearAll) {
        val count = state.pending.size
        ConfirmDialog(
            title = "Clear ${if (count == 1) "1 pending split" else "all $count pending splits"}?",
            body = "These detected expenses will be discarded without being added to " +
                "${project.name}. This cannot be undone.",
            confirmLabel = "Clear all",
            onConfirm = { confirmClearAll = false; onClearAllPending() },
            onDismiss = { confirmClearAll = false },
        )
    }
}

@Composable
private fun PendingHeader(onClearAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Waiting to be split",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClearAll) { Text("Clear all") }
    }
}

@Composable
private fun PendingRow(
    split: PendingSplit,
    currency: String,
    onSplit: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${formatMoney(split.amount.toDoubleOrNull() ?: 0.0, currency)} to ${split.payee}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        relativeTime(split.detectedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSplit) { Text("Split") }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, Modifier.size(20.dp))
        }
    }
}

/** "You owe Srinanda ₹2,894.70" — the headline figure, mirroring Splitwise's summary line. */
@Composable
private fun SummaryHeader(state: UiState, project: Project) {
    val balance = state.myBalance
    val currency = project.currency

    // With exactly two people the balance is unambiguous, so name the other person the way
    // Splitwise does. Beyond that a net figure is the only honest summary.
    val otherName = project.members
        .filter { it.id != project.myMemberId }
        .takeIf { project.members.size == 2 }
        ?.firstOrNull()?.name

    val (text, color) = when {
        // Never claim "settled up" before the figures land — that is a false statement to
        // show someone who actually owes money.
        state.balances.isEmpty() && state.busy ->
            "Loading balances…" to MaterialTheme.colorScheme.onSurfaceVariant
        balance > 0.005 -> {
            val who = otherName?.let { "$it owes you" } ?: "You are owed"
            "$who ${formatMoney(balance, currency)}" to LedgerColors.lent
        }
        balance < -0.005 -> {
            val who = otherName?.let { "You owe $it" } ?: "You owe"
            "$who ${formatMoney(-balance, currency)}" to LedgerColors.borrowed
        }
        else -> "You're all settled up" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alpha by animateFloatAsState(if (state.busy) 0.55f else 1f, label = "summaryAlpha")

    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = color.copy(alpha = alpha),
        )
        if (project.myMemberId == null) {
            Text(
                "Pick which member you are in Settings to see your share.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionChipRow(
    onOpenBalances: () -> Unit,
    onShare: () -> Unit,
    onAddMember: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onOpenBalances,
            label = { Text("Balances") },
            leadingIcon = {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(18.dp))
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary,
                labelColor = MaterialTheme.colorScheme.onPrimary,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            border = null,
        )
        AssistChip(
            onClick = onShare,
            label = { Text("Invite") },
            leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = onAddMember,
            label = { Text("Add member") },
            leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp)) },
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun MonthHeader(month: String) {
    Text(
        month,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ExpenseRow(bill: Bill, project: Project, modifier: Modifier = Modifier) {
    val (monthAbbrev, day) = dateBlock(bill.date)
    val payerName = project.members.firstOrNull { it.id == bill.payerId }?.name
    val share = shareSummary(bill, project.myMemberId)
    val shareColor = when (share.direction) {
        ShareDirection.LENT -> LedgerColors.lent
        ShareDirection.BORROWED -> LedgerColors.borrowed
        ShareDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                monthAbbrev,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                day,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                bill.what.ifBlank { "Expense" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                paidByLabel(bill, payerName, project.myMemberId, bill.currency ?: project.currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                share.label,
                style = MaterialTheme.typography.labelSmall,
                color = shareColor,
            )
            if (share.direction != ShareDirection.NEUTRAL) {
                Text(
                    formatMoney(share.amount, bill.currency ?: project.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = shareColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyExpenses() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No expenses yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Split a bank SMS, or add one yourself with the button below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
