package com.lazy.mediasessiontest.service;

/**
 * MusicService 是一个 {@link MediaBrowserServiceCompat}，用于在后台处理音乐播放。
 * 它管理 {@link MediaSessionCompat}、播放器适配器 ({@link PlayerAdapter}) 和媒体通知 ({@link MediaNotificationManager})。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>作为媒体浏览器服务，允许客户端（如媒体浏览器应用）连接并浏览媒体内容。</li>
 *     <li>管理媒体会话 ({@link MediaSessionCompat})，处理来自媒体控制器和媒体按钮的命令。</li>
 *     <li>使用 {@link PlayerAdapter} (具体实现为 {@link MediaPlayerAdapter}) 来执行实际的媒体播放。</li>
 *     <li>通过 {@link MediaNotificationManager} 显示和更新媒体播放通知。</li>
 *     <li>处理服务的生命周期，包括创建、启动、停止和销毁。</li>
 *     <li>管理播放队列和当前播放的媒体项。</li>
 * </ul>
 *
 * @author xu
 * @date 2021/2/24 16:41
 * @description 媒体浏览器服务 音乐播放器服务
 */

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log; // Added if not present
import android.view.KeyEvent;

import com.lazy.mediasessiontest.service.notifications.MediaNotificationManager;
import com.lazy.mediasessiontest.service.players.MediaPlayerAdapter;
import com.lazy.mediasessiontest.service.contentcatalogs.MusicLibrary;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

public class MusicService extends MediaBrowserServiceCompat {

    private static final String TAG = "MyMediaSeeionTestMusicService"; // 日志标签

    /**
     * 媒体会话 ({@link MediaSessionCompat})，用于与媒体控制器和系统交互。
     */
    private MediaSessionCompat mSession;
    /**
     * 播放器适配器 ({@link PlayerAdapter})，封装了实际的媒体播放逻辑。
     * 在此实现中，使用的是 {@link MediaPlayerAdapter}。
     */
    private PlayerAdapter mPlayback;
    /**
     * 媒体通知管理器 ({@link MediaNotificationManager})，用于创建和管理媒体播放通知。
     */
    private MediaNotificationManager mMediaNotificationManager;
    /**
     * 标记服务是否处于“启动状态”（即正在作为前台服务运行）。
     */
    private boolean mServiceInStartedState;

    /**
     * 当服务首次创建时调用。
     * 初始化媒体会话、播放器适配器和通知管理器。
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate called - MusicService is being created.");
        super.onCreate();
        // 创建媒体会话
        // 参数1: Context
        // 参数2: 日志标签，用于调试
        mSession = new MediaSessionCompat(this, "MusicService");
        // 设置媒体会话为活动状态，使其能够接收媒体按钮事件
        mSession.setActive(true);
        // 设置媒体会话的回调，用于处理来自媒体控制器和媒体按钮的命令
        MediaSessionCallback mCallback = new MediaSessionCallback();
        mSession.setCallback(mCallback);
        // 设置媒体会话的标志，指示它处理哪些类型的事件
        mSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |         // 处理媒体按钮事件 (例如耳机按钮)
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS |   // 处理队列相关的命令 (例如 onAddQueueItem)
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS); // 处理传输控制命令 (例如 onPlay, onPause, onStop)

        // 将此服务的会话令牌设置给 MediaBrowserServiceCompat，
        // 这样客户端（如媒体浏览器应用）就可以获取到这个令牌并与媒体会话交互。
        setSessionToken(mSession.getSessionToken());

        // 设置 MediaButtonReceiver 的 PendingIntent (可选，但推荐用于某些平台)
        // Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        // mediaButtonIntent.setClass(this, MediaButtonReceiver.class); // 确保指向正确的接收器
        // PendingIntent pendingIntent = PendingIntent.getBroadcast(
        // this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        // mSession.setMediaButtonReceiver(pendingIntent);
        // 注意：从 Android 5.0（API 21）开始，系统推荐使用 MediaSessionCompat.setCallback()
        // 来直接接收媒体控制事件。然而，在某些平台（如 Android Automotive、使用蓝牙遥控等情况），
        // 仍需要显式设置 MediaButtonReceiver 才能确保接收媒体键。
        // 如果使用 androidx.media.session.MediaButtonReceiver，它会自动查找服务。

        // 初始化媒体通知管理器
        mMediaNotificationManager = new MediaNotificationManager(this);
        // 初始化播放器适配器，并传入一个 MediaPlayerListener 来接收播放状态的回调
        mPlayback = new MediaPlayerAdapter(this, new MediaPlayerListener());
        Log.d(TAG, "onCreate: MusicService created. MediaSession, NotificationManager, and PlayerAdapter initialized.");
    }

    /**
     * 当应用的任务从最近任务列表中移除时调用。
     * 在这种情况下，服务应该停止自身。
     *
     * @param rootIntent 触发任务移除的 Intent。
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved called - Task is being removed from recents.");
        super.onTaskRemoved(rootIntent);
        // 停止服务
        stopSelf();
        Log.d(TAG, "onTaskRemoved: Service stopped itself.");
    }

    /**
     * 当服务即将被销毁时调用。
     * 执行必要的清理操作，如释放媒体会话和停止播放器。
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called - MusicService is being destroyed.");
        // 清理通知管理器资源
        mMediaNotificationManager.onDestroy();
        // 停止播放并释放播放器资源
        mPlayback.stop();
        // 释放媒体会话资源
        mSession.release();
        Log.d(TAG, "onDestroy: PlayerAdapter stopped, MediaNotificationManager destroyed, and MediaSession released.");
        super.onDestroy(); //确保调用父类的onDestroy
    }

    /**
     * 当客户端（媒体浏览器应用）连接到此服务时调用。
     * 返回内容层次结构的根节点。如果此方法返回 null，则会拒绝连接。
     *
     * @param clientPackageName 连接客户端的包名。
     * @param clientUid         连接客户端的用户ID。
     * @param rootHints         一个包含客户端传递的附加信息的 Bundle，可能为 null。
     * @return {@link BrowserRoot} 对象，表示媒体内容层次结构的根。
     *         根ID是必需的，附加信息 (extras) 是可选的。
     */
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        Log.d(TAG, "onGetRoot called by client: " + clientPackageName + " (UID: " + clientUid + ")");
        // 返回媒体库的根ID。这里使用 MusicLibrary.getRoot() 获取。
        // 第二个参数 Bundle extras 可以用于传递附加信息给客户端，这里为 null。
        String rootId = MusicLibrary.getRoot();
        Log.d(TAG, "onGetRoot: Returning BrowserRoot with rootId: " + rootId);
        return new BrowserRoot(rootId, null);
    }

    /**
     * 当客户端请求加载指定父媒体ID的子媒体项时调用。
     *
     * @param parentMediaId 要加载其子项的父媒体ID。
     * @param result        一个 {@link Result} 对象，用于将加载的媒体项列表发送回客户端。
     *                      必须调用 result.sendResult() 或 result.detach()。
     */
    @Override
    public void onLoadChildren(
            @NonNull final String parentMediaId,
            @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.d(TAG, "onLoadChildren called for parentMediaId: " + parentMediaId);
        // 在这个示例中，我们只有一个扁平的媒体列表，不依赖于 parentMediaId。
        // 对于更复杂的媒体库，这里会根据 parentMediaId 加载相应的子项。
        List<MediaBrowserCompat.MediaItem> mediaItems = MusicLibrary.getMediaItems();
        Log.d(TAG, "onLoadChildren: Sending " + (mediaItems != null ? mediaItems.size() : 0) + " media items.");
        result.sendResult(mediaItems);
    }

    /**
     * MediaSessionCallback 类处理来自媒体控制器和媒体按钮的命令。
     * 它实现了 {@link MediaSessionCompat.Callback} 接口中定义的方法，
     * 如 onPlay, onPause, onSkipToNext 等，并将这些命令转发给播放器适配器 ({@link PlayerAdapter})。
     */
    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        /**
         * 当前的播放列表。
         */
        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
        /**
         * 当前播放列表中活动项的索引。
         * -1 表示没有活动项或列表为空。
         */
        private int mQueueIndex = -1;
        /**
         * 当前已准备好播放的媒体元数据。
         * 当调用 onPrepare() 或在 onPlay() 之前隐式准备时设置。
         */
        private MediaMetadataCompat mPreparedMedia;

        /**
         * 处理媒体按钮事件。
         *
         * @param mediaButtonEvent 包含媒体按钮事件的 Intent。
         * @return 如果事件已处理，则返回 true；否则调用父类实现。
         */
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent called with intent: " + mediaButtonEvent);
            if (mediaButtonEvent != null && Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonEvent.getAction())) {
                KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null) {
                    Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: KeyEvent keyCode = " + keyEvent.getKeyCode() +
                            ", action = " + (keyEvent.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP"));
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) { // 只处理按下事件
                        switch (keyEvent.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: PLAY key pressed.");
                                onPlay();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: PAUSE key pressed.");
                                onPause();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: STOP key pressed.");
                                onStop();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: NEXT key pressed.");
                                onSkipToNext();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                Log.d(TAG, "MediaSessionCallback.onMediaButtonEvent: PREVIOUS key pressed.");
                                onSkipToPrevious();
                                return true;
                            // 可以添加对其他媒体键的处理，如 KEYCODE_MEDIA_PLAY_PAUSE
                        }
                    }
                }
            }
            // 如果事件未被处理，则调用父类实现
            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        /**
         * 处理自定义命令。
         *
         * @param command 自定义命令的字符串。
         * @param extras  包含命令参数的 Bundle。
         * @param cb      用于发送结果的 ResultReceiver。
         */
        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            Log.d(TAG, "MediaSessionCallback.onCommand called with command: " + command);
            // 示例：处理一个特定的自定义命令
            if ("android.intent.action.MEDIA_PLAY".equals(command)) { // 这是一个标准的播放命令，通常由 onPlay() 处理
                Log.d(TAG, "MediaSessionCallback.onCommand: Received MEDIA_PLAY command, delegating to onPlay().");
                onPlay();
                if (cb != null) {
                    cb.send(0, null); // 发送成功结果
                }
            } else {
                // 对于未知的命令，调用父类实现
                super.onCommand(command, extras, cb);
            }
        }

        /**
         * 将媒体项添加到播放队列。
         *
         * @param description 要添加的媒体项的描述。
         */
        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "MediaSessionCallback.onAddQueueItem called with description: " + description.getTitle());
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            // 如果队列之前为空，则将当前索引设置为0
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
            // 更新媒体会话的队列
            mSession.setQueue(mPlaylist);
            Log.d(TAG, "MediaSessionCallback.onAddQueueItem: Item added. Queue size: " + mPlaylist.size() + ", Current index: " + mQueueIndex);
        }

        /**
         * 从播放队列中移除媒体项。
         *
         * @param description 要移除的媒体项的描述。
         */
        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "MediaSessionCallback.onRemoveQueueItem called with description: " + description.getTitle());
            // 注意：QueueItem 的比较是基于 description 和 id。这里使用 description.hashCode() 作为 id。
            // 确保在添加和移除时使用一致的 id 生成逻辑。
            boolean removed = mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            if (removed) {
                // 如果队列为空，则重置索引；否则，确保索引有效
                if (mPlaylist.isEmpty()) {
                    mQueueIndex = -1;
                } else if (mQueueIndex >= mPlaylist.size()) {
                    // 如果移除了当前项或之后的项，可能需要调整索引
                    mQueueIndex = mPlaylist.size() - 1;
                }
                // 更新媒体会话的队列
                mSession.setQueue(mPlaylist);
                Log.d(TAG, "MediaSessionCallback.onRemoveQueueItem: Item removed. Queue size: " + mPlaylist.size() + ", Current index: " + mQueueIndex);
            } else {
                Log.w(TAG, "MediaSessionCallback.onRemoveQueueItem: Item not found in playlist.");
            }
        }

        /**
         * 准备播放当前队列中的媒体项。
         * 这通常涉及加载媒体元数据并将其设置到媒体会话。
         */
        @Override
        public void onPrepare() {
            Log.d(TAG, "MediaSessionCallback.onPrepare called.");
            if (mQueueIndex < 0 || mPlaylist.isEmpty()) {
                Log.w(TAG, "MediaSessionCallback.onPrepare: Queue is empty or index is invalid. Nothing to prepare.");
                // 可以设置一个错误状态或清除元数据
                mSession.setMetadata(null);
                mPreparedMedia = null;
                return;
            }

            // 获取当前队列项的媒体ID
            final String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
            Log.d(TAG, "MediaSessionCallback.onPrepare: Preparing mediaId: " + mediaId);
            // 从音乐库加载元数据
            mPreparedMedia = MusicLibrary.getMetadata(MusicService.this, mediaId);
            if (mPreparedMedia != null) {
                // 将元数据设置到媒体会话
                mSession.setMetadata(mPreparedMedia);
                Log.d(TAG, "MediaSessionCallback.onPrepare: Metadata set for " + mPreparedMedia.getDescription().getTitle());
            } else {
                Log.e(TAG, "MediaSessionCallback.onPrepare: Failed to get metadata for mediaId: " + mediaId);
                // 处理元数据加载失败的情况
                mSession.setMetadata(null);
            }

            // 确保媒体会话处于活动状态
            if (!mSession.isActive()) {
                mSession.setActive(true);
                Log.d(TAG, "MediaSessionCallback.onPrepare: MediaSession activated.");
            }
        }

        /**
         * 开始或恢复播放。
         * 如果没有准备好的媒体，则先调用 onPrepare()。
         */
        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSessionCallback.onPlay called.");
            if (!isReadyToPlay()) {
                Log.w(TAG, "MediaSessionCallback.onPlay: Not ready to play (e.g., playlist empty).");
                return;
            }

            // 如果当前没有准备好的媒体，或者准备的媒体与队列中的当前项不符，则重新准备
            if (mPreparedMedia == null ||
                    (mQueueIndex >= 0 && mQueueIndex < mPlaylist.size() &&
                            !mPreparedMedia.getDescription().getMediaId().equals(mPlaylist.get(mQueueIndex).getDescription().getMediaId()))) {
                Log.d(TAG, "MediaSessionCallback.onPlay: mPreparedMedia is null or doesn't match current queue item. Calling onPrepare().");
                onPrepare();
            }

            if (mPreparedMedia != null) {
                Log.d(TAG, "MediaSessionCallback.onPlay: Requesting playback for: " + mPreparedMedia.getDescription().getTitle());
                mPlayback.playFromMedia(mPreparedMedia);
                // 播放状态的更新将由 MediaPlayerListener 中的 onPlaybackStateChange 处理
            } else {
                Log.e(TAG, "MediaSessionCallback.onPlay: mPreparedMedia is null after prepare. Cannot play.");
                // 可以设置错误状态
                mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_ERROR, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                        .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, "Failed to prepare media for playback.")
                        .build());
            }
        }

        /**
         * 暂停播放。
         */
        @Override
        public void onPause() {
            Log.d(TAG, "MediaSessionCallback.onPause called.");
            mPlayback.pause();
            // 播放状态的更新将由 MediaPlayerListener 中的 onPlaybackStateChange 处理
            Log.d(TAG, "MediaSessionCallback.onPause: Playback pause requested.");
        }

        /**
         * 停止播放。
         * 通常还会将媒体会话设置为非活动状态。
         */
        @Override
        public void onStop() {
            Log.d(TAG, "MediaSessionCallback.onStop called.");
            mPlayback.stop();
            // 将媒体会话设置为非活动状态，表示不再主动处理媒体控制
            mSession.setActive(false);
            // 播放状态的更新将由 MediaPlayerListener 中的 onPlaybackStateChange 处理
            Log.d(TAG, "MediaSessionCallback.onStop: Playback stop requested and session deactivated.");
        }

        /**
         * 跳到播放队列中的下一首媒体项。
         */
        @Override
        public void onSkipToNext() {
            Log.d(TAG, "MediaSessionCallback.onSkipToNext called.");
            if (mPlaylist.isEmpty()) {
                Log.w(TAG, "MediaSessionCallback.onSkipToNext: Playlist is empty.");
                return;
            }
            // 计算下一首的索引，循环到队列开头
            mQueueIndex = (++mQueueIndex % mPlaylist.size());
            Log.d(TAG, "MediaSessionCallback.onSkipToNext: New queue index: " + mQueueIndex);
            // 清除已准备的媒体，以便 onPlay() 重新准备新的媒体项
            mPreparedMedia = null;
            // 开始播放新的媒体项
            onPlay();
        }

        /**
         * 跳到播放队列中的上一首媒体项。
         */
        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "MediaSessionCallback.onSkipToPrevious called.");
            if (mPlaylist.isEmpty()) {
                Log.w(TAG, "MediaSessionCallback.onSkipToPrevious: Playlist is empty.");
                return;
            }
            // 计算上一首的索引，如果当前是第一首，则循环到队列末尾
            mQueueIndex = (mQueueIndex > 0) ? mQueueIndex - 1 : mPlaylist.size() - 1;
            Log.d(TAG, "MediaSessionCallback.onSkipToPrevious: New queue index: " + mQueueIndex);
            // 清除已准备的媒体
            mPreparedMedia = null;
            // 开始播放新的媒体项
            onPlay();
        }

        /**
         * 跳转到媒体流中的指定位置。
         *
         * @param pos 要跳转到的位置（毫秒）。
         */
        @Override
        public void onSeekTo(long pos) {
            Log.d(TAG, "MediaSessionCallback.onSeekTo called with position: " + pos + "ms.");
            mPlayback.seekTo(pos);
            // 播放状态的更新（包括新位置）将由 MediaPlayerListener 中的 onPlaybackStateChange 处理
        }

        /**
         * 检查是否已准备好开始播放。
         * 通常意味着播放列表不为空。
         *
         * @return 如果可以开始播放，则返回 true；否则返回 false。
         */
        private boolean isReadyToPlay() {
            boolean ready = !mPlaylist.isEmpty();
            Log.d(TAG, "MediaSessionCallback.isReadyToPlay called. Result: " + ready);
            return ready;
        }
    }

    /**
     * MediaPlayerListener 类实现了 {@link PlaybackInfoListener} 接口，
     * 用于接收来自播放器适配器 ({@link PlayerAdapter}) 的播放状态更新和事件。
     * 它负责将这些更新转发给媒体会话 ({@link MediaSessionCompat}) 并管理服务的状态（例如，前台/后台）。
     */
    public class MediaPlayerListener extends PlaybackInfoListener {

        /**
         * ServiceManager 内部类，用于管理服务的启动状态和通知。
         */
        private final ServiceManager mServiceManager;

        /**
         * MediaPlayerListener 的构造函数。
         * 初始化 {@link ServiceManager}。
         */
        MediaPlayerListener() {
            Log.d(TAG, "MediaPlayerListener constructor called.");
            mServiceManager = new ServiceManager();
        }

        /**
         * 当播放器状态改变时调用。
         * 将新的播放状态报告给媒体会话，并相应地管理服务的状态（例如，启动/停止前台服务，更新通知）。
         *
         * @param state 新的播放状态 ({@link PlaybackStateCompat})。
         */
        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            Log.d(TAG, "MediaPlayerListener.onPlaybackStateChange called with state: " + state.getState());
            // 将新的播放状态报告给媒体会话
            mSession.setPlaybackState(state);

            // 根据新的播放状态管理服务的启动状态和通知
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    Log.d(TAG, "MediaPlayerListener.onPlaybackStateChange: State is PLAYING.");
                    mServiceManager.moveServiceToStartedState(state); // 将服务移至前台并显示通知
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    Log.d(TAG, "MediaPlayerListener.onPlaybackStateChange: State is PAUSED.");
                    mServiceManager.updateNotificationForPause(state); // 更新通知为暂停状态，服务可移至后台
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    Log.d(TAG, "MediaPlayerListener.onPlaybackStateChange: State is STOPPED.");
                    mServiceManager.moveServiceOutOfStartedState(state); // 将服务移出前台并可能停止服务
                    break;
                case PlaybackStateCompat.STATE_ERROR:
                    Log.e(TAG, "MediaPlayerListener.onPlaybackStateChange: State is ERROR.");
                    // 错误状态也可能需要停止前台服务
                    mServiceManager.moveServiceOutOfStartedState(state);
                    break;
                default:
                    Log.d(TAG, "MediaPlayerListener.onPlaybackStateChange: Unhandled state: " + state.getState());
            }
        }

        /**
         * 当媒体播放完成时调用。
         * 可以在这里处理播放完成后的逻辑，例如播放下一首或停止服务。
         */
        @Override
        public void onPlaybackCompleted() {
            Log.d(TAG, "MediaPlayerListener.onPlaybackCompleted called.");
            super.onPlaybackCompleted(); // 调用父类实现（如果存在）
            // 可以在这里添加特定逻辑，例如：
            // - 如果有播放列表，自动播放下一首：mSession.getController().getTransportControls().skipToNext();
            // - 如果是单曲播放完成，可以停止服务或更新UI。
            // 当前的实现依赖于 MediaPlayerAdapter 在 onCompletion 时将状态设置为 PAUSED，
            // 然后 onPlaybackStateChange 会处理这个 PAUSED 状态。
        }

        /**
         * ServiceManager 是一个内部辅助类，用于封装与服务生命周期和通知管理相关的逻辑。
         * 例如，将服务转换为前台服务、更新通知内容、将服务移出前台状态等。
         */
        class ServiceManager {
            // 隐式构造函数

            /**
             * 将服务移至“启动状态”（即前台服务），并显示/更新媒体播放通知。
             * 当媒体开始播放时调用。
             *
             * @param state 当前的播放状态 ({@link PlaybackStateCompat})。
             */
            private void moveServiceToStartedState(PlaybackStateCompat state) {
                Log.d(TAG, "ServiceManager.moveServiceToStartedState called.");
                // 获取当前媒体的元数据和会话令牌来构建通知
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayback.getCurrentMedia(), state, getSessionToken());

                if (!mServiceInStartedState) {
                    // 如果服务尚未处于启动状态，则启动它。
                    // ContextCompat.startForegroundService() 用于在 Android O 及更高版本上安全地启动前台服务。
                    // 它会先调用 startService()，然后服务需要在5秒内调用 startForeground()。
                    ContextCompat.startForegroundService(
                            MusicService.this,
                            new Intent(MusicService.this, MusicService.class));
                    mServiceInStartedState = true;
                    Log.d(TAG, "ServiceManager.moveServiceToStartedState: Service started via startForegroundService.");
                }

                // 将服务置于前台，并显示通知。
                // NOTIFICATION_ID 是此通知的唯一ID。
                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
                Log.d(TAG, "ServiceManager.moveServiceToStartedState: Service moved to foreground with notification ID: " + MediaNotificationManager.NOTIFICATION_ID);
            }

            /**
             * 当媒体暂停时更新通知。
             * 服务可以从前台状态移出（stopForeground(false)），但通知仍然可见。
             *
             * @param state 当前的播放状态 ({@link PlaybackStateCompat})，应为 PAUSED。
             */
            private void updateNotificationForPause(PlaybackStateCompat state) {
                Log.d(TAG, "ServiceManager.updateNotificationForPause called.");
                // 将服务从前台状态移出，但保留通知。
                // 参数 false 表示通知不会被移除。
                stopForeground(false);
                Log.d(TAG, "ServiceManager.updateNotificationForPause: Service removed from foreground (notification kept).");

                // 获取更新后的通知（例如，显示“播放”按钮而不是“暂停”按钮）
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mPlayback.getCurrentMedia(), state, getSessionToken());
                // 使用 NotificationManager 更新通知
                mMediaNotificationManager.getNotificationManager()
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
                Log.d(TAG, "ServiceManager.updateNotificationForPause: Notification updated for PAUSED state.");
            }

            /**
             * 将服务移出“启动状态”。
             * 当媒体停止或发生错误时调用。服务将停止前台状态，通知将被移除，并且服务可能会停止自身。
             *
             * @param state 当前的播放状态 ({@link PlaybackStateCompat})。
             */
            private void moveServiceOutOfStartedState(PlaybackStateCompat state) {
                Log.d(TAG, "ServiceManager.moveServiceOutOfStartedState called.");
                // 将服务从前台状态移出，并移除通知。
                // 参数 true 表示通知将被移除。
                stopForeground(true);
                Log.d(TAG, "ServiceManager.moveServiceOutOfStartedState: Service removed from foreground (notification removed).");

                // 停止服务自身
                stopSelf();
                mServiceInStartedState = false; // 更新服务状态标记
                Log.d(TAG, "ServiceManager.moveServiceOutOfStartedState: Service stopped itself. mServiceInStartedState set to false.");
            }
        }
    }
}