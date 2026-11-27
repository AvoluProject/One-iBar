package com.avolu.oneibar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.SignalStrength
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.util.Collections
import java.util.WeakHashMap

/** Live SystemUI-side controller. It edits the existing status-bar View hierarchy in memory. */
internal object OneIBarSystemUi {
    @Volatile var config: OneIBarConfig = OneIBarConfig()
        private set

    private val groups = Collections.synchronizedMap(WeakHashMap<OneIBarStatusGroup, Boolean>())
    private val roots = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private var observerRegistered = false
    private var batteryReceiver: BroadcastReceiver? = null
    private var contentObserver: ContentObserver? = null
    private var observerContext: Context? = null
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    @Volatile var networkOverrideType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        private set

    fun refresh(start: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            start.post { refresh(start) }
            return
        }
        val root = (start.rootView as? ViewGroup) ?: (start as? ViewGroup) ?: return
        val context = root.context
        if (context.packageName != OneIBarModule.SYSTEM_UI) return
        roots[root] = true
        ensureObservers(context)
        config = OneIBarConfig.readFromSystem(context)

        if (!config.enabled) {
            restoreAndRemove(root)
            return
        }

        val nodes = collectNodes(root)
        tuneClock(nodes, config)
        val tagged = nodes.filter { it.tag == OneIBarStatusGroup.GROUP_TAG }
        val existing = tagged.firstOrNull() as? OneIBarStatusGroup
        tagged.filter { it !== existing }.forEach { old ->
            (old.parent as? ViewGroup)?.removeView(old)
        }

        val detectedSources = nodes.mapNotNull { view ->
            val kind = classifySource(view) ?: return@mapNotNull null
            SourceView(view, kind)
        }
        val container = findContainer(nodes, detectedSources) ?: run {
            restoreAndRemove(root)
            Log.d(OneIBarModule.TAG, "Could not identify a safe status icon container; skipping injection")
            return
        }
        // The decor tree can also contain QS/lock-screen icons. Touch only views inside
        // the selected status-icon container, never every similarly named view in SystemUI.
        val sources = detectedSources.filter { isDescendant(container, it.view) }
        val kindCount = sources.map { it.kind }.filter { it != SourceKind.NETWORK_LABEL }.toSet().size
        if (kindCount < 2) {
            restoreAndRemove(root)
            Log.d(OneIBarModule.TAG, "Selected container contains fewer than two status indicators")
            return
        }
        val tint = findTint(sources, context)
        val group = if (existing != null && existing.parent === container) {
            existing
        } else {
            existing?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                groups.remove(it)
            }
            OneIBarStatusGroup(context).also { newGroup ->
                val insertionIndex = findInsertionIndex(container, sources)
                val params: ViewGroup.LayoutParams = when (container) {
                    is LinearLayout -> LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { gravity = Gravity.CENTER_VERTICAL }
                    is FrameLayout -> FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END or Gravity.CENTER_VERTICAL
                    )
                    else -> ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(newGroup, insertionIndex, params)
                groups[newGroup] = true
                Log.i(OneIBarModule.TAG, "Inserted native status-bar group into ${resourceName(container)}")
            }
        }

        group.apply(
            config = config,
            tint = tint,
            hasSignalSource = sources.any { it.kind == SourceKind.CELLULAR },
            hasWifiSource = sources.any { it.kind == SourceKind.WIFI },
            hasBatterySource = sources.any { it.kind == SourceKind.BATTERY }
        )
        groups[group] = true
        sources.forEach { source ->
            if (source.view !== group && !isDescendant(group, source.view)) {
                if (source.view.getTag(com.avolu.oneibar.R.id.oneibar_original_visibility) == null) {
                    source.view.setTag(com.avolu.oneibar.R.id.oneibar_original_visibility, source.view.visibility)
                }
                source.view.visibility = View.GONE
            }
        }
    }

    fun animateQuickSettings(expanded: Boolean) {
        synchronized(groups) { groups.keys.toList().forEach { it.animateQuickSettings(expanded) } }
    }

    fun animateStatusBar(visible: Boolean) {
        synchronized(groups) { groups.keys.toList().forEach { it.animateStatusBar(visible) } }
    }

    fun animateIndicatorRefresh() {
        synchronized(groups) { groups.keys.toList().forEach { it.animateIndicatorRefresh() } }
    }

    private fun ensureObservers(context: Context) {
        if (observerRegistered) return
        synchronized(this) {
            if (observerRegistered) return
            val appContext = context.applicationContext
            observerContext = appContext
            val handler = Handler(Looper.getMainLooper())
            contentObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = reloadAndApply()
            }
            try {
                appContext.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(OneIBarConfig.SECURE_KEY), false, contentObserver!!
                )
            } catch (t: Throwable) {
                Log.w(OneIBarModule.TAG, "Could not register config observer", t)
            }

            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    refreshRegisteredGroups()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(batteryReceiver, filter)
                }
            } catch (t: Throwable) {
                Log.w(OneIBarModule.TAG, "Could not register status event receiver", t)
            }
            registerTelephonyEvents(appContext)
            observerRegistered = true
        }
    }

    @Suppress("NewApi")
    private fun registerTelephonyEvents(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DisplayInfoListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    refreshRegisteredGroups()
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    networkOverrideType = telephonyDisplayInfo.overrideNetworkType
                    refreshRegisteredGroups()
                }
            }
            manager.registerTelephonyCallback(context.mainExecutor, callback)
            telephonyManager = manager
            telephonyCallback = callback
        } catch (t: Throwable) {
            Log.w(OneIBarModule.TAG, "Telephony callbacks unavailable; SystemUI hooks and broadcasts remain active", t)
        }
    }

    fun prepareForHotReload() {
        synchronized(this) {
            try { contentObserver?.let { observerContext?.contentResolver?.unregisterContentObserver(it) } } catch (_: Throwable) { }
            try { batteryReceiver?.let { observerContext?.unregisterReceiver(it) } } catch (_: Throwable) { }
            try {
                val callback = telephonyCallback
                if (callback != null) telephonyManager?.unregisterTelephonyCallback(callback)
            } catch (_: Throwable) { }
            contentObserver = null
            batteryReceiver = null
            observerContext = null
            telephonyCallback = null
            telephonyManager = null
            networkOverrideType = 0
            observerRegistered = false
            synchronized(roots) { roots.clear() }
            synchronized(groups) { groups.clear() }
        }
    }

    private fun reloadAndApply() {
        val rootSnapshot = synchronized(roots) { roots.keys.toList() }
        if (rootSnapshot.isEmpty()) return
        config = OneIBarConfig.readFromSystem(rootSnapshot.first().context)
        rootSnapshot.forEach { root ->
            try { refresh(root) } catch (t: Throwable) {
                Log.w(OneIBarModule.TAG, "Could not refresh saved status-bar root", t)
            }
        }
    }

    private fun refreshRegisteredGroups() {
        val rootSnapshot = synchronized(roots) { roots.keys.toList() }
        if (rootSnapshot.isNotEmpty()) {
            config = OneIBarConfig.readFromSystem(rootSnapshot.first().context)
        }
        rootSnapshot.forEach { root ->
            try { refresh(root) } catch (t: Throwable) {
                Log.d(OneIBarModule.TAG, "Group event refresh skipped", t)
            }
        }
        synchronized(groups) {
            groups.keys.toList().forEach { group ->
                group.animateIndicatorRefresh()
                group.currentTint()
                group.getChildAt(0)?.invalidate()
                group.getChildAt(1)?.invalidate()
                group.getChildAt(2)?.invalidate()
                group.getChildAt(3)?.invalidate()
            }
        }
    }


    /** Removes only our tagged group and restores every stock view property we changed. */
    private fun restoreAndRemove(root: ViewGroup) {
        val nodes = collectNodes(root)
        nodes.filter { it.tag == OneIBarStatusGroup.GROUP_TAG }.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            (view as? OneIBarStatusGroup)?.let { groups.remove(it) }
        }
        nodes.forEach { view ->
            val original = view.getTag(com.avolu.oneibar.R.id.oneibar_original_visibility) as? Int
            if (original != null) {
                view.visibility = original
                view.setTag(com.avolu.oneibar.R.id.oneibar_original_visibility, null)
            }
            if (view is TextView) restoreClock(view)
        }
    }

    private fun tuneClock(nodes: List<View>, config: OneIBarConfig) {
        val clocks = nodes.filterIsInstance<TextView>().filter { view ->
            val id = resourceName(view).lowercase()
            val className = view.javaClass.simpleName.lowercase()
            (id == "clock" || id.contains("status_bar_clock") || id.contains("statusbar_clock") || className == "clock") &&
                !id.contains("alarm") && !id.contains("lockscreen") && !id.contains("keyguard")
        }
        clocks.forEach { clock ->
            if (!config.styleClock) {
                restoreClock(clock)
                return@forEach
            }
            if (clock.getTag(com.avolu.oneibar.R.id.oneibar_original_clock_size) == null) {
                clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_size, clock.textSize)
                clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_typeface, clock.typeface)
                clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_padding, clock.includeFontPadding)
                clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_gravity, clock.gravity)
            }
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f * config.clockScalePercent / 100f)
            clock.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            clock.includeFontPadding = false
            clock.gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun restoreClock(clock: TextView) {
        val size = clock.getTag(com.avolu.oneibar.R.id.oneibar_original_clock_size) as? Float ?: return
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        val typeface = clock.getTag(com.avolu.oneibar.R.id.oneibar_original_clock_typeface) as? Typeface
        if (typeface != null) clock.typeface = typeface
        val padding = clock.getTag(com.avolu.oneibar.R.id.oneibar_original_clock_padding) as? Boolean
        if (padding != null) clock.includeFontPadding = padding
        val gravity = clock.getTag(com.avolu.oneibar.R.id.oneibar_original_clock_gravity) as? Int
        if (gravity != null) clock.gravity = gravity
        clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_size, null)
        clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_typeface, null)
        clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_padding, null)
        clock.setTag(com.avolu.oneibar.R.id.oneibar_original_clock_gravity, null)
    }

    private data class SourceView(val view: View, val kind: SourceKind)
    private enum class SourceKind { CELLULAR, WIFI, BATTERY, NETWORK_LABEL }

    private fun classifySource(view: View): SourceKind? {
        if (view.tag == OneIBarStatusGroup.GROUP_TAG) return null
        val id = resourceName(view).lowercase()
        val type = view.javaClass.simpleName.lowercase()
        val key = "$id $type"
        return when {
            key.contains("battery") && !key.contains("percentage") -> SourceKind.BATTERY
            key.contains("wifi") || key.contains("wi_fi") || key.contains("wlan") -> SourceKind.WIFI
            key.contains("mobile_type") || key.contains("network_type") || key.contains("mobile_data_type") -> SourceKind.NETWORK_LABEL
            key.contains("mobile_signal") || key.contains("signal_strength") ||
                key.contains("cellular_signal") || key.contains("statusbarmobileview") || (view is ImageView &&
                (type.contains("signalstrength") || type.contains("mobilesignal"))) -> SourceKind.CELLULAR
            else -> null
        }
    }

    private fun findContainer(nodes: List<View>, sources: List<SourceView>): ViewGroup? {
        val preferred = setOf(
            "system_icon_area", "system_icons", "status_icons", "status_icon_area",
            "status_bar_contents", "statusbar_contents", "system_icons_container", "status_icon_container"
        )
        val primary = sources.filter { it.kind != SourceKind.NETWORK_LABEL }
            .groupBy { it.kind }.values.mapNotNull { it.firstOrNull()?.view }
        if (primary.size < 2) return null

        val candidates = nodes.filterIsInstance<ViewGroup>().filter { candidate ->
            resourceName(candidate).lowercase() in preferred && primary.all { isDescendant(candidate, it) }
        }
        val selected = candidates.maxByOrNull { depth(it) }
        if (selected != null) return selected

        // Fallback to the nearest common ancestor of one representative per icon kind.
        // This avoids selecting the decor root just because QS also contains battery/Wi-Fi views.
        var ancestor = primary.first().parent as? ViewGroup
        while (ancestor != null) {
            if (primary.all { isDescendant(ancestor, it) }) return ancestor
            ancestor = ancestor.parent as? ViewGroup
        }
        return null
    }

    private fun findInsertionIndex(container: ViewGroup, sources: List<SourceView>): Int {
        var first = container.childCount
        for (source in sources) {
            var direct: View = source.view
            while (direct.parent != null && direct.parent !== container) {
                direct = direct.parent as? View ?: break
            }
            if (direct.parent === container) {
                val index = container.indexOfChild(direct)
                if (index >= 0) first = minOf(first, index)
            }
        }
        return first.coerceIn(0, container.childCount)
    }

    private fun findTint(sources: List<SourceView>, context: Context): Int {
        for (source in sources) {
            val image = source.view as? ImageView ?: continue
            val tint = image.imageTintList?.defaultColor
            if (tint != null && Color.alpha(tint) > 0) return tint
        }
        return if (isNightMode(context)) Color.WHITE else Color.BLACK
    }

    private fun collectNodes(root: View): List<View> {
        val result = ArrayList<View>()
        fun walk(view: View, depth: Int) {
            if (depth > 18) return
            result.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i), depth + 1)
            }
        }
        walk(root, 0)
        return result
    }

    private fun isDescendant(parent: View, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }

    private fun depth(view: View): Int {
        var count = 0
        var current: View? = view
        while (current != null) { count++; current = current.parent as? View }
        return count
    }

    private fun resourceName(view: View): String = try {
        if (view.id == View.NO_ID) "" else view.resources.getResourceEntryName(view.id)
    } catch (_: Throwable) { "" }

    private fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}
