package com.andrerinas.openheadunit.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.app.BaseActivity
import com.andrerinas.openheadunit.ota.OtaController
import com.andrerinas.openheadunit.utils.AllgoPackage
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.andrerinas.openheadunit.utils.CompatibilityReport
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.SteeringKeyLog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.io.File

/**
 * DiAuto for MG4 settings: five tabs on the left, each a short list of large cards. Every change is
 * saved at once; the ones read when Android Auto connects say so in their text.
 */
class DaSettingsActivity : BaseActivity() {

    private val settings get() = App.provide(this).settings
    private lateinit var tabBox: LinearLayout
    private lateinit var list: LinearLayout
    private var selectedTab = 0
    private var ota: OtaController? = null
    private var otaStatus: OtaController.Status? = null

    private enum class Tab(val label: Int, val icon: Int) {
        CONNECTION(R.string.dset_tab_connection, R.drawable.ic_wifi),
        DISPLAY(R.string.dset_tab_display, R.drawable.ic_da_display),
        SOUND(R.string.dset_tab_sound, R.drawable.ic_volume),
        CAR(R.string.dset_tab_car, R.drawable.ic_directions_car),
        ABOUT(R.string.dset_tab_about, R.drawable.ic_da_about),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.da_settings)
        tabBox = findViewById(R.id.settings_tabs)
        list = findViewById(R.id.settings_list)
        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }
        selectedTab = savedInstanceState?.getInt(KEY_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        ota = OtaController(this).also { controller ->
            controller.setStateListener { status ->
                otaStatus = status
                if (Tab.values()[selectedTab] == Tab.ABOUT) showTab(selectedTab)
            }
        }
        showTab(selectedTab)
    }

    override fun onResume() {
        super.onResume()
        ota?.checkOnStartup()
    }

    override fun onDestroy() {
        ota?.stop()
        ota = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedTab)
    }

    private fun showTab(index: Int) {
        selectedTab = index
        buildTabs()
        list.removeAllViews()
        when (Tab.values()[index]) {
            Tab.CONNECTION -> connectionTab()
            Tab.DISPLAY -> displayTab()
            Tab.SOUND -> soundTab()
            Tab.CAR -> carTab()
            Tab.ABOUT -> aboutTab()
        }
    }

    private fun buildTabs() {
        tabBox.removeAllViews()
        val accent = ContextCompat.getColor(this, R.color.da_accent)
        val text = ContextCompat.getColor(this, R.color.da_text)
        Tab.values().forEachIndexed { i, tab ->
            val view = layoutInflater.inflate(R.layout.da_settings_tab, tabBox, false)
            val selected = i == selectedTab
            view.findViewById<ImageView>(R.id.tab_icon).apply {
                setImageResource(tab.icon)
                if (selected) imageTintList = ColorStateList.valueOf(accent)
            }
            view.findViewById<TextView>(R.id.tab_label).apply {
                setText(tab.label)
                if (selected) setTextColor(text)
            }
            if (selected) view.setBackgroundResource(R.drawable.da_tab_selected)
            view.setOnClickListener { if (i != selectedTab) showTab(i) }
            tabBox.addView(view)
        }
    }

    // --- Tabs ---------------------------------------------------------------------------------

    private fun connectionTab() {
        val wireless = settings.connectionModes.contains(Settings.ConnectionMode.WIFI)
        row(R.string.dset_wireless, R.string.dset_wireless_summary, yesNo(wireless) { on ->
            val modes = settings.connectionModes.toMutableSet()
            if (on) modes.add(Settings.ConnectionMode.WIFI) else modes.remove(Settings.ConnectionMode.WIFI)
            modes.add(Settings.ConnectionMode.USB)
            settings.connectionModes = modes
        })
        row(R.string.dset_start_with_car, R.string.dset_start_with_car_summary, yesNo(settings.autoStartOnBoot) {
            settings.autoStartOnBoot = it
        })
        val phone = settings.autoStartBluetoothDeviceName.ifBlank { getString(R.string.dset_phone_none) }
        row(R.string.dset_phone, R.string.dset_phone_summary, value(phone) { pickPhone() })
    }

    private fun displayTab() {
        row(R.string.dset_fps, R.string.dset_fps_summary,
            choices(listOf("30", "60"), if (settings.fpsLimit == 60) 1 else 0) {
                settings.fpsLimit = if (it == 1) 60 else 30
                appliesOnReconnect()
            })
        val sizes = listOf(DPI_SMALL, DPI_AUTO, DPI_LARGE)
        val size = sizes.indexOf(settings.dpiPixelDensity).takeIf { it >= 0 } ?: 1
        row(R.string.dset_ui_size, R.string.dset_ui_size_summary,
            choices(listOf(getString(R.string.dset_small), getString(R.string.dset_normal), getString(R.string.dset_large)), size) {
                settings.dpiPixelDensity = sizes[it]
                appliesOnReconnect()
            })
        val modes = listOf(Settings.NightMode.AUTO, Settings.NightMode.DAY, Settings.NightMode.NIGHT)
        val mode = modes.indexOf(settings.nightMode).takeIf { it >= 0 } ?: 0
        row(R.string.dset_night, R.string.dset_night_summary,
            choices(listOf(getString(R.string.dset_night_car), getString(R.string.dset_day), getString(R.string.dset_night_only)), mode) {
                settings.nightMode = modes[it]
                sendBroadcast(Intent(com.andrerinas.openheadunit.aap.AapService.ACTION_REQUEST_NIGHT_MODE_UPDATE).setPackage(packageName))
            })
    }

    private fun soundTab() {
        row(R.string.dset_separate, R.string.dset_separate_summary, yesNo(settings.separateAudioStreams) {
            settings.separateAudioStreams = it
            appliesOnReconnect()
        })
        row(R.string.dset_voice_volume, R.string.dset_voice_volume_summary, level(settings.assistantVolumeOffset) {
            settings.assistantVolumeOffset = it
            settings.navigationVolumeOffset = it
        })
        row(R.string.dset_bt_music, R.string.dset_bt_music_summary, yesNo(settings.musicViaBluetooth) {
            settings.musicViaBluetooth = it
            appliesOnReconnect()
        })
        val buffers = listOf(500, 1000, 1500)
        val buffer = buffers.indexOf(settings.mediaAudioBufferMillis).takeIf { it >= 0 } ?: 1
        row(R.string.dset_buffer, R.string.dset_buffer_summary,
            choices(listOf("0,5 s", "1 s", "1,5 s"), buffer) { settings.mediaAudioBufferMillis = buffers[it] })
    }

    private fun carTab() {
        val capacity = if (settings.batteryCapacityManual)
            getString(R.string.dset_battery_manual, settings.batteryNetCapacityKwh)
            else getString(R.string.dset_auto)
        row(R.string.dset_battery, R.string.dset_battery_summary, value(capacity) {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_DESTINATION, R.id.chargingInfoFragment))
        })
        row(R.string.dset_maps_range, R.string.dset_maps_range_summary, yesNo(settings.batteryForGoogleMaps) {
            settings.batteryForGoogleMaps = it
        })
        row(R.string.dset_variant, R.string.dset_variant_summary, value(variantLabel()) { pickVariant() })
        row(R.string.dset_ev_layout, R.string.dset_ev_layout_summary, value(evLayoutLabel(settings.evModelLayout)) { pickEvLayout() })
        row(R.string.dset_preheat, R.string.dset_preheat_summary, yesNo(settings.batteryPreheatBeforeCharger) {
            settings.batteryPreheatBeforeCharger = it
        })
        row(R.string.dset_preheat_lead, R.string.dset_preheat_lead_summary,
            value(getString(R.string.dset_minutes, settings.batteryPreheatMinutes)) { pickPreheatLead() })
        val keys = SteeringKeyLog.recent().takeLast(5).joinToString(" · ").ifEmpty { "—" }
        row(R.string.dset_wheel_keys, R.string.dset_wheel_keys_summary, value(keys, null))
    }

    private fun aboutTab() {
        val version = BuildConfig.VERSION_NAME.removeSuffix("-debug").removeSuffix("-phone")
        val status = otaStatus
        val available = status?.state == OtaController.State.AVAILABLE
        val summary = when (status?.state) {
            OtaController.State.CHECKING -> getString(R.string.da_update_hint_checking)
            OtaController.State.AVAILABLE -> getString(R.string.da_update_install, status.latestVersion)
            OtaController.State.UP_TO_DATE -> getString(R.string.dset_up_to_date)
            OtaController.State.FAILED -> getString(R.string.dset_update_failed)
            else -> ""
        }
        rowText(getString(R.string.dset_version, getString(R.string.da_brand), version), summary,
            button(getString(if (available) R.string.dset_install else R.string.da_update)) { ota?.checkOrDownload() })
        row(R.string.dset_betas, R.string.dset_betas_summary, yesNo(ota?.isAllowBeta == true) {
            ota?.setAllowBeta(it)
        })
        row(R.string.compat_report, R.string.compat_report_description,
            button(getString(R.string.dset_open)) { CompatibilityReport.show(this) })
        row(R.string.dset_logs, R.string.dset_logs_summary, button(getString(R.string.dset_export)) { exportLogs() })
        row(R.string.restore_allgo, R.string.dset_restore_summary, button(getString(R.string.dset_restore), danger = true) {
            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.restore_allgo)
                .setMessage(R.string.restore_allgo_message)
                .setPositiveButton(R.string.restore_allgo_confirm) { _, _ -> AllgoPackage.restoreAllgo(this) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })
    }

    // --- Actions ------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun variantLabel(): String {
        val chosen = com.andrerinas.openheadunit.vehicle.Mg4Variant.entries.firstOrNull { it.key == settings.mg4Variant }
        if (chosen != null) return chosen.label
        val pack = com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.snapshot()?.capacityWh
        return if (pack == null) getString(R.string.dset_auto)
        else getString(R.string.dset_variant_auto, com.andrerinas.openheadunit.vehicle.Mg4Variant.detect(pack).label)
    }

    private fun pickVariant() {
        val variants = com.andrerinas.openheadunit.vehicle.Mg4Variant.entries
        val labels = listOf(getString(R.string.dset_auto)) + variants.map { it.label }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_variant)
            .setItems(labels.toTypedArray()) { _, i ->
                settings.mg4Variant = if (i == 0) com.andrerinas.openheadunit.vehicle.Mg4Variant.AUTO else variants[i - 1].key
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun evLayoutLabel(key: String): String {
        val layouts = com.andrerinas.openheadunit.vehicle.VehicleEnergyModelEncoder.Layout.entries
        val i = layouts.indexOfFirst { it.key == key }.coerceAtLeast(0)
        return getString(EV_LAYOUT_LABELS[i])
    }

    /** Temporary: lets the driver compare what Maps plans with each model layout. */
    private fun pickEvLayout() {
        val layouts = com.andrerinas.openheadunit.vehicle.VehicleEnergyModelEncoder.Layout.entries
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_ev_layout)
            .setItems(EV_LAYOUT_LABELS.map { getString(it) }.toTypedArray()) { _, i ->
                settings.evModelLayout = layouts[i].key
                appliesOnReconnect()
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickPreheatLead() {
        val minutes = (5..60 step 5).toList()
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_preheat_lead)
            .setItems(minutes.map { getString(R.string.dset_minutes, it) }.toTypedArray()) { _, i ->
                settings.batteryPreheatMinutes = minutes[i]
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickPhone() {
        val bonded = try {
            BluetoothHelper.getBluetoothAdapter(this)?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        } catch (_: SecurityException) {
            emptyList()
        }
        if (bonded.isEmpty()) {
            Toast.makeText(this, R.string.da_no_paired, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_phone)
            .setItems(bonded.map { it.name ?: getString(R.string.da_device) }.toTypedArray()) { _, i ->
                val device = bonded[i]
                settings.autoStartBluetoothDeviceMacs = setOf(device.address)
                settings.autoStartBluetoothDeviceName = device.name ?: getString(R.string.da_device)
                Settings.syncAutoStartBtMacsToDeviceStorage(this, setOf(device.address))
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** The current log file, copied to Download/ where a USB stick or a file manager can reach it. */
    private fun exportLogs() {
        val source = AppLog.currentLogFile ?: AppLog.lastLogFile
        if (source == null || !source.exists()) {
            Toast.makeText(this, R.string.dset_logs_none, Toast.LENGTH_LONG).show()
            return
        }
        val target = runCatching {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, "DiAuto-" + source.name).also { source.copyTo(it, overwrite = true) }
        }.getOrNull()
        Toast.makeText(this,
            getString(R.string.dset_logs_saved, (target ?: source).absolutePath), Toast.LENGTH_LONG).show()
    }

    // --- Rows and controls --------------------------------------------------------------------

    /** The phone reads this setting when it connects: ask to reconnect if a session is running. */
    private fun appliesOnReconnect() {
        if (App.provide(this).commManager.isConnected) reconnectNeeded = true
    }

    private fun row(title: Int, summary: Int, control: View) = rowText(getString(title), getString(summary), control)

    private fun rowText(title: String, summary: String, control: View) {
        val v = layoutInflater.inflate(R.layout.da_settings_row, list, false)
        v.findViewById<TextView>(R.id.row_title).text = title
        v.findViewById<TextView>(R.id.row_summary).apply {
            text = summary
            visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<FrameLayout>(R.id.row_control).addView(control)
        list.addView(v)
    }

    private fun yesNo(on: Boolean, onChange: (Boolean) -> Unit): View =
        choices(listOf(getString(R.string.dset_no), getString(R.string.dset_yes)), if (on) 1 else 0) { onChange(it == 1) }

    private fun choices(labels: List<String>, selected: Int, onChange: (Int) -> Unit): View {
        val accent = ContextCompat.getColor(this, R.color.da_accent)
        val surface = ContextCompat.getColor(this, R.color.da_surface)
        val dark = ContextCompat.getColor(this, R.color.da_background)
        val text = ContextCompat.getColor(this, R.color.da_text)
        val group = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        val buttons = labels.mapIndexed { i, label ->
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                this.text = label
                isAllCaps = false
                textSize = 19f
                minWidth = dp(130)
                minimumHeight = dp(72)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.da_border))
            }.also { group.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(72))) }
        }
        fun paint(sel: Int) = buttons.forEachIndexed { i, b ->
            b.backgroundTintList = ColorStateList.valueOf(if (i == sel) accent else surface)
            b.setTextColor(if (i == sel) dark else text)
        }
        paint(selected)
        buttons.forEachIndexed { i, b ->
            b.setOnClickListener {
                paint(i)
                onChange(i)
            }
        }
        return group
    }

    private fun value(text: String, onClick: (() -> Unit)?): View = TextView(this).apply {
        this.text = if (onClick != null) "$text  ›" else text
        textSize = 21f
        setTextColor(ContextCompat.getColor(context, R.color.da_accent))
        setPadding(dp(16), dp(20), dp(8), dp(20))
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun button(text: String, danger: Boolean = false, onClick: () -> Unit): View = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 20f
        minWidth = dp(200)
        minimumHeight = dp(72)
        cornerRadius = dp(22)
        setTextColor(if (danger) 0xFFFFFFFF.toInt() else ContextCompat.getColor(context, R.color.da_background))
        backgroundTintList = ColorStateList.valueOf(
            if (danger) 0xFFB3404A.toInt() else ContextCompat.getColor(context, R.color.da_accent))
        setOnClickListener { onClick() }
    }

    /** -50 % … +200 % around the music level, in 10 % steps. */
    private fun level(offset: Int, onChange: (Int) -> Unit): View = Slider(this).apply {
        valueFrom = -50f
        valueTo = Settings.MAX_VOICE_VOLUME_OFFSET.toFloat()
        stepSize = 10f
        value = offset.coerceIn(-50, Settings.MAX_VOICE_VOLUME_OFFSET).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT)
        val accent = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.da_accent))
        thumbTintList = accent
        trackActiveTintList = accent
        trackInactiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.da_border))
        setLabelFormatter { v -> "%+d %%".format(v.toInt()) }
        addOnChangeListener { _, v, fromUser -> if (fromUser) onChange(v.toInt()) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        /**
         * Set when a setting that the phone only reads at connection time changed during a session;
         * the projection screen offers to reconnect when it comes back.
         */
        @Volatile var reconnectNeeded = false

        const val EXTRA_TAB = "tab"
        private val EV_LAYOUT_LABELS = intArrayOf(
            R.string.dset_ev_layout_a, R.string.dset_ev_layout_b, R.string.dset_ev_layout_c,
            R.string.dset_ev_layout_d, R.string.dset_ev_layout_e,
        )
        private const val KEY_TAB = "tab"
        private const val DPI_SMALL = 200
        private const val DPI_AUTO = 0
        private const val DPI_LARGE = 260

        fun intent(context: Context, tab: Int = 0): Intent =
            Intent(context, DaSettingsActivity::class.java).putExtra(EXTRA_TAB, tab)
    }
}
