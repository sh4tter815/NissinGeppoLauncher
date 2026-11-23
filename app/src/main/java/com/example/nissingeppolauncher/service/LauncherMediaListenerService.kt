package com.example.nissingeppolauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LauncherMediaListenerService : NotificationListenerService() {
    // MediaSessionManager.getActiveSessions 用の通知リスナーサービス。
    // 通知バッジ用に、アプリごとの通知数も数える（常駐通知とグループのまとめ通知は数えない）

    companion object {
        private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        recompute()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        recompute()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        recompute()
    }

    private fun recompute() {
        try {
            val counts = mutableMapOf<String, Int>()
            for (sbn in activeNotifications ?: emptyArray()) {
                val n = sbn.notification ?: continue
                val flags = n.flags
                if (flags and Notification.FLAG_ONGOING_EVENT != 0) continue
                if (flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                if (sbn.packageName == packageName) continue
                counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            }
            _badgeCounts.value = counts
        } catch (_: Exception) {
        }
    }
}
