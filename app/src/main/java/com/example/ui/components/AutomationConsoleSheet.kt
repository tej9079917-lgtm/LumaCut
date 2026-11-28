package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.LocalMcpJsonRpcServer
import com.example.ui.theme.ActiveRail
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TrackOverlayEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationConsoleBottomSheet(
    server: LocalMcpJsonRpcServer,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val status by server.status.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var scriptJson by remember {
        mutableStateOf(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "tools/list",
              "params": {}
            }
            """.trimIndent()
        )
    }
    var outputJson by remember { mutableStateOf("Ready. Tap a quick MCP tool or Run JSON-RPC Script.") }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    val quickTemplates = remember {
        listOf(
            "MCP tools/list" to """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""",
            "Timeline State" to """{"jsonrpc":"2.0","id":2,"method":"timeline.getState","params":{}}""",
            "+ Add Title" to """{"jsonrpc":"2.0","id":3,"method":"timeline.addTextOverlay","params":{"text":"LumaCut Pro","durationMs":4000}}""",
            "+ Add Caption" to """{"jsonrpc":"2.0","id":4,"method":"timeline.addCaption","params":{"text":"Automated studio cue","durationMs":2500}}""",
            "4K/60 AV1" to """{"jsonrpc":"2.0","id":5,"method":"timeline.setExportConfig","params":{"resolution":"4K","fps":60,"codec":"AV1","tenBitColor":true}}""",
            "Split Clip" to """{"jsonrpc":"2.0","id":6,"method":"timeline.splitAtPlayhead","params":{}}"""
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InspectorCharcoal,
        contentColor = ChalkWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .testTag("automation_mcp_console_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("MCP & JSON-RPC Automation Studio", style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    Text(
                        text = "Loopback-Only (127.0.0.1) • Per-Session Bearer Token Auth",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrackOverlayEmerald
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Automation Console", tint = SilverMuted)
                }
            }

            // Server Control Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSlate)
                    .border(
                        width = 1.dp,
                        color = if (status.isRunning) TrackOverlayEmerald.copy(alpha = 0.6f) else BorderHairline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (status.isRunning) "Local MCP Server Active" else "Local MCP Server Stopped",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (status.isRunning) TrackOverlayEmerald else ChalkWhite
                        )
                        Text(
                            text = "${status.endpointUrl} • ${status.requestsHandled} calls",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                    Switch(
                        checked = status.isRunning,
                        onCheckedChange = { enable ->
                            if (enable) server.start() else server.stop()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = TrackOverlayEmerald
                        ),
                        modifier = Modifier.testTag("mcp_server_toggle")
                    )
                }

                val maskedToken = if (status.authToken.length > 10) {
                    "${status.authToken.take(6)}••••••${status.authToken.takeLast(4)}"
                } else "••••"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Token: $maskedToken",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = CinemaAmber,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val curlCmd =
                                """curl -X POST ${status.endpointUrl} -H "Authorization: Bearer ${status.authToken}" -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'"""
                            clipboard.setText(AnnotatedString(curlCmd))
                            copiedBanner = "Copied local curl command with session Bearer token"
                        },
                        modifier = Modifier.height(34.dp).testTag("mcp_copy_curl_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CinemaAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy CLI", color = ChalkWhite, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = {
                            server.rotateToken()
                            copiedBanner = "Generated fresh 192-bit session Bearer token"
                        },
                        modifier = Modifier.size(34.dp).testTag("mcp_rotate_token_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rotate token", tint = SilverMuted, modifier = Modifier.size(17.dp))
                    }
                }

                Text(status.lastRequestLog, style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                copiedBanner?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.labelSmall, color = TrackOverlayEmerald)
                }
            }

            // Quick MCP Tool Presets
            Text("Quick MCP / JSON-RPC Actions", style = MaterialTheme.typography.labelLarge, color = SilverMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTemplates.forEach { (label, payload) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveRail)
                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                            .clickable {
                                scriptJson = payload
                                outputJson = server.executeJsonRpc(payload)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
                    }
                }
            }

            // Interactive JSON-RPC Script Editor
            OutlinedTextField(
                value = scriptJson,
                onValueChange = { scriptJson = it },
                label = { Text("JSON-RPC 2.0 / MCP Payload") },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaAmber,
                    unfocusedBorderColor = BorderHairline,
                    focusedTextColor = ChalkWhite,
                    unfocusedTextColor = ChalkWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_script_editor")
            )

            Button(
                onClick = { outputJson = server.executeJsonRpc(scriptJson) },
                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = OnAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("mcp_run_script_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Execute Local Automation Command", style = MaterialTheme.typography.titleSmall)
            }

            // Response Console Output
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
                    .testTag("mcp_console_output")
            ) {
                Text(
                    text = outputJson,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TrackOverlayEmerald
                )
            }
        }
    }
}
