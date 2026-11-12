package com.example.blackout

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val info = TextView(this).apply {
            text = "এই অ্যাপ অন্য অ্যাপের উপর কালো পর্দা দেখাতে 'Display over other apps' অনুমতি চায়। " +
                   "পর্দা ছাড়া অন্য কোনো ডেটা সংগ্রহ করা হয় না।"
            textSize = 16f
        }
        val start = Button(this).apply { text = "চালু করুন" }
        val stop = Button(this).apply { text = "বন্ধ করুন" }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(info); addView(start); addView(stop)
        })

        start.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                )
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }

        stop.setOnClickListener {
            startService(Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_STOP))
        }
    }
}
