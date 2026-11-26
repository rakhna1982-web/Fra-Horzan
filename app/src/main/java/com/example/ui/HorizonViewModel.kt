package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CarSpec
import com.example.data.DriverProfile
import com.example.data.HorizonCatalog
import com.example.data.HorizonRepository
import com.example.data.OwnedCar
import com.example.data.ZoneRecord
import com.example.game.ZoneAchievementEvent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class FestivalDashboardData(
    val profile: DriverProfile,
    val garage: List<OwnedCar>,
    val records: List<ZoneRecord>
)

enum class UpgradeSlot { ENGINE, TIRES, NITRO }

class HorizonViewModel(
    private val repository: HorizonRepository,
    private val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureStarterProfileAndCar(currentUserId, initialDisplayName)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Failed to initialize cloud profile"
                }
        }
    }

    val dashboardState: StateFlow<UiState<FestivalDashboardData>> = combine(
        repository.observeProfile(currentUserId),
        repository.observeGarage(currentUserId),
        repository.observeRecords(currentUserId)
    ) { profile, garage, records ->
        if (profile == null) {
            UiState.Loading
        } else {
            UiState.Success(
                FestivalDashboardData(
                    profile = profile,
                    garage = garage,
                    records = records
                )
            )
        }
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing Horizon cloud data", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to sync festival data"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun selectCar(carId: String) {
        viewModelScope.launch {
            repository.selectCar(currentUserId, carId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to select car" }
        }
    }

    fun buyCar(carSpec: CarSpec, currentCredits: Long) {
        if (currentCredits < carSpec.price) {
            _statusBanner.value = "Need ${carSpec.price - currentCredits} more CR to unlock ${carSpec.name}"
            return
        }
        viewModelScope.launch {
            repository.purchaseCar(currentUserId, carSpec, currentCredits)
                .onSuccess { _statusBanner.value = "Unlocked ${carSpec.name}!" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Purchase failed" }
        }
    }

    fun applyPaint(ownedCar: OwnedCar, paintHex: String) {
        viewModelScope.launch {
            repository.updateCarBuild(
                userId = currentUserId,
                carId = ownedCar.carId,
                paintHex = paintHex,
                engineLevel = ownedCar.engineLevel,
                tiresLevel = ownedCar.tiresLevel,
                nitroLevel = ownedCar.nitroLevel,
                upgradeCost = 0L,
                currentCredits = 0L
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Paint update failed"
            }
        }
    }

    fun upgradeCarSlot(ownedCar: OwnedCar, slot: UpgradeSlot, currentCredits: Long) {
        val currentLvl = when (slot) {
            UpgradeSlot.ENGINE -> ownedCar.engineLevel
            UpgradeSlot.TIRES -> ownedCar.tiresLevel
            UpgradeSlot.NITRO -> ownedCar.nitroLevel
        }
        if (currentLvl >= 5L) {
            _statusBanner.value = "Max upgrade tier reached!"
            return
        }
        val cost = HorizonCatalog.upgradeCost(currentLvl)
        if (currentCredits < cost) {
            _statusBanner.value = "Need $cost CR for Tier ${currentLvl + 1} upgrade"
            return
        }

        val newEngine = if (slot == UpgradeSlot.ENGINE) currentLvl + 1L else ownedCar.engineLevel
        val newTires = if (slot == UpgradeSlot.TIRES) currentLvl + 1L else ownedCar.tiresLevel
        val newNitro = if (slot == UpgradeSlot.NITRO) currentLvl + 1L else ownedCar.nitroLevel

        viewModelScope.launch {
            repository.updateCarBuild(
                userId = currentUserId,
                carId = ownedCar.carId,
                paintHex = ownedCar.paintHex,
                engineLevel = newEngine,
                tiresLevel = newTires,
                nitroLevel = newNitro,
                upgradeCost = cost,
                currentCredits = currentCredits
            ).onSuccess {
                _statusBanner.value = "${slot.name.lowercase().replaceFirstChar { it.uppercase() }} upgraded to Tier ${currentLvl + 1}"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Upgrade failed"
            }
        }
    }

    fun saveZoneEvent(
        event: ZoneAchievementEvent.RecordAchieved,
        carId: String,
        existingRecord: ZoneRecord?
    ) {
        viewModelScope.launch {
            repository.upsertZoneRecord(
                userId = currentUserId,
                zoneId = event.zoneId,
                zoneType = event.zoneType,
                newValue = event.value,
                stars = event.stars,
                carId = carId,
                existingRecord = existingRecord
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Could not save zone record"
            }
        }
    }

    fun commitDriveSession(
        currentProfile: DriverProfile,
        earnedCredits: Long,
        earnedXp: Long,
        driftScoreAdded: Long,
        peakSpeedKmh: Long,
        boardsDelta: Long
    ) {
        if (earnedCredits == 0L && earnedXp == 0L && driftScoreAdded == 0L && peakSpeedKmh <= currentProfile.topSpeedKmh && boardsDelta == 0L) {
            return
        }
        viewModelScope.launch {
            repository.recordDriveRewards(
                userId = currentUserId,
                currentProfile = currentProfile,
                earnedCredits = earnedCredits,
                earnedXp = earnedXp,
                driftScoreAdded = driftScoreAdded,
                peakSpeedKmh = peakSpeedKmh,
                boardsDelta = boardsDelta
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Cloud sync warning"
            }
        }
    }

    private companion object {
        const val TAG = "HorizonVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
