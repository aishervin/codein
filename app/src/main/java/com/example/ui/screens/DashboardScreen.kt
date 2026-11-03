package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var processes by remember { mutableStateOf("Loading...") }
    var openPorts by remember { mutableStateOf("Loading...") }
    var tunnelStatus by remember { mutableStateOf("Loading...") }
    var mcpStatus by remember { mutableStateOf("Loading...") }

    fun refreshDashboard() {
        scope.launch {
            val psRes = viewModel.termuxManager.runShellCommand("ps aux | head -n 15")
            processes = psRes.stdout

            val netRes = viewModel.termuxManager.runShellCommand("netstat -tuln 2>/dev/null || ss -tuln 2>/dev/null || echo 'No netstat'")
            openPorts = netRes.stdout

            val cfRes = viewModel.termuxManager.runShellCommand("ps aux | grep cloudflared | grep -v grep || echo 'No active cloudflared tunnel'")
            tunnelStatus = cfRes.stdout

            val mcpRes = viewModel.termuxManager.runShellCommand("cat ~/.config/shen/mcp.json 2>/dev/null || echo 'No MCP servers configured'")
            mcpStatus = mcpRes.stdout
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFa) "⚡ داشبورد سرویس‌ها و پورت‌ها" else "⚡ Services & Tunnels Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = { refreshDashboard() },
                modifier = Modifier.testTag("refresh_dashboard_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Open Ports Card
        DashboardCard(
            title = if (isFa) "🌐 پورت‌های باز (Open Ports)" else "🌐 Open Ports & Listening Sockets",
            content = openPorts
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cloudflare Tunnels Card
        DashboardCard(
            title = if (isFa) "☁️ تانل‌های فعال کلودفلر (Cloudflare Tunnels)" else "☁️ Active Cloudflare Tunnels",
            content = tunnelStatus
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Active Processes Card
        DashboardCard(
            title = if (isFa) "🔄 پروسه‌های فعال (Active Processes)" else "🔄 Active Processes (ps aux)",
            content = processes
        )

        Spacer(modifier = Modifier.height(12.dp))

        // MCP Servers Card
        DashboardCard(
            title = if (isFa) "🧩 سرورهای MCP (Model Context Protocol)" else "🧩 MCP Servers Config",
            content = mcpStatus
        )
    }
}

@Composable
fun DashboardCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
