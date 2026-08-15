package com.srinandahr.splitornosplit.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.Settlement
import com.srinandahr.splitornosplit.data.currencySymbol
import com.srinandahr.splitornosplit.data.involving
import com.srinandahr.splitornosplit.ui.theme.LedgerColors

/**
 * Records a payment between two members.
 *
 * Doubles as the editor for an existing settlement, since a reimbursement is exactly a
 * from / to / amount triple — putting it through the expense form instead would show a
 * "split between" list that can only ever hold one person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    project: Project,
    busy: Boolean,
    suggestions: List<Settlement>,
    editing: Bill?,
    prefill: Settlement?,
    onBack: () -> Unit,
    onRecord: (fromId: Int, toId: Int, amount: String) -> Unit,
    onDelete: (Bill) -> Unit,
) {
    // Editing wins over a suggestion; both are fixed for the life of this screen, so key the
    // initial state on the bill id rather than recomputing on every recomposition.
    var fromId by remember(editing?.id) {
        mutableStateOf(editing?.payerId ?: prefill?.fromId ?: project.myMemberId)
    }
    var toId by remember(editing?.id) {
        mutableStateOf(
            editing?.reimbursedMemberId
                ?: prefill?.toId
                ?: project.members.firstOrNull { it.id != fromId }?.id,
        )
    }
    var amount by remember(editing?.id) {
        mutableStateOf(
            editing?.amount?.let { trimTrailingZeros(it) }
                ?: prefill?.amount?.let { trimTrailingZeros(it) }
                ?: "",
        )
    }
    var fromMenuOpen by remember { mutableStateOf(false) }
    var toMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val amountValid = amount.toDoubleOrNull()?.let { it > 0 } == true
    val distinct = fromId != null && toId != null && fromId != toId
    val canSave = !busy && amountValid && distinct

    // Only offer suggestions the user is actually part of; a settlement between two flatmates
    // is not theirs to record from this phone.
    val mine = remember(suggestions, project.myMemberId) {
        suggestions.involving(project.myMemberId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing != null) "Edit payment" else "Settle up") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete payment",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (editing == null && mine.isNotEmpty()) {
                Text("Suggested", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap one to fill in the form below. You can change the amount if you are " +
                        "only paying part of it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                mine.forEach { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        project = project,
                        onClick = {
                            fromId = suggestion.fromId
                            toId = suggestion.toId
                            amount = trimTrailingZeros(suggestion.amount)
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (editing == null && mine.isEmpty()) {
                Text(
                    "Everyone is settled up. You can still record a payment below if you want " +
                        "one on the record.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Who paid", style = MaterialTheme.typography.titleMedium)
            MemberPicker(
                project = project,
                selectedId = fromId,
                expanded = fromMenuOpen,
                onExpandedChange = { fromMenuOpen = it },
                onSelect = { picked ->
                    fromId = picked
                    // Keep the two ends distinct rather than rejecting the choice later.
                    if (toId == picked) {
                        toId = project.members.firstOrNull { it.id != picked }?.id
                    }
                },
            )

            Text("Who they paid", style = MaterialTheme.typography.titleMedium)
            MemberPicker(
                project = project,
                selectedId = toId,
                expanded = toMenuOpen,
                onExpandedChange = { toMenuOpen = it },
                onSelect = { picked ->
                    toId = picked
                    if (fromId == picked) {
                        fromId = project.members.firstOrNull { it.id != picked }?.id
                    }
                },
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = input
                },
                label = { Text("Amount") },
                prefix = { Text(currencySymbol(project.currency)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amount.isNotEmpty() && !amountValid,
                modifier = Modifier.fillMaxWidth(),
            )

            if (distinct && amountValid) {
                Text(
                    settlementSentence(project, fromId!!, toId!!, amount.toDouble()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onRecord(fromId!!, toId!!, amount) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (editing != null) "Save payment" else "Record payment")
            }
        }
    }

    if (confirmDelete && editing != null) {
        ConfirmDialog(
            title = "Delete this payment?",
            body = "The settlement will be removed and everyone's balance will go back to " +
                "what it was before it was recorded.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; onDelete(editing) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Settlement,
    project: Project,
    onClick: () -> Unit,
) {
    val iAmPaying = suggestion.fromId == project.myMemberId
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (iAmPaying) "You pay ${suggestion.toName}"
                    else "${suggestion.fromName} pays you",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "clears the balance between you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                formatMoney(suggestion.amount, project.currency),
                style = MaterialTheme.typography.titleMedium,
                color = if (iAmPaying) LedgerColors.borrowed else LedgerColors.lent,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MemberPicker(
    project: Project,
    selectedId: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val name = project.members.firstOrNull { it.id == selectedId }?.name ?: "Select"
    Box {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (selectedId == project.myMemberId) "$name (you)" else name)
        }
        DropdownMenu(expanded, onDismissRequest = { onExpandedChange(false) }) {
            project.members.forEach { member ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (member.id == project.myMemberId) "${member.name} (you)"
                            else member.name,
                        )
                    },
                    onClick = {
                        onSelect(member.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

/** "You will owe Srinanda ₹25.00 less after this." — states the effect in plain terms. */
private fun settlementSentence(
    project: Project,
    fromId: Int,
    toId: Int,
    amount: Double,
): String {
    val from = if (fromId == project.myMemberId) "You"
    else project.members.firstOrNull { it.id == fromId }?.name ?: "They"
    val to = if (toId == project.myMemberId) "you"
    else project.members.firstOrNull { it.id == toId }?.name ?: "them"
    val verb = if (fromId == project.myMemberId) "hand" else "hands"
    return "$from $verb ${formatMoney(amount, project.currency)} to $to. " +
        "This is recorded as a payment, not a shared expense."
}

/** 25.0 renders as "25", 25.5 as "25.5" — the amount field expects a bare decimal. */
private fun trimTrailingZeros(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
