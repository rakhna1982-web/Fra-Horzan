package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HorizonRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Driver must be signed in with Google before accessing cloud garage.")
    }

    fun observeProfile(userId: String): Flow<DriverProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DriverProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeGarage(userId: String): Flow<List<OwnedCar>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val colRef = db.collection("users").document(uid).collection("garage")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OwnedCar::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeRecords(userId: String): Flow<List<ZoneRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val colRef = db.collection("users").document(uid).collection("records")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ZoneRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureStarterProfileAndCar(userId: String, displayName: String): Result<Unit> {
        val userDocPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed" }

            val cleanName = displayName.trim().take(50).ifBlank { "Horizon Driver" }
            val userRef = db.collection("users").document(uid)
            val starterCarRef = userRef.collection("garage").document("vortex_gtr")

            val userSnap = userRef.get().await()
            val carSnap = starterCarRef.get().await()

            if (!userSnap.exists() || !carSnap.exists()) {
                val batch = db.batch()
                if (!userSnap.exists()) {
                    batch.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "displayName" to cleanName,
                            "credits" to 25000L,
                            "xp" to 0L,
                            "level" to 1L,
                            "selectedCarId" to "vortex_gtr",
                            "totalDriftScore" to 0L,
                            "topSpeedKmh" to 0L,
                            "boardsSmashed" to 0L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (!carSnap.exists()) {
                    batch.set(
                        starterCarRef,
                        mapOf(
                            "userId" to uid,
                            "carId" to "vortex_gtr",
                            "paintHex" to "#FF4D6D",
                            "engineLevel" to 1L,
                            "tiresLevel" to 1L,
                            "nitroLevel" to 1L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocPath)
            Result.failure(e)
        }
    }

    suspend fun selectCar(userId: String, carId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(carId.isNotBlank())
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "selectedCarId" to carId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun purchaseCar(userId: String, carSpec: CarSpec, currentCredits: Long): Result<Unit> {
        val path = "users/$userId/garage/${carSpec.carId}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(currentCredits >= carSpec.price) { "Not enough Horizon Credits" }

            val userRef = db.collection("users").document(uid)
            val carRef = userRef.collection("garage").document(carSpec.carId)
            val newCredits = (currentCredits - carSpec.price).coerceAtLeast(0L)

            val batch = db.batch()
            batch.set(
                carRef,
                mapOf(
                    "userId" to uid,
                    "carId" to carSpec.carId,
                    "paintHex" to carSpec.defaultPaintHex,
                    "engineLevel" to 1L,
                    "tiresLevel" to 1L,
                    "nitroLevel" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "credits" to newCredits,
                    "selectedCarId" to carSpec.carId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCarBuild(
        userId: String,
        carId: String,
        paintHex: String,
        engineLevel: Long,
        tiresLevel: Long,
        nitroLevel: Long,
        upgradeCost: Long,
        currentCredits: Long
    ): Result<Unit> {
        val path = "users/$userId/garage/$carId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(paintHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            require(engineLevel in 1L..5L && tiresLevel in 1L..5L && nitroLevel in 1L..5L)
            require(currentCredits >= upgradeCost) { "Not enough Horizon Credits" }

            val userRef = db.collection("users").document(uid)
            val carRef = userRef.collection("garage").document(carId)

            val batch = db.batch()
            batch.update(
                carRef,
                mapOf(
                    "paintHex" to paintHex,
                    "engineLevel" to engineLevel,
                    "tiresLevel" to tiresLevel,
                    "nitroLevel" to nitroLevel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (upgradeCost > 0L) {
                batch.update(
                    userRef,
                    mapOf(
                        "credits" to (currentCredits - upgradeCost).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordDriveRewards(
        userId: String,
        currentProfile: DriverProfile,
        earnedCredits: Long,
        earnedXp: Long,
        driftScoreAdded: Long,
        peakSpeedKmh: Long,
        boardsDelta: Long
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)

            val newCredits = (currentProfile.credits + earnedCredits.coerceAtLeast(0L)).coerceAtMost(100_000_000L)
            val newXp = (currentProfile.xp + earnedXp.coerceAtLeast(0L)).coerceAtMost(100_000_000L)
            val newLevel = (1L + (newXp / 1500L)).coerceIn(1L, 999L)
            val newDrift = (currentProfile.totalDriftScore + driftScoreAdded.coerceAtLeast(0L)).coerceAtMost(1_000_000_000L)
            val newTopSpeed = maxOf(currentProfile.topSpeedKmh, peakSpeedKmh).coerceIn(0L, 600L)
            val newBoards = (currentProfile.boardsSmashed + boardsDelta.coerceAtLeast(0L)).coerceIn(0L, 50L)

            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "credits" to newCredits,
                        "xp" to newXp,
                        "level" to newLevel,
                        "totalDriftScore" to newDrift,
                        "topSpeedKmh" to newTopSpeed,
                        "boardsSmashed" to newBoards,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertZoneRecord(
        userId: String,
        zoneId: String,
        zoneType: ZoneType,
        newValue: Long,
        stars: Long,
        carId: String,
        existingRecord: ZoneRecord?
    ): Result<Unit> {
        val path = "users/$userId/records/$zoneId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val clampedValue = newValue.coerceIn(0L, 100_000_000L)
            val clampedStars = stars.coerceIn(0L, 3L)

            val docRef = db.collection("users").document(uid).collection("records").document(zoneId)
            if (existingRecord == null) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "zoneId" to zoneId,
                        "zoneType" to zoneType.name,
                        "bestValue" to clampedValue,
                        "stars" to clampedStars,
                        "carId" to carId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val isBetter = if (zoneType == ZoneType.CIRCUIT) {
                    existingRecord.bestValue == 0L || (clampedValue > 0L && clampedValue < existingRecord.bestValue)
                } else {
                    clampedValue > existingRecord.bestValue
                }
                if (isBetter || clampedStars > existingRecord.stars) {
                    docRef.update(
                        mapOf(
                            "bestValue" to if (isBetter) clampedValue else existingRecord.bestValue,
                            "stars" to maxOf(clampedStars, existingRecord.stars),
                            "carId" to if (isBetter) carId else existingRecord.carId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
