package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonOrangeSoft
import com.example.ui.theme.TextMuted

private data class ComposioService(
    val toolkit: String,
    val title: String,
    val description: String,
    val icon: Int? = null
)

private val composioServices = listOf(
    ComposioService("github", "GitHub", "Repositories, issues and pull requests", R.drawable.ic_social_github),
    ComposioService("cloudflare", "Cloudflare", "Zones, DNS and Workers", R.drawable.ic_brand_cloudflare),
    ComposioService("gmail", "Gmail", "Email and labels", R.drawable.ic_brand_gmail),
    ComposioService("slack", "Slack", "Messages and workspaces", R.drawable.ic_brand_slack),
    ComposioService("googledrive", "Google Drive", "Files and documents", R.drawable.ic_brand_google),
    ComposioService("googlecalendar", "Google Calendar", "Events and calendars", R.drawable.ic_brand_google),
    ComposioService("notion", "Notion", "Pages and databases"),
    ComposioService("linear", "Linear", "Issues and projects"),
    ComposioService("outlook", "Microsoft Outlook", "Email and calendar")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeinIntegrationsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    bridgeReady: Boolean,
    agentReady: Boolean,
    agentBusy: Boolean,
    statuses: Map<String, String>,
    onRequestConnectionLink: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val normalizedQuery = query.trim().lowercase()
    val filteredServices = if (normalizedQuery.isBlank()) composioServices else {
        composioServices.filter {
            it.title.lowercase().contains(normalizedQuery) || it.toolkit.contains(normalizedQuery)
        }
    }
    val customService = if (normalizedQuery.isNotBlank() && filteredServices.isEmpty()) {
        val label = query.trim().take(60)
        val toolkit = label.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(48)
        toolkit.takeIf(String::isNotBlank)?.let {
            ComposioService(it, label, "Search and connect this Composio service")
        }
    } else null
    val shownServices = filteredServices + listOfNotNull(customService)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = NeonOrange.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = NeonOrange, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connected services", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Manage Composio connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Tap a service to ask the active AI model for a Composio sign-in link. It will appear in chat and open in your external browser.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search services") },
                placeholder = { Text("GitHub, Gmail, or another service") }
            )
            if (!bridgeReady) {
                Text("Connect the Termux bridge before using Composio tools.", color = NeonOrangeSoft, fontSize = 11.sp)
            } else if (agentBusy) {
                Text("Finish the current chat action before requesting another integration.", color = NeonOrangeSoft, fontSize = 11.sp)
            } else if (!agentReady) {
                Text("Select an active Termux-backed AI model to create connection links in chat.", color = NeonOrangeSoft, fontSize = 11.sp)
            }
            if (shownServices.isEmpty()) {
                Text("No matching services. Search by name to request another Composio service.", color = TextMuted, fontSize = 12.sp)
            }
            shownServices.forEach { service ->
                IntegrationServiceCard(
                    service = service,
                    status = statuses[service.toolkit] ?: "unknown",
                    connectEnabled = bridgeReady && agentReady && !agentBusy,
                    onConnect = { onRequestConnectionLink(service.toolkit, service.title) }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
            Text("More services can be entered in Search. Composio receives permission only through its own OAuth page.", color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
        }
    }
}

@Composable
private fun IntegrationServiceCard(
    service: ComposioService,
    status: String,
    connectEnabled: Boolean,
    onConnect: () -> Unit
) {
    val (statusLabel, statusColor) = when (status) {
        "active" -> "Active" to Color(0xFF4DDB8A)
        "connecting" -> "Connecting" to NeonOrangeSoft
        "checking" -> "Checking" to Color(0xFF73B9FF)
        "unknown" -> "Not verified" to TextMuted
        else -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = connectEnabled, onClick = onConnect)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(
                    color = if (service.toolkit == "github") Color(0xFF24292F) else Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (service.icon == null) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = Color(0xFF5B6472), modifier = Modifier.padding(10.dp))
                    } else {
                        Image(painterResource(service.icon), contentDescription = service.title, modifier = Modifier.padding(9.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(service.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(service.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Tap to create link in chat",
                    color = if (connectEnabled) NeonOrangeSoft else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
