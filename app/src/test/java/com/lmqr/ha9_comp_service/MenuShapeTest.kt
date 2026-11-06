package com.lmqr.ha9_comp_service

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuShapeTest {
    @Test
    fun followsTheScreenCornerInsideTheInset() {
        assertEquals(50, concentricRadius(outerRadiusPx = 61, insetPx = 11, minRadiusPx = 18))
    }

    @Test
    fun restsOnTheScreenEdgeWithGestureNavigation() {
        assertEquals(0, menuBottomOffset(navigationMode = 2, navBarHeight = 54))
    }

    @Test
    fun staysAboveNavigationButtons() {
        assertEquals(54, menuBottomOffset(navigationMode = 0, navBarHeight = 54))
    }

    @Test
    fun keepsTheMenuRadiusOnSquareScreens() {
        assertEquals(18, concentricRadius(outerRadiusPx = 0, insetPx = 11, minRadiusPx = 18))
    }
}
