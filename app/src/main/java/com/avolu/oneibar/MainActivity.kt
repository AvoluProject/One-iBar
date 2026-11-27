package com.avolu.oneibar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Small native control panel. No overlay permission and no accessibility service are used. */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private var config = OneIBarConfig()
    private val prefs by lazy { getSharedPreferences("oneibar_ui", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = loadConfig()
        buildUi()
        persistConfig()
    }

    private fun loadConfig(): OneIBarConfig {
        val local = prefs.getString("config", null)
        val secure = try { Settings.Secure.getString(contentResolver, OneIBarConfig.SECURE_KEY) } catch (_: Throwable) { null }
        return OneIBarConfig.fromJson(secure ?: local)
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
            setBackgroundColor(Color.rgb(246, 247, 250))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)

        root.addView(text("One iBar", 30f, true, Color.rgb(18, 18, 22)))
        root.addView(text("iOS 27 Status Bar Engine", 14f, false, Color.rgb(95, 97, 105)))
        root.addView(text("v0.2.0-alpha03 • SystemUI in-memory view modification", 11f, false, Color.rgb(120, 122, 130)))

        val previewCard = card()
        previewCard.addView(text("LIVE PREVIEW", 10f, true, Color.rgb(125, 127, 135)))
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = rounded(Color.WHITE, dp(14))
        }
        preview.addView(iconPreview(OneIBarDrawable.Kind.CELLULAR, 23, 18))
        preview.addView(iconPreview(OneIBarDrawable.Kind.WIFI, 23, 18).apply { setPadding(dp(5), 0, 0, 0) })
        preview.addView(iconPreview(OneIBarDrawable.Kind.BATTERY, 34, 18).apply { setPadding(dp(7), 0, 0, 0) })
        previewCard.addView(preview, marginTop(12))
        previewCard.addView(text("Sinyal  •  Wi‑Fi  •  Baterai kapsul iOS 27", 12f, false, Color.DKGRAY), marginTop(9))
        root.addView(previewCard, marginTop(18))

        val permissionCard = card()
        permissionCard.addView(text("IZIN KONFIGURASI LIVE", 13f, true, Color.rgb(30, 32, 38)))
        statusText = text("Memeriksa izin…", 12f, false, Color.rgb(92, 94, 102))
        permissionCard.addView(statusText, marginTop(6))
        permissionCard.addView(text("Jalankan perintah sekali di Termux yang sudah diberi akses root. Setelah itu, perubahan pengaturan diterapkan langsung ke SystemUI tanpa reboot.", 12f, false, Color.rgb(92, 94, 102)), marginTop(6))
        val copyButton = button("SALIN PERINTAH ROOT") {
            val command = "su -c 'pm grant com.avolu.oneibar android.permission.WRITE_SECURE_SETTINGS'"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("One iBar permission", command))
            Toast.makeText(this, "Perintah disalin", Toast.LENGTH_SHORT).show()
        }
        permissionCard.addView(copyButton, marginTop(10))
        val retryButton = button("TERAPKAN ULANG") { persistConfig() }
        permissionCard.addView(retryButton, marginTop(7))
        root.addView(permissionCard, marginTop(14))

        addSection("STATUS BAR")
        addToggle("Aktifkan One iBar", config.enabled) { config = config.copy(enabled = it); persistConfig() }
        addToggle("Ikon sinyal seluler", config.showSignal) { config = config.copy(showSignal = it); persistConfig() }
        addToggle("Ikon Wi‑Fi", config.showWifi) { config = config.copy(showWifi = it); persistConfig() }
        addToggle("Ikon baterai", config.showBattery) { config = config.copy(showBattery = it); persistConfig() }
        addToggle("Label jaringan (4G / LTE / 5G)", config.showNetworkLabel) { config = config.copy(showNetworkLabel = it); persistConfig() }
        addToggle("Persentase di dalam baterai", config.showBatteryPercent) { config = config.copy(showBatteryPercent = it); persistConfig() }
        addToggle("Gaya jam ala iOS", config.styleClock) { config = config.copy(styleClock = it); persistConfig() }

        addSection("NETWORK LABEL")
        addDropdown("Gaya label jaringan", listOf("AUTO", "4G", "LTE", "5G", "5G+", "OFF"), config.networkMode) {
            config = config.copy(networkMode = it)
            persistConfig()
        }
        addDropdown("Warna ikon", listOf("AUTO", "LIGHT", "DARK"), config.themeMode) {
            config = config.copy(themeMode = it)
            persistConfig()
        }

        addSection("IOS 27 GEOMETRY")
        addSeek("Skala ikon", config.scalePercent, 85, 115, "%") {
            config = config.copy(scalePercent = it)
            persistConfig()
        }
        addSeek("Jarak antar ikon", config.gapDp, 2, 10, " dp") {
            config = config.copy(gapDp = it)
            persistConfig()
        }
        addSeek("Offset vertikal", config.verticalOffsetDp, -3, 3, " dp") {
            config = config.copy(verticalOffsetDp = it)
            persistConfig()
        }
        addSeek("Offset horizontal", config.horizontalOffsetDp, -6, 6, " dp") {
            config = config.copy(horizontalOffsetDp = it)
            persistConfig()
        }
        addSeek("Posisi baterai", config.batteryOffsetDp, -4, 4, " dp") {
            config = config.copy(batteryOffsetDp = it)
            persistConfig()
        }
        addSeek("Ukuran jam", config.clockScalePercent, 90, 110, "%") {
            config = config.copy(clockScalePercent = it)
            persistConfig()
        }

        addSection("ANIMATION & BEHAVIOR")
        addToggle("Animasi status bar", config.animate) { config = config.copy(animate = it); persistConfig() }
        addSeek("Durasi animasi", config.animationMs, 80, 360, " ms") {
            config = config.copy(animationMs = it)
            persistConfig()
        }
        root.addView(button("RESET KE DEFAULT IOS 27") {
            config = OneIBarConfig()
            prefs.edit().putString("config", config.toJson()).apply()
            Toast.makeText(this, "Pengaturan direset", Toast.LENGTH_SHORT).show()
            recreate()
        }, marginTop(20))
        root.addView(text("One iBar mengubah View dan Drawable di dalam proses SystemUI. Tidak membuat jendela overlay, tidak mengganti APK SystemUI, dan tidak meminta izin Accessibility.", 11f, false, Color.rgb(115, 117, 125)), marginTop(16))
        updatePermissionStatus()
    }

    private fun persistConfig() {
        prefs.edit().putString("config", config.toJson()).apply()
        val result = try {
            Settings.Secure.putString(contentResolver, OneIBarConfig.SECURE_KEY, config.toJson())
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
        updatePermissionStatus(result)
    }

    private fun updatePermissionStatus(writeSucceeded: Boolean? = null) {
        val granted = try {
            checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        statusText.text = when {
            writeSucceeded == true -> "✓ Izin aktif. Konfigurasi terkirim ke SystemUI dan diterapkan live."
            granted -> "Izin terdeteksi. Tekan Terapkan Ulang untuk mengirim konfigurasi."
            else -> "Belum diberi izin WRITE_SECURE_SETTINGS. Salin perintah root di atas, jalankan di Termux, lalu tekan Terapkan Ulang."
        }
        statusText.setTextColor(if (granted) Color.rgb(28, 128, 75) else Color.rgb(165, 90, 35))
    }

    private fun addSection(title: String) {
        root.addView(text(title, 11f, true, Color.rgb(115, 117, 125)), marginTop(21))
    }

    private fun addToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(10), dp(5))
            background = rounded(Color.WHITE, dp(12))
        }
        row.addView(text(label, 14f, false, Color.rgb(35, 37, 43)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(this).apply { isChecked = checked }
        toggle.setOnCheckedChangeListener { _, value -> onChanged(value) }
        row.addView(toggle)
        root.addView(row, marginTop(6))
    }

    private fun addDropdown(label: String, options: List<String>, selected: String, onChanged: (String) -> Unit) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(8))
            background = rounded(Color.WHITE, dp(12))
        }
        wrapper.addView(text(label, 13f, false, Color.rgb(45, 47, 53)))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(options.indexOf(selected).coerceAtLeast(0))
        var first = true
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) { first = false; return }
                onChanged(options[position])
            }
        }
        wrapper.addView(spinner)
        root.addView(wrapper, marginTop(6))
    }

    private fun addSeek(label: String, value: Int, minValue: Int, maxValue: Int, suffix: String, onChanged: (Int) -> Unit) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(8))
            background = rounded(Color.WHITE, dp(12))
        }
        val labelView = text("$label: $value$suffix", 13f, false, Color.rgb(45, 47, 53))
        wrapper.addView(labelView)
        val seek = SeekBar(this).apply {
            max = maxValue - minValue
            progress = (value - minValue).coerceIn(0, max)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = minValue + progress
                labelView.text = "$label: $v$suffix"
                if (fromUser) onChanged(v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        wrapper.addView(seek)
        root.addView(wrapper, marginTop(6))
    }

    private fun iconPreview(kind: OneIBarDrawable.Kind, widthDp: Int, heightDp: Int): View {
        val density = resources.displayMetrics.density
        return android.widget.ImageView(this).apply {
            setImageDrawable(OneIBarDrawable(this@MainActivity, kind, Color.BLACK, false))
            layoutParams = LinearLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt())
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Color.WHITE, dp(16))
        elevation = dp(1).toFloat()
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }

    private fun button(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun marginTop(top: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
