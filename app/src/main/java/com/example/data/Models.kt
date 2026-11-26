package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DriverProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Horizon Driver",
    val credits: Long = 25000L,
    val xp: Long = 0L,
    val level: Long = 1L,
    val selectedCarId: String = "vortex_gtr",
    val totalDriftScore: Long = 0L,
    val topSpeedKmh: Long = 0L,
    val boardsSmashed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnedCar(
    @DocumentId val id: String = "",
    val userId: String = "",
    val carId: String = "vortex_gtr",
    val paintHex: String = "#FF4D6D",
    val engineLevel: Long = 1L,
    val tiresLevel: Long = 1L,
    val nitroLevel: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ZoneType {
    SPEED_TRAP,
    DRIFT_ZONE,
    CIRCUIT
}

@Keep
@IgnoreExtraProperties
data class ZoneRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val zoneId: String = "",
    val zoneType: String = ZoneType.SPEED_TRAP.name,
    val bestValue: Long = 0L,
    val stars: Long = 0L,
    val carId: String = "vortex_gtr",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CarSpec(
    val carId: String,
    val name: String,
    val carClass: String,
    val price: Long,
    val defaultPaintHex: String,
    val tagline: String,
    val baseTopSpeedKmh: Float,
    val baseAcceleration: Float,
    val asphaltGrip: Float,
    val offroadGrip: Float,
    val driftFactor: Float
)

data class PaintSwatch(
    val label: String,
    val hex: String
)

object HorizonCatalog {
    val paintSwatches = listOf(
        PaintSwatch("Festival Coral", "#FF4D6D"),
        PaintSwatch("Electric Cyan", "#00E5FF"),
        PaintSwatch("Solar Gold", "#FFB703"),
        PaintSwatch("Emerald Neon", "#00E676"),
        PaintSwatch("Apex Violet", "#9D4EDD"),
        PaintSwatch("Pearl Ice", "#EEF2F6")
    )

    val cars = listOf(
        CarSpec(
            carId = "vortex_gtr",
            name = "Vortex GT-R",
            carClass = "S1 · STREET",
            price = 0L,
            defaultPaintHex = "#FF4D6D",
            tagline = "Balanced twin-turbo festival coupe built for canyon asphalt.",
            baseTopSpeedKmh = 255f,
            baseAcceleration = 460f,
            asphaltGrip = 6.2f,
            offroadGrip = 3.2f,
            driftFactor = 0.88f
        ),
        CarSpec(
            carId = "apex_s15",
            name = "Apex Drift S15",
            carClass = "A · DRIFT",
            price = 18000L,
            defaultPaintHex = "#00E5FF",
            tagline = "Tail-happy drift weapon engineered for long chained hairpins.",
            baseTopSpeedKmh = 242f,
            baseAcceleration = 445f,
            asphaltGrip = 5.4f,
            offroadGrip = 2.9f,
            driftFactor = 1.15f
        ),
        CarSpec(
            carId = "canyon_baja",
            name = "Canyon Baja RS",
            carClass = "A · OFFROAD",
            price = 26000L,
            defaultPaintHex = "#FFB703",
            tagline = "Long-travel desert trophy truck that shreds sand and gravel.",
            baseTopSpeedKmh = 232f,
            baseAcceleration = 435f,
            asphaltGrip = 5.3f,
            offroadGrip = 5.6f,
            driftFactor = 0.94f
        ),
        CarSpec(
            carId = "titan_rally",
            name = "Titan Rally Evo",
            carClass = "S1 · RALLY",
            price = 42000L,
            defaultPaintHex = "#00E676",
            tagline = "AWD gravel specialist with explosive low-end launch.",
            baseTopSpeedKmh = 268f,
            baseAcceleration = 510f,
            asphaltGrip = 6.5f,
            offroadGrip = 5.2f,
            driftFactor = 0.96f
        ),
        CarSpec(
            carId = "solstice_hyper",
            name = "Solstice Hyper X",
            carClass = "S2 · HYPER",
            price = 75000L,
            defaultPaintHex = "#9D4EDD",
            tagline = "Featherweight carbon hypercar built to shatter speed traps.",
            baseTopSpeedKmh = 325f,
            baseAcceleration = 590f,
            asphaltGrip = 7.4f,
            offroadGrip = 3.5f,
            driftFactor = 0.92f
        )
    )

    fun getCar(carId: String): CarSpec = cars.firstOrNull { it.carId == carId } ?: cars.first()

    fun upgradeCost(currentLevel: Long): Long = when (currentLevel) {
        1L -> 4500L
        2L -> 8500L
        3L -> 14000L
        4L -> 22000L
        else -> 0L
    }
}
