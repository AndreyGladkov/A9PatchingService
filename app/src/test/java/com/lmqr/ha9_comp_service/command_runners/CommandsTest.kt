package com.lmqr.ha9_comp_service.command_runners

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandsTest {
    @Test
    fun scrollSpeedCommandsMatchTheDaemonProtocol() {
        assertEquals("tsc1", Commands.SCROLL_SPEED(true))
        assertEquals("tsc0", Commands.SCROLL_SPEED(false))
        assertEquals("tsd300", Commands.SCROLL_SPEED_SETTLE(300))
        assertEquals("tsm800", Commands.SCROLL_SPEED_MAX_AFTER_LIFT(800))
    }
}
