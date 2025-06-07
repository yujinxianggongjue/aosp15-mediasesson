package com.example.mymediasessiontest

import android.app.Service
import android.car.Car
import android.car.CarNotConnectedException
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.os.SystemClock // 新增导入
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarMediaMonitorService : Service() {
    private val TAG = "CarMediaMonitorService"

    private var car: Car? = null
    private var carMediaManager: CarMediaManager? = null
    private var activeMediaController: MediaController? = null
    private var mediaSessionManager: MediaSessionManager? = null

    private val _mediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val mediaMetadata: StateFlow<MediaMetadata?> = _mediaMetadata.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState?>(null)
    val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()

    private val _albumArt = MutableStateFlow<ByteArray?>(null)
    val albumArt: StateFlow<ByteArray?> = _albumArt.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow<Int>(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    // 新增：实时进度 StateFlow
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    // Runnable：每500ms更新一次真实进度
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            _playbackState.value?.let { state ->
                if (state.state == PlaybackState.STATE_PLAYING) { // 仅在播放时计算
                    val elapsedTime = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                    val currentCalculatedPosition = state.position + (elapsedTime * state.playbackSpeed).toLong()
                    _currentPosition.value = currentCalculatedPosition
                } else {
                    // 如果不是播放状态，直接使用媒体控制器报告的 position
                    _currentPosition.value = state.position
                }
            }
            handler.postDelayed(this, 500) // 每500ms更新一次
        }
    }

    // Runnable：每 500ms 更新一次歌词
    private val lyricUpdateRunnable = object : Runnable {
        override fun run() {
            updateCurrentLyric()
            handler.postDelayed(this, 500) // 每500毫秒更新一次
        }
    }

    // 修改媒体源监听器实现
    private val mediaSourceListener = object : CarMediaManager.MediaSourceChangedListener {
        override fun onMediaSourceChanged(componentName: ComponentName?) { // 修改这里，允许 componentName 为 null
            if (componentName == null) {
                Log.w(TAG, "Media source changed event. New source ComponentName is NULL.")
                // 当 componentName 为 null 时，我们仍然调用 updateActiveMediaController。
                // updateActiveMediaController 内部会通过 carMediaManager.getMediaSource() 重新获取当前媒体源，
                // 如果此时没有活动媒体源，它会正确地清除媒体信息。
            } else {
                Log.i(TAG, "Media source changed event. New source ComponentName: $componentName, PackageName: ${componentName.packageName}")
            }
            updateActiveMediaController()
        }
    }

    // 新增：MediaSessionManager 的活动会话变化监听器
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.i(TAG, "Active media sessions changed event via MediaSessionManager. Controller count: ${controllers?.size ?: 0}")
            handleMediaSessionsChangedFromSystem(controllers)
        }

    // 媒体控制器回调
    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "MediaController.Callback: Metadata changed: ${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            _mediaMetadata.value = metadata
            // 获取专辑封面 (如果需要在此处处理，则添加逻辑，否则依赖 handleMediaSessionsChangedFromSystem 或 UI 层)
            // 例如，可以保留之前的专辑封面逻辑：
            metadata?.let {
                val art = it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (art != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    _albumArt.value = stream.toByteArray()
                } else {
                     // 如果元数据有更新但没有专辑封面，可能需要清除旧的封面
                    if (_mediaMetadata.value?.description?.mediaId == metadata.description?.mediaId) {
                        // 同一首歌，但新元数据无封面
                        _albumArt.value = null
                    }
                }
                 loadLyrics(it) // 确保在元数据非空时加载歌词
            } ?: run {
                // 元数据为 null，清除歌词和专辑封面
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _albumArt.value = null
                Log.d(TAG, "MediaController.Callback: Metadata is null, cleared lyrics and album art.")
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "MediaController.Callback: Playback state changed: ${state?.state}, Position: ${state?.position}")
            _playbackState.value = state
            if (state?.state == PlaybackState.STATE_PLAYING) {
                startProgressUpdate()
                startLyricUpdate()
            } else {
                stopProgressUpdate()
                stopLyricUpdate()
                // 当非播放状态时，也更新一次当前位置，以反映暂停等状态的精确位置
                state?.let { _currentPosition.value = it.position }
            }
        }
    }

    // 修改：专门处理来自 MediaSessionManager 的活动会话变化的逻辑
    private fun handleMediaSessionsChangedFromSystem(updatedControllers: List<MediaController>?) {
        Log.i(TAG, "handleMediaSessionsChangedFromSystem called. Controller count from MediaSessionManager: ${updatedControllers?.size ?: 0}")

        var newController: MediaController? = null

        if (updatedControllers.isNullOrEmpty()) {
            Log.i(TAG, "handleMediaSessionsChangedFromSystem: No active controllers from MediaSessionManager.")
        } else {
            newController = updatedControllers.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            if (newController != null) {
                Log.i(TAG, "handleMediaSessionsChangedFromSystem: Found a playing controller: ${newController.packageName}")
            } else {
                newController = updatedControllers.first()
                Log.i(TAG, "handleMediaSessionsChangedFromSystem: No playing controller, selected first available: ${newController.packageName}")
            }
        }

        if (activeMediaController?.sessionToken == newController?.sessionToken && activeMediaController != null) {
            Log.i(TAG, "handleMediaSessionsChangedFromSystem: Selected controller is the same as current (${newController?.packageName}). Forcing state update.")
            // 即使控制器相同，也主动获取一次最新状态并更新，确保UI同步
            val currentMetadata = activeMediaController!!.metadata // non-null due to activeMediaController != null
            val currentPlaybackState = activeMediaController!!.playbackState

            _mediaMetadata.value = currentMetadata
            _playbackState.value = currentPlaybackState
            _currentPosition.value = currentPlaybackState?.position ?: 0L // 更新初始位置

            currentMetadata?.let {
                loadLyrics(it)
                val art = it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (art != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    _albumArt.value = stream.toByteArray()
                } else {
                    _albumArt.value = null
                }
            } ?: run {
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _albumArt.value = null
            }
            
            Log.d(TAG, "handleMediaSessionsChangedFromSystem (same controller): _mediaMetadata.value. Title: ${currentMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            Log.d(TAG, "handleMediaSessionsChangedFromSystem (same controller): _playbackState.value. State: ${currentPlaybackState?.state}, Position: ${currentPlaybackState?.position}")

            if (currentPlaybackState?.state == PlaybackState.STATE_PLAYING) {
                startProgressUpdate()
                startLyricUpdate()
            } else {
                stopProgressUpdate()
                stopLyricUpdate()
            }
            return
        }

        activeMediaController?.unregisterCallback(mediaControllerCallback)
        Log.d(TAG, "handleMediaSessionsChangedFromSystem: Unregistered callback from old controller: ${activeMediaController?.packageName}")

        activeMediaController = newController

        if (activeMediaController != null) {
            activeMediaController!!.registerCallback(mediaControllerCallback, handler)
            Log.i(TAG, "handleMediaSessionsChangedFromSystem: Registered callback for new controller: ${activeMediaController!!.packageName}")
            
            val currentMetadata = activeMediaController!!.metadata
            val currentPlaybackState = activeMediaController!!.playbackState

            _mediaMetadata.value = currentMetadata
            _playbackState.value = currentPlaybackState
            _currentPosition.value = currentPlaybackState?.position ?: 0L // 更新初始位置

            // 显式更新专辑封面和加载歌词
            currentMetadata?.let {
                loadLyrics(it)
                val art = it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (art != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    _albumArt.value = stream.toByteArray()
                } else {
                    _albumArt.value = null
                }
            } ?: run {
                // 如果元数据为空，清除歌词和专辑封面
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _albumArt.value = null
            }
            
            Log.d(TAG, "handleMediaSessionsChangedFromSystem (new controller): _mediaMetadata.value. Title: ${currentMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            Log.d(TAG, "handleMediaSessionsChangedFromSystem (new controller): _playbackState.value. State: ${currentPlaybackState?.state}, Position: ${currentPlaybackState?.position}")

            if (currentPlaybackState?.state == PlaybackState.STATE_PLAYING) {
                startProgressUpdate()
                startLyricUpdate()
            } else {
                stopProgressUpdate()
                stopLyricUpdate()
            }
        } else {
            Log.i(TAG, "handleMediaSessionsChangedFromSystem: No new controller selected. Clearing media info.")
            _mediaMetadata.value = null
            _playbackState.value = null
            _albumArt.value = null
            _lyrics.value = emptyList()
            _currentLyricIndex.value = -1
            _currentPosition.value = 0L // 重置进度
            stopProgressUpdate()
            stopLyricUpdate()
        }
    }


    private val carServiceLifecycleListener = object : Car.CarServiceLifecycleListener {
        override fun onLifecycleChanged(carObj: Car, ready: Boolean) {
            if (ready) {
                Log.d(TAG, "Car service is ready. Initializing managers.")
                this@CarMediaMonitorService.car = carObj // Assign the ready car instance
                try {
                    carMediaManager = carObj.getCarManager(Car.CAR_MEDIA_SERVICE) as? CarMediaManager
                    carMediaManager?.addMediaSourceListener(
                        mediaSourceListener,
                        CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
                    )
                    updateActiveMediaController()
                    Log.d(TAG, "CarMediaManager initialized and listener added.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing car managers after connection", e)
                }
            } else {
                Log.w(TAG, "Car service is not ready or disconnected.")
                carMediaManager?.removeMediaSourceListener(
                    mediaSourceListener,
                    CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
                )
                carMediaManager = null
                activeMediaController?.unregisterCallback(mediaControllerCallback)
                activeMediaController = null
                // If the car service is not ready or disconnected,
                // the current 'carObj' might not be usable for future connect() calls.
                // Nullify 'this@CarMediaMonitorService.car' to ensure Car.createCar() is called next time.
                if (this@CarMediaMonitorService.car == carObj) { // Ensure we are nulling the same instance
                    this@CarMediaMonitorService.car = null
                    Log.d(TAG, "Car instance nulled due to onLifecycleChanged(ready=false).")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CarMediaMonitorService onCreate")

        // 初始化 MediaSessionManager
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        Log.i(TAG, "MediaSessionManager instance after getSystemService: $mediaSessionManager") // 新增：打印 mediaSessionManager 实例状态

        if (mediaSessionManager != null) {
            Log.d(TAG, "Attempting to register MediaSessionManager.OnActiveSessionsChangedListener...") // 新增：尝试注册前的日志
            try {
                // 因为我们已经检查过 mediaSessionManager != null，这里可以使用 !! 断言
                mediaSessionManager!!.addOnActiveSessionsChangedListener(activeSessionsChangedListener, null, handler)
                Log.d(TAG, "MediaSessionManager.OnActiveSessionsChangedListener registered.")
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException when registering OnActiveSessionsChangedListener. Check MEDIA_CONTENT_CONTROL permission.", se)
            } catch (t: Throwable) { // 修改：捕获所有类型的 Throwable
                Log.e(TAG, "Unexpected Throwable when registering MediaSessionManager.OnActiveSessionsChangedListener.", t)
            }
        } else {
            Log.w(TAG, "MediaSessionManager is null, cannot register OnActiveSessionsChangedListener.") // 新增：mediaSessionManager 为 null 的情况
        }

        connectToCar()
    }

    private fun connectToCar() {
        Log.d(TAG, "connectToCar called.")
        if (this.car == null) {
            Log.i(TAG, "Car instance is null. Calling Car.createCar() with listener.") // 使用 Info 级别日志
            try {
                // Car.createCar 将尝试连接并通知监听器。
                // 监听器将在准备就绪后分配 car 实例 (this@CarMediaMonitorService.car = carObj)。
                // 将 createCar 的结果分配给 this.car，以便在监听器触发之前
                // 后续对 connectToCar 的调用可以看到 car 对象存在并且正在连接中。
                this.car = Car.createCar(
                    applicationContext, // 使用 applicationContext
                    null, // 回调的 Handler (如果为 null 则在主线程)
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                    carServiceLifecycleListener
                )
                // 不要在此处调用 car.connect()。
                // createCar 返回的 Car 对象（并已分配给 this.car）
                // 将自动尝试连接并通知监听器。
                // IllegalStateException 是由此处之前的显式 connect 调用引起的。
                Log.d(TAG, "Car.createCar() invoked and instance assigned. Waiting for CarServiceLifecycleListener to confirm connection and readiness.")
            } catch (e: Exception) { // 为安全起见捕获通用 Exception
                Log.e(TAG, "Exception during Car.createCar(): ${e.message}", e)
                this.car = null // 如果创建失败，确保 car 为 null 以允许重试。
            }
        } else {
            // Car 实例已存在
            if (this.car?.isConnected == true) {
                Log.d(TAG, "Car instance already exists and is connected.")
                // 如果管理器由于某种原因未设置，请尝试设置它们。
                // 这主要由 onLifecycleChanged 处理，但可以作为后备。
                if (carMediaManager == null && this.car != null) {
                     Log.w(TAG, "Car connected but carMediaManager is null. Attempting to reinitialize.")
                     try {
                        carMediaManager = this.car?.getCarManager(Car.CAR_MEDIA_SERVICE) as? CarMediaManager
                        carMediaManager?.addMediaSourceListener(mediaSourceListener, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
                     } catch (e: Exception) {
                         Log.e(TAG, "Error re-initializing carMediaManager for already connected car", e)
                     }
                }
                updateActiveMediaController() // 确保 UI 是最新的
            } else if (this.car?.isConnecting == true) {
                Log.d(TAG, "Car instance already exists and is currently connecting. Waiting for listener.")
            } else {
                // Car 实例存在但未连接且未在连接中。尝试重新连接。
                Log.d(TAG, "Car instance exists but is not connected/connecting. Attempting to reconnect by calling car.connect().")
                try {
                    this.car?.connect()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException when trying to reconnect existing Car instance: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception when trying to reconnect existing Car instance: ${e.message}", e)
                }
            }
        }
    }
    
    private fun updateActiveMediaController() {
        Log.d(TAG, "Attempting to update active media controller (via CarMediaManager)...")
        try {
            activeMediaController?.unregisterCallback(mediaControllerCallback) // 先注销旧的
            //如果不先注销旧的回调，在多次切换音源或者重新连接时，旧的回调仍然会触发，造成 重复的事件处理 和 多次更新 UI。
            //并且于它持有 Handler 的引用，GC 无法清理，导致 内存泄漏
            /*Android 的 MediaController 设计是基于绑定和监听的。
		    如果音源发生了切换，比如从 QQ 音乐到网易云音乐，而你不注销原来的监听器：
		    QQ 音乐的 MediaController.Callback 依然会监听；
		    但 UI 已经不展示 QQ 音乐的界面，造成数据错乱。*/
            activeMediaController = null

            val currentMediaSourceComponent = carMediaManager?.getMediaSource(CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)
            Log.i(TAG, "updateActiveMediaController: currentMediaSourceComponent from CarMediaManager: $currentMediaSourceComponent")

            if (currentMediaSourceComponent == null) {
                Log.w(TAG, "No active media source component found from CarMediaManager. Clearing media info.")
                _mediaMetadata.value = null
                _playbackState.value = null
                _albumArt.value = null
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _currentPosition.value = 0L
                stopProgressUpdate()
                stopLyricUpdate()
                return
            }

            Log.i(TAG, "Target media source package determined by CarMediaManager: ${currentMediaSourceComponent.packageName}")
            val controllers = mediaSessionManager?.getActiveSessions(null)

            if (controllers == null) {
                Log.w(TAG, "MediaSessionManager getActiveSessions returned null. Cannot update controller.")
                return
            }
            
            // 尝试找到与CarMediaManager报告的源匹配的控制器
            val targetController = controllers.find { it.packageName == currentMediaSourceComponent.packageName }

            if (targetController == null) {
                Log.w(TAG, "No MediaController found matching CarMediaManager source: ${currentMediaSourceComponent.packageName}. Clearing media info.")
                _mediaMetadata.value = null
                _playbackState.value = null
                _albumArt.value = null
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _currentPosition.value = 0L
                stopProgressUpdate()
                stopLyricUpdate()
                return
            }
            
            Log.i(TAG, "Found matching MediaController via CarMediaManager: ${targetController.packageName}")
            activeMediaController = targetController
            activeMediaController!!.registerCallback(mediaControllerCallback, handler)

            val currentMetadata = activeMediaController!!.metadata
            val currentPlaybackState = activeMediaController!!.playbackState
            _mediaMetadata.value = currentMetadata
            _playbackState.value = currentPlaybackState
            _currentPosition.value = currentPlaybackState?.position ?: 0L

            currentMetadata?.let {
                loadLyrics(it)
                val art = it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (art != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    _albumArt.value = stream.toByteArray()
                } else {
                    _albumArt.value = null
                }
            } ?: run {
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
                _albumArt.value = null
            }

            if (currentPlaybackState?.state == PlaybackState.STATE_PLAYING) {
                startProgressUpdate()
                startLyricUpdate()
            } else {
                stopProgressUpdate()
                stopLyricUpdate()
            }

        } catch (e: CarNotConnectedException) {
            Log.e(TAG, "CarNotConnectedException in updateActiveMediaController", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in updateActiveMediaController. Check MEDIA_CONTENT_CONTROL permission.", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error in updateActiveMediaController", t)
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "CarMediaMonitorService onDestroy.")
        
        // 注销 MediaSessionManager 的监听器
        mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        Log.d(TAG, "MediaSessionManager.OnActiveSessionsChangedListener unregistered.")

        stopLyricUpdate()

        activeMediaController?.unregisterCallback(mediaControllerCallback)
        activeMediaController = null

        carMediaManager?.removeMediaSourceListener(
            mediaSourceListener,
            CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK
        )
        carMediaManager = null

        if (this.car != null) {
            // Check if connected or connecting before trying to disconnect
            if (this.car?.isConnecting == true || this.car?.isConnected == true) {
                 Log.d(TAG, "Disconnecting car instance in onDestroy.")
                 try {
                    this.car?.disconnect()
                 } catch (e: Exception) {
                    Log.e(TAG, "Exception during car.disconnect()", e)
                 }
            }
            this.car = null
        }
        
        super.onDestroy() // Call super last
        Log.d(TAG, "CarMediaMonitorService onDestroy completed.")
    }

    private fun loadLyrics(metadata: MediaMetadata?) {
        val songTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""

        _currentLyricIndex.value = -1
        val mockLyrics = getMockLyrics(songTitle)
        _lyrics.value = mockLyrics
        if (_playbackState.value?.state == PlaybackState.STATE_PLAYING) {
            startLyricUpdate()
        }
    }

    private fun getMockLyrics(songTitle: String): List<LyricLine> {
        return listOf(
            LyricLine(0, "歌词 - $songTitle"),
        )
    }

    private fun startProgressUpdate() {
        handler.removeCallbacks(progressUpdateRunnable)
        handler.post(progressUpdateRunnable)
    }
    private fun stopProgressUpdate() {
        handler.removeCallbacks(progressUpdateRunnable)
    }

    private fun startLyricUpdate() {
        handler.removeCallbacks(lyricUpdateRunnable)
        handler.post(lyricUpdateRunnable)
    }

    private fun stopLyricUpdate() {
        handler.removeCallbacks(lyricUpdateRunnable)
    }

    private fun updateCurrentLyric() {
        val currentPosition = activeMediaController?.playbackState?.position ?: 0
        val lyrics = _lyrics.value
        if (lyrics.isEmpty()) return
        var index = -1
        for (i in lyrics.indices) {
            if (i == lyrics.size - 1 || currentPosition < lyrics[i + 1].timeMs) {
                if (currentPosition >= lyrics[i].timeMs) {
                    index = i
                }
                break
            }
        }
        if (index != _currentLyricIndex.value) {
            _currentLyricIndex.value = index
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): CarMediaMonitorService = this@CarMediaMonitorService
    }

    // 媒体控制方法，也可放在  inner class LocalBinder : Binder中
    fun play() {
        activeMediaController?.transportControls?.play()
    }
    fun pause() {
        activeMediaController?.transportControls?.pause()
    }
    fun next() {
        activeMediaController?.transportControls?.skipToNext()
    }
    fun previous() {
        activeMediaController?.transportControls?.skipToPrevious()
    }
    fun fastForward() {
        activeMediaController?.transportControls?.fastForward()
    }
    fun rewind() {
        activeMediaController?.transportControls?.rewind()
    }
}

// 歌词行数据类
data class LyricLine(
    val timeMs: Long,  // 歌词时间点（毫秒）
    val text: String   // 歌词文本
)
