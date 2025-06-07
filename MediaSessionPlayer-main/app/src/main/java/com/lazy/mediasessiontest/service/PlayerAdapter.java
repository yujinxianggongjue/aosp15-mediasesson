package com.lazy.mediasessiontest.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * PlayerAdapter 是一个抽象基类，定义了媒体播放器应具备的核心功能和行为。
 * 它封装了音频焦点的管理 ({@link AudioFocusHelper}) 和对音频输出设备变化的响应 (例如耳机拔出)。
 * 具体的播放器实现 (如 {@link com.lazy.mediasessiontest.service.players.MediaPlayerAdapter}) 需要继承此类并实现其抽象方法。
 * <p>
 * 主要职责：
 * <ul>
 *     <li>定义播放控制接口 (play, pause, stop, seekTo, setVolume)。</li>
 *     <li>管理音频焦点，确保在播放时请求焦点，在失去焦点时做出适当响应。</li>
 *     <li>监听 {@link AudioManager#ACTION_AUDIO_BECOMING_NOISY} 广播，以便在音频输出变为“嘈杂”（例如耳机拔出）时暂停播放。</li>
 *     <li>提供播放媒体元数据 ({@link MediaMetadataCompat}) 的抽象方法。</li>
 * </ul>
 *
 * author : xu
 * date : 2021/2/24 16:47
 * description : 播放器基类，封装了音频焦点管理和耳机拔出监听
 */
public abstract class PlayerAdapter {
    /**
     * 日志标签，用于在此类中记录日志。
     */
    private static final String TAG = "MyMediaSeeionTestPlayerAdapter";

    /**
     * 默认媒体音量 (1.0f 表示最大音量)。
     */
    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    /**
     * 当音频焦点暂时丢失且允许“降低音量”(ducking) 时的媒体音量。
     */
    private static final float MEDIA_VOLUME_DUCK = 0.2f;

    /**
     * 用于监听 {@link AudioManager#ACTION_AUDIO_BECOMING_NOISY} 事件的 IntentFilter。
     * 当音频输出路径发生变化，可能导致音频通过扬声器播放时（例如拔下耳机），会发送此广播。
     */
    private static final IntentFilter AUDIO_NOISY_INTENT_FILTER =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    /**
     * 标记 {@link #mAudioNoisyReceiver} 是否已注册。
     */
    private boolean mAudioNoisyReceiverRegistered = false;
    /**
     * 广播接收器，用于处理 {@link AudioManager#ACTION_AUDIO_BECOMING_NOISY} 事件。
     * 当接收到此事件且当前正在播放时，会暂停播放。
     */
    private final BroadcastReceiver mAudioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "mAudioNoisyReceiver.onReceive called with action: " + intent.getAction());
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        Log.d(TAG, "Audio becoming noisy (e.g., headphones unplugged).");
                        // 耳机拔出时，如果正在播放，则暂停
                        if (isPlaying()) {
                            Log.d(TAG, "Audio noisy: Pausing playback.");
                            pause();
                        }
                    }
                }
            };

    /**
     * 应用程序上下文。
     */
    private final Context mApplicationContext;
    /**
     * 系统的 AudioManager 服务实例，用于管理音频焦点和音量。
     */
    private final AudioManager mAudioManager;
    /**
     * 音频焦点管理辅助类。
     */
    private final AudioFocusHelper mAudioFocusHelper;

    /**
     * 标记是否应在重新获得音频焦点后恢复播放。
     * 当因暂时失去焦点（如 {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT}）而暂停播放时，此标志设为 true。
     */
    private boolean mPlayOnAudioFocus = false;

    /**
     * PlayerAdapter 的构造函数。
     *
     * @param context 应用程序上下文，不能为空。
     */
    public PlayerAdapter(@NonNull Context context) {
        Log.d(TAG, "PlayerAdapter constructor called.");
        mApplicationContext = context.getApplicationContext(); // 使用 ApplicationContext 避免内存泄漏
        mAudioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusHelper = new AudioFocusHelper();
        Log.d(TAG, "PlayerAdapter initialized with AudioManager and AudioFocusHelper.");
    }

    /**
     * 从给定的媒体元数据开始播放。
     * 子类需要实现此方法以加载并播放指定的媒体内容。
     *
     * @param metadata 要播放的媒体的 {@link MediaMetadataCompat} 对象。
     */
    public abstract void playFromMedia(MediaMetadataCompat metadata);

    /**
     * 获取当前正在播放或已加载的媒体元数据。
     *
     * @return 当前的 {@link MediaMetadataCompat} 对象；如果没有当前媒体，则返回 null。
     */
    public abstract MediaMetadataCompat getCurrentMedia();

    /**
     * 检查播放器当前是否正在播放。
     *
     * @return 如果正在播放，则返回 true；否则返回 false。
     */
    public abstract boolean isPlaying();

    /**
     * 开始或恢复播放。
     * 此方法首先尝试请求音频焦点。如果成功获取焦点，则注册 {@link #mAudioNoisyReceiver}
     * 并调用 {@link #onPlay()} 执行实际的播放操作。
     */
    public final void play() {
        Log.d(TAG, "play() called - Attempting to start/resume playback.");
        if (mAudioFocusHelper.requestAudioFocus()) {
            Log.d(TAG, "Audio focus granted. Proceeding with play.");
            Log.d("PlayerAdapter", "play"); // Existing log
            registerAudioNoisyReceiver(); // 注册耳机拔出监听
            onPlay(); // 调用子类实现的播放逻辑
        } else {
            Log.w(TAG, "Audio focus request failed. Playback not started.");
        }
    }

    /**
     * 执行实际的播放操作。
     * 子类需要实现此方法来启动或恢复其内部播放器的播放。
     * 此方法在成功获取音频焦点后由 {@link #play()} 调用。
     */
    protected abstract void onPlay();

    /**
     * 暂停播放。
     * 此方法会注销 {@link #mAudioNoisyReceiver} 并调用 {@link #onPause()} 执行实际的暂停操作。
     * 注意：原始代码中关于 mPlayOnAudioFocus 的注释掉的逻辑 (`//mAudioFocusHelper.abandonAudioFocus();`)
     * 意味着在主动暂停时可能不会立即放弃焦点，这取决于具体的焦点管理策略。
     * 通常，主动暂停也应该考虑是否释放焦点，或者至少更新焦点状态。
     */
    public final void pause() {
        Log.d(TAG, "pause() called - Pausing playback.");
        // 原始代码中有一个条件判断 `if (!mPlayOnAudioFocus)`，然后注释掉了 `abandonAudioFocus()`。
        // 这可能意味着只有在不是因为失去焦点而暂停时，才考虑放弃焦点。
        // 然而，通常用户主动暂停时，也应该释放焦点或至少通知系统不再需要独占焦点。
        // 如果 mPlayOnAudioFocus 为 true，表示是因暂时失去焦点而暂停，此时不应主动放弃焦点。
        // 如果是用户主动暂停，可以考虑放弃焦点，或者至少允许其他应用短暂使用焦点。
        // 为了简单起见，这里不主动放弃焦点，焦点管理主要由 AudioFocusHelper 处理。
        // if (!mPlayOnAudioFocus) {
        //     // Consider abandoning audio focus if pause is user-initiated and not due to transient loss
        //     // mAudioFocusHelper.abandonAudioFocus();
        // }
        Log.d("MediaPlayerAdapter", "pause"); // Existing log, note: uses different TAG (MediaPlayerAdapter)
        unregisterAudioNoisyReceiver(); // 注销耳机拔出监听
        onPause(); // 调用子类实现的暂停逻辑
    }

    /**
     * 执行实际的暂停操作。
     * 子类需要实现此方法来暂停其内部播放器的播放。
     * 此方法由 {@link #pause()} 调用。
     */
    protected abstract void onPause();

    /**
     * 停止播放。
     * 此方法会放弃音频焦点，注销 {@link #mAudioNoisyReceiver}，并调用 {@link #onStop()} 执行实际的停止操作。
     */
    public final void stop() {
        Log.d(TAG, "stop() called - Stopping playback.");
        mAudioFocusHelper.abandonAudioFocus(); // 放弃音频焦点
        unregisterAudioNoisyReceiver(); // 注销耳机拔出监听
        onStop(); // 调用子类实现的停止逻辑
    }

    /**
     * 执行实际的停止操作。
     * 子类需要实现此方法来停止其内部播放器的播放并释放相关资源。
     * 此方法由 {@link #stop()} 调用。
     */
    protected abstract void onStop();

    /**
     * 跳转到媒体流中的指定位置。
     *
     * @param position 要跳转到的位置（毫秒）。
     */
    public abstract void seekTo(long position);

    /**
     * 设置播放音量。
     *
     * @param volume 音量级别，范围通常从 0.0 (静音) 到 1.0 (最大音量)。
     */
    public abstract void setVolume(float volume);


    /**
     * 注册 {@link #mAudioNoisyReceiver} 以监听 {@link AudioManager#ACTION_AUDIO_BECOMING_NOISY} 事件。
     * 仅当接收器尚未注册时才执行注册。
     */
    private void registerAudioNoisyReceiver() {
        Log.d(TAG, "registerAudioNoisyReceiver called.");
        if (!mAudioNoisyReceiverRegistered) {
            Log.d(TAG, "Registering ACTION_AUDIO_BECOMING_NOISY receiver.");
            mApplicationContext.registerReceiver(mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER);
            mAudioNoisyReceiverRegistered = true;
        } else {
            Log.d(TAG, "Audio noisy receiver already registered.");
        }
    }

    /**
     * 注销 {@link #mAudioNoisyReceiver}。
     * 仅当接收器已注册时才执行注销。
     */
    private void unregisterAudioNoisyReceiver() {
        Log.d(TAG, "unregisterAudioNoisyReceiver called.");
        if (mAudioNoisyReceiverRegistered) {
            Log.d(TAG, "Unregistering ACTION_AUDIO_BECOMING_NOISY receiver.");
            try {
                mApplicationContext.unregisterReceiver(mAudioNoisyReceiver);
            } catch (IllegalArgumentException e) {
                // 如果接收器未注册，unregisterReceiver 可能会抛出 IllegalArgumentException。
                // 尽管 mAudioNoisyReceiverRegistered 应该能防止这种情况，但捕获以增加健壮性。
                Log.w(TAG, "Error unregistering mAudioNoisyReceiver. It might have already been unregistered.", e);
            }
            mAudioNoisyReceiverRegistered = false;
        } else {
            Log.d(TAG, "Audio noisy receiver not registered, no need to unregister.");
        }
    }

    /**
     * AudioFocusHelper 是一个内部类，用于封装与音频焦点管理相关的逻辑。
     * 它实现了 {@link AudioManager.OnAudioFocusChangeListener} 接口来响应音频焦点的变化。
     */
    private final class AudioFocusHelper
            implements AudioManager.OnAudioFocusChangeListener {
        // 隐式构造函数

        /**
         * 请求音频焦点。
         *
         * @return 如果成功获取音频焦点，则返回 true；否则返回 false。
         */
        private boolean requestAudioFocus() {
            Log.d(TAG, "AudioFocusHelper.requestAudioFocus called.");
            // 请求永久音频焦点 (AUDIOFOCUS_GAIN) 用于音乐播放 (STREAM_MUSIC)
            final int result = mAudioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            Log.d("PlayerAdapter", "requestAudioFocus result: " + result); // Existing log, added result for clarity
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        /**
         * 放弃音频焦点。
         */
        private void abandonAudioFocus() {
            Log.d(TAG, "AudioFocusHelper.abandonAudioFocus called.");
            mAudioManager.abandonAudioFocus(this);
        }

        /**
         * 当音频焦点状态发生改变时由系统调用。
         *
         * @param focusChange 新的音频焦点状态。
         *                    可能是 {@link AudioManager#AUDIOFOCUS_GAIN},
         *                    {@link AudioManager#AUDIOFOCUS_LOSS},
         *                    {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT},
         *                    或 {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}。
         */
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "AudioFocusHelper.onAudioFocusChange called with focusChange: " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    // 重新获得了音频焦点
                    Log.d(TAG, "Audio focus gained (AUDIOFOCUS_GAIN).");
                    Log.d("PlayerAdapter", "AUDIOFOCUS_GAIN"); // Existing log
                    if (mPlayOnAudioFocus && !isPlaying()) {
                        // 如果之前因为暂时失去焦点而暂停，并且现在可以播放，则恢复播放
                        Log.d(TAG, "Resuming playback due to mPlayOnAudioFocus being true.");
                        play();
                    } else if (isPlaying()) {
                        // 如果正在播放（例如，之前是 ducking 状态），则恢复正常音量
                        Log.d(TAG, "Setting volume to default because already playing.");
                        setVolume(MEDIA_VOLUME_DEFAULT);
                    }
                    mPlayOnAudioFocus = false; // 重置标志
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // 暂时失去焦点，但允许继续播放，只是需要降低音量 (ducking)
                    // 例如，导航应用开始播报语音提示
                    Log.d(TAG, "Audio focus lost transiently, can duck (AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK). Lowering volume.");
                    Log.d("PlayerAdapter", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"); // Existing log
                    setVolume(MEDIA_VOLUME_DUCK); // 降低音量
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // 暂时失去焦点，应暂停播放
                    // 例如，接听电话
                    Log.d(TAG, "Audio focus lost transiently (AUDIOFOCUS_LOSS_TRANSIENT). Pausing playback.");
                    Log.d("PlayerAdapter", "AUDIOFOCUS_LOSS_TRANSIENT"); // Existing log
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true; // 设置标志，以便在重新获得焦点时恢复播放
                        Log.d(TAG, "Setting mPlayOnAudioFocus to true and pausing.");
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    // 永久失去音频焦点，应停止播放并释放资源
                    // 例如，另一个音乐应用开始播放
                    Log.d(TAG, "Audio focus lost permanently (AUDIOFOCUS_LOSS). Stopping playback.");
                    Log.d("PlayerAdapter", "AUDIOFOCUS_LOSS"); // Existing log
                    mAudioManager.abandonAudioFocus(this); // 确保放弃焦点
                    mPlayOnAudioFocus = false; // 重置标志
                    stop(); // 停止播放
                    break;
                default:
                    Log.w(TAG, "Unhandled audio focus change: " + focusChange);
            }
        }
    }
}
