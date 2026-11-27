package com.avolu.oneibar

import android.content.Context
import android.provider.Settings
import org.json.JSONObject

/** Configuration shared between the companion app and SystemUI through Settings.Secure. */
data class OneIBarConfig(
    val enabled: Boolean = true,
    val showSignal: Boolean = true,
    val showWifi: Boolean = true,
    val showBattery: Boolean = true,
    val showNetworkLabel: Boolean = false,
    val networkMode: String = "AUTO",
    val scalePercent: Int = 100,
    val gapDp: Int = 6,
    val verticalOffsetDp: Int = 0,
    val horizontalOffsetDp: Int = 0,
    val batteryOffsetDp: Int = 0,
    val themeMode: String = "AUTO",
    val showBatteryPercent: Boolean = false,
    val styleClock: Boolean = true,
    val clockScalePercent: Int = 100,
    val animate: Boolean = true,
    val animationMs: Int = 190
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("showSignal", showSignal)
        .put("showWifi", showWifi)
        .put("showBattery", showBattery)
        .put("showNetworkLabel", showNetworkLabel)
        .put("networkMode", networkMode)
        .put("scalePercent", scalePercent.coerceIn(85, 115))
        .put("gapDp", gapDp.coerceIn(2, 10))
        .put("verticalOffsetDp", verticalOffsetDp.coerceIn(-3, 3))
        .put("horizontalOffsetDp", horizontalOffsetDp.coerceIn(-6, 6))
        .put("batteryOffsetDp", batteryOffsetDp.coerceIn(-4, 4))
        .put("themeMode", themeMode)
        .put("showBatteryPercent", showBatteryPercent)
        .put("styleClock", styleClock)
        .put("clockScalePercent", clockScalePercent.coerceIn(90, 110))
        .put("animate", animate)
        .put("animationMs", animationMs.coerceIn(80, 360))
        .toString()

    companion object {
        const val SECURE_KEY = "oneibar_config_v2"

        fun fromJson(json: String?): OneIBarConfig {
            if (json.isNullOrBlank()) return OneIBarConfig()
            return try {
                val o = JSONObject(json)
                OneIBarConfig(
                    enabled = o.optBoolean("enabled", true),
                    showSignal = o.optBoolean("showSignal", true),
                    showWifi = o.optBoolean("showWifi", true),
                    showBattery = o.optBoolean("showBattery", true),
                    showNetworkLabel = o.optBoolean("showNetworkLabel", false),
                    networkMode = o.optString("networkMode", "AUTO").uppercase(),
                    scalePercent = o.optInt("scalePercent", 100).coerceIn(85, 115),
                    gapDp = o.optInt("gapDp", 6).coerceIn(2, 10),
                    verticalOffsetDp = o.optInt("verticalOffsetDp", 0).coerceIn(-3, 3),
                    horizontalOffsetDp = o.optInt("horizontalOffsetDp", 0).coerceIn(-6, 6),
                    batteryOffsetDp = o.optInt("batteryOffsetDp", 0).coerceIn(-4, 4),
                    themeMode = o.optString("themeMode", "AUTO").uppercase(),
                    showBatteryPercent = o.optBoolean("showBatteryPercent", false),
                    styleClock = o.optBoolean("styleClock", true),
                    clockScalePercent = o.optInt("clockScalePercent", 100).coerceIn(90, 110),
                    animate = o.optBoolean("animate", true),
                    animationMs = o.optInt("animationMs", 190).coerceIn(80, 360)
                )
            } catch (_: Throwable) {
                OneIBarConfig()
            }
        }

        fun readFromSystem(context: Context): OneIBarConfig = try {
            fromJson(Settings.Secure.getString(context.contentResolver, SECURE_KEY))
        } catch (_: Throwable) {
            OneIBarConfig()
        }
    }
}
