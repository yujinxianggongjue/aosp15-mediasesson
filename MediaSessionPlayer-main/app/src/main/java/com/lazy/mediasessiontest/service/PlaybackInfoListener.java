package com.lazy.mediasessiontest.service;

import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

/**
 * PlaybackInfoListener 是一个抽象类，用于监听媒体播放信息的变化。
 * 实现此接口的类可以接收关于播放状态更改和播放完成的通知。
 * 它通常由媒体播放器适配器（如 {@link com.lazy.mediasessiontest.service.players.MediaPlayerAdapter}）使用，
 * 以便将播放事件通知给关心这些事件的组件（如 {@link MusicService.MediaPlayerListener}）。
 *
 * author : xu
 * date : 2021/2/24 16:43
 * description : 播放信息回调监听器
 */
public abstract class PlaybackInfoListener {
    /**
     * 日志标签，用于在此类中记录日志。
     */
    private static final String TAG = "MyMediaSeeionTestPlaybackInfoListener";

    /**
     * 当播放状态发生改变时调用。
     * 例如，当播放器从播放状态变为暂停状态，或从暂停状态变为播放状态时，此方法会被调用。
     *
     * @param state 最新的播放状态对象 ({@link PlaybackStateCompat})，包含了当前的播放状态 (如 PLAYING, PAUSED, STOPPED)、
     *              当前播放位置、可用的操作等信息。
     */
    public abstract void onPlaybackStateChange(PlaybackStateCompat state);

    /**
     * 当当前媒体播放完成时调用。
     * 这通常意味着媒体文件已经播放到了末尾。
     * 实现者可以在此方法中处理播放完成后的逻辑，例如播放下一首歌曲或更新UI。
     */
    public void onPlaybackCompleted() {
        Log.d(TAG, "onPlaybackCompleted called - Media playback has completed.");
    }

    /**
     * 当播放过程中发生错误时调用。
     *
     * @param error 描述错误的字符串。
     */
    public void onError(String error) {
        Log.e(TAG, "onError called with error: " + error);
        // 默认实现只是记录错误，子类可以覆盖此方法以执行更具体的错误处理。
    }
}
