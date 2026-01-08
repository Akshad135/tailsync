package com.tailsync.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tailsync.app.data.SettingsRepository
import com.tailsync.app.network.ConnectionState
import com.tailsync.app.service.MainService
import com.tailsync.app.ui.screens.DashboardScreen
import com.tailsync.app.ui.screens.SettingsScreen
import com.tailsync.app.ui.screens.SetupGuideScreen
import com.tailsync.app.ui.theme.TailSyncTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object SetupGuide : Screen("setup_guide")
}

class MainActivity : ComponentActivity() {

    private var mainService: MainService? = null
    private var isBound = false
    private lateinit var settingsRepository: SettingsRepository

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MainService.LocalBinder
            mainService = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMainService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startMainService()
            }
        } else {
            startMainService()
        }

        setContent {
            TailSyncTheme {
                MainApp(
                    getMainService = { mainService },
                    settingsRepository = settingsRepository,
                    onStartService = { startMainService() },
                    onStopService = { stopMainService() }
                )
            }
        }

        // Check if opened from tile with settings intent
        if (intent?.getBooleanExtra("open_settings", false) == true) {
            // Navigation handled in composable
        }
    }

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    override fun onStop() {
        super.onStop()
        unbindFromService()
    }

    private fun bindToService() {
        val intent = Intent(this, MainService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun startMainService() {
        val intent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMainService() {
        val intent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun MainApp(
    getMainService: () -> MainService?,
    settingsRepository: SettingsRepository,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Collect settings
    val serverUrl by settingsRepository.serverUrl.collectAsState(initial = "")
    val serverPort by settingsRepository.serverPort.collectAsState(initial = 8765)
    val autoConnect by settingsRepository.autoConnect.collectAsState(initial = true)
    val savedLastSyncedText by settingsRepository.lastSyncedText.collectAsState(initial = "")
    val savedLastSyncedTime by settingsRepository.lastSyncedTime.collectAsState(initial = 0L)
    val clipboardHistory by settingsRepository.clipboardHistory.collectAsState(initial = emptyList())

    // Service state
    val service = getMainService()
    val connectionState by service?.connectionState?.collectAsState() 
        ?: remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    val lastSyncedText by service?.lastSyncedText?.collectAsState() 
        ?: remember { mutableStateOf(savedLastSyncedText) }
    val lastSyncedTime by service?.lastSyncedTime?.collectAsState() 
        ?: remember { mutableStateOf(savedLastSyncedTime) }
    
    // Error state for dialog
    val errorTitle by service?.errorTitle?.collectAsState() 
        ?: remember { mutableStateOf<String?>(null) }
    val errorDetails by service?.errorDetails?.collectAsState() 
        ?: remember { mutableStateOf<String?>(null) }

    // Show error dialog when error occurs
    if (errorTitle != null && errorDetails != null) {
        com.tailsync.app.ui.components.ErrorDialog(
            title = errorTitle!!,
            errorDetails = errorDetails!!,
            onDismiss = { service?.clearError() }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    connectionState = connectionState,
                    clipboardHistory = clipboardHistory,
                    onSyncNow = { service?.syncClipboardNow() },
                    onConnect = { service?.connect() },
                    onDisconnect = { service?.disconnect() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToSetup = { navController.navigate(Screen.SetupGuide.route) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    serverUrl = serverUrl,
                    serverPort = serverPort,
                    autoConnect = autoConnect,
                    isServiceRunning = service != null,
                    onServerUrlChange = { url ->
                        scope.launch { settingsRepository.setServerUrl(url) }
                    },
                    onServerPortChange = { port ->
                        scope.launch { settingsRepository.setServerPort(port) }
                    },
                    onAutoConnectChange = { enabled ->
                        scope.launch { settingsRepository.setAutoConnect(enabled) }
                    },
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SetupGuide.route) {
                SetupGuideScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
