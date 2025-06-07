package com.lazy.mediasessiontest.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * MediaSeekBar 是一个自定义的 SeekBar，用于显示和控制媒体播放进度。
 * 它与 MediaControllerCompat 关联，以同步播放状态和元数据。
 * 当媒体播放时，进度条会自动更新。用户也可以拖动进度条来改变播放位置。
 */
public class MediaSeekBar extends AppCompatSeekBar {
    private static final String TAG = "MyMediaSeeionTestMediaSeekBar";

    /**
     * 关联的媒体控制器，用于获取播放状态和元数据，以及发送控制命令。
     */
    private MediaControllerCompat mMediaController;
    /**
     * 媒体控制器的回调，用于响应播放状态和元数据的变化。
     */
    private ControllerCallback mControllerCallback;

    /**
     * 标记用户是否正在拖动进度条。
     * 如果为 true，则表示用户正在拖动，此时应暂停自动更新进度条。
     */
    private boolean mIsTracking = false;

    /**
     * SeekBar 的监听器，用于处理用户拖动进度条的事件。
     */
    private OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        /**
         * 当进度条的进度发生改变时调用。
         *
         * @param seekBar  进度条实例。
         * @param progress 当前进度。
         * @param fromUser 如果进度改变是由用户操作引起的，则为 true。
         */
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.d(TAG, "mOnSeekBarChangeListener.onProgressChanged called, progress: " + progress + ", fromUser: " + fromUser);
            // 当前实现中，此回调主要用于日志记录。
            // 进度的实际设置由 ValueAnimator 或用户拖动后的 onStopTrackingTouch 处理。
        }

        /**
         * 当用户开始拖动进度条时调用。
         *
         * @param seekBar 进度条实例。
         */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "mOnSeekBarChangeListener.onStartTrackingTouch called");
            mIsTracking = true; // 标记用户开始拖动
        }

        /**
         * 当用户停止拖动进度条时调用。
         *
         * @param seekBar 进度条实例。
         */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "mOnSeekBarChangeListener.onStopTrackingTouch called, seeking to: " + getProgress());
            // 用户停止拖动后，将播放位置设置为进度条的当前位置。
            if (mMediaController != null && mMediaController.getTransportControls() != null) {
                mMediaController.getTransportControls().seekTo(getProgress());
            }
            mIsTracking = false; // 标记用户停止拖动
        }
    };

    /**
     * 用于平滑更新进度条的动画器。
     * 当媒体播放时，此动画器会根据播放速度和剩余时间来更新进度条。
     */
    private ValueAnimator mProgressAnimator;

    /**
     * 构造函数。
     * @param context 上下文。
     */
    public MediaSeekBar(Context context) {
        super(context);
        Log.d(TAG, "MediaSeekBar constructor(Context) called");
        // 设置内部的 OnSeekBarChangeListener
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    /**
     * 构造函数。
     * @param context 上下文。
     * @param attrs   属性集。
     */
    public MediaSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "MediaSeekBar constructor(Context, AttributeSet) called");
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    /**
     * 构造函数。
     * @param context      上下文。
     * @param attrs        属性集。
     * @param defStyleAttr 默认样式属性。
     */
    public MediaSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Log.d(TAG, "MediaSeekBar constructor(Context, AttributeSet, int) called");
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    /**
     * 禁止外部设置 OnSeekBarChangeListener。
     * MediaSeekBar 内部已经处理了进度条的监听逻辑。
     *
     * @param l 尝试设置的监听器。
     * @throws UnsupportedOperationException 始终抛出此异常。
     */
    @Override
    public final void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        Log.d(TAG, "setOnSeekBarChangeListener called - throwing UnsupportedOperationException");
        // Prohibit adding seek listeners to this subclass.
        throw new UnsupportedOperationException("Cannot add listeners to a MediaSeekBar");
    }

    /**
     * 设置关联的 MediaControllerCompat。
     * 当设置新的控制器时，会注册回调以接收播放状态和元数据的更新。
     * 如果传入 null，则会取消注册之前的回调。
     *
     * @param mediaController 要关联的 MediaControllerCompat，或者为 null 以断开连接。
     */
    public void setMediaController(final MediaControllerCompat mediaController) {
        Log.d(TAG, "setMediaController called with controller: " + (mediaController == null ? "null" : mediaController.getPackageName()));
        if (mediaController != null) {
            // 如果已经有一个控制器回调，先注销旧的
            if (mMediaController != null && mControllerCallback != null) {
                mMediaController.unregisterCallback(mControllerCallback);
            }
            mControllerCallback = new ControllerCallback();
            mediaController.registerCallback(mControllerCallback);
            // 更新元数据和播放状态，以确保 SeekBar 初始化正确
            MediaMetadataCompat metadata = mediaController.getMetadata();
            if (metadata != null) {
                mControllerCallback.onMetadataChanged(metadata);
            }
            PlaybackStateCompat playbackState = mediaController.getPlaybackState();
            if (playbackState != null) {
                mControllerCallback.onPlaybackStateChanged(playbackState);
            }
        } else if (mMediaController != null && mControllerCallback != null) {
            // 如果传入的 mediaController 为 null，且当前有关联的控制器，则注销回调
            mMediaController.unregisterCallback(mControllerCallback);
            mControllerCallback = null;
        }
        mMediaController = mediaController;
    }

    /**
     * 断开与 MediaControllerCompat 的连接。
     * 这会取消注册回调并释放对控制器的引用。
     */
    public void disconnectController() {
        Log.d(TAG, "disconnectController called");
        if (mMediaController != null) {
            if (mControllerCallback != null) {
                mMediaController.unregisterCallback(mControllerCallback);
                mControllerCallback = null;
            }
            mMediaController = null;
        }
        // 如果有正在进行的动画，也取消它
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
            mProgressAnimator = null;
        }
    }

    /**
     * 内部类，用于处理来自 MediaControllerCompat 的回调。
     * 同时实现了 ValueAnimator.AnimatorUpdateListener 以便在动画更新时更新 SeekBar 进度。
     */
    private class ControllerCallback
            extends MediaControllerCompat.Callback
            implements ValueAnimator.AnimatorUpdateListener {

        /**
         * 当媒体会话被销毁时调用。
         */
        @Override
        public void onSessionDestroyed() {
            Log.d(TAG, "ControllerCallback.onSessionDestroyed called");
            super.onSessionDestroyed();
            // 可以在这里处理会话销毁的逻辑，例如重置 SeekBar
        }

        /**
         * 当播放状态发生改变时调用。
         *
         * @param state 最新的播放状态。
         */
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            Log.d(TAG, "ControllerCallback.onPlaybackStateChanged called with state: " + (state == null ? "null" : state.getState()));
            super.onPlaybackStateChanged(state);

            // 如果有正在进行的动画，先取消它，因为播放状态可能已改变（例如暂停、停止）。
            if (mProgressAnimator != null) {
                mProgressAnimator.cancel();
                mProgressAnimator = null;
            }

            // 获取当前播放位置，如果 state 为 null，则默认为 0。
            final int progress = state != null ? (int) state.getPosition() : 0;
            // 设置 SeekBar 的当前进度。
            setProgress(progress);

            // 如果媒体正在播放，则启动 ValueAnimator 来平滑更新 SeekBar。
            if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                // 计算从当前进度到媒体结束的剩余时间。
                // getMax() 返回的是媒体总时长。
                // state.getPlaybackSpeed() 是播放速度，通常为 1.0。
                final int timeToEnd = (int) ((getMax() - progress) / state.getPlaybackSpeed());

                // 创建一个从当前进度到最大进度的 ValueAnimator。
                mProgressAnimator = ValueAnimator.ofInt(progress, getMax())
                        .setDuration(timeToEnd); // 动画持续时间为剩余播放时间
                mProgressAnimator.setInterpolator(new LinearInterpolator()); // 使用线性插值器，使进度均匀变化
                mProgressAnimator.addUpdateListener(this); // 将此回调作为动画更新监听器
                mProgressAnimator.start(); // 启动动画
                Log.d(TAG, "ControllerCallback.onPlaybackStateChanged: Animator started, duration: " + timeToEnd);
            } else {
                Log.d(TAG, "ControllerCallback.onPlaybackStateChanged: Not playing or state is null, animator not started.");
            }
        }

        /**
         * 当媒体元数据发生改变时调用。
         *
         * @param metadata 最新的媒体元数据。
         */
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            Log.d(TAG, "ControllerCallback.onMetadataChanged called with metadata: " + (metadata == null ? "null" : metadata.getDescription().getTitle()));
            super.onMetadataChanged(metadata);

            // 获取媒体总时长，如果 metadata 为 null，则默认为 0。
            final int max = metadata != null
                    ? (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                    : 0;
            // 设置 SeekBar 的初始进度为 0。
            setProgress(0);
            // 设置 SeekBar 的最大值为媒体总时长。
            setMax(max);
            Log.d(TAG, "ControllerCallback.onMetadataChanged: SeekBar max set to " + max);
        }

        /**
         * 当 ValueAnimator 的值更新时调用。
         *
         * @param valueAnimator 正在运行的 ValueAnimator。
         */
        @Override
        public void onAnimationUpdate(final ValueAnimator valueAnimator) {
            // Log.d(TAG, "ControllerCallback.onAnimationUpdate called"); // 此日志过于频繁，可按需开启
            // 如果用户正在拖动进度条，则取消动画，以避免冲突。
            if (mIsTracking) {
                valueAnimator.cancel();
                Log.d(TAG, "ControllerCallback.onAnimationUpdate: User is tracking, animation cancelled.");
                return;
            }

            // 获取动画器计算出的当前值。
            final int animatedIntValue = (int) valueAnimator.getAnimatedValue();
            // 设置 SeekBar 的进度。
            setProgress(animatedIntValue);
        }
    }
}
