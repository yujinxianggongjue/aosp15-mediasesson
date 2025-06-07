package com.lazy.mediasessiontest.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.AlertDialog; // For AlertDialog.Builder
import android.content.Intent; // For Intent to notification settings

import com.lazy.mediasessiontest.R;
import com.lazy.mediasessiontest.client.MediaBrowserHelper;
import com.lazy.mediasessiontest.client.MediaSessionManager;
import com.lazy.mediasessiontest.service.MusicService;
import com.lazy.mediasessiontest.service.contentcatalogs.MusicLibrary;

import java.util.List;

/**
 * MainActivity 是应用的主界面，用于显示当前播放的媒体信息和提供播放控制。
 * 它通过 {@link MediaBrowserHelper} 连接到 {@link MusicService}，
 * 并使用 {@link MediaControllerCompat} 来控制播放和接收状态更新。
 * 同时，它也使用 {@link MediaSessionManager} 来获取其他应用正在播放的媒体信息。
 */
public class MainActivity extends AppCompatActivity {
    /**
     * 日志标签，用于在此类中记录日志。
     */
    private static final String TAG = "MyMediaSeeionTestMainActivity";

    // UI 元素
    private ImageView mAlbumArt; // 显示专辑封面
    private TextView mTitleTextView; // 显示歌曲标题
    private TextView mArtistTextView; // 显示艺术家名称
    private ImageView mMediaControlsImage; // 播放/暂停按钮的图标 (根据状态变化)
    private MediaSeekBar mSeekBarAudio; // 显示和控制播放进度的拖动条

    // 媒体浏览和会话管理
    private MediaBrowserHelper mMediaBrowserHelper; // 辅助类，用于连接到 MediaBrowserService (MusicService)
    private MediaSessionManager mMediaSessionManager; // 辅助类，用于获取系统上其他活动的媒体会话信息

    /**
     * 标记当前是否正在播放。
     * 此状态由 {@link MediaBrowserListener#onPlaybackStateChanged(PlaybackStateCompat)} 更新。
     */
    private boolean mIsPlaying;

    /**
     * 当 Activity 首次创建时调用。
     * 初始化 UI 元素、媒体浏览器助手 ({@link MediaBrowserHelper}) 和媒体会话管理器 ({@link MediaSessionManager})。
     * 并为播放控制按钮设置点击监听器。
     *
     * @param savedInstanceState 如果 Activity 被重新创建，则此 Bundle 包含先前保存的状态。否则为 null。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called - Activity is being created.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 UI 控件
        mTitleTextView = findViewById(R.id.song_title);
        mArtistTextView = findViewById(R.id.song_artist);
        mAlbumArt = findViewById(R.id.album_art);
        mMediaControlsImage = findViewById(R.id.media_controls); // 这个 ImageView 可能用于显示播放/暂停状态
        mSeekBarAudio = findViewById(R.id.seekbar_audio);

        // 设置点击监听器
        final ClickListener clickListener = new ClickListener();
        findViewById(R.id.button_previous).setOnClickListener(clickListener);
        findViewById(R.id.button_play).setOnClickListener(clickListener);
        findViewById(R.id.button_next).setOnClickListener(clickListener);
        findViewById(R.id.button_get_info).setOnClickListener(clickListener); // 获取其他媒体信息按钮

        // 初始化媒体浏览器助手，用于连接到 MusicService
        mMediaBrowserHelper = new MediaBrowserConnection(this);
        // 注册回调以接收来自 MediaController 的事件 (如播放状态改变、元数据改变)
        mMediaBrowserHelper.registerCallback(new MediaBrowserListener());
        // 初始化媒体会话管理器，用于获取其他应用的媒体播放信息
        mMediaSessionManager = new MediaSessionManager(this);
        Log.d(TAG, "onCreate: UI initialized, MediaBrowserHelper and MediaSessionManager created.");
    }

    /**
     * 当 Activity 即将对用户可见时调用。
     * 在此方法中，我们启动媒体浏览器的连接过程。
     */
    @Override
    public void onStart() {
        Log.d(TAG, "onStart called - Activity is becoming visible.");
        super.onStart();
        // 连接到 MediaBrowserService (MusicService)
        mMediaBrowserHelper.onStart();
        Log.d("MainActivity", "mMediaBrowserHelper.onStart"); // Existing log
        Log.d(TAG, "onStart: MediaBrowserHelper.onStart() called to connect to service.");
    }

    /**
     * 当 Activity 不再对用户可见时调用。
     * 在此方法中，我们断开媒体控制器与 SeekBar 的连接，并停止媒体浏览器的连接。
     */
    @Override
    public void onStop() {
        Log.d(TAG, "onStop called - Activity is no longer visible.");
        super.onStop();
        // 断开 SeekBar 与 MediaController 的连接，以避免内存泄漏和不必要的更新
        mSeekBarAudio.disconnectController();
        // 断开与 MediaBrowserService 的连接
        mMediaBrowserHelper.onStop();
        Log.d(TAG, "onStop: SeekBar disconnected, MediaBrowserHelper.onStop() called.");
    }

    /**
     * ClickListener 类实现了 {@link View.OnClickListener} 接口，
     * 用于处理播放控制按钮（上一首、播放/暂停、下一首）和获取信息按钮的点击事件。
     * 它通过 {@link MediaBrowserHelper#getTransportControls()} 获取媒体传输控制器，
     * 并调用相应的控制方法 (如 skipToPrevious, play, pause, skipToNext)。
     * 对于获取信息按钮，它使用 {@link MediaSessionManager} 来获取并显示其他正在播放的媒体信息。
     */
    private class ClickListener implements View.OnClickListener {
        // 隐式构造函数

        /**
         * 当一个已注册此监听器的视图被点击时调用。
         *
         * @param v 被点击的视图。
         */
        @Override
        public void onClick(View v) {
            Log.d(TAG, "ClickListener.onClick called for view ID: " + v.getId());
            // 根据被点击按钮的 ID 执行相应的操作
            switch (v.getId()) {
                case R.id.button_previous:
                    Log.d(TAG, "Previous button clicked.");
                    Log.d("MainActivity", "mMediaBrowserHelper.button_previous"); // Existing log
                    // 请求播放上一首
                    mMediaBrowserHelper.getTransportControls().skipToPrevious();
                    break;
                case R.id.button_play:
                    // 根据当前播放状态切换播放/暂停
                    if (mIsPlaying) {
                        Log.d(TAG, "Play/Pause button clicked. Currently playing, so pausing.");
                        Log.d("MainActivity", "mMediaBrowserHelper.button_play"); // Existing log for pause
                        mMediaBrowserHelper.getTransportControls().pause();
                    } else {
                        Log.d(TAG, "Play/Pause button clicked. Currently paused/stopped, so playing.");
                        Log.d("MainActivity", "mMediaBrowserHelper.play"); // Existing log for play
                        mMediaBrowserHelper.getTransportControls().play();
                    }
                    break;
                case R.id.button_next:
                    Log.d(TAG, "Next button clicked.");
                    Log.d("MainActivity", "mMediaBrowserHelper.skipToNext"); // Existing log
                    // 请求播放下一首
                    mMediaBrowserHelper.getTransportControls().skipToNext();
                    break;
                case R.id.button_get_info:
                    Log.d(TAG, "Get Info button clicked.");
                    // 检查通知访问权限
                    if (!mMediaSessionManager.checkNotificationPermission()) {
                        Log.w(TAG, "Notification listener permission not granted. Opening settings.");
                        // 如果没有权限，则跳转到通知访问权限设置页面
                        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        return;
                    }
                    // 获取当前所有活动的媒体会话信息
                    List<MediaSessionManager.MediaInfo> mediaInfoList = mMediaSessionManager.getActiveSessions();
                    StringBuilder infoBuilder = new StringBuilder("所有正在播放的媒体:\n\n");
                    if (mediaInfoList.isEmpty()) {
                        Log.d(TAG, "No active media sessions found.");
                        infoBuilder.append("没有正在播放的媒体");
                        mAlbumArt.setImageBitmap(null); // 清除专辑封面
                    } else {
                        Log.d(TAG, "Found " + mediaInfoList.size() + " active media session(s).");
                        for (MediaSessionManager.MediaInfo mediaInfo : mediaInfoList) {
                            infoBuilder.append(mediaInfo.toString()).append("\n\n");
                            // 尝试显示第一个媒体的专辑封面
                            if (mediaInfo.albumArt != null && mAlbumArt.getDrawable() == null) { // 只设置一次
                                Log.d(TAG, "Displaying album art for: " + mediaInfo.packageName);
                                mAlbumArt.setImageBitmap(mediaInfo.albumArt);
                                // break; // 如果只想显示第一个，可以取消注释
                            }
                        }
                    }
                    // 使用 AlertDialog 显示获取到的媒体信息
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("媒体播放信息")
                            .setMessage(infoBuilder.toString())
                            .setPositiveButton("确定", null)
                            .show();
                    Log.d(TAG, "Media info dialog shown.");
                    break;
                default:
                    Log.w(TAG, "ClickListener.onClick: Unhandled view ID: " + v.getId());
                    break;
            }
        }
    }

    /**
     * MediaBrowserConnection 类继承自 {@link MediaBrowserHelper}，
     * 负责处理与 {@link MusicService} 的连接。
     * 它在连接成功后设置 {@link MediaSeekBar} 的媒体控制器，
     * 并在加载子媒体项后将它们添加到播放队列并准备播放。
     */
    private class MediaBrowserConnection extends MediaBrowserHelper {
        /**
         * MediaBrowserConnection 的构造函数。
         *
         * @param context 上下文环境。
         */
        private MediaBrowserConnection(Context context) {
            // 调用父类构造函数，指定要连接的 MediaBrowserServiceCompat (MusicService)
            super(context, MusicService.class);
            Log.d(TAG, "MediaBrowserConnection constructor called, targeting MusicService.");
        }

        /**
         * 当成功连接到 {@link MusicService} 时调用。
         * 在此方法中，我们将获取到的 {@link MediaControllerCompat} 设置给 {@link MediaSeekBar}。
         *
         * @param mediaController 连接到的媒体服务的媒体控制器。
         */
        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
            Log.d(TAG, "MediaBrowserConnection.onConnected: Successfully connected to MusicService.");
            Log.d("MainActivity", "MediaControllerCompat.onConnected"); // Existing log
            // 将媒体控制器设置给 SeekBar，使其能够同步播放进度和处理用户拖动
            mSeekBarAudio.setMediaController(mediaController);
            Log.d(TAG, "MediaBrowserConnection.onConnected: MediaController set for MediaSeekBar.");
        }

        /**
         * 当请求的子媒体项加载完成时调用。
         * 在此示例中，我们将所有加载的媒体项添加到播放队列，并调用 prepare() 以便立即开始播放。
         *
         * @param parentId 父媒体ID。
         * @param children 加载的子媒体项列表。
         */
        @Override
        protected void onChildrenLoaded(@NonNull String parentId,
                                        @NonNull List<MediaBrowserCompat.MediaItem> children) {
            Log.d(TAG, "MediaBrowserConnection.onChildrenLoaded: Children loaded for parentId: " + parentId + ", Count: " + children.size());
            super.onChildrenLoaded(parentId, children); // 调用父类实现

            final MediaControllerCompat mediaController = getMediaController();
            if (mediaController == null) {
                Log.e(TAG, "MediaBrowserConnection.onChildrenLoaded: MediaController is null, cannot process children.");
                return;
            }
            Log.d("MainActivity", "MediaControllerCompat.onChildrenLoaded"); // Existing log

            // 将所有加载的媒体项添加到播放队列
            Log.d(TAG, "MediaBrowserConnection.onChildrenLoaded: Adding " + children.size() + " media items to the queue.");
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mediaController.addQueueItem(mediaItem.getDescription());
            }

            // 调用 prepare()，这样当用户按下播放按钮时就可以立即开始播放。
            // prepare() 会让 MediaSession 准备播放队列中的第一个项目。
            mediaController.getTransportControls().prepare();
            Log.d(TAG, "MediaBrowserConnection.onChildrenLoaded: MediaController.prepare() called.");
        }
    }

    /**
     * MediaBrowserListener 类继承自 {@link MediaControllerCompat.Callback}，
     * 用于接收来自 {@link MediaControllerCompat} 的回调事件，例如播放状态的改变和媒体元数据的更新。
     * 它根据这些事件更新 UI。
     */
    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        // 隐式构造函数

        /**
         * 当播放状态发生改变时调用。
         * 更新 {@link #mIsPlaying} 标志和播放/暂停按钮的视觉状态。
         *
         * @param playbackState 最新的播放状态 ({@link PlaybackStateCompat})。
         */
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            if (playbackState == null) {
                Log.w(TAG, "MediaBrowserListener.onPlaybackStateChanged: Received null playbackState.");
                mIsPlaying = false;
            } else {
                Log.d(TAG, "MediaBrowserListener.onPlaybackStateChanged: New state = " + playbackState.getState());
                mIsPlaying = playbackState.getState() == PlaybackStateCompat.STATE_PLAYING;
            }
            Log.d("MainActivity", "onPlaybackStateChanged"); // Existing log
            // 更新播放/暂停按钮的按下状态以反映当前播放状态
            mMediaControlsImage.setPressed(mIsPlaying);
            Log.d(TAG, "MediaBrowserListener.onPlaybackStateChanged: mIsPlaying set to " + mIsPlaying + ", controls image updated.");
        }

        /**
         * 当媒体元数据发生改变时调用 (例如，切换到新的歌曲)。
         * 更新 UI 上的歌曲标题、艺术家名称和专辑封面。
         *
         * @param mediaMetadata 最新的媒体元数据 ({@link MediaMetadataCompat})。
         */
        @Override
        public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
            Log.d(TAG, "MediaBrowserListener.onMetadataChanged called.");
            if (mediaMetadata == null) {
                Log.w(TAG, "MediaBrowserListener.onMetadataChanged: Received null mediaMetadata. Clearing UI.");
                // 如果元数据为空，可以清除UI或显示默认信息
                mTitleTextView.setText("");
                mArtistTextView.setText("");
                mAlbumArt.setImageBitmap(null); // 或者设置一个默认封面
                return;
            }
            String title = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artist = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            String mediaId = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);

            Log.d(TAG, "MediaBrowserListener.onMetadataChanged: Title=" + title + ", Artist=" + artist + ", MediaId=" + mediaId);

            mTitleTextView.setText(title);
            mArtistTextView.setText(artist);
            // 从 MusicLibrary 获取并设置专辑封面
            mAlbumArt.setImageBitmap(MusicLibrary.getAlbumBitmap(MainActivity.this, mediaId));
            Log.d(TAG, "MediaBrowserListener.onMetadataChanged: UI updated with new metadata.");
        }

        /**
         * 当媒体会话被销毁时调用。
         */
        @Override
        public void onSessionDestroyed() {
            Log.d(TAG, "MediaBrowserListener.onSessionDestroyed: Media session has been destroyed.");
            super.onSessionDestroyed();
            // 可以在这里处理会话销毁的逻辑，例如重置UI或尝试重新连接
        }

        /**
         * 当播放队列发生改变时调用 (例如，添加或移除了歌曲)。
         *
         * @param queue 最新的播放队列 (List of {@link MediaSessionCompat.QueueItem})。
         */
        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            Log.d(TAG, "MediaBrowserListener.onQueueChanged: Queue changed. New size: " + (queue != null ? queue.size() : "null"));
            Log.d("MainActivity", "MediaSessionCompat.onQueueChanged"); // Existing log
            super.onQueueChanged(queue);
            // 可以在这里更新与播放队列相关的UI（如果需要）
        }
    }
}