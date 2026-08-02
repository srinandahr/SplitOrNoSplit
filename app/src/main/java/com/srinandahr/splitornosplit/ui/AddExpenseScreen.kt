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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.currencySymbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    project: Project,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (amount: String, description: String, payerId: Int, owerIds: List<Int>) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payerId by remember { mutableStateOf(project.myMemberId ?: project.members.firstOrNull()?.id) }
    var payerMenuOpen by remember { mutableStateOf(false) }
    // Everyone is included by default — the common case, and what the SMS flow always does.
    val owerIds = remember { mutableStateListOf<Int>().apply { addAll(project.members.map { it.id }) } }

    val payerName = project.members.firstOrNull { it.id == payerId }?.name ?: "Select"
    val amountValid = amount.toDoubleOrNull()?.let { it > 0 } == true
    val canSave = !busy && amountValid && description.isNotBlank() &&
        payerId != null && owerIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What for?") },
                placeholder = { Text("Groceries") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { input ->
                    // Digits and a single decimal point only; the API rejects anything else.
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = input
                },
                label = { Text("Amount") },
                prefix = { Text(currencySymbol(project.currency)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amount.isNotEmpty() && !amountValid,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Paid by", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(
                    onClick = { payerMenuOpen = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (payerId == project.myMemberId) "$payerName (you)" else payerName)
                }
                DropdownMenu(payerMenuOpen, onDismissRequest = { payerMenuOpen = false }) {
                    project.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name) },
                            onClick = {
                                payerId = member.id
                                payerMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text("Split between", style = MaterialTheme.typography.titleMedium)
            project.members.forEach { member ->
                val checked = member.id in owerIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) owerIds.remove(member.id) else owerIds.add(member.id)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            if (checked) owerIds.remove(member.id) else owerIds.add(member.id)
                        },
                    )
                    Text(
                        if (member.id == project.myMemberId) "${member.name} (you)" else member.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (owerIds.isNotEmpty() && amountValid) {
                val each = amount.toDouble() / owerIds.size
                Text(
                    "${formatMoney(each, project.currency)} each",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onSave(amount, description.trim(), payerId!!, owerIds.toList()) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Save expense")
            }
        }
    }
}
