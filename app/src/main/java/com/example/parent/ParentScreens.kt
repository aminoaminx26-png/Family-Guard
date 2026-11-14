package com.example.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.domain.model.AppLimit
import com.example.core.domain.model.AppResult
import com.example.core.domain.model.AppUsageEntry
import com.example.core.domain.model.ChildDevice
import com.example.core.domain.model.DeviceAlert
import com.example.core.domain.model.GeneratedPairingCode
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.ErrorBanner
import com.example.core.ui.components.OfflineBanner
import com.example.core.ui.components.StatusPill
import com.example.core.ui.components.UsageBarChart
import com.example.core.ui.components.UsageBarItem
import com.example.core.ui.components.toDisplayMessage
import com.example.core.utils.FormatUtils
import com.example.core.utils.NetworkUtils
import com.example.ui.theme.GuardianAmber
import com.example.ui.theme.GuardianCrimson
import com.example.ui.theme.GuardianEmerald
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

private enum class ParentTab(val labelRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.nav_dashboard, Icons.Rounded.Dashboard),
    DEVICES(R.string.nav_devices, Icons.Rounded.Devices),
    LIMITS(R.string.nav_limits, Icons.Rounded.HourglassTop),
    ALERTS_PRIVACY(R.string.nav_privacy_alerts, Icons.Rounded.Security)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentRootScreen(
    viewModel: ParentViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val appUsageState by viewModel.appUsageState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val limitsState by viewModel.limitsState.collectAsStateWithLifecycle()
    val generatedCode by viewModel.generatedPairingCode.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val privacyMode = userProfile?.privacyModeEnabled == true
    val isOnline = remember { NetworkUtils.isOnline(context) }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = ParentTab.entries[selectedTabIndex]

    var showPairingDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var limitPrefillPkg by remember { mutableStateOf("") }
    var limitPrefillName by remember { mutableStateOf("") }

    val devices = (devicesState as? AppResult.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.togglePrivacyMode(!privacyMode) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("privacy_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (privacyMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (privacyMode) R.string.privacy_mode_on else R.string.privacy_mode_off
                                ),
                                tint = if (privacyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.switchToChildMode() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("parent_switch_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(R.string.switch_mode)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        ParentTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == ParentTab.DEVICES) {
                    FloatingActionButton(
                        onClick = { showPairingDialog = true },
                        modifier = Modifier.testTag("add_child_device_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add_child_device)
                        )
                    }
                } else if (selectedTab == ParentTab.LIMITS && selectedDevice != null) {
                    FloatingActionButton(
                        onClick = {
                            limitPrefillPkg = ""
                            limitPrefillName = ""
                            showLimitDialog = true
                        },
                        modifier = Modifier.testTag("add_limit_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add_limit)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(12.dp))
                        ParentTab.entries.forEachIndexed { index, tab ->
                            NavigationRailItem(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        if (!isOnline) {
                            OfflineBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        actionError?.let { err ->
                            ErrorBanner(
                                message = err.toDisplayMessage(context),
                                onRetry = { viewModel.clearError() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        when (selectedTab) {
                            ParentTab.DASHBOARD -> ParentDashboardTab(
                                displayName = userProfile?.displayName.orEmpty(),
                                privacyMode = privacyMode,
                                devicesState = devicesState,
                                selectedDevice = selectedDevice,
                                appUsageState = appUsageState,
                                alertsState = alertsState,
                                onSelectDevice = viewModel::selectDevice,
                                onOpenPairing = { showPairingDialog = true },
                                onOpenAlerts = { selectedTabIndex = ParentTab.ALERTS_PRIVACY.ordinal }
                            )

                            ParentTab.DEVICES -> DevicesTab(
                                devicesState = devicesState,
                                selectedDeviceId = selectedDevice?.id,
                                privacyMode = privacyMode,
                                onSelectDevice = viewModel::selectDevice,
                                onToggleMonitoring = viewModel::setMonitoringActive,
                                onOpenPairing = { showPairingDialog = true }
                            )

                            ParentTab.LIMITS -> LimitsAndAppsTab(
                                selectedDevice = selectedDevice,
                                privacyMode = privacyMode,
                                appUsageState = appUsageState,
                                limitsState = limitsState,
                                onQuickAddLimit = { pkg, name ->
                                    limitPrefillPkg = pkg
                                    limitPrefillName = name
                                    showLimitDialog = true
                                },
                                onDeleteLimit = { pkg ->
                                    selectedDevice?.let { viewModel.deleteLimit(it.id, pkg) }
                                }
                            )

                            ParentTab.ALERTS_PRIVACY -> AlertsAndPrivacyTab(
                                selectedDevice = selectedDevice,
                                alertsState = alertsState,
                                isBusy = isBusy,
                                onAcknowledgeAlert = { alertId ->
                                    selectedDevice?.let { viewModel.acknowledgeAlert(it.id, alertId) }
                                },
                                onDeleteChildData = { devId -> viewModel.deleteChildDeviceData(devId) },
                                onSwitchToChildMode = viewModel::switchToChildMode,
                                onSignOut = { viewModel.signOut(context) },
                                onDeleteAccount = { viewModel.deleteAccount(context) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPairingDialog) {
        PairingCodeDialog(
            generatedCode = generatedCode,
            isBusy = isBusy,
            onGenerate = { nickname -> viewModel.generatePairingCode(nickname) },
            onCopy = { code ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("pairing_code", code))
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.code_copied))
                }
            },
            onDismiss = {
                showPairingDialog = false
                viewModel.clearGeneratedCode()
            }
        )
    }

    if (showLimitDialog && selectedDevice != null) {
        SetAppLimitDialog(
            initialPackage = limitPrefillPkg,
            initialAppName = limitPrefillName,
            onSave = { pkg, name, mins ->
                selectedDevice?.let { dev ->
                    viewModel.upsertLimit(dev, pkg, name, mins)
                }
                showLimitDialog = false
            },
            onDismiss = { showLimitDialog = false }
        )
    }
}

@Composable
private fun ParentDashboardTab(
    displayName: String,
    privacyMode: Boolean,
    devicesState: AppResult<List<ChildDevice>>,
    selectedDevice: ChildDevice?,
    appUsageState: AppResult<List<AppUsageEntry>>,
    alertsState: AppResult<List<DeviceAlert>>,
    onSelectDevice: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenAlerts: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(132.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_guardian_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xCC0A2540),
                                        Color(0x990D47A1),
                                        Color(0x66006B5F)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.greeting_hello)} ${displayName.ifBlank { "" }} 👋".trim(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (privacyMode) {
                                StatusPill(
                                    label = stringResource(R.string.privacy_mode_on),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.auth_transparency_badge),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        when (devicesState) {
            AppResult.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AppResult.Empty -> item {
                EmptyStateView(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = stringResource(R.string.no_devices_title),
                    subtitle = stringResource(R.string.no_devices_subtitle),
                    actionLabel = stringResource(R.string.generate_pairing_code),
                    onAction = onOpenPairing
                )
            }
            is AppResult.Error -> item {
                ErrorBanner(message = devicesState.error.toDisplayMessage(LocalContext.current))
            }
            is AppResult.Success -> {
                val devices = devicesState.data
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices, key = { it.id }) { dev ->
                            val label = if (privacyMode) stringResource(R.string.privacy_masked_child) else dev.childName
                            FilterChip(
                                selected = dev.id == selectedDevice?.id,
                                onClick = { onSelectDevice(dev.id) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                selectedDevice?.let { dev ->
                    val alertsList = (alertsState as? AppResult.Success)?.data.orEmpty()
                    val unackedCount = alertsList.count { !it.acknowledged }
                    val appsList = (appUsageState as? AppResult.Success)?.data.orEmpty()

                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (privacyMode) stringResource(R.string.privacy_masked_child) else dev.childName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (privacyMode) stringResource(R.string.privacy_masked_device) else dev.deviceName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        label = stringResource(if (dev.isOnline) R.string.status_online else R.string.status_offline),
                                        containerColor = if (dev.isOnline) GuardianEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (dev.isOnline) GuardianEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.screen_time_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FormatUtils.formatHms(dev.totalScreenTimeMs),
                                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DashboardMetricMini(
                                        label = stringResource(R.string.apps_used_label),
                                        value = dev.appsUsedCount.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    DashboardMetricMini(
                                        label = stringResource(R.string.alerts_label),
                                        value = unackedCount.toString(),
                                        modifier = Modifier.weight(1f),
                                        onClick = onOpenAlerts
                                    )
                                    DashboardMetricMini(
                                        label = stringResource(R.string.monitoring_status_label),
                                        value = stringResource(if (dev.monitoringActive) R.string.status_active else R.string.status_paused),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val syncTime = dev.lastSyncAt?.toDate()?.time?.let {
                                        FormatUtils.formatRelativeTime(it)
                                    } ?: stringResource(R.string.never_synced)
                                    Text(
                                        text = "${stringResource(R.string.last_sync_label)}: $syncTime",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(
                                            when (dev.syncStatus) {
                                                "synced" -> R.string.status_synced
                                                "syncing" -> R.string.status_syncing
                                                "failed" -> R.string.status_failed
                                                else -> R.string.status_pending
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.most_used_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (appsList.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.no_usage_data),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val chartItems = appsList.take(5).mapIndexed { idx, app ->
                                        UsageBarItem(
                                            label = if (privacyMode) "${stringResource(R.string.privacy_masked_app)} #${idx + 1}" else app.appName,
                                            valueMs = app.usageTimeMs
                                        )
                                    }
                                    UsageBarChart(items = chartItems)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricMini(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DevicesTab(
    devicesState: AppResult<List<ChildDevice>>,
    selectedDeviceId: String?,
    privacyMode: Boolean,
    onSelectDevice: (String) -> Unit,
    onToggleMonitoring: (String, Boolean) -> Unit,
    onOpenPairing: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.my_children_devices),
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedButton(
                    onClick = onOpenPairing,
                    modifier = Modifier.testTag("generate_pairing_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_child_device))
                }
            }
        }

        when (devicesState) {
            AppResult.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AppResult.Empty -> item {
                EmptyStateView(
                    icon = Icons.Rounded.Devices,
                    title = stringResource(R.string.no_devices_title),
                    subtitle = stringResource(R.string.no_devices_subtitle),
                    actionLabel = stringResource(R.string.generate_pairing_code),
                    onAction = onOpenPairing
                )
            }
            is AppResult.Error -> item {
                ErrorBanner(message = devicesState.error.toDisplayMessage(LocalContext.current))
            }
            is AppResult.Success -> {
                items(devicesState.data, key = { it.id }) { dev ->
                    val isSelected = dev.id == selectedDeviceId
                    Card(
                        onClick = { onSelectDevice(dev.id) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhoneAndroid,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (privacyMode) stringResource(R.string.privacy_masked_child) else dev.childName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (privacyMode) stringResource(R.string.privacy_masked_device)
                                            else "${dev.deviceName} • ${stringResource(R.string.android_version_fmt, dev.androidVersion)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StatusPill(
                                    label = stringResource(if (dev.isOnline) R.string.status_online else R.string.status_offline),
                                    containerColor = if (dev.isOnline) GuardianEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (dev.isOnline) GuardianEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${stringResource(R.string.screen_time_label)}: ${FormatUtils.formatHms(dev.totalScreenTimeMs)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val seen = dev.lastSeenAt?.toDate()?.time?.let {
                                        FormatUtils.formatRelativeTime(it)
                                    } ?: "—"
                                    Text(
                                        text = stringResource(R.string.last_seen_fmt, seen),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.monitoring_toggle),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Switch(
                                        checked = dev.monitoringActive,
                                        onCheckedChange = { onToggleMonitoring(dev.id, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitsAndAppsTab(
    selectedDevice: ChildDevice?,
    privacyMode: Boolean,
    appUsageState: AppResult<List<AppUsageEntry>>,
    limitsState: AppResult<List<AppLimit>>,
    onQuickAddLimit: (String, String) -> Unit,
    onDeleteLimit: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.app_limits_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.app_limits_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (selectedDevice == null) {
            item {
                EmptyStateView(
                    icon = Icons.Rounded.HourglassTop,
                    title = stringResource(R.string.no_devices_title),
                    subtitle = stringResource(R.string.no_devices_subtitle)
                )
            }
            return@LazyColumn
        }

        val limits = (limitsState as? AppResult.Success)?.data.orEmpty()
        val limitsByPkg = limits.associateBy { it.packageName }

        item {
            Text(
                text = stringResource(R.string.add_limit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (limits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_limits_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(limits, key = { it.packageName }) { limit ->
                Card(shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (privacyMode) stringResource(R.string.privacy_masked_app) else limit.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.limit_minutes_fmt, limit.dailyLimitMinutes.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { onDeleteLimit(limit.packageName) }) {
                            Text(stringResource(R.string.remove_limit))
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.todays_usage_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        when (appUsageState) {
            AppResult.Loading -> item { CircularProgressIndicator() }
            AppResult.Empty -> item {
                Text(
                    text = stringResource(R.string.no_usage_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AppResult.Error -> item {
                ErrorBanner(message = appUsageState.error.toDisplayMessage(LocalContext.current))
            }
            is AppResult.Success -> {
                items(appUsageState.data, key = { it.packageName }) { app ->
                    val existingLimit = limitsByPkg[app.packageName]
                    val isExceeded = existingLimit != null &&
                        app.usageTimeMs >= existingLimit.dailyLimitMinutes * 60_000L
                    Card(shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (privacyMode) stringResource(R.string.privacy_masked_app) else app.appName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${FormatUtils.formatShortDuration(app.usageTimeMs)} • ${
                                        stringResource(R.string.launches_fmt, app.launchCount.toInt())
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isExceeded) {
                                    Spacer(Modifier.height(4.dp))
                                    StatusPill(
                                        label = stringResource(R.string.limit_exceeded_badge),
                                        containerColor = GuardianAmber.copy(alpha = 0.2f),
                                        contentColor = GuardianAmber
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onQuickAddLimit(app.packageName, app.appName) }
                            ) {
                                Text(stringResource(R.string.add_limit))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsAndPrivacyTab(
    selectedDevice: ChildDevice?,
    alertsState: AppResult<List<DeviceAlert>>,
    isBusy: Boolean,
    onAcknowledgeAlert: (String) -> Unit,
    onDeleteChildData: (String) -> Unit,
    onSwitchToChildMode: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var confirmDeleteChild by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.alerts_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        when (alertsState) {
            AppResult.Loading -> item { CircularProgressIndicator() }
            AppResult.Empty -> item {
                Card(shape = MaterialTheme.shapes.medium) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = GuardianEmerald)
                        Text(stringResource(R.string.no_alerts), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is AppResult.Error -> item {
                ErrorBanner(message = alertsState.error.toDisplayMessage(LocalContext.current))
            }
            is AppResult.Success -> {
                items(alertsState.data, key = { it.id }) { alert ->
                    val tint = when (alert.severity) {
                        "critical" -> GuardianCrimson
                        "warning" -> GuardianAmber
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Card(shape = MaterialTheme.shapes.medium) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = tint
                                )
                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusPill(
                                    label = stringResource(
                                        if (alert.acknowledged) R.string.acknowledged else R.string.alerts_label
                                    ),
                                    containerColor = tint.copy(alpha = 0.15f),
                                    contentColor = tint
                                )
                            }
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!alert.acknowledged) {
                                TextButton(onClick = { onAcknowledgeAlert(alert.id) }) {
                                    Text(stringResource(R.string.acknowledge))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.privacy_data_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrivacyItem(
                        title = stringResource(R.string.privacy_what_title),
                        body = stringResource(R.string.privacy_what_body)
                    )
                    PrivacyItem(
                        title = stringResource(R.string.privacy_why_title),
                        body = stringResource(R.string.privacy_why_body)
                    )
                    PrivacyItem(
                        title = stringResource(R.string.privacy_where_title),
                        body = stringResource(R.string.privacy_where_body)
                    )

                    if (selectedDevice != null) {
                        OutlinedButton(
                            onClick = { confirmDeleteChild = true },
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("delete_child_data_button")
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.delete_child_data))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.account_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Card(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSwitchToChildMode,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.switch_mode))
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.logout))
                    }
                    Button(
                        onClick = { confirmDeleteAccount = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("delete_account_button")
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_account))
                    }
                }
            }
        }
    }

    if (confirmDeleteChild && selectedDevice != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChild = false },
            title = { Text(stringResource(R.string.delete_child_confirm_title)) },
            text = { Text(stringResource(R.string.delete_child_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteChild = false
                        onDeleteChildData(selectedDevice.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChild = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = false },
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.delete_account_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteAccount = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAccount = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PrivacyItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PairingCodeDialog(
    generatedCode: GeneratedPairingCode?,
    isBusy: Boolean,
    onGenerate: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pairing_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.pairing_code_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (generatedCode == null) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text(stringResource(R.string.pairing_device_label_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = generatedCode.rawCode,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            OutlinedButton(onClick = { onCopy(generatedCode.rawCode) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.copy_code))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedCode == null) {
                Button(
                    onClick = { onGenerate(nickname) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("generate_pairing_code_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.generate_pairing_code))
                    }
                }
            } else {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = {
            if (generatedCode == null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun SetAppLimitDialog(
    initialPackage: String,
    initialAppName: String,
    onSave: (String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var packageName by remember { mutableStateOf(initialPackage.ifBlank { "com.google.android.youtube" }) }
    var appName by remember { mutableStateOf(initialAppName.ifBlank { "YouTube" }) }
    var minutesText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
        title = { Text(stringResource(R.string.add_limit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text(stringResource(R.string.app_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.package_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input -> minutesText = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.daily_limit_minutes_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val mins = minutesText.toIntOrNull() ?: 0
            Button(
                onClick = { onSave(packageName, appName, mins) },
                enabled = packageName.isNotBlank() && appName.isNotBlank() && mins in 1..1440
            ) {
                Text(stringResource(R.string.save_limit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
