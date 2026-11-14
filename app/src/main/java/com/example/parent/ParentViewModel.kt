package com.example.parent

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.PreferencesManager
import com.example.core.data.remote.AuthRepository
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.data.remote.mapToAppError
import com.example.core.domain.model.AppError
import com.example.core.domain.model.AppLimit
import com.example.core.domain.model.AppResult
import com.example.core.domain.model.AppRole
import com.example.core.domain.model.AppUsageEntry
import com.example.core.domain.model.ChildDevice
import com.example.core.domain.model.DeviceAlert
import com.example.core.domain.model.GeneratedPairingCode
import com.example.core.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModel(
    private val repository: FamilyGuardRepository,
    private val prefs: PreferencesManager,
    private val authRepository: AuthRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    val selectedDeviceId: StateFlow<String?> = _selectedDeviceId.asStateFlow()

    private val _generatedPairingCode = MutableStateFlow<GeneratedPairingCode?>(null)
    val generatedPairingCode: StateFlow<GeneratedPairingCode?> = _generatedPairingCode.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionError = MutableStateFlow<AppError?>(null)
    val actionError: StateFlow<AppError?> = _actionError.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { err ->
            unwrapFirestoreError(err)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val devicesState: StateFlow<AppResult<List<ChildDevice>>> =
        repository.observeParentDevices(currentUserId)
            .map<List<ChildDevice>, AppResult<List<ChildDevice>>> { list ->
                if (list.isEmpty()) {
                    AppResult.Empty
                } else {
                    if (_selectedDeviceId.value == null || list.none { it.id == _selectedDeviceId.value }) {
                        _selectedDeviceId.value = list.first().id
                    }
                    AppResult.Success(list)
                }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (root == null && error is CancellationException) throw error
                emit(AppResult.Error(mapToAppError(root ?: error)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = AppResult.Loading
            )

    val selectedDevice: StateFlow<ChildDevice?> = _selectedDeviceId
        .flatMapLatest { devId ->
            if (devId.isNullOrBlank()) flowOf(null)
            else repository.observeDevice(devId).catch { emit(null) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val appUsageState: StateFlow<AppResult<List<AppUsageEntry>>> = _selectedDeviceId
        .flatMapLatest { devId ->
            if (devId.isNullOrBlank()) {
                flowOf(AppResult.Empty)
            } else {
                repository.observeTodayApps(devId, currentUserId)
                    .map { apps ->
                        if (apps.isEmpty()) AppResult.Empty else AppResult.Success(apps)
                    }
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        if (root == null && error is CancellationException) throw error
                        emit(AppResult.Error(mapToAppError(root ?: error)))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppResult.Loading
        )

    val alertsState: StateFlow<AppResult<List<DeviceAlert>>> = _selectedDeviceId
        .flatMapLatest { devId ->
            if (devId.isNullOrBlank()) {
                flowOf(AppResult.Empty)
            } else {
                repository.observeAlerts(devId, currentUserId)
                    .map { alerts ->
                        if (alerts.isEmpty()) AppResult.Empty else AppResult.Success(alerts)
                    }
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        if (root == null && error is CancellationException) throw error
                        emit(AppResult.Error(mapToAppError(root ?: error)))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppResult.Loading
        )

    val limitsState: StateFlow<AppResult<List<AppLimit>>> = _selectedDeviceId
        .flatMapLatest { devId ->
            if (devId.isNullOrBlank()) {
                flowOf(AppResult.Empty)
            } else {
                repository.observeLimits(devId, currentUserId)
                    .map { limits ->
                        if (limits.isEmpty()) AppResult.Empty else AppResult.Success(limits)
                    }
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        if (root == null && error is CancellationException) throw error
                        emit(AppResult.Error(mapToAppError(root ?: error)))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppResult.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(AppRole.PARENT)
        }
    }

    fun selectDevice(deviceId: String) {
        _selectedDeviceId.value = deviceId
    }

    fun clearError() {
        _actionError.value = null
    }

    fun togglePrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePrivacyMode(currentUserId, enabled)
                .onFailure { _actionError.value = mapToAppError(it) }
        }
    }

    fun generatePairingCode(childNickname: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            repository.generatePairingCode(childNickname)
                .onSuccess { _generatedPairingCode.value = it }
                .onFailure { _actionError.value = mapToAppError(it) }
            _isBusy.value = false
        }
    }

    fun clearGeneratedCode() {
        _generatedPairingCode.value = null
    }

    fun setMonitoringActive(deviceId: String, active: Boolean) {
        viewModelScope.launch {
            repository.setDeviceMonitoring(deviceId, active)
                .onFailure { _actionError.value = mapToAppError(it) }
        }
    }

    fun upsertLimit(
        device: ChildDevice,
        packageName: String,
        appName: String,
        dailyMinutes: Int
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertAppLimit(device, packageName, appName, dailyMinutes)
                .onFailure { _actionError.value = mapToAppError(it) }
            _isBusy.value = false
        }
    }

    fun deleteLimit(deviceId: String, packageName: String) {
        viewModelScope.launch {
            repository.deleteAppLimit(deviceId, packageName)
                .onFailure { _actionError.value = mapToAppError(it) }
        }
    }

    fun acknowledgeAlert(deviceId: String, alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(deviceId, alertId)
                .onFailure { _actionError.value = mapToAppError(it) }
        }
    }

    fun deleteChildDeviceData(deviceId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteChildDeviceData(deviceId)
                .onSuccess { _selectedDeviceId.value = null }
                .onFailure { _actionError.value = mapToAppError(it) }
            _isBusy.value = false
        }
    }

    fun switchToChildMode() {
        viewModelScope.launch {
            prefs.setRole(AppRole.CHILD)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }

    fun deleteAccount(context: Context) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteParentAccountData(currentUserId)
                .onSuccess {
                    authRepository.deleteFirebaseUser(context)
                        .onFailure { authRepository.signOut(context) }
                }
                .onFailure { _actionError.value = mapToAppError(it) }
            _isBusy.value = false
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError != null) {
            Log.w(TAG, "Firestore stream error: ${firestoreError.code}", firestoreError)
        }
        return firestoreError
    }

    private companion object {
        const val TAG = "ParentViewModel"
    }
}
