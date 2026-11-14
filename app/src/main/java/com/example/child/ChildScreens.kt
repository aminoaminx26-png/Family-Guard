package com.example.child

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.data.local.LimitEntity
import com.example.core.data.local.SyncEntity
import com.example.core.data.local.UsageEntity
import com.example.core.domain.model.AppError
import com.example.core.domain.model.ChildDevice
import com.example.core.ui.components.ErrorBanner
import com.example.core.ui.components.OfflineBanner
import com.example.core.ui.components.StatusPill
import com.example.core.ui.components.toDisplayMessage
import com.example.core.usage.UsageStatsHelper
import com.example.core.utils.FormatUtils
import com.example.core.utils.NetworkUtils
import com.example.core.utils.NotificationHelper
import com.example.ui.theme.GuardianAmber
import com.example.ui.theme.GuardianEmerald
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildRootScreen(
    viewModel: ChildViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionPrefs by viewModel.sessionPrefs.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val hasUsageAccess by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val justConnected by viewModel.justConnected.collectAsStateWithLifecycle()
    val linkedDevice by viewModel.linkedDevice.collectAsStateWithLifecycle()
    val localUsage by viewModel.localUsage.collectAsStateWithLifecycle()
    val localSyncStatus by viewModel.localSyncStatus.collectAsStateWithLifecycle()
    val localLimits by viewModel.localLimits.collectAsStateWithLifecycle()

    // Re-check Usage Access permission whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkUsageAccess(context)
    }

    var showUnlinkConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.app_name))
                    }
                },
                navigationIcon = {
                    if (sessionPrefs.linkedDeviceId.isBlank() && setupStep == ChildSetupStep.ENTER_CODE) {
                        IconButton(onClick = viewModel::backToWelcome) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    }
                },
                actions = {
                    if (sessionPrefs.linkedDeviceId.isNotBlank()) {
                        IconButton(
                            onClick = { showUnlinkConfirm = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LinkOff,
                                contentDescription = stringResource(R.string.unlink_device)
                            )
                        }
                    }
                    IconButton(
                        onClick = viewModel::switchToParentMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("child_switch_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = stringResource(R.string.switch_mode)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.signOut(context) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.logout)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                if (sessionPrefs.linkedDeviceId.isBlank()) {
                    when (setupStep) {
                        ChildSetupStep.WELCOME -> ChildWelcomeStep(
                            onStartSetup = viewModel::startPairingSetup
                        )
                        ChildSetupStep.ENTER_CODE -> {
                            BackHandler { viewModel.backToWelcome() }
                            ChildEnterCodeStep(
                                isBusy = isBusy,
                                errorState = errorState,
                                onConnect = { code, name ->
                                    viewModel.connectWithPairingCode(context, code, name)
                                },
                                onClearError = viewModel::clearError
                            )
                        }
                    }
                } else {
                    ChildConnectedDashboard(
                        childName = linkedDevice?.childName ?: sessionPrefs.childName,
                        linkedDevice = linkedDevice,
                        hasUsageAccess = hasUsageAccess,
                        isBusy = isBusy,
                        justConnected = justConnected,
                        errorState = errorState,
                        localUsage = localUsage,
                        localSyncStatus = localSyncStatus,
                        localLimits = localLimits,
                        onDismissConnectedBanner = viewModel::dismissConnectedBanner,
                        onOpenUsageAccess = {
                            context.startActivity(UsageStatsHelper.buildUsageAccessSettingsIntent())
                        },
                        onSyncNow = { viewModel.syncNow(context) }
                    )
                }
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text(stringResource(R.string.unlink_device)) },
            text = { Text(stringResource(R.string.unlink_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkConfirm = false
                        viewModel.unlinkLocally(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.unlink_device))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ChildWelcomeStep(onStartSetup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_child_shield),
            contentDescription = stringResource(R.string.child_welcome_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.large)
        )

        Text(
            text = stringResource(R.string.child_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.child_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onStartSetup,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("child_start_setup_button"),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text = stringResource(R.string.start_setup))
        }
    }
}

@Composable
private fun ChildEnterCodeStep(
    isBusy: Boolean,
    errorState: AppError?,
    onConnect: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.enter_parent_code_title),
            style = MaterialTheme.typography.headlineMedium
        )

        AnimatedVisibility(visible = errorState != null) {
            errorState?.let {
                ErrorBanner(
                    message = it.toDisplayMessage(context),
                    onRetry = onClearError
                )
            }
        }

        OutlinedTextField(
            value = codeInput,
            onValueChange = { raw ->
                codeInput = raw.uppercase().take(12)
                if (errorState != null) onClearError()
            },
            label = { Text(stringResource(R.string.enter_parent_code_hint)) },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pairing_code_input")
        )

        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text(stringResource(R.string.child_name_input_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_name_input")
        )

        Button(
            onClick = { onConnect(codeInput, childName) },
            enabled = !isBusy && codeInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("child_connect_button"),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = stringResource(R.string.connect_device))
            }
        }
    }
}

@Composable
private fun ChildConnectedDashboard(
    childName: String,
    linkedDevice: ChildDevice?,
    hasUsageAccess: Boolean,
    isBusy: Boolean,
    justConnected: Boolean,
    errorState: AppError?,
    localUsage: List<UsageEntity>,
    localSyncStatus: SyncEntity?,
    localLimits: List<LimitEntity>,
    onDismissConnectedBanner: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    val isOnline = remember { NetworkUtils.isOnline(context) }
    var notifGranted by remember { mutableStateOf(NotificationHelper.canPostNotifications(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> notifGranted = granted }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isOnline) {
            item { OfflineBanner() }
        }

        if (justConnected) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = GuardianEmerald.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = GuardianEmerald)
                            Text(
                                text = stringResource(R.string.device_connected_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = GuardianEmerald
                            )
                        }
                        TextButton(onClick = onDismissConnectedBanner) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }

        errorState?.let { err ->
            item { ErrorBanner(message = err.toDisplayMessage(context)) }
        }

        // Mandatory Transparent Status Banner (Never hidden)
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.transparency_active_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.transparency_active_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.transparency_active_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (childName.isNotBlank()) {
                        StatusPill(
                            label = "${stringResource(R.string.child_device_label)}: $childName",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Official Usage Access Check
        item {
            if (!hasUsageAccess) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.usage_access_required_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.usage_access_required_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onOpenUsageAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_usage_access_button")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_android_settings))
                        }
                    }
                }
            } else {
                Card(shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = GuardianEmerald)
                        Text(
                            text = stringResource(R.string.usage_access_granted),
                            style = MaterialTheme.typography.titleMedium,
                            color = GuardianEmerald
                        )
                    }
                }
            }
        }

        // Runtime Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
            item {
                Card(shape = MaterialTheme.shapes.medium) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.notifications_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.notifications_permission_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.grant_notifications))
                        }
                    }
                }
            }
        }

        // Screen Time & WorkManager Sync Card
        item {
            val totalMs = localUsage.sumOf { it.usageTimeMs }.takeIf { it > 0L }
                ?: (linkedDevice?.totalScreenTimeMs ?: 0L)
            Card(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.screen_time_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FormatUtils.formatHms(totalMs),
                        style = MaterialTheme.typography.displayLarge.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    val lastSyncStr = localSyncStatus?.lastSuccessTimestamp?.takeIf { it > 0L }?.let {
                        FormatUtils.formatRelativeTime(it)
                    } ?: stringResource(R.string.never_synced)
                    Text(
                        text = "${stringResource(R.string.last_sync_label)}: $lastSyncStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSyncNow,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("child_sync_now_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sync_now))
                        }
                    }
                }
            }
        }

        // Active App Limits (Transparent Warnings)
        if (localLimits.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.app_limits_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(localLimits, key = { it.packageName }) { limit ->
                val usedMs = localUsage.firstOrNull { it.packageName == limit.packageName }?.usageTimeMs ?: 0L
                val exceeded = usedMs >= limit.dailyLimitMinutes * 60_000L
                Card(shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(limit.appName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${FormatUtils.formatShortDuration(usedMs)} / ${
                                    stringResource(R.string.limit_minutes_fmt, limit.dailyLimitMinutes)
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exceeded) {
                            StatusPill(
                                label = stringResource(R.string.limit_exceeded_badge),
                                containerColor = GuardianAmber.copy(alpha = 0.2f),
                                contentColor = GuardianAmber
                            )
                        }
                    }
                }
            }
        }

        if (localUsage.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.todays_usage_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(localUsage.take(10), key = { it.packageName }) { item ->
                Card(shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = FormatUtils.formatShortDuration(item.usageTimeMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
