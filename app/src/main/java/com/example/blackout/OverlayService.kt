package com.example.blackout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.hypot

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.blackout.STOP"
        private const val CHANNEL = "overlay"
        private const val HIDE_DELAY = 3000L
        private const val LONG_PRESS = 600L
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var watcher: View? = null
    private var blackout: FrameLayout? = null
    private var clockBox: LinearLayout? = null
    private lateinit var button: TextView
    private lateinit var btnParams: LP
    private var buttonAttached = false
    private var isOn = false

    private val hideRunnable = Runnable { hideControls() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildButton()
        showWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeSafely(watcher); watcher = null
        removeSafely(blackout); blackout = null
        if (buttonAttached) removeSafely(button)
        buttonAttached = false
        super.onDestroy()
    }

    // ---------- নোটিফিকেশন ----------
    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "বন্ধ করুন",
            stopIntent
        ).build()

        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Black screen চালু আছে")
            .setContentText("স্ক্রিনে ট্যাপ করলে বাটন আসবে")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .addAction(action)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- অফ অবস্থা: ট্যাপ লক্ষ করার অদৃশ্য উইন্ডো ----------
    private fun showWatcher() {
        if (watcher != null) return
        val v = View(this)
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) showControls()
            false
        }
        val p = LP(
            1, 1, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL or LP.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(v, p)
        watcher = v
    }

    // ---------- অন অবস্থা: কালো পর্দা ----------
    private fun showBlackout() {
        if (blackout != null) return

        val time = TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            textSize = 56f; setTextColor(0x99FFFFFF.toInt()); gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM yyyy"; format24Hour = "EEEE, d MMMM yyyy"
            textSize = 18f; setTextColor(0x77FFFFFF.toInt()); gravity = Gravity.CENTER
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(time); addView(date)
        }
        clockBox = box

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(box, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) showControls()
                true
            }
        }
        val p = LP(
            LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(root, p)
        blackout = root
    }

    private fun removeBlackout() {
        removeSafely(blackout); blackout = null; clockBox = null
    }

    // ---------- অন/অফ ----------
    private fun toggle() {
        handler.removeCallbacks(hideRunnable)
        detachButton()
        if (!isOn) {
            isOn = true
            removeSafely(watcher); watcher = null
            showBlackout()
        } else {
            isOn = false
            removeBlackout()
            showWatcher()
        }
    }

    // ---------- বাটন ও ঘড়ি দেখানো/লুকানো ----------
    private fun showControls() {
        if (isOn) clockBox?.visibility = View.VISIBLE
        attachButton()
        scheduleHide()
    }

    private fun hideControls() {
        clockBox?.visibility = View.GONE
        detachButton()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private fun attachButton() {
        if (buttonAttached) return
        wm.addView(button, btnParams)
        buttonAttached = true
    }

    private fun detachButton() {
        if (!buttonAttached) return
        removeSafely(button)
        buttonAttached = false
    }

    private fun openSettings() {
        if (isOn) toggle()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------- ভাসমান বাটন ----------
    private fun buildButton() {
        val size = (44 * resources.displayMetrics.density).toInt()
        val prefs = getSharedPreferences("pos", MODE_PRIVATE)

        btnParams = LP(
            size, size, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 40)
            y = prefs.getInt("y", 300)
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false; var longFired = false
        val longPress = Runnable { longFired = true; openSettings() }

        button = TextView(this).apply {
            text = "🔒"; textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x44FFFFFF)
                setStroke(2, 0x55FFFFFF)
            }
            alpha = 0.7f
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = btnParams.x; startY = btnParams.y
                        dragging = false; longFired = false
                        handler.removeCallbacks(hideRunnable)
                        handler.postDelayed(longPress, LONG_PRESS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX; val dy = e.rawY - downY
                        if (!dragging && hypot(dx, dy) > slop) {
                            dragging = true
                            handler.removeCallbacks(longPress)
                        }
                        if (dragging) {
                            btnParams.x = startX + dx.toInt()
                            btnParams.y = startY + dy.toInt()
                            wm.updateViewLayout(v, btnParams)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPress)
                        if (dragging) {
                            prefs.edit().putInt("x", btnParams.x).putInt("y", btnParams.y).apply()
                            scheduleHide()
                        } else if (!longFired) {
                            toggle()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        scheduleHide()
                    }
                }
                true
            }
        }
    }

    private fun removeSafely(v: View?) {
        if (v == null) return
        try { wm.removeView(v) } catch (_: Exception) {}
    }
}
