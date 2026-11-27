package com.avolu.oneibar

import android.content.Context
import android.graphics.Color
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** A real child view inside Samsung's status-bar icon container (no overlay window). */
internal class OneIBarStatusGroup(context: Context) : LinearLayout(context) {
    private val signal = ImageView(context)
    private val network = TextView(context)
    private val wifi = ImageView(context)
    private val battery = ImageView(context)
    private var lastConfig: OneIBarConfig = OneIBarConfig()
    private var iconTint: Int = Color.BLACK
    private var iconsInitialized = false
    private var baseTranslationY = 0f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, 0)
        tag = GROUP_TAG
        addView(network)
        addView(signal)
        addView(wifi)
        addView(battery)
        contentDescription = "One iBar iOS 27 status indicators"
    }

    fun apply(
        config: OneIBarConfig,
        tint: Int,
        hasSignalSource: Boolean,
        hasWifiSource: Boolean,
        hasBatterySource: Boolean
    ) {
        val previousConfig = lastConfig
        val density = resources.displayMetrics.density
        val scale = config.scalePercent / 100f
        val parentHeight = (parent as? View)?.height ?: 0
        val availableHeightDp = if (parentHeight > 0) (parentHeight / density) - 2f else 18f
        val iconHeightDp = min(18f * scale, max(14f, availableHeightDp))
        val signalWidth = 23f * scale
        val wifiWidth = 23f * scale
        val batteryWidth = 30f * scale
        val gap = config.gapDp * scale

        signal.visibility = if (config.showSignal && hasSignalSource) VISIBLE else GONE
        wifi.visibility = if (config.showWifi && hasWifiSource) VISIBLE else GONE
        battery.visibility = if (config.showBattery && hasBatterySource) VISIBLE else GONE
        network.visibility = if (config.showNetworkLabel && config.networkMode != "OFF") VISIBLE else GONE

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val finalTint = when (config.themeMode) {
            "LIGHT" -> Color.BLACK
            "DARK" -> Color.WHITE
            else -> if (tint == Color.BLACK || tint == Color.WHITE) tint else if (isNight) Color.WHITE else Color.BLACK
        }
        val iconsNeedRebuild = !iconsInitialized || iconTint != finalTint ||
            previousConfig.showBatteryPercent != config.showBatteryPercent
        if (iconsNeedRebuild) {
            signal.setImageDrawable(OneIBarDrawable(context, OneIBarDrawable.Kind.CELLULAR, finalTint))
            wifi.setImageDrawable(OneIBarDrawable(context, OneIBarDrawable.Kind.WIFI, finalTint))
            battery.setImageDrawable(OneIBarDrawable(context, OneIBarDrawable.Kind.BATTERY, finalTint, config.showBatteryPercent))
            iconsInitialized = true
            iconTint = finalTint
        } else {
            signal.drawable?.invalidateSelf()
            wifi.drawable?.invalidateSelf()
            battery.drawable?.invalidateSelf()
        }
        network.text = networkLabel(context, config.networkMode)
        if (config.showNetworkLabel && config.networkMode == "AUTO" && network.text.isBlank()) {
            network.visibility = GONE
        }
        network.setTextColor(finalTint)
        network.textSize = 9.2f * scale
        network.includeFontPadding = false
        network.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        network.gravity = Gravity.CENTER

        setChildLayout(signal, signalWidth, iconHeightDp, if (network.visibility == VISIBLE) gap * 0.35f else gap)
        val networkWidth = if (network.visibility == VISIBLE) max(13f, network.text.toString().length * 6.0f * scale) else 0f
        setChildLayout(network, networkWidth, iconHeightDp, gap * 0.65f)
        setChildLayout(wifi, wifiWidth, iconHeightDp, gap)
        setChildLayout(battery, batteryWidth, iconHeightDp, 0f, config.batteryOffsetDp)

        baseTranslationY = config.verticalOffsetDp * density
        translationY = baseTranslationY
        translationX = config.horizontalOffsetDp * density
        lastConfig = config
        requestLayout()
        invalidate()
    }

    private fun setChildLayout(
        view: View,
        widthDp: Float,
        heightDp: Float,
        endGapDp: Float,
        startGapDp: Float = 0f
    ) {
        val d = resources.displayMetrics.density
        val params = (view.layoutParams as? LayoutParams) ?: LayoutParams(0, 0)
        params.width = (widthDp * d).toInt().coerceAtLeast(0)
        params.height = (heightDp * d).toInt().coerceAtLeast(0)
        params.marginStart = (startGapDp * d).toInt()
        params.marginEnd = (endGapDp * d).toInt().coerceAtLeast(0)
        view.layoutParams = params
    }

    fun animateQuickSettings(expanded: Boolean) {
        if (!lastConfig.animate) return
        val targetAlpha = if (expanded) 0.94f else 1f
        val targetY = if (expanded) baseTranslationY + resources.displayMetrics.density else baseTranslationY
        animate().cancel()
        animate().alpha(targetAlpha).translationY(targetY)
            .setDuration(min(180, lastConfig.animationMs).toLong()).start()
    }

    fun currentTint(): Int = iconTint

    fun animateIndicatorRefresh() {
        if (!lastConfig.animate) return
        animate().cancel()
        scaleX = 0.97f
        scaleY = 0.97f
        animate().scaleX(1f).scaleY(1f)
            .setDuration(135L).start()
    }

    fun animateStatusBar(visible: Boolean) {
        if (!lastConfig.animate) {
            alpha = if (visible) 1f else 0f
            return
        }
        animate().cancel()
        animate().alpha(if (visible) 1f else 0f)
            .setDuration(lastConfig.animationMs.toLong()).start()
    }

    private fun networkLabel(context: Context, setting: String): String {
        if (setting == "OFF") return ""
        if (setting in setOf("4G", "LTE", "5G", "5G+")) return setting
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val service = tm.serviceState
            if (service != null && service.state != android.telephony.ServiceState.STATE_IN_SERVICE) return ""
            if (OneIBarSystemUi.networkOverrideType == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) {
                return "5G+"
            }
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE -> "E"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                else -> ""
            }
        } catch (_: Throwable) { "" }
    }

    companion object {
        const val GROUP_TAG = "com.avolu.oneibar.statusbar.group.v2"
    }
}
