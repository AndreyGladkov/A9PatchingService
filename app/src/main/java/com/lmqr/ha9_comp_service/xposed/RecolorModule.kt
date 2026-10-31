package com.lmqr.ha9_comp_service.xposed

import android.app.Application
import android.app.Instrumentation
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val SERVICE_PACKAGE = "com.lmqr.ha9_comp_service"

class RecolorModule : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return
        if (!lpparam.isFirstApplication || lpparam.packageName in excludedPackages)
            return

        val session = RecolorSession(lpparam.packageName)
        FrameworkDeoptimizer.deoptimizeDrawCallers()
        CanvasHooks.install(session)
        VectorDrawableHook.install(session)
        XposedHelpers.findAndHookMethod(
            Instrumentation::class.java,
            "callApplicationOnCreate",
            Application::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    session.attach(param.args[0] as Application)
                }
            }
        )
    }

    private companion object {
        val excludedPackages = setOf(SERVICE_PACKAGE, "android")
    }
}
