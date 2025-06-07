package com.lazy.mediasessiontest.service.players;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log; // Already present

import com.lazy.mediasessiontest.service.PlaybackInfoListener;
import com.lazy.mediasessiontest.service.PlayerAdapter;
import com.lazy.mediasessiontest.service.contentcatalogs.MusicLibrary;

/**
 * MediaPlayerAdapter 是一个 {@link PlayerAdapter} 的实现，它使用 Android 内置的 {@link MediaPlayer} 类来播放本地媒体文件。
 * 这个适配器负责管理 MediaPlayer 的生命周期、处理播放控制命令（播放、暂停、停止、跳转等），
 * 以及通过 {@link PlaybackInfoListener} 回调通知播放状态的变化。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>封装 {@link MediaPlayer} 的核心播放逻辑。</li>
 *     <li>从应用的 assets 目录加载和播放音频文件。</li>
 *     <li>实现 {@link PlayerAdapter} 定义的播放控制接口。</li>
 *     <li>管理播放状态 ({@link PlaybackStateCompat}) 并通知监听器。</li>
 *     <li>处理 MediaPlayer 的生命周期，包括初始化、准备、播放、暂停、停止和释放。</li>
 * </ul>
 *
 * @author xu
 * @date 2021/2/24 16:48
 * @description 使用 MediaPlayer 实现的播放器适配器
 */
public final class MediaPlayerAdapter extends PlayerAdapter {
    private static final String TAG = "MyMediaSeeionTestMediaPlayerAdapter"; // 日志标签

    private final Context mContext;
    private MediaPlayer mMediaPlayer;
    /**
     * 当前正在播放或准备播放的媒体文件名 (位于 assets 目录下)。
     */
    private String mFilename;
    /**
     * 播放信息回调监听器，用于通知播放状态的变化。
     */
    private PlaybackInfoListener mPlaybackInfoListener;
    /**
     * 当前正在播放或已加载的媒体元数据。
     */
    private MediaMetadataCompat mCurrentMedia;
    /**
     * 当前播放器的状态，使用 {@link PlaybackStateCompat} 中定义的常量。
     */
    private int mState;
    /**
     * 标记当前媒体是否已经播放到完成。
     * 用于处理在播放完成后再次播放同一媒体的情况。
     */
    private boolean mCurrentMediaPlayedToCompletion;

    /**
     * 存储当播放器未处于播放状态时（例如暂停或停止时）调用 seekTo 的目标位置。
     * 当播放器稍后开始播放时，会首先跳转到这个位置。
     * 如果值为 -1，表示没有待处理的跳转请求。
     */
    private int mSeekWhileNotPlaying = -1;

    /**
     * MediaPlayerAdapter 的构造函数。
     *
     * @param context  应用程序上下文。
     * @param listener 播放信息回调监听器。
     */
    public MediaPlayerAdapter(Context context, PlaybackInfoListener listener) {
        super(context); // 调用父类构造函数
        Log.d(TAG, "MediaPlayerAdapter constructor called.");
        Log.d("MediaPlayerAdapter", "new MediaPlayerAdapter"); // Existing log, kept for compatibility if needed
        mContext = context.getApplicationContext(); // 使用 ApplicationContext 避免内存泄漏
        mPlaybackInfoListener = listener;
        mState = PlaybackStateCompat.STATE_NONE; // 初始状态
    }

    /**
     * 初始化 MediaPlayer 实例。
     * 如果 mMediaPlayer 为 null，则创建一个新的实例并设置完成监听器。
     * 完成监听器在当前媒体播放完成时被调用。
     */
    private void initializeMediaPlayer() {
        Log.d(TAG, "initializeMediaPlayer called.");
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            Log.d(TAG, "New MediaPlayer instance created.");
            Log.d("MediaPlayerAdapter", "new MediaPlayer"); // Existing log
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                /**
                 * 当 MediaPlayer 到达媒体源的末尾时调用。
                 *
                 * @param mediaPlayer 完成播放的 MediaPlayer 实例。
                 */
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    Log.d(TAG, "mMediaPlayer.onCompletion called. Media playback completed.");
                    // 通知监听器播放已完成
                    mPlaybackInfoListener.onPlaybackCompleted();
                    // 将播放器状态设置为暂停 (或停止，取决于期望的行为)
                    // 通常，播放完成后状态应为 PAUSED 或 STOPPED，允许用户重新播放或播放下一首。
                    // 这里设置为 PAUSED，如果需要停止并释放资源，则应为 STOPPED。
                    mCurrentMediaPlayedToCompletion = true; // 标记已播放完成
                    setNewState(PlaybackStateCompat.STATE_PAUSED); // 更新状态
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer.onError called. what: " + what + ", extra: " + extra);
                    // 发生错误，通常需要释放 MediaPlayer 并通知错误
                    mPlaybackInfoListener.onError("MediaPlayer error: " + what + " " + extra);
                    setNewState(PlaybackStateCompat.STATE_ERROR);
                    release(); // 释放资源
                    return true; // 返回 true 表示已处理错误
                }
            });
        }
    }

    /**
     * 从给定的媒体元数据开始播放。
     * 此方法会获取媒体ID，然后找到对应的文件名并开始播放。
     *
     * @param metadata 要播放的媒体的 {@link MediaMetadataCompat} 对象。
     */
    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {
        Log.d(TAG, "playFromMedia called with mediaId: " + (metadata != null ? metadata.getDescription().getMediaId() : "null"));
        if (metadata == null) {
            Log.e(TAG, "playFromMedia: metadata is null, cannot play.");
            return;
        }
        mCurrentMedia = metadata;
        Log.d("MediaPlayerAdapter", "playFromMedia"); // Existing log
        final String mediaId = metadata.getDescription().getMediaId();
        String filename = MusicLibrary.getMusicFilename(mediaId);
        if (filename == null) {
            Log.e(TAG, "playFromMedia: Could not find music filename for mediaId: " + mediaId);
            // 可以通知错误或设置错误状态
            mPlaybackInfoListener.onError("Cannot find media file for " + mediaId);
            setNewState(PlaybackStateCompat.STATE_ERROR);
            return;
        }
        playFile(filename);
    }

    /**
     * 获取当前正在播放或已加载的媒体元数据。
     *
     * @return 当前的 {@link MediaMetadataCompat} 对象；如果没有当前媒体，则返回 null。
     */
    @Override
    public MediaMetadataCompat getCurrentMedia() {
        Log.d(TAG, "getCurrentMedia called. Returning: " + (mCurrentMedia != null ? mCurrentMedia.getDescription().getMediaId() : "null"));
        return mCurrentMedia;
    }

    /**
     * 根据给定的文件名播放位于 assets 目录下的音频文件。
     * 此方法处理 MediaPlayer 的初始化、数据源设置、准备和开始播放的逻辑。
     *
     * @param filename 要播放的音频文件名 (相对于 assets 目录)。
     */
    private void playFile(String filename) {
        Log.d(TAG, "playFile called with filename: " + filename);
        Log.d("MediaPlayerAdapter", "playFile  " + filename); // Existing log

        if (filename == null || filename.isEmpty()) {
            Log.e(TAG, "playFile: filename is null or empty.");
            mPlaybackInfoListener.onError("Invalid filename for playback.");
            setNewState(PlaybackStateCompat.STATE_ERROR);
            return;
        }

        // 检查媒体文件是否已更改，或者上一个文件是否已播放完成（需要重新加载）
        boolean mediaChanged = (mFilename == null || !filename.equals(mFilename));
        if (mCurrentMediaPlayedToCompletion) {
            // 上一个音频文件已播放完成，即使资源ID没有改变，
            // 但播放器可能已被释放，因此强制重新加载媒体文件进行播放。
            Log.d(TAG, "playFile: Current media was played to completion, forcing reload.");
            mediaChanged = true;
            mCurrentMediaPlayedToCompletion = false; // 重置标志
        }

        if (!mediaChanged) {
            // 文件未更改，如果当前未播放，则开始播放
            if (!isPlaying()) {
                Log.d(TAG, "playFile: Media not changed and not playing, calling play().");
                play();
            } else {
                Log.d(TAG, "playFile: Media not changed and already playing.");
            }
            return;
        } else {
            // 媒体已更改或需要重新加载，释放旧的 MediaPlayer 实例
            Log.d(TAG, "playFile: Media changed or needs reload, releasing previous MediaPlayer.");
            release();
        }

        mFilename = filename; // 更新当前文件名

        initializeMediaPlayer(); // 确保 MediaPlayer 已初始化

        try {
            Log.d(TAG, "playFile: Opening AssetFileDescriptor for: " + mFilename);
            AssetFileDescriptor assetFileDescriptor = mContext.getAssets().openFd(mFilename);
            // 设置 MediaPlayer 的数据源
            mMediaPlayer.setDataSource(
                    assetFileDescriptor.getFileDescriptor(),
                    assetFileDescriptor.getStartOffset(),
                    assetFileDescriptor.getLength());
            assetFileDescriptor.close(); // 关闭 AssetFileDescriptor
            Log.d(TAG, "playFile: MediaPlayer data source set.");
        } catch (Exception e) {
            Log.e(TAG, "playFile: Failed to set MediaPlayer data source for file: " + mFilename, e);
            // throw new RuntimeException("Failed to open file: " + mFilename, e); // 原始代码抛出运行时异常
            mPlaybackInfoListener.onError("Failed to open file: " + mFilename);
            setNewState(PlaybackStateCompat.STATE_ERROR);
            release();
            return;
        }

        try {
            Log.d(TAG, "playFile: Preparing MediaPlayer.");
            mMediaPlayer.prepare(); // 准备 MediaPlayer 进行播放 (同步操作)
            Log.d(TAG, "playFile: MediaPlayer prepared.");
        } catch (Exception e) {
            Log.e(TAG, "playFile: Failed to prepare MediaPlayer for file: " + mFilename, e);
            // throw new RuntimeException("Failed to open file: " + mFilename, e); // 原始代码抛出运行时异常
            mPlaybackInfoListener.onError("Failed to prepare file: " + mFilename);
            setNewState(PlaybackStateCompat.STATE_ERROR);
            release();
            return;
        }

        // 准备完成后开始播放
        play();
    }

    /**
     * 停止播放并释放 MediaPlayer 资源。
     * 状态将更新为 {@link PlaybackStateCompat#STATE_STOPPED}。
     */
    @Override
    public void onStop() {
        Log.d(TAG, "onStop called.");
        // 无论 MediaPlayer 是否已创建/启动，都必须更新状态，
        // 以便 MediaNotificationManager 可以移除通知。
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release(); // 释放 MediaPlayer 资源
    }

    /**
     * 释放 MediaPlayer 资源。
     * 如果 mMediaPlayer 不为 null，则调用其 release() 方法并将其设置为 null。
     */
    private void release() {
        Log.d(TAG, "release called.");
        if (mMediaPlayer != null) {
            Log.d(TAG, "Releasing MediaPlayer instance.");
            mMediaPlayer.release(); // 释放 MediaPlayer 占用的资源
            mMediaPlayer = null;    // 将引用置为 null，以便垃圾回收和下次重新初始化
            mFilename = null;       // 清除当前文件名
            mCurrentMedia = null;   // 清除当前媒体信息
            mSeekWhileNotPlaying = -1; // 清除待处理的跳转
        } else {
            Log.d(TAG, "MediaPlayer instance is already null, no need to release.");
        }
    }

    /**
     * 检查 MediaPlayer 当前是否正在播放。
     *
     * @return 如果 MediaPlayer 存在并且正在播放，则返回 true；否则返回 false。
     */
    @Override
    public boolean isPlaying() {
        boolean playing = mMediaPlayer != null && mMediaPlayer.isPlaying();
        Log.d(TAG, "isPlaying called. Result: " + playing);
        return playing;
    }

    /**
     * 开始或恢复播放。
     * 如果 MediaPlayer 存在且未在播放，则调用其 start() 方法并更新状态。
     */
    @Override
    protected void onPlay() {
        Log.d(TAG, "onPlay called.");
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            Log.d(TAG, "MediaPlayer is not null and not playing, starting playback.");
            if (mSeekWhileNotPlaying != -1) {
                Log.d(TAG, "onPlay: Applying pending seek to " + mSeekWhileNotPlaying + "ms before starting.");
                mMediaPlayer.seekTo(mSeekWhileNotPlaying);
                // mSeekWhileNotPlaying 会在 setNewState 中被重置
            }
            mMediaPlayer.start(); // 开始或恢复播放
            setNewState(PlaybackStateCompat.STATE_PLAYING); // 更新状态为播放中
        } else {
            if (mMediaPlayer == null) {
                Log.w(TAG, "onPlay: MediaPlayer is null, cannot start playback.");
            } else {
                Log.d(TAG, "onPlay: MediaPlayer is already playing or in an invalid state to start.");
            }
        }
    }

    /**
     * 暂停播放。
     * 如果 MediaPlayer 存在且正在播放，则调用其 pause() 方法并更新状态。
     */
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause called.");
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            Log.d(TAG, "MediaPlayer is not null and playing, pausing playback.");
            mMediaPlayer.pause(); // 暂停播放
            setNewState(PlaybackStateCompat.STATE_PAUSED); // 更新状态为暂停
        } else {
            if (mMediaPlayer == null) {
                Log.w(TAG, "onPause: MediaPlayer is null, cannot pause.");
            } else {
                Log.d(TAG, "onPause: MediaPlayer is not playing or in an invalid state to pause.");
            }
        }
    }

    /**
     * 更新播放器的内部状态，并通知 {@link PlaybackInfoListener} 播放状态已更改。
     *
     * @param newPlayerState 新的播放器状态，应为 {@link PlaybackStateCompat} 中定义的常量之一。
     */
    private void setNewState(@PlaybackStateCompat.State int newPlayerState) {
        Log.d(TAG, "setNewState called. Old state: " + mState + ", New state: " + newPlayerState);
        mState = newPlayerState;

        // 无论是播放完成还是被停止，mCurrentMediaPlayedToCompletion 都设置为 true。
        // 这似乎有点反直觉，因为 "stopped" 不一定意味着 "played to completion"。
        // 这个逻辑可能需要根据具体需求调整。
        // 如果停止时也标记为完成，那么下次播放同一文件时会强制重新加载。
        if (mState == PlaybackStateCompat.STATE_STOPPED) {
            Log.d(TAG, "setNewState: State is STOPPED, setting mCurrentMediaPlayedToCompletion to true.");
            mCurrentMediaPlayedToCompletion = true;
        }

        // 解决 MediaPlayer.getCurrentPosition() 在未播放时可能变化的问题。
        // 如果在未播放时有跳转请求 (mSeekWhileNotPlaying >= 0)，则报告该位置。
        final long reportPosition;
        if (mSeekWhileNotPlaying >= 0) {
            reportPosition = mSeekWhileNotPlaying;
            Log.d(TAG, "setNewState: Reporting position from mSeekWhileNotPlaying: " + reportPosition);

            // 如果新的状态是播放中，则重置 mSeekWhileNotPlaying，因为跳转已（或即将）应用。
            if (mState == PlaybackStateCompat.STATE_PLAYING) {
                Log.d(TAG, "setNewState: State changed to PLAYING, resetting mSeekWhileNotPlaying.");
                mSeekWhileNotPlaying = -1;
            }
        } else {
            // 否则，报告 MediaPlayer 的当前位置（如果可用）。
            reportPosition = mMediaPlayer == null ? 0 : mMediaPlayer.getCurrentPosition();
            Log.d(TAG, "setNewState: Reporting position from MediaPlayer: " + reportPosition);
        }

        // 构建新的 PlaybackStateCompat 对象
        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        // 设置可用的播放操作
        stateBuilder.setActions(getAvailableActions());
        // 设置当前状态、报告的位置、播放速度和更新时间
        stateBuilder.setState(mState,
                reportPosition,
                1.0f, // 播放速度，1.0f 表示正常速度
                SystemClock.elapsedRealtime()); // 事件发生的时间戳
        Log.d(TAG, "setNewState: Notifying PlaybackInfoListener of state change.");
        // 通知监听器播放状态已更改
        mPlaybackInfoListener.onPlaybackStateChange(stateBuilder.build());
    }

    /**
     * 获取当前会话可用的播放操作。
     * 注意：如果某个功能未在此处返回的位掩码中列出，则 MediaSession 将不会处理它。
     * 例如，如果不希望 MediaSession 处理 ACTION_STOP，则不要将其包含在返回的位掩码中。
     *
     * @return 一个包含当前可用操作的位掩码 (long 类型)。
     */
    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        // 默认可用的基本操作
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID  // 从媒体ID播放
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH      // 从搜索结果播放
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT          // 跳到下一首
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS      // 跳到上一首
                | PlaybackStateCompat.ACTION_SEEK_TO;              // 跳转到指定位置

        // 根据当前播放器状态，添加或移除特定的操作
        switch (mState) {
            case PlaybackStateCompat.STATE_STOPPED:
                // 停止状态下：可以播放、暂停 (暂停在这里可能意义不大，但通常与播放一起提供)
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                // 播放状态下：可以停止、暂停、跳转
                actions |= PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SEEK_TO; // SEEK_TO 已在默认中，这里重复是安全的
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                // 暂停状态下：可以播放、播放/暂停切换、停止、跳转
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE // 切换播放/暂停状态
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_SEEK_TO; // SEEK_TO 已在默认中
                break;
            default:
                // 其他状态 (如 NONE, CONNECTING, BUFFERING, ERROR)：通常允许播放、暂停、停止
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
        }
        Log.d(TAG, "getAvailableActions called for state " + mState + ". Returning actions: " + actions);
        return actions;
    }

    /**
     * 跳转到媒体流中的指定位置。
     *
     * @param position 要跳转到的位置（毫秒）。
     */
    @Override
    public void seekTo(long position) {
        Log.d(TAG, "seekTo called with position: " + position);
        if (mMediaPlayer != null) {
            if (!mMediaPlayer.isPlaying()) {
                // 如果 MediaPlayer 未在播放，则记录跳转位置，待播放开始时应用
                Log.d(TAG, "seekTo: MediaPlayer is not playing. Storing seek position: " + position);
                mSeekWhileNotPlaying = (int) position;
            }
            Log.d(TAG, "seekTo: Seeking MediaPlayer to " + position);
            mMediaPlayer.seekTo((int) position); // 执行跳转

            // 设置状态（到当前状态），因为位置已更改，应报告给客户端。
            // 这会触发 PlaybackStateCompat 的更新，其中包含新的位置信息。
            Log.d(TAG, "seekTo: Calling setNewState to report new position.");
            setNewState(mState);
        } else {
            Log.w(TAG, "seekTo: MediaPlayer is null, cannot seek.");
        }
    }

    /**
     * 设置播放音量。
     *
     * @param volume 音量级别，范围从 0.0 (静音) 到 1.0 (最大音量)。
     */
    @Override
    public void setVolume(float volume) {
        Log.d(TAG, "setVolume called with volume: " + volume);
        if (mMediaPlayer != null) {
            // MediaPlayer.setVolume 需要两个参数：左声道音量和右声道音量
            Log.d(TAG, "setVolume: Setting MediaPlayer volume to " + volume);
            mMediaPlayer.setVolume(volume, volume);
        } else {
            Log.w(TAG, "setVolume: MediaPlayer is null, cannot set volume.");
        }
    }
}
