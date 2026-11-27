package com.avolu.oneibar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

/** Native Canvas glyphs: iOS-27-style four bars, three Wi-Fi arcs and a filled capsule battery. */
internal class OneIBarDrawable(
    private val context: Context,
    private val kind: Kind,
    private val tint: Int,
    private val showPercent: Boolean = false
) : Drawable() {
    enum class Kind { CELLULAR, WIFI, BATTERY }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alphaValue = 255
    private var colorFilterValue: ColorFilter? = null
    private var reveal = 0f
    private val animator: ValueAnimator? = if (OneIBarSystemUi.config.animate) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = OneIBarSystemUi.config.animationMs.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    } else null

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) {
            canvas.restoreToCount(save)
            return
        }
        val baseW = intrinsicWidth.toFloat()
        val baseH = intrinsicHeight.toFloat()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / baseW, b.height() / baseH)
        paint.isAntiAlias = true
        paint.alpha = (alphaValue * max(0.18f, reveal)).toInt().coerceIn(0, 255)
        paint.colorFilter = colorFilterValue
        when (kind) {
            Kind.CELLULAR -> drawCellular(canvas)
            Kind.WIFI -> drawWifi(canvas)
            Kind.BATTERY -> drawBattery(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawCellular(canvas: Canvas) {
        val level = readSignalLevel().coerceIn(0, 4)
        val heights = floatArrayOf(6.0f, 9.0f, 12.0f, 15.0f)
        for (i in heights.indices) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < level) tint else inactiveColor()
            val left = 1.0f + i * 5.55f
            val top = 16.0f - heights[i]
            canvas.drawRoundRect(RectF(left, top, left + 3.35f, 16.0f), 1.55f, 1.55f, paint)
        }
    }

    private fun drawWifi(canvas: Canvas) {
        paint.color = if (isWifiConnected()) tint else inactiveColor()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.45f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val arc1 = Path().apply {
            moveTo(1.0f, 5.2f); quadTo(11.7f, -3.0f, 22.4f, 5.2f)
        }
        val arc2 = Path().apply {
            moveTo(4.8f, 9.2f); quadTo(11.7f, 3.4f, 18.6f, 9.2f)
        }
        val arc3 = Path().apply {
            moveTo(8.0f, 12.8f); quadTo(11.7f, 9.8f, 15.4f, 12.8f)
        }
        canvas.drawPath(arc1, paint)
        canvas.drawPath(arc2, paint)
        canvas.drawPath(arc3, paint)
        paint.style = Paint.Style.FILL
        val dot = Path().apply {
            moveTo(8.8f, 14.2f); quadTo(11.7f, 11.8f, 14.6f, 14.2f)
            lineTo(11.7f, 17.2f); close()
        }
        canvas.drawPath(dot, paint)
    }

    private fun drawBattery(canvas: Canvas) {
        val level = readBatteryLevel().coerceIn(0, 100)
        val charging = isCharging()
        val body = RectF(0.8f, 1.1f, 26.7f, 16.1f)
        val cap = RectF(27.5f, 5.45f, 29.2f, 11.75f)
        paint.style = Paint.Style.FILL
        paint.color = inactiveColor()
        canvas.drawRoundRect(body, 7.1f, 7.1f, paint)
        canvas.drawRoundRect(cap, 1.8f, 1.8f, paint)

        val inset = RectF(body.left + 1.0f, body.top + 1.0f, body.right - 1.0f, body.bottom - 1.0f)
        val fillRight = inset.left + inset.width() * (level / 100f)
        if (fillRight > inset.left + 0.2f) {
            val clip = canvas.save()
            val capsule = Path().apply { addRoundRect(inset, 6.1f, 6.1f, Path.Direction.CW) }
            canvas.clipPath(capsule)
            paint.color = when {
                charging -> Color.rgb(38, 174, 91)
                level <= 20 -> Color.rgb(255, 59, 48)
                else -> tint
            }
            canvas.drawRect(inset.left, inset.top, fillRight, inset.bottom, paint)
            canvas.restoreToCount(clip)
        }
        if (charging) {
            // Compact white bolt keeps the charging state legible at status-bar size.
            val bolt = Path().apply {
                moveTo(15.0f, 2.8f)
                lineTo(10.9f, 9.1f)
                lineTo(13.8f, 9.1f)
                lineTo(12.0f, 13.8f)
                lineTo(18.0f, 7.7f)
                lineTo(15.0f, 7.7f)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawPath(bolt, paint)
        } else if (showPercent && body.width() > 18f) {
            paint.color = if (level > 52) Color.WHITE else tint
            paint.textSize = 7.0f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            canvas.drawText(level.toString(), body.centerX(), body.centerY() + 2.35f, paint)
        }
    }

    private fun readBatteryLevel(): Int = try {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 100
    } catch (_: Throwable) { 100 }

    private fun isCharging(): Boolean = try {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val state = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
    } catch (_: Throwable) { false }

    private fun readSignalLevel(): Int = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.signalStrength?.level ?: 3
    } catch (_: Throwable) { 3 }

    private fun isWifiConnected(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Throwable) { false }

    private fun inactiveColor(): Int {
        val alpha = (Color.alpha(tint) * 0.34f).toInt().coerceIn(70, 190)
        return Color.argb(alpha, Color.red(tint), Color.green(tint), Color.blue(tint))
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { colorFilterValue = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = when (kind) {
        Kind.CELLULAR -> 23
        Kind.WIFI -> 23
        Kind.BATTERY -> 30
    }
    override fun getIntrinsicHeight(): Int = 18
    override fun onBoundsChange(bounds: android.graphics.Rect) { invalidateSelf() }
    override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
}
