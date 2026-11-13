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
import kotlin.math.roundToInt

/**
 * DiAuto for MG4 settings: five tabs on the left, each a short list of large cards. Every change is
 * saved at once; the ones read when Android Auto connects say so in their text.
 */
class DaSettingsActivity : BaseActivity() {

    private val settings get() = App.provide(this).settings
    private lateinit var tabBox: LinearLayout
    private lateinit var list: LinearLayout
    private var selectedTab = 0
    /** Every tab: advanced options folded away until asked for, each time Settings opens. */
    private var showAdvanced = false
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
        selectedTab = savedInstanceState?.getInt(KEY_TAB) ?: intent.getIntExtra(EXTRA_TAB, lastTab)
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
        list.removeCallbacks(batteryRecheck)
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
        lastTab = index
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
        // One choice for both: No starts nothing until DiAuto is opened, In the background connects
        // without taking the screen, Yes connects and takes it.
        val startLabels = listOf(R.string.dset_no, R.string.dset_open_background, R.string.dset_yes).map { getString(it) }
        val start = when {
            !settings.autoStartOnBoot -> 0
            settings.openProjectionOnConnect -> 2
            else -> 1
        }
        row(R.string.dset_start_with_car, R.string.dset_start_with_car_summary, choices(startLabels, start) {
            settings.autoStartOnBoot = it > 0
            if (it > 0) settings.openProjectionOnConnect = it == 2
        })
        row(R.string.dset_phones, R.string.dset_phones_summary, value(phonesLabel()) { pickPhone() })
        row(R.string.dset_usb_wifi, R.string.dset_usb_wifi_summary, yesNo(settings.usbWifiClient) {
            settings.usbWifiClient = it
        })
        // Fine-tuning most drivers never need: folded away so the tab stays short.
        if (!advancedRow(R.string.dset_advanced_summary)) return
        val firstCalls = listOf(0, 5, 15, 30)
        row(R.string.dset_first_call_delay, R.string.dset_first_call_delay_summary,
            choices(firstCalls.map { "$it s" }, firstCalls.indexOf(settings.firstPhoneCallDelaySeconds).takeIf { it >= 0 } ?: 2) {
                settings.firstPhoneCallDelaySeconds = firstCalls[it]
            })
        val delays = listOf(10, 30, 60, 120, 300)
        val delay = delays.indexOf(settings.stationRestoreDelaySeconds).takeIf { it >= 0 } ?: 2
        row(R.string.dset_station_delay, R.string.dset_station_delay_summary,
            choices(listOf("10 s", "30 s", "1 min", "2 min", "5 min"), delay) {
                settings.stationRestoreDelaySeconds = delays[it]
            })
        wifiChannelRow()
    }

    /** The "Advanced options" row that folds the rest of the tab away; true when it is open. */
    private fun advancedRow(summary: Int = R.string.dset_advanced_summary_general): Boolean {
        row(R.string.dset_advanced, summary,
            value(getString(if (showAdvanced) R.string.dset_advanced_hide else R.string.dset_advanced_show)) {
                showAdvanced = !showAdvanced
                showTab(selectedTab)
            })
        return showAdvanced
    }

    /** In Auto, the channel in use is shown greyed after the row's title. */
    private fun wifiChannelRow() {
        val channels = listOf(0) + com.andrerinas.openheadunit.utils.WifiChannelPicker.CHANNELS
        val title = getString(R.string.dset_wifi_channel)
        val titleText: CharSequence = if (settings.wifiChannel == 0) {
            val current = com.andrerinas.openheadunit.utils.WifiChannelPicker.configuredChannel(this).toString()
            android.text.SpannableString("$title  $current").apply {
                setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@DaSettingsActivity, R.color.da_muted)),
                    title.length, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else title
        rowText(titleText, getString(R.string.dset_wifi_channel_summary),
            choices(listOf(getString(R.string.dset_wifi_channel_auto)) + channels.drop(1).map { it.toString() }, channels.indexOf(settings.wifiChannel)) {
                settings.wifiChannel = channels[it]
                // Applied now when no phone is on the access point, else at its next start.
                if (!App.provide(this).commManager.isConnected) {
                    val app = applicationContext
                    Thread { com.andrerinas.openheadunit.utils.HotspotManager.restart(app) }.start()
                }
                showTab(selectedTab)
            })
    }

    private fun displayTab() {
        val sizes = listOf(DPI_SMALL, DPI_AUTO, DPI_LARGE)
        val size = sizes.indexOf(settings.dpiPixelDensity).takeIf { it >= 0 } ?: 1
        row(R.string.dset_ui_size, R.string.dset_ui_size_summary,
            choices(listOf(getString(R.string.dset_small), getString(R.string.dset_normal), getString(R.string.dset_large)), size) {
                settings.dpiPixelDensity = sizes[it]
                appliesOnReconnect()
            })
        val modes = listOf(Settings.NightMode.AUTO, Settings.NightMode.SUN, Settings.NightMode.DAY, Settings.NightMode.NIGHT)
        val mode = modes.indexOf(settings.nightMode).takeIf { it >= 0 } ?: 0
        row(R.string.dset_night, R.string.dset_night_summary_modes,
            choices(listOf(R.string.dset_night_car, R.string.dset_night_sun, R.string.dset_day, R.string.dset_night_only).map { getString(it) }, mode) {
                settings.nightMode = modes[it]
                sendBroadcast(Intent(com.andrerinas.openheadunit.aap.AapService.ACTION_REQUEST_NIGHT_MODE_UPDATE).setPackage(packageName))
            })
        if (!advancedRow()) return
        row(R.string.dset_car_pulldown, R.string.dset_car_pulldown_summary, yesNo(settings.carPullDownOverAndroidAuto) {
            settings.carPullDownOverAndroidAuto = it
        })
        row(R.string.dset_fps, R.string.dset_fps_summary,
            choices(listOf("30", "60"), if (settings.fpsLimit == 60) 1 else 0) {
                settings.fpsLimit = if (it == 1) 60 else 30
                appliesOnReconnect()
            })
    }

    private fun soundTab() {
        row(R.string.dset_separate, R.string.dset_separate_summary, yesNo(settings.separateAudioStreams) {
            settings.separateAudioStreams = it
            appliesOnReconnect()
            showTab(selectedTab)
        })
        // On its own channel the voice can be cleaned up instead (from the next prompt on).
        // Voice processing is still being tested: only in test mode (Settings > About).
        if (settings.testMode && (settings.separateAudioStreams || settings.musicViaBluetooth)) {
            note(R.string.dset_voice_experimental)
            row(R.string.dset_voice_enhance, R.string.dset_voice_enhance_summary,
                choices(listOf(R.string.dset_voice_enhance_standard, R.string.dset_voice_enhance_light,
                    R.string.dset_voice_enhance_strong, R.string.dset_voice_enhance_neural).map { getString(it) }, settings.voiceEnhancement) {
                    settings.voiceEnhancement = it
                })
            row(R.string.dset_voice_clarity, R.string.dset_voice_clarity_summary, yesNo(settings.voiceClarity) {
                settings.voiceClarity = it
            })
            val pads = listOf(0, 3, 6)
            row(R.string.dset_voice_headroom, R.string.dset_voice_headroom_summary,
                choices(listOf("0 dB", "-3 dB", "-6 dB"), pads.indexOf(settings.voiceHeadroomDb).coerceAtLeast(0)) {
                    settings.voiceHeadroomDb = pads[it]
                })
        }
        // Only mixed with the music can the voice be boosted (see AapAudio).
        if (!settings.separateAudioStreams && !settings.musicViaBluetooth) {
            row(R.string.dset_voice_volume, R.string.dset_voice_volume_summary, level(settings.assistantVolumeOffset) {
                settings.assistantVolumeOffset = it
                settings.navigationVolumeOffset = it
                // The level is read when the voice channels open, at connection.
                appliesOnReconnect()
            })
        }
        row(R.string.dset_bt_music, R.string.dset_bt_music_summary, yesNo(settings.musicViaBluetooth) {
            settings.musicViaBluetooth = it
            appliesOnReconnect()
            showTab(selectedTab)
        })
        if (!advancedRow()) return
        if (!settings.musicViaBluetooth) {
            row(R.string.dset_music_pcm, R.string.dset_music_pcm_summary, yesNo(!settings.useAacAudio) {
                settings.useAacAudio = !it
                appliesOnReconnect()
            })
        }
        val buffers = listOf(500, 1000, 1500)
        val buffer = buffers.indexOf(settings.mediaAudioBufferMillis).takeIf { it >= 0 } ?: 1
        row(R.string.dset_buffer, R.string.dset_buffer_summary,
            choices(listOf("0,5 s", "1 s", "1,5 s"), buffer) { settings.mediaAudioBufferMillis = buffers[it] })
    }

    private fun carTab() {
        // Each choice is saved at once; the reset button puts everything back on automatic.
        val battery = com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.current(this)
        // One choice for all: the version gives Maps its capacity, charging curve and consumption model.
        com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.snapshot()?.takeIf { !it.demo }?.let { snap ->
            rowText(getString(R.string.battery_live_from_car), "", value(
                "${snap.batteryPercent.roundToInt()} % · " + getString(R.string.battery_value_km, snap.rangeKm) + " · " +
                    getString(R.string.battery_live_energy_value, snap.currentWh / 1000.0, snap.capacityWh / 1000.0),
                null,
            ))
        }
        row(R.string.dset_maps_range, R.string.dset_maps_range_summary, yesNo(settings.batteryForGoogleMaps) {
            settings.batteryForGoogleMaps = it
        })
        // Features being tested: only in test mode (Settings > About).
        if (settings.testMode) {
            row(R.string.dset_ev_layout, R.string.dset_ev_layout_summary, value(evLayoutLabel(settings.evModelLayout)) { pickEvLayout() })
            // 720p crops far less of the 1920x640 picture than 1080p (#23); 1080p stays the default.
            val res = listOf(Settings.Resolution._1920x1080, Settings.Resolution._1280x720)
            row(R.string.dset_video_res, R.string.dset_video_res_summary,
                choices(listOf("1080p", "720p"), if (settings.resolutionId == Settings.Resolution._1280x720.id) 1 else 0) {
                    settings.resolutionId = res[it].id
                    appliesOnReconnect()
                })
        }
        // On by default; off goes back to the car's average. Switching it on first explains what it does.
        row(R.string.trip_option, R.string.trip_option_summary, yesNo(settings.tripRecording) {
            settings.tripRecording = it
            showTab(selectedTab)
        })
        if (settings.tripRecording) {
            rowText(getString(R.string.trip_start), getString(R.string.trip_button_summary),
                button(getString(TripDialogs.buttonLabel(this))) { TripDialogs.toggle(this) { showTab(selectedTab) } })
            val saved = com.andrerinas.openheadunit.vehicle.TripRecorder.savedCount(this)
            rowText(getString(R.string.trip_files), getString(R.string.trip_files_summary, saved),
                button(getString(R.string.trip_copy)) { copyTripsToUsb() })
        }
        row(R.string.dset_maps_model, R.string.dset_maps_model_summary, yesNo(settings.mapsEnergyModel) { on ->
            if (!on) {
                settings.mapsEnergyModel = false
                com.andrerinas.openheadunit.vehicle.Mg4EnergyReporter.resend()
                return@yesNo
            }
            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.dset_maps_model)
                .setMessage(R.string.dset_maps_model_explain)
                .setPositiveButton(R.string.dset_maps_model_enable) { _, _ ->
                    settings.mapsEnergyModel = true
                    com.andrerinas.openheadunit.vehicle.Mg4EnergyReporter.resend()
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { showTab(selectedTab) }
                .show()
                .apply { findViewById<android.widget.TextView>(android.R.id.message)?.textSize = 26f }
        })
        if (settings.testMode && settings.mapsEnergyModel) {
            row(R.string.dset_maps_overlay, R.string.dset_maps_overlay_summary, yesNo(settings.mapsModelOverlay) {
                settings.mapsModelOverlay = it
            })
        }
        // ABRP live data over the car's T-box: off by default, the token is the user's.
        // Turning it on asks for the token first, with a test; cancelling leaves it off.
        row(R.string.dset_abrp, R.string.dset_abrp_summary, yesNo(settings.abrpEnabled) { on ->
            if (on) {
                abrpTokenDialog(enabling = true)
            } else {
                settings.abrpEnabled = false
                showTab(selectedTab)
            }
        })
        if (settings.abrpEnabled) {
            rowText(getString(R.string.dset_abrp_token), getString(R.string.dset_abrp_token_summary),
                value(masked(settings.abrpToken)) { abrpTokenDialog(enabling = false) })
            rowText(getString(R.string.dset_abrp_key), getString(R.string.dset_abrp_key_summary),
                value(masked(settings.abrpApiKey)) {
                    editText(R.string.dset_abrp_key, settings.abrpApiKey) { settings.abrpApiKey = it }
                })
        }
        if (com.andrerinas.openheadunit.vehicle.ChargeStopPreheatPolicy.supportedOnThisCar()) {
            row(R.string.dset_preheat, R.string.dset_preheat_summary, yesNo(settings.batteryPreheatBeforeCharger) {
                settings.batteryPreheatBeforeCharger = it
            })
            row(R.string.dset_preheat_lead, R.string.dset_preheat_lead_summary,
                value(getString(R.string.dset_minutes, settings.batteryPreheatMinutes)) { pickPreheatLead() })
        }
        if (!advancedRow()) return
        // Until the car gives its battery, the default 64 kWh pack would show wrong figures.
        val waiting = batteryWaiting(battery)
        list.removeCallbacks(batteryRecheck)
        if (waiting) list.postDelayed(batteryRecheck, BATTERY_RECHECK_MS)
        val waitText = getString(R.string.dset_battery_waiting)
        rowText(getString(R.string.dset_variant), getString(R.string.dset_variant_summary),
            value(if (waiting) waitText else variantLabel(battery)) { pickVariant() })
        rowText(getString(R.string.battery_net_capacity_kwh), getString(R.string.battery_net_capacity_enter),
            value(if (waiting) waitText else batteryCapacityText(this, battery)) { editCapacity(battery.netKwh) })
        rowText(getString(R.string.battery_soh_percent), getString(R.string.dset_soh_summary),
            value(if (waiting) waitText else batterySohText(this, battery)) { editSoh(battery.sohPercent) })
        val keys = SteeringKeyLog.recent().takeLast(5).joinToString(" · ").ifEmpty { "—" }
        row(R.string.dset_wheel_keys, R.string.dset_wheel_keys_summary, value(keys, null))
        row(R.string.dset_battery_reset, R.string.dset_battery_reset_summary,
            button(getString(R.string.dset_battery_reset_button)) { resetBattery() })
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
        row(R.string.tuto_settings_title, R.string.tuto_settings_summary,
            button(getString(R.string.tuto_settings_button)) { startActivity(DaTutorialActivity.intent(this)) })
        row(R.string.dset_logs, R.string.dset_logs_summary, button(getString(R.string.dset_export)) { exportLogs() })
        row(R.string.restore_allgo, R.string.dset_restore_summary, button(getString(R.string.dset_restore), danger = true) {
            MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
                .setTitle(R.string.restore_allgo)
                .setMessage(R.string.restore_allgo_message)
                .setPositiveButton(R.string.restore_allgo_confirm) { _, _ -> AllgoPackage.restoreAllgo(this) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })
        if (!advancedRow()) return
        row(R.string.compat_report, R.string.compat_report_description,
            button(getString(R.string.dset_open)) { CompatibilityReport.show(this) })
        row(R.string.dset_log_record, R.string.dset_log_record_summary, yesNo(settings.exporterCaptureEnabled) {
            settings.exporterCaptureEnabled = it
            AppLog.init(settings, applicationContext)
            showTab(selectedTab)
        })
        row(R.string.dset_test_mode, R.string.dset_test_mode_summary, yesNo(settings.testMode) {
            settings.testMode = it
            // The energy model sent to Maps may change with it.
            appliesOnReconnect()
        })
    }

    // --- Actions ------------------------------------------------------------------------------

    private fun batteryWaiting(battery: com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.Resolved): Boolean =
        com.andrerinas.openheadunit.vehicle.BatteryWaitPolicy.waiting(
            battery.netSource,
            com.andrerinas.openheadunit.vehicle.Mg4DashBattery.read(this)?.packChosen == true,
            com.evsuite.hardware.EVHardware.lastVehicleConfigFailure,
            android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime(),
        )

    /** While the Car tab waits for the car, redrawn once the battery is known (or the wait is over). */
    private val batteryRecheck: Runnable = object : Runnable {
        override fun run() {
            if (isFinishing || Tab.values()[selectedTab] != Tab.CAR || !showAdvanced) return
            val battery = com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.current(this@DaSettingsActivity)
            if (batteryWaiting(battery)) list.postDelayed(this, BATTERY_RECHECK_MS) else showTab(selectedTab)
        }
    }

    private fun variantLabel(battery: com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.Resolved): String {
        val chosen = com.andrerinas.openheadunit.vehicle.Mg4Variant.entries.firstOrNull { it.key == settings.mg4Variant }
        return chosen?.label ?: getString(R.string.dset_variant_auto,
            com.andrerinas.openheadunit.vehicle.Mg4Variant.detect((battery.netKwh * 1000).toInt()).label)
    }

    /** Sets the version and its usable capacity together; automatic follows the car for both. */
    private fun pickVariant() {
        val variants = com.andrerinas.openheadunit.vehicle.Mg4Variant.entries
        val labels = listOf(getString(R.string.dset_auto)) + variants.map { it.label }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_variant)
            .setItems(labels.toTypedArray()) { _, i ->
                if (i == 0) {
                    settings.mg4Variant = com.andrerinas.openheadunit.vehicle.Mg4Variant.AUTO
                    settings.batteryCapacityManual = false
                } else {
                    settings.mg4Variant = variants[i - 1].key
                    settings.batteryNetCapacityKwh = variants[i - 1].usableKwh
                    settings.batteryCapacityManual = true
                }
                com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
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

    private fun editCapacity(current: Float) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.getDefault(), "%.1f", current))
            textSize = 30f
            setSelectAllOnFocus(true)
            minWidth = dp(320)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(32), dp(16), dp(32), dp(8))
            addView(input)
        }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.battery_net_capacity_kwh)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().replace(',', '.').toFloatOrNull()?.let {
                    settings.batteryNetCapacityKwh = it.coerceIn(20f, 120f)
                    settings.batteryCapacityManual = true
                    com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
                }
                showTab(selectedTab)
            }
            .setNeutralButton(R.string.battery_auto) { _, _ ->
                settings.batteryCapacityManual = false
                settings.mg4Variant = com.andrerinas.openheadunit.vehicle.Mg4Variant.AUTO
                com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * The ABRP token, with a button that sends one reading now and says what ABRP answered.
     * [enabling]: opened by switching the option on, which only stays on once a token is saved.
     */
    private fun abrpTokenDialog(enabling: Boolean) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(settings.abrpToken)
            hint = getString(R.string.dset_abrp_token_hint)
            textSize = 24f
            setSelectAllOnFocus(true)
            minWidth = dp(560)
        }
        val result = TextView(this).apply {
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.da_text))
            setPadding(0, dp(16), 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(32), dp(16), dp(32), dp(8))
            addView(TextView(context).apply {
                text = getString(R.string.dset_abrp_token_summary)
                textSize = 20f
                setTextColor(ContextCompat.getColor(context, R.color.da_text))
                setPadding(0, 0, 0, dp(12))
            })
            addView(input)
            addView(result)
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_abrp_token)
            .setView(box)
            .setPositiveButton(R.string.dset_abrp_save, null)
            .setNeutralButton(R.string.dset_abrp_test, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { showTab(selectedTab) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val token = input.text.toString().trim()
                if (token.isEmpty()) {
                    result.text = getString(R.string.dset_abrp_token_empty)
                    return@setOnClickListener
                }
                settings.abrpToken = token
                if (enabling) settings.abrpEnabled = true
                dialog.dismiss()
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener { button ->
                val token = input.text.toString().trim()
                if (token.isEmpty()) {
                    result.text = getString(R.string.dset_abrp_token_empty)
                    return@setOnClickListener
                }
                button.isEnabled = false
                result.text = getString(R.string.dset_abrp_testing)
                val key = settings.abrpApiKey
                Thread {
                    val (outcome, detail) = runCatching {
                        com.andrerinas.openheadunit.vehicle.AbrpUploader.test(this, token, key)
                    }.getOrElse {
                        com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.NO_INTERNET to (it.message ?: "")
                    }
                    runOnUiThread {
                        button.isEnabled = true
                        result.text = when (outcome) {
                            com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.ACCEPTED -> getString(R.string.dset_abrp_test_ok)
                            com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.REFUSED -> getString(R.string.dset_abrp_test_refused, detail)
                            com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.RATE_LIMITED -> getString(R.string.dset_abrp_test_rate)
                            com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.NO_INTERNET -> getString(R.string.dset_abrp_test_offline)
                            com.andrerinas.openheadunit.vehicle.AbrpUploader.Outcome.NO_CHARGE -> getString(R.string.dset_abrp_test_no_soc)
                        }
                    }
                }.apply { isDaemon = true }.start()
            }
        }
        dialog.show()
    }

    /** A token or key: enough of it to recognise, not enough to read off the screen. */
    private fun masked(text: String): String =
        if (text.isBlank()) getString(R.string.dset_abrp_not_set) else "••••" + text.takeLast(4)

    private fun editText(title: Int, current: String, onSave: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(current)
            textSize = 24f
            setSelectAllOnFocus(true)
            minWidth = dp(560)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(32), dp(16), dp(32), dp(8))
            addView(input)
        }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSave(input.text.toString().trim())
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editSoh(current: Float) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.getDefault(), "%.1f", current))
            textSize = 30f
            setSelectAllOnFocus(true)
            minWidth = dp(320)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(32), dp(16), dp(32), dp(8))
            addView(input)
        }
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.battery_soh_percent)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().replace(',', '.').toFloatOrNull()?.let {
                    settings.batteryStateOfHealthPercent = it.coerceIn(1f, 100f)
                    settings.batterySohManual = true
                    com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
                }
                showTab(selectedTab)
            }
            .setNeutralButton(R.string.battery_auto) { _, _ ->
                settings.batterySohManual = false
                com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Battery, version and Maps model back to what the car says. */
    private fun resetBattery() {
        settings.batteryCapacityManual = false
        settings.batterySohManual = false
        settings.mg4Variant = com.andrerinas.openheadunit.vehicle.Mg4Variant.AUTO
        com.andrerinas.openheadunit.vehicle.BatteryCapacityMeter.clear(this)
        com.andrerinas.openheadunit.vehicle.Mg4EnergyProvider.refresh()
        showTab(selectedTab)
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

    private fun bondedPhones(): List<android.bluetooth.BluetoothDevice> = try {
        BluetoothHelper.getBluetoothAdapter(this)?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun phonesLabel(): String {
        val names = bondedPhones().associate { it.address to (it.name ?: getString(R.string.da_device)) }
        val chosen = settings.autoStartBluetoothDeviceMacs.mapNotNull { names[it] }
        return chosen.joinToString(", ").ifBlank {
            settings.autoStartBluetoothDeviceName.ifBlank { getString(R.string.dset_phone_none) }
        }
    }

    /** The phones to connect, ticked, and their order: the first one present goes wireless. */
    private fun pickPhone() {
        val bonded = bondedPhones()
        if (bonded.isEmpty()) {
            Toast.makeText(this, R.string.da_no_paired, Toast.LENGTH_LONG).show()
            return
        }
        val names = bonded.associate { it.address to (it.name ?: getString(R.string.da_device)) }
        var chosen = settings.autoStartBluetoothDeviceMacs.filter { it in names }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun arrow(text: String, enabled: Boolean, onClick: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = 30f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, if (enabled) R.color.da_accent else R.color.da_muted))
            isEnabled = enabled
            setOnClickListener { onClick() }
        }
        fun render() {
            box.removeAllViews()
            box.addView(TextView(this).apply {
                setText(R.string.dset_phones_hint)
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.da_muted))
                setPadding(0, 0, 0, dp(8))
            })
            val others = bonded.map { it.address }.filter { it !in chosen }
            (chosen + others).forEach { mac ->
                val rank = chosen.indexOf(mac)
                box.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(72)
                    addView(android.widget.CheckBox(context).apply {
                        isChecked = rank >= 0
                        text = if (rank >= 0) "${rank + 1}.  ${names[mac]}" else names[mac]
                        textSize = 22f
                        setTextColor(ContextCompat.getColor(context, R.color.da_text))
                        setOnCheckedChangeListener { _, on ->
                            chosen = if (on) chosen + mac else chosen - mac
                            render()
                        }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (rank >= 0) {
                        addView(arrow("▲", rank > 0) {
                            chosen = com.andrerinas.openheadunit.connection.PhonePriority.move(chosen, mac, -1)
                            render()
                        }, LinearLayout.LayoutParams(dp(72), dp(72)))
                        addView(arrow("▼", rank < chosen.lastIndex) {
                            chosen = com.andrerinas.openheadunit.connection.PhonePriority.move(chosen, mac, 1)
                            render()
                        }, LinearLayout.LayoutParams(dp(72), dp(72)))
                    }
                })
            }
        }
        render()
        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(R.string.dset_phones)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val macs = LinkedHashSet(chosen)
                settings.autoStartBluetoothDeviceMacs = macs
                settings.autoStartBluetoothDeviceName = chosen.firstOrNull()?.let { names[it] }.orEmpty()
                Settings.syncAutoStartBtMacsToDeviceStorage(this, macs)
                showTab(selectedTab)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyTripsToUsb() {
        Thread {
            val moved = com.andrerinas.openheadunit.vehicle.TripRecorder.moveToUsb(this)
            runOnUiThread {
                Toast.makeText(this,
                    if (moved == null) getString(R.string.trip_copy_failed)
                    else getString(R.string.trip_copied, moved.count) + "\n" + moved.folder,
                    Toast.LENGTH_LONG).show()
                showTab(selectedTab)
            }
        }.start()
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
            File(dir, "MG4Link-" + source.name).also { source.copyTo(it, overwrite = true) }
        }.getOrNull()
        Toast.makeText(this,
            getString(R.string.dset_logs_saved, (target ?: source).absolutePath), Toast.LENGTH_LONG).show()
    }

    // --- Rows and controls --------------------------------------------------------------------

    /** The phone reads this setting when it connects: ask to reconnect if a session is running. */
    private fun appliesOnReconnect() {
        if (App.provide(this).commManager.isConnected) reconnectNeeded = true
    }

    /** A line of explanation between rows. */
    private fun note(text: Int) {
        list.addView(TextView(this).apply {
            this.text = getString(text)
            textSize = 19f
            setTextColor(ContextCompat.getColor(context, R.color.da_muted))
            setPadding(dp(8), dp(20), dp(8), dp(4))
        })
    }

    private fun row(title: Int, summary: Int, control: View) = rowText(getString(title), getString(summary), control)

    private fun rowText(title: CharSequence, summary: String, control: View) {
        val v = layoutInflater.inflate(R.layout.da_settings_row, list, false)
        v.findViewById<TextView>(R.id.row_title).text = title
        v.findViewById<TextView>(R.id.row_summary).apply {
            text = summary
            visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<FrameLayout>(R.id.row_control).addView(control)
        // A value row (text with ›) reacts across its whole width: on the car's screen the text
        // alone is a small target (#17). Buttons and choices keep their own targets.
        if (control is TextView && control !is android.widget.Button && control.hasOnClickListeners()) {
            v.setOnClickListener { control.performClick() }
            val ripple = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                v.foreground = ContextCompat.getDrawable(this, ripple.resourceId)
            }
        }
        list.addView(v)
    }

    private fun yesNo(on: Boolean, onChange: (Boolean) -> Unit): View =
        choices(listOf(getString(R.string.dset_no), getString(R.string.dset_yes)), if (on) 1 else 0) { onChange(it == 1) }

    private fun choices(labels: List<CharSequence>, selected: Int, onChange: (Int) -> Unit): View {
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
                textSize = 21f
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

    /** -50 % … +200 % around the music level, in 5 % steps (the +75 % default sits between two 10 % steps). */
    /** The voice level: its value on the left, the slider with its marks every 50 % below it. */
    private fun level(offset: Int, onChange: (Int) -> Unit): View {
        val accentColor = ContextCompat.getColor(this, R.color.da_accent)
        val shown = TextView(this).apply {
            textSize = 22f
            setTextColor(accentColor)
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            minWidth = dp(110)
        }
        fun label(v: Int) = if (v == 0) "0 %" else "%+d %%".format(v)
        val slider = Slider(this).apply {
            valueFrom = -50f
            valueTo = Settings.MAX_VOICE_VOLUME_OFFSET.toFloat()
            stepSize = 5f
            value = (offset.coerceIn(-50, Settings.MAX_VOICE_VOLUME_OFFSET) / 5 * 5).toFloat()
            isTickVisible = false
            val accent = ColorStateList.valueOf(accentColor)
            thumbTintList = accent
            trackActiveTintList = accent
            trackInactiveTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.da_border))
            setLabelFormatter { v -> label(v.toInt()) }
            addOnChangeListener { _, v, fromUser ->
                shown.text = label(v.toInt())
                if (fromUser) onChange(v.toInt())
            }
        }
        shown.text = label(slider.value.toInt())
        // The end labels are wider than the track's margin: the marks reach past the slider.
        val overhang = dp(28)
        val marks = SliderMarks(this, slider, overhang, (-50..Settings.MAX_VOICE_VOLUME_OFFSET step 50).toList(), ::label)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(slider, LinearLayout.LayoutParams(dp(480), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = overhang
                marginEnd = overhang
            })
            addView(marks, LinearLayout.LayoutParams(dp(480) + 2 * overhang, dp(28)))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(shown)
            addView(column, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            })
        }
    }

    /** Labels under a slider, each centred on the point of the track it names. */
    private class SliderMarks(
        context: Context,
        private val slider: Slider,
        private val overhang: Int,
        private val values: List<Int>,
        private val label: (Int) -> String,
    ) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.da_muted)
            textSize = 17f * resources.displayMetrics.scaledDensity
            textAlign = android.graphics.Paint.Align.CENTER
        }
        private val tick = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.da_border)
            strokeWidth = 2f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val start = (overhang + slider.trackSidePadding).toFloat()
            val end = (width - overhang - slider.trackSidePadding).toFloat()
            val span = slider.valueTo - slider.valueFrom
            values.forEach { v ->
                val x = start + (end - start) * (v - slider.valueFrom) / span
                canvas.drawLine(x, 0f, x, 6f * resources.displayMetrics.density, tick)
                canvas.drawText(label(v), x, height - 4f * resources.displayMetrics.density, paint)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val BATTERY_RECHECK_MS = 3_000L

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

        /** The tab open last, kept while the app runs: a restart (car start) opens the first one. */
        private var lastTab = 0

        /** Opens on [tab], or on the tab used last when none is given. */
        fun intent(context: Context, tab: Int? = null): Intent =
            Intent(context, DaSettingsActivity::class.java).apply { tab?.let { putExtra(EXTRA_TAB, it) } }
    }
}
