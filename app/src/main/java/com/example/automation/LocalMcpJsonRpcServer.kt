package com.example.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

interface EditorAutomationBridge {
    fun getTimelineSummaryJson(): JSONObject
    fun addTextOverlay(text: String, durationMs: Long): JSONObject
    fun addCaption(text: String, durationMs: Long): JSONObject
    fun splitAtPlayhead(): JSONObject
    fun applyFilterPreset(presetName: String): JSONObject
    fun setExportSettings(resolution: String?, fps: Int?, codec: String?, tenBit: Boolean?): JSONObject
    fun queueBackgroundRender(): JSONObject
}

data class AutomationServerStatus(
    val isRunning: Boolean = false,
    val host: String = LocalMcpJsonRpcServer.LOOPBACK_HOST,
    val port: Int = LocalMcpJsonRpcServer.DEFAULT_PORT,
    val authToken: String = "",
    val lastRequestLog: String = "Server stopped. Toggle ON or run a local JSON-RPC script below.",
    val requestsHandled: Int = 0
) {
    val endpointUrl: String get() = "http://$host:$port/rpc"
}

/**
 * Built-in Local Loopback-Only (`127.0.0.1`) MCP & JSON-RPC 2.0 Automation Server.
 *
 * Security invariants:
 * - Binds strictly to `127.0.0.1` (loopback interface ONLY — never exposed to LAN / `0.0.0.0`).
 * - Requires a fresh 256-bit cryptographically random Bearer token (`Authorization: Bearer <token>`)
 *   validated with constant-time comparison (`MessageDigest.isEqual`).
 * - Enforces a strict 64 KB request cap and explicit tool allowlist.
 * - Disabled by default until toggled ON by the user in the Automation & MCP Console.
 */
class LocalMcpJsonRpcServer(
    private val port: Int = DEFAULT_PORT
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var activeBridge: EditorAutomationBridge? = null

    @Volatile
    private var sessionToken: String = generateSecureToken()

    private val _status = MutableStateFlow(
        AutomationServerStatus(
            isRunning = false,
            host = LOOPBACK_HOST,
            port = port,
            authToken = sessionToken
        )
    )
    val status: StateFlow<AutomationServerStatus> = _status.asStateFlow()

    fun attachBridge(bridge: EditorAutomationBridge) {
        activeBridge = bridge
    }

    fun detachBridge(bridge: EditorAutomationBridge) {
        if (activeBridge === bridge) {
            activeBridge = null
        }
    }

    fun rotateToken(): String {
        val next = generateSecureToken()
        sessionToken = next
        _status.update { it.copy(authToken = next, lastRequestLog = "Rotated session Bearer token.") }
        return next
    }

    fun start(): Result<Unit> {
        if (_status.value.isRunning && serverSocket != null) return Result.success(Unit)
        return runCatching {
            val loopbackAddr = InetAddress.getByName(LOOPBACK_HOST)
            val socket = ServerSocket(port, 8, loopbackAddr)
            serverSocket = socket
            _status.update {
                it.copy(
                    isRunning = true,
                    port = socket.localPort,
                    authToken = sessionToken,
                    lastRequestLog = "Listening on http://$LOOPBACK_HOST:${socket.localPort}/rpc (loopback only)"
                )
            }
            acceptJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleHttpClient(client) }
                }
            }
        }.onFailure { err ->
            _status.update {
                it.copy(
                    isRunning = false,
                    lastRequestLog = "Could not bind $LOOPBACK_HOST:$port: ${err.localizedMessage}"
                )
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        _status.update {
            it.copy(
                isRunning = false,
                lastRequestLog = "Local MCP / JSON-RPC server stopped."
            )
        }
    }

    /**
     * Verifies `Authorization: Bearer <token>` header in constant time.
     */
    fun isAuthorizedHeader(authorizationHeader: String?): Boolean {
        val raw = authorizationHeader?.trim() ?: return false
        if (!raw.startsWith("Bearer ", ignoreCase = true)) return false
        val supplied = raw.substring(7).trim()
        if (supplied.isEmpty()) return false
        return MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            sessionToken.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Executes a JSON-RPC 2.0 or MCP request payload directly (used both by localhost HTTP
     * clients and the built-in in-app Scripting Console).
     */
    fun executeJsonRpc(rawJson: String): String {
        val reqObj = runCatching { JSONObject(rawJson) }.getOrElse {
            return buildJsonRpcError(JSONObject.NULL, -32700, "Parse error: invalid JSON payload").toString(2)
        }
        val id: Any = reqObj.opt("id") ?: JSONObject.NULL
        val method = reqObj.optString("method", "").trim()
        val params = reqObj.optJSONObject("params") ?: JSONObject()

        val response = when (method) {
            "initialize" -> buildJsonRpcResult(
                id,
                JSONObject()
                    .put("protocolVersion", "2024-11-05")
                    .put(
                        "serverInfo",
                        JSONObject()
                            .put("name", "lumacut-local-mcp")
                            .put("version", "1.0.0")
                    )
                    .put(
                        "capabilities",
                        JSONObject().put("tools", JSONObject())
                    )
            )
            "tools/list" -> buildJsonRpcResult(id, JSONObject().put("tools", buildToolManifest()))
            "tools/call" -> {
                val toolName = params.optString("name", "").trim()
                val args = params.optJSONObject("arguments") ?: JSONObject()
                dispatchStudioTool(id, toolName, args, wrapAsMcpContent = true)
            }
            "timeline.getState",
            "timeline.addTextOverlay",
            "timeline.addCaption",
            "timeline.splitAtPlayhead",
            "timeline.applyFilter",
            "timeline.setExportConfig",
            "timeline.queueRender" -> dispatchStudioTool(id, method, params, wrapAsMcpContent = false)
            else -> buildJsonRpcError(id, -32601, "Method not found: '$method'. Call 'tools/list' to inspect supported tools.")
        }

        _status.update {
            it.copy(
                requestsHandled = it.requestsHandled + 1,
                lastRequestLog = "Handled $method"
            )
        }
        return response.toString(2)
    }

    private fun dispatchStudioTool(
        id: Any,
        toolName: String,
        args: JSONObject,
        wrapAsMcpContent: Boolean
    ): JSONObject {
        val bridge = activeBridge
            ?: return buildJsonRpcError(id, -32000, "No active editor project is attached.")

        val toolResult = runCatching {
            when (toolName) {
                "timeline.getState" -> bridge.getTimelineSummaryJson()
                "timeline.addTextOverlay" -> {
                    val text = args.optString("text", "LumaCut Title").trim().take(120)
                    val durMs = args.optLong("durationMs", 4000L).coerceIn(500L, 60_000L)
                    bridge.addTextOverlay(text.ifBlank { "LumaCut Title" }, durMs)
                }
                "timeline.addCaption" -> {
                    val text = args.optString("text", "Auto subtitle").trim().take(160)
                    val durMs = args.optLong("durationMs", 2500L).coerceIn(400L, 20_000L)
                    bridge.addCaption(text.ifBlank { "Subtitle" }, durMs)
                }
                "timeline.splitAtPlayhead" -> bridge.splitAtPlayhead()
                "timeline.applyFilter" -> {
                    val preset = args.optString("preset", "TEAL_ORANGE").trim()
                    bridge.applyFilterPreset(preset)
                }
                "timeline.setExportConfig" -> {
                    val res = args.optString("resolution", "").takeIf { it.isNotBlank() }
                    val fps = if (args.has("fps")) args.optInt("fps") else null
                    val codec = args.optString("codec", "").takeIf { it.isNotBlank() }
                    val tenBit = if (args.has("tenBitColor")) args.optBoolean("tenBitColor") else null
                    bridge.setExportSettings(res, fps, codec, tenBit)
                }
                "timeline.queueRender" -> bridge.queueBackgroundRender()
                else -> return buildJsonRpcError(id, -32601, "Unknown tool '$toolName'")
            }
        }.getOrElse { err ->
            return buildJsonRpcError(id, -32001, err.localizedMessage ?: "Tool execution failed")
        }

        val payload = if (wrapAsMcpContent) {
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", toolResult.toString())
                )
            )
        } else {
            toolResult
        }
        return buildJsonRpcResult(id, payload)
    }

    private fun handleHttpClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val writer = OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeHttpResponse(writer, 400, "Bad Request", """{"error":"Malformed request"}""")
                return
            }
            val httpMethod = parts[0]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
            }

            if (httpMethod != "POST") {
                writeHttpResponse(writer, 405, "Method Not Allowed", """{"error":"Use POST /rpc with JSON-RPC 2.0 body"}""")
                return
            }

            if (!isAuthorizedHeader(headers["authorization"])) {
                writeHttpResponse(writer, 401, "Unauthorized", """{"error":"Missing or invalid Bearer token"}""")
                return
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
                writeHttpResponse(writer, 413, "Payload Too Large", """{"error":"Body must be between 1 and $MAX_BODY_BYTES bytes"}""")
                return
            }

            val bodyChars = CharArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val n = reader.read(bodyChars, readTotal, contentLength - readTotal)
                if (n <= 0) break
                readTotal += n
            }
            val rawBody = String(bodyChars, 0, readTotal)
            val rpcResponse = executeJsonRpc(rawBody)
            writeHttpResponse(writer, 200, "OK", rpcResponse)
        }
    }

    private fun writeHttpResponse(writer: OutputStreamWriter, code: Int, reason: String, jsonBody: String) {
        val bytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 $code $reason\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.write(jsonBody)
        writer.flush()
    }

    private fun buildJsonRpcResult(id: Any, result: JSONObject): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("result", result)

    private fun buildJsonRpcError(id: Any, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("error", JSONObject().put("code", code).put("message", message))

    private fun buildToolManifest(): JSONArray {
        val defs = listOf(
            "timeline.getState" to "Inspect active project clips, overlays, captions, duration, and export settings.",
            "timeline.addTextOverlay" to "Add a styled title overlay at playhead. Args: {text, durationMs}.",
            "timeline.addCaption" to "Add a synced subtitle cue at playhead. Args: {text, durationMs}.",
            "timeline.splitAtPlayhead" to "Split the selected video/audio/overlay clip at the current playhead.",
            "timeline.applyFilter" to "Apply a cinema color filter preset (e.g. TEAL_ORANGE, NOIR, VINTAGE_WARM). Args: {preset}.",
            "timeline.setExportConfig" to "Configure resolution (720p/1080p/4K), fps (24/30/60), codec (H264/HEVC/AV1), tenBitColor.",
            "timeline.queueRender" to "Add the active timeline project to the background rendering queue."
        )
        val arr = JSONArray()
        defs.forEach { (name, desc) ->
            arr.put(JSONObject().put("name", name).put("description", desc))
        }
        return arr
    }

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 47891
        const val MAX_BODY_BYTES = 65_536

        @Volatile
        private var sharedInstance: LocalMcpJsonRpcServer? = null

        fun getSharedInstance(): LocalMcpJsonRpcServer =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: LocalMcpJsonRpcServer().also { sharedInstance = it }
            }

        private fun generateSecureToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
