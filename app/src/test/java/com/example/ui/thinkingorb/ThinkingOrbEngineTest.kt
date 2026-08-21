package com.smartprocurement.internal.ui.thinkingorb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingOrbEngineTest {
    @Test
    fun `official state mapping and per size speeds are preserved`() {
        val expected = mapOf(
            ThinkingOrbState.Working to (1.885 to 3.9),
            ThinkingOrbState.Searching to (2.015 to 2.665),
            ThinkingOrbState.Solving to (1.82 to 1.95),
            ThinkingOrbState.Listening to (4.388 to 3.998),
            ThinkingOrbState.Connecting to (3.315 to 6.63),
            ThinkingOrbState.Weaving to (1.625 to 2.75),
            ThinkingOrbState.Composing to (2.34 to 3.12),
            ThinkingOrbState.Breathing to (3.24 to 3.78),
            ThinkingOrbState.Shaping to (2.405 to 2.08)
        )
        expected.forEach { (state, speed) ->
            assertEquals(speed.first, ThinkingOrbEngine.resolve(state, OrbPresetSize.Large).speed, 0.0)
            assertEquals(speed.second, ThinkingOrbEngine.resolve(state, OrbPresetSize.Inline).speed, 0.0)
        }
    }

    @Test
    fun `every official state makes a valid static reduced motion frame`() {
        val buffer = OrbFrameBuffer()
        ThinkingOrbState.entries.forEach { state ->
            ThinkingOrbEngine.frame(state, OrbPresetSize.Large, .6, buffer)
            assertTrue("$state should render dots", buffer.dotCount > 0)
            for (index in 0 until buffer.dotCount) {
                assertTrue("$state radius must be finite", buffer.dotRadius(index).isFinite())
                assertTrue("$state radius must respect its floor", buffer.dotRadius(index) >= .25)
                assertTrue("$state opacity is valid", buffer.dotAlpha(index) in 0.02..1.0)
            }
        }
    }

    @Test
    fun `z draw order is far to near`() {
        val buffer = OrbFrameBuffer()
        ThinkingOrbEngine.frame(ThinkingOrbState.Searching, OrbPresetSize.Large, 1.3, buffer)
        var previous = Double.NEGATIVE_INFINITY
        for (index in 0 until buffer.dotCount) {
            // The engine intentionally exposes final painter order rather than leaving sorting to Canvas.
            val z = buffer.dotZ(index)
            assertTrue("dots must be z sorted", z >= previous)
            previous = z
        }
    }
}
