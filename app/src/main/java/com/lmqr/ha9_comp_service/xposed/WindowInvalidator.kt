package com.lmqr.ha9_comp_service.xposed

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

object WindowInvalidator {
    // Touched only from the main thread: invalidateTree always runs in a posted Runnable.
    private val descendantInvalidators = HashMap<Class<*>, Method?>()

    fun invalidateAll() = logFailures {
        rootViews().forEach { root -> root.post { logFailures { invalidateTree(root) } } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> {
        val global = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.view.WindowManagerGlobal", null),
            "getInstance"
        )
        return synchronized(XposedHelpers.getObjectField(global, "mLock")) {
            ArrayList(XposedHelpers.getObjectField(global, "mViews") as List<View>)
        }
    }

    private fun invalidateTree(view: View) {
        view.invalidate()
        descendantInvalidatorOf(view.javaClass)?.invoke(view)
        if (view is ViewGroup)
            for (i in 0 until view.childCount)
                invalidateTree(view.getChildAt(i))
    }

    // AndroidComposeView records each graphics layer in its own RenderNode, which
    // View.invalidate() does not re-record; its invalidateDescendants() does. The name only
    // survives in apps that do not obfuscate Compose, other apps redraw on their next update.
    private fun descendantInvalidatorOf(viewClass: Class<*>): Method? {
        if (viewClass !in descendantInvalidators)
            descendantInvalidators[viewClass] = viewClass.methods
                .firstOrNull { it.name == "invalidateDescendants" && it.parameterCount == 0 }
        return descendantInvalidators[viewClass]
    }

    private inline fun logFailures(block: () -> Unit) {
        runCatching(block).onFailure(XposedBridge::log)
    }
}
