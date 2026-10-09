/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.subscriptions.impl.onboarding.confetti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ConfettiPhysicsTest {

    @Test
    fun whenComputingKeyframesThenReturnsOneFramePerStepPlusInitial() {
        val output = ConfettiPhysics.computeKeyframes(anInput())

        val expected = ConfettiPhysics.KEYFRAME_STEPS + 1
        listOf(output.tx, output.ty, output.scale, output.rotX, output.rotY, output.rotZ, output.opacity).forEach {
            assertEquals(expected, it.size)
        }
    }

    @Test
    fun whenVelocityGravityAndWobbleAreZeroThenParticleHoldsAtOrigin() {
        val output = ConfettiPhysics.computeKeyframes(
            anInput(startVelocity = 0.0, decay = 1.0, gravity = 0.0, wobbleSpeed = 0.0, wobbleOffset = 0.0, size = 0.0, fadeOutEnd = 1.0),
        )

        assertEquals(0.0, output.tx.last(), 0.0001)
        assertEquals(0.0, output.ty.last(), 0.0001)
        assertEquals(1.0, output.scale.last(), 0.0001)
    }

    @Test
    fun whenBurstStartsThenParticleIsInvisiblySmallAndFullyOpaque() {
        val output = ConfettiPhysics.computeKeyframes(anInput())

        assertEquals(0.0, output.scale.first(), 0.0001)
        assertEquals(1.0, output.opacity.first(), 0.0001)
    }

    @Test
    fun whenFadeOutEndIsReachedThenParticleIsTransparent() {
        val output = ConfettiPhysics.computeKeyframes(anInput(fadeOutEnd = 0.5))

        assertEquals(0.0, output.opacity.last(), 0.0001)
    }

    @Test
    fun whenLaunchedUpwardsThenParticleRisesBeforeFalling() {
        val output = ConfettiPhysics.computeKeyframes(anInput(startVelocity = 25.0, gravity = 1.0))

        assertTrue(output.ty.minOrNull()!! < 0.0)
        assertTrue(output.ty.last() > output.ty.minOrNull()!!)
    }

    private fun anInput(
        startVelocity: Double = 25.0,
        decay: Double = 0.91,
        gravity: Double = 1.0,
        wobbleSpeed: Double = 0.08,
        wobbleOffset: Double = 1.0,
        size: Double = 1.0,
        fadeOutEnd: Double = 0.8,
    ) = ConfettiPhysics.Input(
        angle = -PI / 2,
        startVelocity = startVelocity,
        decay = decay,
        gravity = gravity,
        drift = 0.0,
        wobbleSpeed = wobbleSpeed,
        wobbleOffset = wobbleOffset,
        size = size,
        ticks = 150,
        xTiltRotations = 0.0,
        tiltRotations = 3.0,
        zTiltRotations = 0.0,
        rotation = 45.0,
        fadeOutEnd = fadeOutEnd,
    )
}
