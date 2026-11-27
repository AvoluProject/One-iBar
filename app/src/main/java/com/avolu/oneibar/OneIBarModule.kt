package com.avolu.oneibar

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** The single libxposed entry point. Scope is intentionally restricted to SystemUI. */
class OneIBarModule : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Log.i(TAG, "Loaded in ${param.processName}; framework API=${getApiVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEM_UI || param.processName != SYSTEM_UI) return
        Log.i(TAG, "SystemUI package loaded; scanning guarded Samsung/AOSP hook points")
        try {
            SystemUiHooks(this).install(param.defaultClassLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "Hook installation aborted safely; SystemUI left unchanged", t)
        }
    }

    /** API 102 hot reload: allow replacement, then install new-generation hook callbacks. */
    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        Log.i(TAG, "Preparing API-102 hot reload")
        OneIBarSystemUi.prepareForHotReload()
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        var replaced = 0
        for (handle in param.oldHookHandles) {
            try {
                handle.replaceHook { chain -> SystemUiHookDispatcher.dispatch(chain) }
                replaced++
            } catch (t: Throwable) {
                Log.w(TAG, "Could not replace old hook ${handle.id ?: "(no id)"}", t)
            }
        }
        Log.i(TAG, "Hot reload completed; replaced $replaced existing hooks")
    }

    companion object {
        const val TAG = "OneIBar"
        const val SYSTEM_UI = "com.android.systemui"
    }
}
