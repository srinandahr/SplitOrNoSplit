package com.srinandahr.splitornosplit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.srinandahr.splitornosplit.R
import com.srinandahr.splitornosplit.data.Member
import com.srinandahr.splitornosplit.data.Project

@Composable
fun SetupScreen(onCreate: () -> Unit, onJoin: () -> Unit, canGoBack: Boolean, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            // NOT R.mipmap.ic_launcher: on API 26+ that resolves to the <adaptive-icon> XML in
            // mipmap-anydpi-v26, which painterResource cannot load (it accepts only vectors and
            // rasters). ic_launcher_foreground has no anydpi variant, so it resolves to a webp.
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text("Split or No Split", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Detect bank SMS, split in one tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))

        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Create a new group")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Join an existing group")
        }

        if (canGoBack) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Cancel") }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Groups are stored on ihatemoney.org, a free and open-source service. " +
                "No account or API key needed. You can point the app at your own server instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CreateProjectScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (instance: String, name: String, email: String, currency: String, members: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Project.DEFAULT_CURRENCY) }
    var instance by remember { mutableStateOf(Project.DEFAULT_INSTANCE) }
    var showAdvanced by remember { mutableStateOf(false) }
    var newMember by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<String>() }

    fun addMember() {
        val trimmed = newMember.trim()
        if (trimmed.isNotEmpty() && members.none { it.equals(trimmed, ignoreCase = true) }) {
            members.add(trimmed)
        }
        newMember = ""
    }

    FormScaffold("Create a group", onBack) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Group name") },
            placeholder = { Text("Flatmates") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Your email") },
            supportingText = { Text("Used only to recover the group code if you lose it.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = currency,
            onValueChange = { if (it.length <= 3) currency = it.uppercase() },
            label = { Text("Currency (ISO code)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Members", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add everyone splitting expenses, including yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newMember,
                onValueChange = { newMember = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = { addMember() }, enabled = newMember.isNotBlank()) { Text("Add") }
        }
        members.forEach { member ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(member, Modifier.weight(1f))
                    IconButton(onClick = { members.remove(member) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove $member")
                    }
                }
            }
        }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide server settings" else "Use my own server")
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = instance,
                onValueChange = { instance = it },
                label = { Text("I Hate Money server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onCreate(instance, name.trim(), email.trim(), currency, members.toList()) },
            enabled = !busy && name.isNotBlank() && email.isNotBlank() &&
                currency.length == 3 && members.size >= 2,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Create group")
        }
        if (members.size < 2) {
            Text(
                "Add at least two members.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun JoinProjectScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onJoin: (instance: String, projectId: String, code: String) -> Unit,
) {
    var projectId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var instance by remember { mutableStateOf(Project.DEFAULT_INSTANCE) }
    var pastedLink by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            val parsed = JoinLink.parse(contents)
            if (parsed != null) {
                val (parsedInstance, parsedId, parsedCode) = parsed
                instance = parsedInstance
                projectId = parsedId
                code = parsedCode
                linkError = null
            } else {
                linkError = "That QR code isn't a Split or No Split invite."
            }
        }
    }

    FormScaffold("Join a group", onBack) {
        Button(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the group's QR code")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false),
                )
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Scan QR code")
        }

        Text(
            "or paste the invite link, or type the details:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = pastedLink,
            onValueChange = { pasted ->
                pastedLink = pasted
                val parsed = JoinLink.parse(pasted)
                if (parsed != null) {
                    val (parsedInstance, parsedId, parsedCode) = parsed
                    instance = parsedInstance
                    projectId = parsedId
                    code = parsedCode
                    linkError = null
                } else {
                    linkError = if (pasted.isBlank()) null
                    else "That doesn't look like an invite link."
                }
            },
            label = { Text("Paste invite link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        linkError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = projectId,
            onValueChange = { projectId = it },
            label = { Text("Project ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Private code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = instance,
            onValueChange = { instance = it },
            label = { Text("Server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onJoin(instance, projectId, code) },
            enabled = !busy && projectId.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Join group")
        }
    }
}

@Composable
fun PickMemberScreen(members: List<Member>, onBack: () -> Unit, onPick: (Int) -> Unit) {
    FormScaffold("Which one are you?", onBack) {
        Text(
            "Expenses this phone splits will be recorded as paid by the member you pick.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (members.isEmpty()) {
            Text(
                "This group has no members yet. Add them on the server first.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        members.forEach { member ->
            OutlinedButton(
                onClick = { onPick(member.id) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(member.name)
            }
        }
    }
}

@Composable
private fun FormScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}
