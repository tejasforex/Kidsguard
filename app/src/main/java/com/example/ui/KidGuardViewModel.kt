package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.device.DeviceTelemetrySnapshot
import com.example.data.device.DeviceUsageRepository
import com.example.data.model.AppRule
import com.example.data.model.ChildProfile
import com.example.data.model.FamilySettings
import com.example.data.model.RewardQuest
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.ParentalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KidGuardUiState(
    val isLoading: Boolean = true,
    val familySettings: FamilySettings? = null,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val appRules: List<AppRule> = emptyList(),
    val quests: List<RewardQuest> = emptyList(),
    val deviceSnapshot: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(
        hasUsageAccess = false,
        totalMinutesToday = 0L,
        installedApps = emptyList(),
    ),
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) {
    val selectedChild: ChildProfile?
        get() = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()

    val kidModeChild: ChildProfile?
        get() {
            val activeId = familySettings?.activeKidModeChildId.orEmpty()
            return if (activeId.isNotBlank()) {
                children.firstOrNull { it.id == activeId } ?: selectedChild
            } else {
                selectedChild
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KidGuardViewModel(
    private val repository: ParentalRepository,
    private val deviceUsageRepository: DeviceUsageRepository,
    private val currentUserId: String,
    private val displayName: String,
) : ViewModel() {

    private val selectedChildIdFlow = MutableStateFlow<String?>(null)
    private val deviceSnapshotFlow = MutableStateFlow(
        DeviceTelemetrySnapshot(
            hasUsageAccess = false,
            totalMinutesToday = 0L,
            installedApps = emptyList(),
        )
    )
    private val errorMessageFlow = MutableStateFlow<String?>(null)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val settingsFlow = repository.observeFamilySettings(currentUserId)
        .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing family settings", root)
            errorMessageFlow.value = root.message ?: "Failed to load family settings"
            emit(null)
        }

    private val childrenFlow = repository.observeChildren(currentUserId)
        .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing child profiles", root)
            errorMessageFlow.value = root.message ?: "Failed to load child profiles"
            emit(emptyList())
        }

    private val activeChildIdFlow = combine(childrenFlow, selectedChildIdFlow) { children, explicit ->
        when {
            explicit != null && children.any { it.id == explicit } -> explicit
            else -> children.firstOrNull()?.id
        }
    }

    private val appRulesFlow = activeChildIdFlow.flatMapLatest { childId ->
        if (childId.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeAppRules(currentUserId, childId).catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Error observing app rules", root)
                errorMessageFlow.value = root.message ?: "Failed to load app rules"
                emit(emptyList())
            }
        }
    }

    private val questsFlow = activeChildIdFlow.flatMapLatest { childId ->
        if (childId.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeQuests(currentUserId, childId).catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Error observing quests", root)
                errorMessageFlow.value = root.message ?: "Failed to load reward quests"
                emit(emptyList())
            }
        }
    }

    val uiState: StateFlow<KidGuardUiState> = combine(
        settingsFlow,
        childrenFlow,
        activeChildIdFlow,
        appRulesFlow,
        questsFlow,
        deviceSnapshotFlow,
        errorMessageFlow,
        statusMessageFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as FamilySettings?
        @Suppress("UNCHECKED_CAST")
        val children = values[1] as List<ChildProfile>
        val activeChildId = values[2] as String?
        @Suppress("UNCHECKED_CAST")
        val rules = values[3] as List<AppRule>
        @Suppress("UNCHECKED_CAST")
        val quests = values[4] as List<RewardQuest>
        val snapshot = values[5] as DeviceTelemetrySnapshot
        val error = values[6] as String?
        val status = values[7] as String?

        KidGuardUiState(
            isLoading = settings == null && error == null,
            familySettings = settings,
            children = children,
            selectedChildId = activeChildId,
            appRules = rules,
            quests = quests,
            deviceSnapshot = snapshot,
            errorMessage = error,
            statusMessage = status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = KidGuardUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            try {
                repository.ensureFamilySettings(currentUserId, displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Failed to initialize family account"
            }
        }
        refreshDeviceTelemetry()
    }

    fun selectChild(childId: String) {
        selectedChildIdFlow.value = childId
    }

    fun clearMessages() {
        errorMessageFlow.value = null
        statusMessageFlow.value = null
    }

    fun refreshDeviceTelemetry() {
        viewModelScope.launch {
            try {
                deviceSnapshotFlow.value = deviceUsageRepository.loadDeviceSnapshot()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load device usage snapshot", e)
            }
        }
    }

    fun addChild(
        name: String,
        age: Long,
        avatarKey: String,
        dailyLimitMinutes: Long,
        weekendLimitMinutes: Long,
    ) {
        val rating = when {
            age <= 7L -> "kids_4_7"
            age <= 12L -> "preteen_8_12"
            else -> "teen_13_plus"
        }
        viewModelScope.launch {
            try {
                val id = repository.createChildProfile(
                    userId = currentUserId,
                    name = name,
                    age = age,
                    avatarKey = avatarKey,
                    dailyLimitMinutes = dailyLimitMinutes,
                    weekendLimitMinutes = weekendLimitMinutes,
                    contentRating = rating,
                )
                selectedChildIdFlow.value = id
                statusMessageFlow.value = "$name's profile created"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not create child profile"
            }
        }
    }

    fun updateChildInfo(
        childId: String,
        name: String,
        age: Long,
        avatarKey: String,
        dailyLimitMinutes: Long,
        weekendLimitMinutes: Long,
    ) {
        viewModelScope.launch {
            try {
                repository.updateChildProfileInfo(
                    userId = currentUserId,
                    childId = childId,
                    name = name,
                    age = age,
                    avatarKey = avatarKey,
                    dailyLimitMinutes = dailyLimitMinutes,
                    weekendLimitMinutes = weekendLimitMinutes,
                )
                statusMessageFlow.value = "$name's profile updated"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not update child profile"
            }
        }
    }

    fun deleteChild(childId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChildProfile(currentUserId, childId)
                if (selectedChildIdFlow.value == childId) {
                    selectedChildIdFlow.value = null
                }
                statusMessageFlow.value = "Child profile removed"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not remove child profile"
            }
        }
    }

    fun togglePause(child: ChildProfile) {
        viewModelScope.launch {
            try {
                val nextPaused = !child.isPaused
                repository.updateChildControls(
                    userId = currentUserId,
                    childId = child.id,
                    isPaused = nextPaused,
                    usedMinutesToday = child.usedMinutesToday,
                    bonusMinutesToday = child.bonusMinutesToday,
                )
                statusMessageFlow.value = if (nextPaused) {
                    "${child.name}'s screen access paused"
                } else {
                    "${child.name}'s screen access resumed"
                }
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not update pause state"
            }
        }
    }

    fun grantQuickBonus(child: ChildProfile, deltaMinutes: Long) {
        viewModelScope.launch {
            try {
                val updatedBonus = (child.bonusMinutesToday + deltaMinutes).coerceIn(0L, 360L)
                repository.updateChildControls(
                    userId = currentUserId,
                    childId = child.id,
                    isPaused = child.isPaused,
                    usedMinutesToday = child.usedMinutesToday,
                    bonusMinutesToday = updatedBonus,
                )
                statusMessageFlow.value = if (deltaMinutes >= 0) {
                    "Added +${deltaMinutes}m bonus for ${child.name}"
                } else {
                    "Adjusted bonus time for ${child.name}"
                }
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not adjust bonus time"
            }
        }
    }

    fun syncDeviceUsageToChild(child: ChildProfile) {
        val snapshot = deviceSnapshotFlow.value
        if (!snapshot.hasUsageAccess) return
        viewModelScope.launch {
            try {
                repository.updateChildControls(
                    userId = currentUserId,
                    childId = child.id,
                    isPaused = child.isPaused,
                    usedMinutesToday = snapshot.totalMinutesToday.coerceIn(0L, 1440L),
                    bonusMinutesToday = child.bonusMinutesToday,
                )
                statusMessageFlow.value = "Synced ${snapshot.totalMinutesToday}m device usage today"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not sync device usage"
            }
        }
    }

    fun resetDailyUsage(child: ChildProfile) {
        viewModelScope.launch {
            try {
                repository.updateChildControls(
                    userId = currentUserId,
                    childId = child.id,
                    isPaused = false,
                    usedMinutesToday = 0L,
                    bonusMinutesToday = 0L,
                )
                statusMessageFlow.value = "Reset today's screen timer for ${child.name}"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not reset timer"
            }
        }
    }

    fun saveScheduleAndSafety(updatedChild: ChildProfile) {
        viewModelScope.launch {
            try {
                repository.updateChildScheduleAndSafety(currentUserId, updatedChild)
                statusMessageFlow.value = "Schedule & safety filters saved"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not save schedule settings"
            }
        }
    }

    fun saveAppRule(
        childId: String,
        existingRuleId: String?,
        packageName: String,
        appName: String,
        category: String,
        status: String,
        dailyLimitMinutes: Long,
        usedMinutesToday: Long,
    ) {
        viewModelScope.launch {
            try {
                repository.upsertAppRule(
                    userId = currentUserId,
                    childId = childId,
                    existingRuleId = existingRuleId,
                    packageName = packageName,
                    appName = appName,
                    category = category,
                    status = status,
                    dailyLimitMinutes = dailyLimitMinutes,
                    usedMinutesToday = usedMinutesToday,
                )
                statusMessageFlow.value = "Saved rule for $appName"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not save app rule"
            }
        }
    }

    fun removeAppRule(childId: String, ruleId: String) {
        viewModelScope.launch {
            try {
                repository.deleteAppRule(currentUserId, childId, ruleId)
                statusMessageFlow.value = "App rule reset to default"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not delete app rule"
            }
        }
    }

    fun createQuest(childId: String, title: String, rewardMinutes: Long) {
        viewModelScope.launch {
            try {
                repository.createQuest(currentUserId, childId, title, rewardMinutes)
                statusMessageFlow.value = "Quest added"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not create quest"
            }
        }
    }

    fun requestQuestApproval(childId: String, questId: String) {
        viewModelScope.launch {
            try {
                repository.requestQuestApproval(currentUserId, childId, questId)
                statusMessageFlow.value = "Sent to parent for approval!"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not submit quest"
            }
        }
    }

    fun approveQuest(child: ChildProfile, quest: RewardQuest) {
        viewModelScope.launch {
            try {
                repository.approveQuestAndGrantBonus(currentUserId, child, quest)
                statusMessageFlow.value = "Approved! +${quest.rewardMinutes}m granted to ${child.name}"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not approve quest"
            }
        }
    }

    fun declineQuest(childId: String, quest: RewardQuest) {
        viewModelScope.launch {
            try {
                repository.declineQuestApproval(currentUserId, childId, quest)
                statusMessageFlow.value = "Returned quest to ${quest.title}"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not decline quest"
            }
        }
    }

    fun deleteQuest(childId: String, questId: String) {
        viewModelScope.launch {
            try {
                repository.deleteQuest(currentUserId, childId, questId)
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not remove quest"
            }
        }
    }

    fun setParentPin(pin: String) {
        viewModelScope.launch {
            try {
                val currentName = uiState.value.familySettings?.displayName ?: displayName
                repository.setParentPin(currentUserId, currentName, pin)
                statusMessageFlow.value = "Parent PIN saved"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not save Parent PIN"
            }
        }
    }

    fun verifyParentPin(pin: String): Boolean {
        val storedHash = uiState.value.familySettings?.parentPinHash.orEmpty()
        if (storedHash.isBlank()) return true
        return ParentalRepository.hashPin(pin) == storedHash
    }

    fun enterKidMode(childId: String) {
        viewModelScope.launch {
            try {
                repository.setKidMode(currentUserId, active = true, childId = childId)
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not enter Kid Mode"
            }
        }
    }

    fun exitKidMode(pin: String): Boolean {
        if (!verifyParentPin(pin)) return false
        viewModelScope.launch {
            try {
                repository.setKidMode(currentUserId, active = false, childId = "")
                statusMessageFlow.value = "Returned to Parent Dashboard"
            } catch (e: Exception) {
                errorMessageFlow.value = e.message ?: "Could not exit Kid Mode"
            }
        }
        return true
    }

    fun launchApp(packageName: String): Boolean {
        return deviceUsageRepository.launchApp(packageName)
    }

    private companion object {
        const val TAG = "KidGuardVM"
    }
}
