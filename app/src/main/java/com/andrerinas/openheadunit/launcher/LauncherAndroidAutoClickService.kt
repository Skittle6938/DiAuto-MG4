package com.andrerinas.openheadunit.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.andrerinas.openheadunit.main.MainActivity
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * Watches OEM launcher Android Auto tiles across SAIC MG4 launcher variants.
 *
 * When the icon is visually inactive (not selected / not connected) and the user
 * taps it, the OEM shows a "connect USB" toast. This service intercepts that tap
 * and opens DiAuto instead.
 *
 * Supported homes:
 * - [PKG_HMI] (`iv_android_auto` / `tv_android_auto`)
 * - [PKG_EH32] (`aa_icon` / `aa_name`, and CPAA tile only when labelled Android Auto)
 */
class LauncherAndroidAutoClickService : AccessibilityService() {

    private var lastLaunchElapsedMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in LAUNCHER_PACKAGES) return

        val settings = try {
            Settings(this)
        } catch (_: Exception) {
            return
        }
        if (!settings.openOnLauncherAndroidAutoClick) return

        val source = event.source ?: return
        try {
            val viewId = source.viewIdResourceName
            if (viewId.isNullOrEmpty()) {
                resolveFromRoot(pkg, source)
                return
            }
            if (isAndroidAutoControl(viewId, source)) {
                maybeLaunch(source)
            }
        } finally {
            source.recycle()
        }
    }

    private fun resolveFromRoot(pkg: String, clicked: AccessibilityNodeInfo) {
        val root = rootInActiveWindow ?: return
        try {
            for (suffix in ANDROID_AUTO_ID_SUFFIXES) {
                val fullId = "$pkg$suffix"
                val matches = root.findAccessibilityNodeInfosByViewId(fullId) ?: continue
                try {
                    for (node in matches) {
                        if (sameNode(clicked, node) || overlapsBounds(clicked, node)) {
                            if (isAndroidAutoControl(fullId, node)) {
                                maybeLaunch(node)
                            }
                            return
                        }
                    }
                } finally {
                    matches.forEach { it.recycle() }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun sameNode(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        return a == b || (a.windowId == b.windowId && a.hashCode() == b.hashCode())
    }

    private fun overlapsBounds(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        val ra = Rect()
        val rb = Rect()
        a.getBoundsInScreen(ra)
        b.getBoundsInScreen(rb)
        return ra.intersect(rb)
    }

    private fun maybeLaunch(node: AccessibilityNodeInfo) {
        // Launcher uses setSelected(connected). Inactive = not selected → OEM USB toast path.
        if (node.isSelected) {
            AppLog.d("$TAG: Android Auto tile is active/connected — ignoring")
            return
        }
        launchDiAuto()
    }

    private fun isAndroidAutoControl(viewId: String, node: AccessibilityNodeInfo): Boolean {
        val suffix = viewId.substringAfter(":id/", viewId)
        if (suffix in AA_ONLY_IDS) return true
        // Shared CarPlay/AA card — only when the visible label is Android Auto.
        if (suffix in CPAA_SHARED_IDS) return looksLikeAndroidAutoLabel(node)
        return false
    }

    private fun looksLikeAndroidAutoLabel(node: AccessibilityNodeInfo): Boolean {
        val texts = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.parent?.text?.toString(),
            node.parent?.contentDescription?.toString(),
        )
        return texts.any { text ->
            val t = text.trim()
            t.contains("Android Auto", ignoreCase = true) &&
                !t.contains("CarPlay", ignoreCase = true)
        }
    }

    private fun launchDiAuto() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchElapsedMs < DEBOUNCE_MS) return
        lastLaunchElapsedMs = now

        AppLog.i("$TAG: inactive Android Auto tile clicked — launching MG4Link")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra(MainActivity.EXTRA_LAUNCH_SOURCE, "Launcher Android Auto button")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            AppLog.e("$TAG: failed to launch MainActivity", e)
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.i("$TAG: service connected")
    }

    companion object {
        private const val TAG = "LauncherAaClick"
        private const val PKG_HMI = "com.saicmotor.hmi.launcher"
        private const val PKG_EH32 = "com.saicmotor.launcher"
        private const val DEBOUNCE_MS = 800L

        private val LAUNCHER_PACKAGES = setOf(PKG_HMI, PKG_EH32)

        /** Dedicated AA controls (never CarPlay). */
        private val AA_ONLY_IDS = setOf(
            "iv_android_auto",
            "tv_android_auto",
            "aa_icon",
            "aa_name",
        )

        /** Combined CP/AA card — require Android Auto label. */
        private val CPAA_SHARED_IDS = setOf(
            "iv_cpaa_img",
            "tv_cpaa_txt",
        )

        private val ANDROID_AUTO_ID_SUFFIXES = (AA_ONLY_IDS + CPAA_SHARED_IDS).map { ":id/$it" }
    }
}
