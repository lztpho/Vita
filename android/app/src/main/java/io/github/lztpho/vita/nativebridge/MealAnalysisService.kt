// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.lztpho.vita.R

class MealAnalysisService : Service() {
    companion object {
        private const val CHANNEL_ID = "vita-meal-analysis"
        private const val NOTIFICATION_ID = 7302

        fun start(context: Context) {
            val intent = Intent(context, MealAnalysisService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MealAnalysisService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "餐食分析", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "餐食分析进度"
                    setShowBadge(false)
                }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        startForeground(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(R.drawable.vita_launcher_icon)
                .setContentTitle("Vita 正在分析餐食")
                .setContentText("正在分析餐食")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
