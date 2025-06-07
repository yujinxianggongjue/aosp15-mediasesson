package com.lazy.mediasessiontest.client;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaController;
// Remove conflicting import and use fully qualified name
// import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体会话管理器 (MediaSessionManager)
 * 主要功能是获取系统中所有活跃的媒体会话 (MediaSession) 的信息。
 * 这对于需要与系统中的其他媒体播放器交互或显示其信息的应用非常有用。
 *
 * 注意：此类需要通知监听权限 (Notification Listener Permission) 才能正常工作。
 * 如果没有此权限，{@link #getActiveSessions()} 方法将返回一个空列表。
 */
public class MediaSessionManager {
    private static final String TAG = "MyMediaSeeionTestMediaSessionManager";
    /**
     * Android 应用上下文。
     */
    private Context mContext;
    /**
     * 系统的 MediaSessionManager 服务实例。
     * 用于获取活跃的媒体会话。
     * 注意：这里使用的是 android.media.session.MediaSessionManager，而不是 androidx 的版本。
     */
    private android.media.session.MediaSessionManager mMediaSessionManager;
    /**
     * 通知监听服务的组件名称。
     * 在获取活跃会话时，需要提供此组件名称以表明是哪个服务在请求信息。
     */
    private ComponentName mNotificationListener;
    /**
     * 存储从系统获取到的媒体控制器列表 (MediaControllerCompat)。
     * 尽管在此类中目前没有直接使用这个列表进行后续操作，但可以用于扩展功能。
     */
    private List<MediaControllerCompat> mMediaControllers;

    /**
     * MediaSessionManager 的构造函数。
     *
     * @param context 应用程序上下文。
     */
    public MediaSessionManager(Context context) {
        Log.d(TAG, "MediaSessionManager constructor called");
        mContext = context;
        mMediaControllers = new ArrayList<>();
        // 创建通知监听服务的组件名称
        // MediaNotificationListenerService 是一个需要用户在系统中手动开启权限的服务
        mNotificationListener = new ComponentName(context, MediaNotificationListenerService.class);
        // 获取系统的 MediaSessionManager 服务
        mMediaSessionManager = (android.media.session.MediaSessionManager)
                context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    /**
     * 检查应用程序是否具有通知监听权限。
     * 通知监听权限允许应用读取系统通知，包括媒体播放通知，从而间接获取媒体会话信息。
     *
     * @return 如果应用具有通知监听权限，则返回 true；否则返回 false。
     */
    public boolean checkNotificationPermission() {
        Log.d(TAG, "checkNotificationPermission called");
        // 获取系统中所有已启用的通知监听器列表
        String listeners = Settings.Secure.getString(mContext.getContentResolver(),
                "enabled_notification_listeners");
        // 检查当前应用的包名是否在列表中
        return listeners != null && listeners.contains(mContext.getPackageName());
    }

    /**
     * 获取系统中所有活跃的媒体会话信息。
     * 此方法会遍历所有活跃的媒体控制器 (MediaController)，并从中提取媒体元数据。
     *
     * @return 一个包含 {@link MediaInfo} 对象的列表，每个对象代表一个活跃的媒体会话信息。
     *         如果应用没有通知监听权限，或者没有活跃的会话，则返回空列表。
     */
    public List<MediaInfo> getActiveSessions() {
        Log.d(TAG, "getActiveSessions called");
        List<MediaInfo> mediaInfoList = new ArrayList<>();

        // 首先检查通知监听权限
        if (!checkNotificationPermission()) {
            Log.w(TAG, "getActiveSessions: No notification listener permission. Returning empty list.");
            return mediaInfoList; // 没有权限则直接返回空列表
        }

        try {
            // 获取所有活跃的媒体控制器 (android.media.session.MediaController)
            // 需要传入 mNotificationListener 来表明是哪个服务在请求
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mNotificationListener);
            Log.d(TAG, "getActiveSessions: Found " + controllers.size() + " active controllers.");

            for (MediaController controller : controllers) {
                // 尝试将 android.media.session.MediaController 转换为 MediaControllerCompat
                // MediaControllerCompat 提供了更方便的 API 来处理媒体元数据
                try {
                    // 从原始 MediaController 获取 SessionToken
                    MediaSessionCompat.Token token = MediaSessionCompat.Token.fromToken(controller.getSessionToken());
                    // 使用 SessionToken 创建 MediaControllerCompat 实例
                    MediaControllerCompat mediaController = new MediaControllerCompat(mContext, token);
                    // 获取媒体元数据
                    MediaMetadataCompat metadata = mediaController.getMetadata();

                    if (metadata != null) {
                        // 如果元数据不为空，则提取信息并创建 MediaInfo 对象
                        MediaInfo mediaInfo = new MediaInfo();
                        mediaInfo.title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                        mediaInfo.artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                        mediaInfo.album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
                        // 尝试获取专辑封面，如果 METADATA_KEY_ALBUM_ART 为空，则尝试 METADATA_KEY_DISPLAY_ICON
                        mediaInfo.albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                        if (mediaInfo.albumArt == null) {
                            mediaInfo.albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
                        }
                        mediaInfo.duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                        mediaInfo.packageName = controller.getPackageName(); // 获取媒体会话所属应用的包名
                        // 获取其他可选的元数据字段
                        mediaInfo.author = metadata.getString(MediaMetadataCompat.METADATA_KEY_AUTHOR);
                        mediaInfo.writer = metadata.getString(MediaMetadataCompat.METADATA_KEY_WRITER);
                        mediaInfo.composer = metadata.getString(MediaMetadataCompat.METADATA_KEY_COMPOSER);
                        mediaInfo.compilation = metadata.getString(MediaMetadataCompat.METADATA_KEY_COMPILATION);
                        mediaInfo.date = metadata.getString(MediaMetadataCompat.METADATA_KEY_DATE);
                        mediaInfo.year = metadata.getLong(MediaMetadataCompat.METADATA_KEY_YEAR);
                        mediaInfo.genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
                        mediaInfo.trackNumber = metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER);
                        mediaInfo.numTracks = metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS);
                        mediaInfo.discNumber = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER);
                        mediaInfo.albumArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
                        mediaInfo.displayTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
                        mediaInfo.displaySubtitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
                        mediaInfo.displayDescription = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION);
                        mediaInfo.mediaId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                        mediaInfo.mediaUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI);

                        mediaInfoList.add(mediaInfo);
                        Log.d(TAG, "getActiveSessions: Added MediaInfo for package: " + mediaInfo.packageName + ", title: " + mediaInfo.title);
                    } else {
                        Log.d(TAG, "getActiveSessions: Metadata is null for controller from package: " + controller.getPackageName());
                    }
                } catch (RemoteException e) {
                    // MediaControllerCompat 的构造函数或 getMetadata() 可能抛出 RemoteException
                    Log.e(TAG, "getActiveSessions: RemoteException while processing controller for package "
                            + controller.getPackageName() + ": " + e.getMessage());
                } catch (Exception e) {
                    // 捕获其他潜在的异常，例如 Token.fromToken 可能失败
                     Log.e(TAG, "getActiveSessions: Exception while processing controller for package "
                            + controller.getPackageName() + ": " + e.getMessage());
                }
            }
        } catch (SecurityException e) {
            // 如果没有通知监听权限，getActiveSessions() 会抛出 SecurityException
            Log.e(TAG, "getActiveSessions: SecurityException - " + e.getMessage() +
                    ". This usually means Notification Listener permission is missing or not granted correctly.");
        } catch (Exception e) {
            // 捕获其他未预料的异常
            Log.e(TAG, "getActiveSessions: Unexpected exception - " + e.getMessage());
        }

        Log.d(TAG, "getActiveSessions: Returning " + mediaInfoList.size() + " MediaInfo objects.");
        return mediaInfoList;
    }

    /**
     * 媒体信息数据类 (MediaInfo)
     * 用于封装从媒体会话中提取的各种元数据。
     */
    public static class MediaInfo {
        public String title; // 标题
        public String artist; // 艺术家
        public String album; // 专辑
        public String packageName; // 媒体会话所属应用的包名
        public Bitmap albumArt; // 专辑封面
        public String lyrics; // 歌词 (当前实现中未填充)
        public long duration; // 时长 (毫秒)
        public String author; // 作者
        public String writer; // 作词
        public String composer; // 作曲
        public String compilation; // 合辑信息
        public String date; // 日期 (如发布日期)
        public long year; // 年份
        public String genre; // 流派
        public long trackNumber; // 音轨号
        public long numTracks; // 总音轨数
        public long discNumber; // 碟片号
        public String albumArtist; // 专辑艺术家
        public String displayTitle; // 显示标题 (通常用于UI)
        public String displaySubtitle; // 显示副标题 (通常用于UI)
        public String displayDescription; // 显示描述 (通常用于UI)
        public String mediaId; // 媒体ID
        public String mediaUri; // 媒体URI

        /**
         * 返回媒体信息的字符串表示形式，方便调试和日志输出。
         *
         * @return 格式化的媒体信息字符串。
         */
        @Override
        public String toString() {
            Log.d(TAG, "MediaInfo.toString called for title: " + title);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("应用包名: %s\n", packageName));
            if (displayTitle != null) sb.append(String.format("显示标题: %s\n", displayTitle));
            if (displaySubtitle != null) sb.append(String.format("显示副标题: %s\n", displaySubtitle));
            if (displayDescription != null) sb.append(String.format("显示描述: %s\n", displayDescription));
            if (title != null) sb.append(String.format("标题: %s\n", title)); // 之前可能是“歌词”，修正为“标题”
            if (artist != null) sb.append(String.format("歌手: %s\n", artist));
            if (albumArtist != null) sb.append(String.format("专辑艺术家: %s\n", albumArtist));
            if (album != null) sb.append(String.format("专辑: %s\n", album));
            if (author != null) sb.append(String.format("作者: %s\n", author));
            if (writer != null) sb.append(String.format("作词: %s\n", writer));
            if (composer != null) sb.append(String.format("作曲: %s\n", composer));
            if (genre != null) sb.append(String.format("流派: %s\n", genre));
            if (trackNumber > 0) sb.append(String.format("音轨号: %d", trackNumber));
            if (numTracks > 0) sb.append(String.format("/%d\n", numTracks));
            else if (trackNumber > 0) sb.append("\n"); // 如果只有音轨号没有总数，也换行
            if (discNumber > 0) sb.append(String.format("碟片号: %d\n", discNumber));
            if (year > 0) sb.append(String.format("年份: %d\n", year));
            if (date != null) sb.append(String.format("日期: %s\n", date));
            if (duration > 0) sb.append(String.format("时长: %d秒\n", duration / 1000)); // 将毫秒转换为秒
            if (albumArt != null) sb.append("专辑封面: 可用\n");
            else sb.append("专辑封面: 不可用\n");
            if (lyrics != null && !lyrics.isEmpty()) sb.append("歌词: \n").append(lyrics);
            if (mediaId != null) sb.append(String.format("媒体ID: %s\n", mediaId));
            if (mediaUri != null) sb.append(String.format("媒体URI: %s\n", mediaUri));
            return sb.toString();
        }
    }
}