package com.example.mymediaplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class AudioCaptureService : Service() {
    companion object {
        private const val TAG = "AudioCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 单例实例，方便MainActivity访问
        private var instance: AudioCaptureService? = null
        
        fun getInstance(): AudioCaptureService? {
            return instance
        }
    }

    private var mediaProjection: MediaProjection? = null
    private val audioPlaybackCapture = AudioPlaybackCapture()
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从Intent中获取MediaProjection数据
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val outputPath = intent?.getStringExtra("outputPath")

        if (resultCode != Activity.RESULT_OK || data == null || outputPath == null) {
            Log.e(TAG, "无效的数据，无法启动服务")
            stopSelf()
            return START_NOT_STICKY
        }

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        // 创建MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "创建MediaProjection失败")
            stopSelf()
            return START_NOT_STICKY
        }

        // 开始录制
        outputFile = File(outputPath)
        startCapture(outputFile!!)

        return START_STICKY
    }

    fun startCapture(file: File) {
        mediaProjection?.let {
            audioPlaybackCapture.startCapture(it, file)
        }
    }

    fun stopCapture(): String? {
        return audioPlaybackCapture.stopCapture()
    }

    override fun onDestroy() {
        audioPlaybackCapture.stopCapture()
        mediaProjection?.stop()
        mediaProjection = null
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频捕获服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于捕获系统音频"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在录制系统音频")
            .setContentText("点击返回应用")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}