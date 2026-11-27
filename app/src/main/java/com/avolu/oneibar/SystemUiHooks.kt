package com.avolu.oneibar

import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/** Finds only known status-bar lifecycle/update methods; absent firmware targets are skipped. */
internal class SystemUiHooks(private val module: XposedModule) {
    private val installed = HashSet<String>()

    fun install(classLoader: ClassLoader) {
        val targets = mapOf(
            "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl" to setOf(
                "refreshIconGroups", "updateIcon", "setIcon", "addSystemIcon", "onViewAttached", "hideBySimplification"
            ),
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl" to setOf(
                "refreshIconGroups", "updateIcon", "setIcon", "addSystemIcon", "onViewAttached", "hideBySimplification"
            ),
            "com.android.systemui.statusbar.phone.ui.SignalClusterView" to setOf(
                "setWifiIndicators", "setMobileDataIndicators", "apply", "updateWifiState", "updateMobileType"
            ),
            "com.android.systemui.statusbar.phone.SignalClusterView" to setOf(
                "setWifiIndicators", "setMobileDataIndicators", "apply", "updateWifiState", "updateMobileType"
            ),
            "com.android.systemui.BatteryMeterView" to setOf(
                "onBatteryLevelChanged", "updateBatteryLevel", "setBatteryLevel", "updateDrawable", "updatePercentText"
            ),
            "com.android.systemui.statusbar.phone.BatteryMeterView" to setOf(
                "onBatteryLevelChanged", "updateBatteryLevel", "setBatteryLevel", "updateDrawable", "updatePercentText"
            ),
            "com.android.systemui.statusbar.phone.CollapsedStatusBarFragment" to setOf(
                "animateShow", "animateHide", "showSystemIconArea", "hideSystemIconArea", "updateVisibilities"
            ),
            "com.android.systemui.statusbar.phone.PhoneStatusBarView" to setOf(
                "onFinishInflate", "onAttachedToWindow", "onDetachedFromWindow"
            ),
            "com.android.systemui.statusbar.phone.StatusBarWindowView" to setOf(
                "onFinishInflate", "onAttachedToWindow"
            ),
            "com.android.systemui.shade.NotificationPanelViewController" to setOf(
                "expandToQs", "setQsExpanded", "onTrackingStarted", "onTrackingStopped"
            ),
            "com.android.systemui.statusbar.phone.KeyguardStatusBarViewController" to setOf(
                "setKeyguardShowing", "updateVisibilities", "animateShow", "animateHide"
            ),
            "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager" to setOf(
                "show", "hide", "onKeyguardFadedAway", "setKeyguardGoingAway", "setKeyguardFadingAway"
            )
        )

        var foundClasses = 0
        var foundHooks = 0
        for ((name, methodNames) in targets) {
            val clazz = try {
                Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                continue
            }
            foundClasses++
            val methods = try { clazz.declaredMethods } catch (_: Throwable) { emptyArray<Method>() }
            for (method in methods) {
                if (method.name !in methodNames || method.isSynthetic || method.isBridge) continue
                val id = "oneibar:${clazz.name}#${method.name}/${method.parameterCount}"
                if (!installed.add(id)) continue
                try {
                    module.hook(method)
                        .setId(id)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain -> SystemUiHookDispatcher.dispatch(chain) }
                    foundHooks++
                } catch (t: Throwable) {
                    Log.w(OneIBarModule.TAG, "Skipped hook $id", t)
                }
            }
        }
        Log.i(OneIBarModule.TAG, "Hook scan done: classes=$foundClasses hooks=$foundHooks")
        if (foundHooks == 0) {
            Log.w(OneIBarModule.TAG, "No known hook matched this firmware; no SystemUI view was changed")
        }
    }
}

/** Shared stateless dispatcher, also used to replace callbacks during API-102 hot reload. */
internal object SystemUiHookDispatcher {
    fun dispatch(chain: XposedInterface.Chain): Any? {
        val executable = chain.executable as? Method
        val methodName = executable?.name.orEmpty()
        val arguments = chain.args
        val owner = chain.thisObject
        val result = chain.proceed()

        try {
            when {
                owner is View -> OneIBarSystemUi.refresh(owner)
                owner != null -> scanViewFields(owner)
            }

            if (methodName.contains("battery", ignoreCase = true) ||
                methodName.contains("wifi", ignoreCase = true) ||
                methodName.contains("signal", ignoreCase = true) ||
                methodName.contains("mobileData", ignoreCase = true)) {
                OneIBarSystemUi.animateIndicatorRefresh()
            }
            if (methodName.contains("QsExpanded", ignoreCase = true) ||
                methodName.contains("expandToQs", ignoreCase = true)) {
                val expanded = arguments.firstOrNull { it is Boolean } as? Boolean
                OneIBarSystemUi.animateQuickSettings(expanded ?: true)
            } else if (methodName.contains("onTrackingStarted", ignoreCase = true)) {
                OneIBarSystemUi.animateQuickSettings(true)
            } else if (methodName.contains("onTrackingStopped", ignoreCase = true)) {
                OneIBarSystemUi.animateIndicatorRefresh()
            }
            if (methodName.contains("animateHide", ignoreCase = true) ||
                methodName.contains("hideSystemIconArea", ignoreCase = true)) {
                OneIBarSystemUi.animateStatusBar(false)
            } else if (methodName.contains("animateShow", ignoreCase = true) ||
                methodName.contains("showSystemIconArea", ignoreCase = true) ||
                methodName.contains("onKeyguardFadedAway", ignoreCase = true)) {
                OneIBarSystemUi.animateStatusBar(true)
            }
        } catch (t: Throwable) {
            Log.w(OneIBarModule.TAG, "Post-hook refresh skipped for $methodName", t)
        }
        return result
    }

    private fun scanViewFields(owner: Any) {
        var type: Class<*>? = owner.javaClass
        var levels = 0
        while (type != null && type != Any::class.java && levels < 4) {
            val fields = try { type.declaredFields } catch (_: Throwable) { emptyArray() }
            for (field in fields) {
                if (!View::class.java.isAssignableFrom(field.type)) continue
                try {
                    field.isAccessible = true
                    (field.get(owner) as? View)?.let { OneIBarSystemUi.refresh(it) }
                } catch (_: Throwable) {
                    // Samsung private fields may be blocked or uninitialized on a given build.
                }
            }
            type = type.superclass
            levels++
        }
    }
}
