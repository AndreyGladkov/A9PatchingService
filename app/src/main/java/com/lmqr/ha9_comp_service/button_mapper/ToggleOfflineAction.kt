package com.lmqr.ha9_comp_service.button_mapper

import android.content.Context
import com.lmqr.ha9_comp_service.A9AccessibilityService

class ToggleOfflineAction : ButtonAction {
    override fun execute(context: Context) {
        (context as? A9AccessibilityService)?.toggleOffline()
    }
}
