package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToLaunchTest {
    private fun gesture(left: Boolean = false) = PullToLaunch(0f, 0f, 0, left, 8f, 40f).apply {
        update(0f, 0f, 0, "app")
    }

    private fun arm(p: PullToLaunch, direction: Float = -1f) {
        p.update(10f * direction, 0f, 150, "app")
        p.update(45f * direction, 0f, 300, "app")
        assertTrue(p.armed)
    }

    @Test fun deliberatePullAndRelease() {
        val p = gesture()
        arm(p)
        p.update(-45f, 0f, 350, "app", release = true)
        assertTrue(p.armed)
        assertEquals("app", p.key)
    }

    @Test fun leftHandedDirectionIsMirrored() {
        arm(gesture(left = true), 1f)
        val wrong = gesture(left = true)
        wrong.update(-50f, 0f, 200, "app")
        assertFalse(wrong.armed)
    }

    @Test fun outwardRetreatCancels() {
        val p = gesture()
        arm(p)
        p.update(-30f, 0f, 350, "app")
        assertFalse(p.armed)
        p.update(-30f, 0f, 400, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun subSlopJitterAroundThresholdStaysArmed() {
        val p = gesture()
        arm(p)
        p.update(-39f, 0f, 400, "app")
        assertTrue(p.armed)
        assertEquals(1f, p.progress, 0f)
    }

    @Test fun outwardRetreatCancelsEvenAboveThreshold() {
        val p = gesture()
        arm(p)
        p.update(-70f, 0f, 400, "app")
        p.update(-55f, 0f, 500, "app")
        assertFalse(p.armed)
        p.update(-55f, 0f, 550, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun verticalScrollAndDiagonalDriftNeverArm() {
        val p = gesture()
        for (i in 1..30) {
            p.update(-i * 3f, i * 12f, i * 50L, "app")
            assertFalse(p.armed)
            assertEquals(0f, p.progress, 0f)
        }
    }

    @Test fun selectionChangeCancelsRatherThanLaunchingAnotherApp() {
        val p = gesture()
        arm(p)
        p.update(-45f, 0f, 350, "other", release = true)
        assertFalse(p.armed)
        assertNull(p.key)
    }

    @Test fun renewedVerticalMotionCancels() {
        val p = gesture()
        arm(p)
        p.update(-45f, 12f, 350, "app")
        assertFalse(p.armed)
    }

    @Test fun releaseCannotArm() {
        val p = gesture()
        p.update(-10f, 0f, 150, "app")
        p.update(-45f, 0f, 300, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun fastDiagonalReleaseCancels() {
        val p = gesture()
        arm(p)
        p.update(-90f, 80f, 320, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun fastHorizontalReleaseCancels() {
        val p = gesture()
        arm(p)
        p.update(-180f, 0f, 320, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun fastReleaseAfterHoldingStillCancels() {
        val p = gesture()
        arm(p)
        p.update(-200f, 0f, 2000, "app", release = true)
        assertFalse(p.armed)
    }

    @Test fun subSlopDriftDoesNotArm() {
        val p = gesture()
        p.update(-7f, 0f, 200, "app")
        p.update(-7f, 0f, 400, "app", release = true)
        assertFalse(p.armed)
        assertEquals(0f, p.progress, 0f)
    }

    @Test fun missingSelectionCancels() {
        val p = gesture()
        arm(p)
        p.update(-45f, 0f, 350, null, release = true)
        assertFalse(p.armed)
    }

    @Test fun scrollThenTurnInwardInOneTouch() {
        val p = gesture()
        p.update(-3f, 30f, 100, "other")
        p.update(-4f, 60f, 200, "target")
        p.update(-4f, 60f, 350, "target")
        p.update(-14f, 60f, 500, "target")
        p.update(-49f, 60f, 650, "target")
        assertTrue(p.armed)
        assertEquals("target", p.key)
    }
}
