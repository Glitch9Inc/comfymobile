package sh.hnet.comfychair.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.hnet.comfychair.BrowserAuthActivity
import sh.hnet.comfychair.CertificateIssue
import sh.hnet.comfychair.ComfyUIClient
import sh.hnet.comfychair.MainContainerActivity
import sh.hnet.comfychair.R
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.AuthCredentials
import sh.hnet.comfychair.model.AuthType
import sh.hnet.comfychair.model.Server
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.storage.CredentialStorage
import sh.hnet.comfychair.storage.ServerStorage
import sh.hnet.comfychair.ui.components.ConnectionSplitButton
import sh.hnet.comfychair.ui.components.ServerDialog
import sh.hnet.comfychair.ui.components.ServerDropdown
import sh.hnet.comfychair.util.ServerUrlUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background

/**
 * Connection states for the login screen
 */
enum class ConnectionState {
    IDLE,
    CONNECTING,
    FAILED,
    CONNECTED
}

/**
 * Login screen composable - handles connection to ComfyUI server
 */
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Server and credential storage
    val serverStorage = remember { ServerStorage(context.applicationContext) }
    val credentialStorage = remember { CredentialStorage(context.applicationContext) }

    // Check offline mode
    var isOfflineMode by remember { mutableStateOf(AppSettings.isOfflineMode(context)) }

    // State
    var servers by remember { mutableStateOf(serverStorage.getServers()) }
    var selectedServer by remember {
        mutableStateOf(
            serverStorage.getSelectedServerId()?.let { id -> servers.find { it.id == id } }
                ?: servers.firstOrNull()
        )
    }
    var connectionState by remember { mutableStateOf(ConnectionState.IDLE) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var comfyUIClient by remember { mutableStateOf<ComfyUIClient?>(null) }
    var hasAutoConnected by remember { mutableStateOf(false) }
    // Running connection attempt (cancelled when the user taps the button while connecting)
    var connectJob by remember { mutableStateOf<Job?>(null) }

    // Dialog state
    var showServerDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<Server?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showOfflinePrompt by remember { mutableStateOf(false) }
    var offlinePromptServer by remember { mutableStateOf<Server?>(null) }

    // Browser auth state:
    // - pendingBrowserAuthServer: the server whose cookies are being captured
    // - pendingBrowserAuthUrl: non-null triggers BrowserAuthActivity launch (cleared after launch)
    // - retryAfterAuthServer: non-null triggers a retry connect after successful auth
    var pendingBrowserAuthServer by remember { mutableStateOf<Server?>(null) }
    var pendingBrowserAuthUrl by remember { mutableStateOf<String?>(null) }
    var retryAfterAuthServer by remember { mutableStateOf<Server?>(null) }

    // Launcher for BrowserAuthActivity.
    // Declared before attemptConnection so that the lambda captures only state vars
    // (all declared above), not the local function. attemptConnection sets state to
    // trigger the launch; LaunchedEffect below calls the launcher.
    val browserAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val server = pendingBrowserAuthServer ?: return@rememberLauncherForActivityResult
        pendingBrowserAuthServer = null

        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = result.data
                ?.getStringExtra(BrowserAuthActivity.RESULT_COOKIES)
                .orEmpty()
            if (cookies.isNotEmpty()) {
                credentialStorage.saveCredentials(server.id, AuthCredentials.Cookie(cookies))
                retryAfterAuthServer = server  // Triggers retry via LaunchedEffect below
            } else {
                // User tapped Done before completing sign-in
                Toast.makeText(
                    context,
                    R.string.error_browser_auth_cancelled,
                    Toast.LENGTH_SHORT
                ).show()
                connectionState = ConnectionState.IDLE
            }
        } else {
            // User closed the WebView without authenticating
            connectionState = ConnectionState.IDLE
        }
    }

    // String resources
    val warningSelfSigned = stringResource(R.string.warning_self_signed_cert)
    val warningUnknownCa = stringResource(R.string.warning_unknown_ca)

    // Connection function.
    // isRetryAfterAuth: true when called after a successful WebView auth — prevents a
    // second WebView launch if the connection still fails (e.g. wrong cookies).
    // When browser auth is needed, sets pendingBrowserAuth* state instead of calling
    // the launcher directly (avoids a forward-reference to browserAuthLauncher).
    fun attemptConnection(server: Server, isRetryAfterAuth: Boolean = false) {
        connectionState = ConnectionState.CONNECTING
        warningMessage = null

        connectJob = scope.launch {
            // Load credentials for the server
            val credentials = credentialStorage.getCredentials(server.id, server.authType)

            // For BROWSER auth with no saved cookies, skip the connection test and go
            // straight to the WebView so the user can sign in before we try the server.
            if (server.authType == AuthType.BROWSER
                && credentials is AuthCredentials.None
                && !isRetryAfterAuth
            ) {
                pendingBrowserAuthServer = server
                pendingBrowserAuthUrl =
                    ServerUrlUtils.buildServerUrl("https", server.hostname, server.port)
                connectionState = ConnectionState.IDLE
                return@launch
            }

            val client = ComfyUIClient(
                context.applicationContext,
                server.hostname,
                server.port,
                credentials
            )
            comfyUIClient = client

            // Test connection (cancellable: a late result after cancel is ignored)
            val result = suspendCancellableCoroutine<Triple<Boolean, String?, CertificateIssue>> { continuation ->
                client.testConnection { success, errorMessage, certIssue, _ ->
                    if (continuation.isActive) continuation.resume(Triple(success, errorMessage, certIssue))
                }
            }

            val (success, errorMessage, certIssue) = result

            if (success) {
                connectionState = ConnectionState.CONNECTED

                // Handle certificate warnings
                val navigateDelay = when (certIssue) {
                    CertificateIssue.SELF_SIGNED -> {
                        warningMessage = warningSelfSigned
                        1000L
                    }
                    CertificateIssue.UNKNOWN_CA -> {
                        warningMessage = warningUnknownCa
                        1000L
                    }
                    CertificateIssue.NONE -> 500L
                }

                delay(navigateDelay)

                // Establish connection via ConnectionManager
                val detectedProtocol = client.getWorkingProtocol() ?: "http"

                // Save selected server
                serverStorage.setSelectedServerId(server.id)

                ConnectionManager.connect(
                    context = context.applicationContext,
                    serverId = server.id,
                    hostname = server.hostname,
                    port = server.port,
                    protocol = detectedProtocol,
                    credentials = credentials
                )

                // Navigate to main activity
                val intent = Intent(context, MainContainerActivity::class.java)
                context.startActivity(intent)
            } else if (server.authType == AuthType.BROWSER && !isRetryAfterAuth) {
                // Connection failed with browser auth and WebView hasn't been tried yet.
                // The server may be reachable but behind an SSO that blocked the request.
                val protocol = client.getWorkingProtocol() ?: "https"
                pendingBrowserAuthServer = server
                pendingBrowserAuthUrl =
                    ServerUrlUtils.buildServerUrl(protocol, server.hostname, server.port)
                connectionState = ConnectionState.IDLE
            } else {
                connectionState = ConnectionState.FAILED

                // Check if offline cache exists - offer offline mode
                if (ConnectionManager.hasOfflineCache(context, server.id)) {
                    offlinePromptServer = server
                    showOfflinePrompt = true
                } else {
                    // No cache available, just show error Toast
                    errorMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }

                delay(2000)
                connectionState = ConnectionState.IDLE
            }
        }
    }

    // Launch BrowserAuthActivity when attemptConnection signals it via state
    LaunchedEffect(pendingBrowserAuthUrl) {
        val url = pendingBrowserAuthUrl ?: return@LaunchedEffect
        pendingBrowserAuthUrl = null  // Clear to prevent re-trigger on recomposition
        browserAuthLauncher.launch(BrowserAuthActivity.createIntent(context, url))
    }

    // Retry connection after successful browser auth (triggered by launcher result handler)
    LaunchedEffect(retryAfterAuthServer) {
        val server = retryAfterAuthServer ?: return@LaunchedEffect
        retryAfterAuthServer = null
        attemptConnection(server, isRetryAfterAuth = true)
    }

    // Stop a running connection attempt and go back to idle
    fun cancelConnection() {
        connectJob?.cancel()
        connectJob = null
        comfyUIClient?.shutdown()
        comfyUIClient = null
        connectionState = ConnectionState.IDLE
        warningMessage = null
        Toast.makeText(context, R.string.msg_connection_cancelled, Toast.LENGTH_SHORT).show()
    }

    // Offline connection function - loads from cache instead of connecting to server.
    // requireCache = false lets the user start offline even without cached server data
    // (only the on-device gallery is usable then).
    fun attemptOfflineConnection(server: Server?, requireCache: Boolean = true) {
        val serverId = server?.id ?: ConnectionManager.OFFLINE_SERVER_ID
        // Check if cache exists for this server
        if (requireCache && !ConnectionManager.hasOfflineCache(context, serverId)) {
            Toast.makeText(context, R.string.error_no_offline_cache, Toast.LENGTH_LONG).show()
            return
        }

        connectionState = ConnectionState.CONNECTING

        connectJob = scope.launch {
            // Load data from cache
            val success = ConnectionManager.loadFromOfflineCache(context, serverId, requireCache)

            if (success) {
                connectionState = ConnectionState.CONNECTED

                delay(500)

                // Save selected server
                server?.let { serverStorage.setSelectedServerId(it.id) }

                // Navigate to main activity (no actual connection established)
                val intent = Intent(context, MainContainerActivity::class.java)
                context.startActivity(intent)
            } else {
                connectionState = ConnectionState.FAILED
                Toast.makeText(context, R.string.error_no_offline_cache, Toast.LENGTH_LONG).show()
                delay(2000)
                connectionState = ConnectionState.IDLE
            }
        }
    }

    // Reset state when activity resumes (e.g., after logout or back navigation)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Reload servers in case they were modified
                servers = serverStorage.getServers()
                selectedServer = serverStorage.getSelectedServerId()?.let { id -> servers.find { it.id == id } }
                    ?: servers.firstOrNull()

                // Reload offline mode setting in case it was changed in settings
                isOfflineMode = AppSettings.isOfflineMode(context)

                // Reset connection state when screen becomes visible again
                if (connectionState == ConnectionState.CONNECTED) {
                    connectionState = ConnectionState.IDLE
                    warningMessage = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-connect on fresh app launch (if enabled in settings, we have a selected server, user didn't explicitly logout, and not in offline mode)
    LaunchedEffect(Unit) {
        val isAutoConnectEnabled = AppSettings.isAutoConnectEnabled(context)
        val shouldAutoConnect = isAutoConnectEnabled && !ConnectionManager.isUserInitiatedLogout && !isOfflineMode
        ConnectionManager.clearLogoutFlag()

        if (!hasAutoConnected && shouldAutoConnect && selectedServer != null) {
            hasAutoConnected = true
            delay(500)
            if (connectionState == ConnectionState.IDLE) {
                attemptConnection(selectedServer!!)
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            comfyUIClient?.shutdown()
        }
    }

    // Server dialog
    if (showServerDialog) {
        // Load existing credentials for editing
        val existingCredentials = remember(serverToEdit) {
            serverToEdit?.let { server ->
                credentialStorage.getCredentials(server.id, server.authType)
            } ?: AuthCredentials.None
        }

        ServerDialog(
            server = serverToEdit,
            existingCredentials = existingCredentials,
            isNameTaken = { name, excludeServerId ->
                serverStorage.isServerNameTaken(name, excludeServerId)
            },
            onDismiss = {
                showServerDialog = false
                serverToEdit = null
            },
            onSave = { name, hostname, port, authType, credentials ->
                if (serverToEdit != null) {
                    // Update existing server
                    val updatedServer = serverToEdit!!.copy(
                        name = name,
                        hostname = hostname,
                        port = port,
                        authType = authType
                    )
                    serverStorage.updateServer(updatedServer)

                    // Save credentials
                    credentialStorage.saveCredentials(updatedServer.id, credentials)

                    servers = serverStorage.getServers()

                    // If editing the selected server, update selection
                    if (selectedServer?.id == updatedServer.id) {
                        selectedServer = updatedServer
                    }
                } else {
                    // Add new server
                    val newServer = Server.create(name, hostname, port, authType)
                    serverStorage.addServer(newServer)

                    // Save credentials
                    credentialStorage.saveCredentials(newServer.id, credentials)

                    servers = serverStorage.getServers()

                    // Select the new server
                    selectedServer = newServer
                    serverStorage.setSelectedServerId(newServer.id)
                }
                showServerDialog = false
                serverToEdit = null
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation && selectedServer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.title_server_delete)) },
            text = { Text(stringResource(R.string.msg_server_delete, selectedServer!!.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        val serverToDelete = selectedServer!!
                        serverStorage.deleteServer(serverToDelete.id)

                        // Also delete credentials for the server
                        credentialStorage.deleteCredentials(serverToDelete.id)

                        servers = serverStorage.getServers()

                        // Select another server or null
                        selectedServer = servers.firstOrNull()
                        selectedServer?.let { serverStorage.setSelectedServerId(it.id) }

                        showDeleteConfirmation = false
                        Toast.makeText(context, R.string.msg_server_deleted_success, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.button_server_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Offline prompt dialog - shown when connection fails but cache exists
    if (showOfflinePrompt && offlinePromptServer != null) {
        AlertDialog(
            onDismissRequest = { showOfflinePrompt = false },
            title = { Text(stringResource(R.string.title_offline_prompt)) },
            text = { Text(stringResource(R.string.msg_offline_prompt)) },
            confirmButton = {
                Button(
                    onClick = {
                        showOfflinePrompt = false
                        // Enable offline mode and attempt offline connection
                        AppSettings.setOfflineMode(context, true)
                        isOfflineMode = true
                        offlinePromptServer?.let { server ->
                            attemptOfflineConnection(server)
                        }
                    }
                ) {
                    Text(stringResource(R.string.button_offline_prompt_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOfflinePrompt = false }) {
                    Text(stringResource(R.string.button_offline_prompt_dismiss))
                }
            }
        )
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1A8CFF).copy(alpha = 0.18f),
                    0.45f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painter = painterResource(R.drawable.logo_cm),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // App name: "Comfy" + accent "Mobile"
        Text(
            text = buildAnnotatedString {
                append("Comfy")
                withStyle(SpanStyle(color = Color(0xFF1A8CFF))) { append("Mobile") }
            },
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Server card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Server dropdown
                ServerDropdown(
                    servers = servers,
                    selectedServer = selectedServer,
                    onServerSelected = { server ->
                        selectedServer = server
                        serverStorage.setSelectedServerId(server.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connect split button with server management
                ConnectionSplitButton(
                    connectionState = connectionState,
                    hasSelectedServer = selectedServer != null,
                    isOfflineMode = isOfflineMode,
                    onCancel = { cancelConnection() },
                    onConnect = {
                        if (selectedServer != null) {
                            if (isOfflineMode) {
                                attemptOfflineConnection(selectedServer!!)
                            } else {
                                attemptConnection(selectedServer!!)
                            }
                        }
                    },
                    onGoOnline = {
                        AppSettings.setOfflineMode(context, false)
                        isOfflineMode = false
                    },
                    onAddServer = {
                        serverToEdit = null
                        showServerDialog = true
                    },
                    onEditServer = {
                        serverToEdit = selectedServer
                        showServerDialog = true
                    },
                    onRemoveServer = {
                        showDeleteConfirmation = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Start in offline mode right away (no connection attempt)
                if (!isOfflineMode && connectionState == ConnectionState.IDLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            AppSettings.setOfflineMode(context, true)
                            isOfflineMode = true
                            attemptOfflineConnection(selectedServer, requireCache = false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.button_start_offline))
                    }
                }

                // Warning message
                warningMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    }
}
