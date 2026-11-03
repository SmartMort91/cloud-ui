package com.lagradost.cloudstream3.ui

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity

/**
 * Optional launch timing readout (Settings > Layout > Startup timing). It shows one message after
 * the first home load with the time since the app process started for each step, so it is clear
 * which part of a slow start is slow: the app window, the plugins, the home rows or the hero.
 *
 * Costs nothing when the option is off, apart from a few clock reads.
 */
object StartupTimer {
    const val OPTION = "startup_timing"

    private val marks = LinkedHashMap<String, Long>()
    private var reported = false
    private var installed = false
    private val handler = Handler(Looper.getMainLooper())

    private fun sinceProcessStart(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        } else {
            SystemClock.elapsedRealtime()
        }

    /** Call once from the main activity, it starts listening for the plugin events */
    fun install() {
        if (installed) return
        installed = true
        mark("window")
        MainActivity.mainPluginsLoadedEvent += { mark("home plugin") }
        MainActivity.afterPluginsLoadedEvent += { mark("plugins") }
    }

    /** Only the first time each step happens counts, later reloads are not a launch */
    fun mark(step: String) {
        synchronized(marks) {
            if (reported || marks.containsKey(step)) return
            marks[step] = sinceProcessStart()
            if (step == "rows") {
                // Report whatever has happened a little later even if the hero never arrives
                handler.postDelayed({ report() }, 8000)
            }
            if (step == "hero" && marks.containsKey("rows")) report()
        }
    }

    private fun report() {
        val text = synchronized(marks) {
            if (reported) return
            reported = true
            marks.entries.joinToString("  ") { (name, ms) -> "$name ${"%.1f".format(ms / 1000.0)}s" }
        }
        if (UiPrefs.getOpt(OPTION, "off") == "on") {
            showToast("Startup: $text", android.widget.Toast.LENGTH_LONG)
        }
    }
}
