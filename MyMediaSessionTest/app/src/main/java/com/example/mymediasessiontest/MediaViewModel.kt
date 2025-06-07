package com.example.mymediasessiontest

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private var mediaMonitorService: CarMediaMonitorService? = null
    private var bound = false
    
    private val _mediaInfo = MutableStateFlow(MediaInfo())
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CarMediaMonitorService.LocalBinder
            mediaMonitorService = binder.getService()
            bound = true
            
            // 开始监听媒体信息更新
            viewModelScope.launch {
                combine(
                    mediaMonitorService!!.mediaMetadata,
                    mediaMonitorService!!.playbackState,
                    mediaMonitorService!!.albumArt,
                    mediaMonitorService!!.lyrics,
                    mediaMonitorService!!.currentLyricIndex
                ) { metadata, playbackState, albumArt, lyrics, currentLyricIndex ->
                    MediaInfo.fromMetadata(
                        metadata, 
                        playbackState, 
                        albumArt,
                        lyrics,
                        currentLyricIndex
                    )
                }.collect { info ->
                    _mediaInfo.value = info
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            mediaMonitorService = null
            bound = false
        }
    }
    
    init {
        bindService()
    }
    
    private fun bindService() {
        val intent = Intent(getApplication(), CarMediaMonitorService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
        super.onCleared()
    }
    
    // 媒体控制方法
    fun play() {
        mediaMonitorService?.play()
    }
    fun pause() {
        mediaMonitorService?.pause()
    }
    fun next() {
        mediaMonitorService?.next()
    }
    fun previous() {
        mediaMonitorService?.previous()
    }
    fun fastForward() {
        mediaMonitorService?.fastForward()
    }
    fun rewind() {
        mediaMonitorService?.rewind()
    }
}