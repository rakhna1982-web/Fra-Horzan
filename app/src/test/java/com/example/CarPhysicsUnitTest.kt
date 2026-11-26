package com.example

import com.example.data.HorizonCatalog
import com.example.data.OwnedCar
import com.example.game.CarState
import com.example.game.DriveInput
import com.example.game.DriveMode
import com.example.game.HorizonWorldMap
import com.example.game.SurfaceType
import com.example.game.stepCarPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPhysicsUnitTest {
    @Test
    fun throttleAcceleratesCarForwardOnAsphalt() {
        val carSpec = HorizonCatalog.getCar("vortex_gtr")
        val ownedCar = OwnedCar(carId = "vortex_gtr", engineLevel = 1L, tiresLevel = 1L, nitroLevel = 1L)
        var state = CarState(x = HorizonWorldMap.START_X, y = HorizonWorldMap.START_Y, angleRad = 0f)

        repeat(40) {
            state = stepCarPhysics(
                current = state,
                input = DriveInput(throttle = 1f),
                carSpec = carSpec,
                ownedCar = ownedCar,
                dt = 0.016f,
                driveMode = DriveMode.FREE_ROAM
            ).state
        }

        assertEquals(SurfaceType.ASPHALT, state.surfaceType)
        assertTrue("Expected positive speed after throttle", state.speedKmh > 40f)
        assertTrue("Expected forward movement", state.x > HorizonWorldMap.START_X)
    }

    @Test
    fun handbrakeAndSteeringTriggerDriftAndSkidMarks() {
        val carSpec = HorizonCatalog.getCar("apex_s15")
        val ownedCar = OwnedCar(carId = "apex_s15")
        var state = CarState(
            x = HorizonWorldMap.START_X,
            y = HorizonWorldMap.START_Y,
            vx = 240f,
            vy = 0f,
            angleRad = 0f,
            speedKmh = 105f
        )

        var skidsGenerated = 0
        repeat(20) {
            val res = stepCarPhysics(
                current = state,
                input = DriveInput(throttle = 1f, steer = 1f, handbrake = true),
                carSpec = carSpec,
                ownedCar = ownedCar,
                dt = 0.016f,
                driveMode = DriveMode.FREE_ROAM
            )
            state = res.state
            skidsGenerated += res.newSkids.size
        }

        assertTrue("Expected skid marks during handbrake turn", skidsGenerated > 0)
        assertTrue("Expected drift points to accumulate", state.currentDriftPoints > 0f)
    }

    @Test
    fun drivingThroughBonusBoardAwardsCreditsAndXp() {
        val board = HorizonWorldMap.bonusBoards.first()
        val carSpec = HorizonCatalog.getCar("vortex_gtr")
        val state = CarState(x = board.x - 10f, y = board.y, vx = 120f, vy = 0f)

        val res = stepCarPhysics(
            current = state,
            input = DriveInput(throttle = 1f),
            carSpec = carSpec,
            ownedCar = null,
            dt = 0.05f,
            driveMode = DriveMode.FREE_ROAM
        )

        assertTrue(res.state.smashedBoardIds.contains(board.boardId))
        assertEquals(board.rewardCredits, res.state.sessionCredits)
        assertEquals(board.rewardXp, res.state.sessionXp)
    }
}
