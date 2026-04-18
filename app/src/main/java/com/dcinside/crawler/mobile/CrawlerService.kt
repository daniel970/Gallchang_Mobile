package com.dcinside.crawler.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 크롤링을 백그라운드에서도 중단 없이 돌리기 위한 포그라운드 서비스.
 *
 * 실제 크롤은 [CrawlerController] 의 프로세스-스코프 CoroutineScope 에서 돌고,
 * 이 서비스는 "이 앱이 지금 장시간 작업 중" 임을 OS 에 알리고 상단 알림으로
 * 진행도를 표시하는 역할만 한다.
 */
class CrawlerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14+ 는 foregroundServiceType 을 반드시 같이 지정해야 한다.
        val initial = buildNotification(percent = 0, text = getString(R.string.noti_text_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }

        // 기존 구독이 있다면 정리 후 재구독 (서비스가 재시작되는 경우 대비)
        collectJob?.cancel()
        collectJob = CrawlerController.events
            .onEach { progress -> handleProgress(progress) }
            .launchIn(scope)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleProgress(progress: CrawlProgress) {
        when (progress) {
            is CrawlProgress.PageCompleted -> {
                val text = getString(
                    R.string.noti_text_progress,
                    progress.page,
                    progress.percent,
                    progress.cumulativeArticles,
                )
                updateNotification(progress.percent, text)
            }
            is CrawlProgress.Started -> {
                updateNotification(0, getString(R.string.noti_text_starting))
            }
            is CrawlProgress.PageFailed, is CrawlProgress.PageSkipped -> Unit
            is CrawlProgress.Finished -> {
                stopForegroundAndSelf()
            }
            CrawlProgress.Cancelled -> {
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.noti_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.noti_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(percent: Int, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.noti_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_crawl)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(percent: Int, text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(percent, text))
    }

    companion object {
        private const val CHANNEL_ID = "crawler_progress"
        private const val NOTIFICATION_ID = 2026
    }
}
