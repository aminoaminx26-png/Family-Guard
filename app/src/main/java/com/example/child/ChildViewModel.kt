package com.example.child

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.LimitEntity
import com.example.core.data.local.LocalSessionPrefs
import com.example.core.data.local.PreferencesManager
import com.example.core.data.local.SyncEntity
import com.example.core.data.local.UsageEntity
import com.example.core.data.remote.AuthRepository
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.data.remote.mapToAppError
import com.example.core.domain.model.AppError
import com.example.core.domain.model.AppRole
import com.example.core.domain.model.ChildDevice
import com.example.core.usage.UsageStatsHelper
import com.example.core.utils.NetworkUtils
import com.example.core.utils.NotificationHelper
import com.example.core.worker.UsageSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChildSetupStep {
    WELCOME,
    ENTER_CODE
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChildViewModel(
    private val repository: FamilyGuardRepository,
    private val prefs: PreferencesManager,
    private val authRepository: AuthRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _setupStep = MutableStateFlow(ChildSetupStep.WELCOME)
    val setupStep: StateFlow<ChildSetupStep> = _setupStep.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorState = MutableStateFlow<AppError?>(null)
    val errorState: StateFlow<AppError?> = _errorState.asStateFlow()

    private val _justConnected = MutableStateFlow(false)
    val justConnected: StateFlow<Boolean> = _justConnected.asStateFlow()

    val sessionPrefs: StateFlow<LocalSessionPrefs> = prefs.prefsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LocalSessionPrefs()
        )

    val linkedDevice: StateFlow<ChildDevice?> = sessionPrefs
        .flatMapLatest { session ->
            if (session.linkedDeviceId.isBlank()) flowOf(null)
            else repository.observeDevice(session.linkedDeviceId).catch { emit(null) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val localUsage: StateFlow<List<UsageEntity>> = repository.observeLocalTodayUsage()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val localSyncStatus: StateFlow<SyncEntity?> = repository.observeLocalSyncStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val localLimits: StateFlow<List<LimitEntity>> = repository.observeLocalLimits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun startPairingSetup() {
        _errorState.value = null
        _setupStep.value = ChildSetupStep.ENTER_CODE
    }

    fun backToWelcome() {
        _errorState.value = null
        _setupStep.value = ChildSetupStep.WELCOME
    }

    fun clearError() {
        _errorState.value = null
    }

    fun dismissConnectedBanner() {
        _justConnected.value = false
    }

    fun checkUsageAccess(context: Context) {
        _hasUsageAccess.value = UsageStatsHelper.hasUsageAccessPermission(context)
    }

    fun connectWithPairingCode(context: Context, rawCode: String, childName: String) {
        if (!NetworkUtils.isOnline(context)) {
            _errorState.value = AppError.NoInternet
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _errorState.value = null
            val usageGranted = UsageStatsHelper.hasUsageAccessPermission(context)
            _hasUsageAccess.value = usageGranted
            repository.redeemPairingCode(rawCode, childName, usageGranted)
                .onSuccess {
                    _justConnected.value = true
                    NotificationHelper.showTransparentProtectionNotification(context)
                    UsageSyncWorker.schedulePeriodic(context)
                    UsageSyncWorker.enqueueImmediate(context)
                }
                .onFailure { err ->
                    _errorState.value = if (err is FamilyGuardRepository.PairingException) {
                        err.appError
                    } else {
                        mapToAppError(err)
                    }
                }
            _isBusy.value = false
        }
    }

    fun syncNow(context: Context) {
        val deviceId = sessionPrefs.value.linkedDeviceId
        if (deviceId.isBlank()) return
        val granted = UsageStatsHelper.hasUsageAccessPermission(context)
        _hasUsageAccess.value = granted

        viewModelScope.launch {
            _isBusy.value = true
            _errorState.value = null
            val apps = if (granted) UsageStatsHelper.collectTodayAppUsage(context) else emptyList()
            NotificationHelper.showTransparentProtectionNotification(context)
            repository.syncChildUsage(deviceId, apps, granted)
                .onSuccess { exceeded ->
                    exceeded.filter { it.warningEnabled }.forEach { limit ->
                        NotificationHelper.showAppLimitWarning(
                            context = context,
                            appName = limit.appName,
                            limitMinutes = limit.dailyLimitMinutes.toInt()
                        )
                    }
                    if (!NetworkUtils.isOnline(context)) {
                        _errorState.value = AppError.NoInternet
                    }
                }
                .onFailure { err ->
                    _errorState.value = if (err is FamilyGuardRepository.PairingException) {
                        if (err.appError == AppError.DeviceUnlinked) {
                            prefs.clearChildPairing()
                        }
                        err.appError
                    } else {
                        mapToAppError(err)
                    }
                }
            _isBusy.value = false
        }
    }

    fun unlinkLocally(context: Context) {
        viewModelScope.launch {
            UsageSyncWorker.cancel(context)
            prefs.clearChildPairing()
            _setupStep.value = ChildSetupStep.WELCOME
        }
    }

    fun switchToParentMode() {
        viewModelScope.launch {
            prefs.setRole(AppRole.PARENT)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            UsageSyncWorker.cancel(context)
            authRepository.signOut(context)
        }
    }
}
