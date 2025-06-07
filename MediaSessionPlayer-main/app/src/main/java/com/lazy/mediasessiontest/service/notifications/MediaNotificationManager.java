package com.lazy.mediasessiontest.service.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log; // Already present

import com.lazy.mediasessiontest.ui.MainActivity;
import com.lazy.mediasessiontest.R;
import com.lazy.mediasessiontest.service.MusicService;
import com.lazy.mediasessiontest.service.contentcatalogs.MusicLibrary;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

/**
 * MediaNotificationManager 类负责创建和管理与媒体播放相关的通知。
 * 它处理通知渠道的创建（适用于 Android O 及更高版本）、构建通知内容，
 * 以及根据播放状态更新通知的操作按钮（播放、暂停、上一首、下一首）。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>为媒体播放服务创建和显示通知。</li>
 *     <li>根据当前的播放状态 ({@link PlaybackStateCompat}) 和媒体元数据 ({@link MediaMetadataCompat}) 更新通知。</li>
 *     <li>处理通知操作按钮的点击事件，这些事件通过 {@link MediaButtonReceiver} 转发给 {@link MusicService}。</li>
 *     <li>在 Android O 及更高版本上创建通知渠道。</li>
 * </ul>
 *
 * @author xu
 * @date 2021/2/24 16:50
 * @description 自定义通知管理器
 */
public class MediaNotificationManager {

    /**
     * 媒体播放通知的唯一ID。
     */
    public static final int NOTIFICATION_ID = 412;

    private static final String TAG = "MyMediaSeeionTestMediaNotificationManager"; // 日志标签
    /**
     * Android O 及更高版本上通知渠道的ID。
     */
    private static final String CHANNEL_ID = "com.example.android.musicplayer.channel";
    /**
     * 用于创建 {@link PendingIntent} 的请求代码。
     */
    private static final int REQUEST_CODE = 501;

    /**
     * 关联的 {@link MusicService} 实例。
     */
    private final MusicService mService;

    /**
     * 通知中的“播放”操作按钮。
     */
    private final NotificationCompat.Action mPlayAction;
    /**
     * 通知中的“暂停”操作按钮。
     */
    private final NotificationCompat.Action mPauseAction;
    /**
     * 通知中的“下一首”操作按钮。
     */
    private final NotificationCompat.Action mNextAction;
    /**
     * 通知中的“上一首”操作按钮。
     */
    private final NotificationCompat.Action mPrevAction;
    /**
     * 系统的 {@link NotificationManager} 服务实例。
     */
    private final NotificationManager mNotificationManager;

    /**
     * MediaNotificationManager 的构造函数。
     * 初始化通知操作按钮和 {@link NotificationManager}。
     *
     * @param service 关联的 {@link MusicService} 实例。
     */
    public MediaNotificationManager(MusicService service) {
        Log.d(TAG, "MediaNotificationManager constructor called");
        mService = service;

        // 获取系统的 NotificationManager 服务
        mNotificationManager =
                (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        // 初始化播放操作按钮
        mPlayAction =
                new NotificationCompat.Action(
                        R.drawable.notice_stop, // 播放按钮的图标 (这里似乎是停止图标，可能需要根据状态调整或命名)
                        mService.getString(R.string.label_play), // 播放按钮的标签
                        MediaButtonReceiver.buildMediaButtonPendingIntent( // 点击按钮时触发的 PendingIntent
                                mService,
                                PlaybackStateCompat.ACTION_PLAY)); // 对应的播放控制动作
        // 初始化暂停操作按钮
        mPauseAction =
                new NotificationCompat.Action(
                        R.drawable.notice_start, // 暂停按钮的图标 (这里似乎是开始图标)
                        mService.getString(R.string.label_pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                mService,
                                PlaybackStateCompat.ACTION_PAUSE));
        // 初始化下一首操作按钮
        mNextAction =
                new NotificationCompat.Action(
                        R.drawable.notice_right, // 下一首按钮的图标
                        mService.getString(R.string.label_next),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                mService,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT));
        // 初始化上一首操作按钮
        mPrevAction =
                new NotificationCompat.Action(
                        R.drawable.notice_left, // 上一首按钮的图标
                        mService.getString(R.string.label_previous),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                mService,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        // 取消所有现有的通知。
        // 这对于处理服务被系统杀死并重启的情况很有用，确保不会有旧的通知残留。
        mNotificationManager.cancelAll();
        Log.d(TAG, "MediaNotificationManager constructor: All previous notifications cancelled.");
    }

    /**
     * 当服务销毁时调用，用于执行清理操作。
     * 目前此方法仅记录日志，可以根据需要添加其他清理逻辑。
     */
    public void onDestroy() {
        Log.d(TAG, "onDestroy called. MediaNotificationManager is being destroyed.");
        // 可以在这里添加必要的清理代码，例如注销广播接收器等（如果在本类中注册了的话）。
        // 由于通知的生命周期通常与服务的生命周期绑定，当服务停止时，通知也应该被移除。
        // 如果服务是前台服务，stopForeground(true) 会移除通知。
        // 如果服务不是前台服务，或者希望在 onDestroy 时确保通知被移除，可以调用:
        // mNotificationManager.cancel(NOTIFICATION_ID);
        Log.d(TAG, "onDestroy: "); // Existing log
    }

    /**
     * 获取 {@link NotificationManager} 实例。
     *
     * @return {@link NotificationManager} 实例。
     */
    public NotificationManager getNotificationManager() {
        Log.d(TAG, "getNotificationManager called");
        return mNotificationManager;
    }

    /**
     * 根据当前的媒体元数据和播放状态构建并返回一个 {@link Notification} 对象。
     * 此通知用于媒体播放控制。
     *
     * @param metadata 当前播放的媒体元数据 ({@link MediaMetadataCompat})。
     * @param state    当前的播放状态 ({@link PlaybackStateCompat})。
     * @param token    媒体会话的 {@link MediaSessionCompat.Token}。
     * @return 构建好的 {@link Notification} 对象。
     */
    public Notification getNotification(MediaMetadataCompat metadata,
                                        @NonNull PlaybackStateCompat state,
                                        MediaSessionCompat.Token token) {
        Log.d(TAG, "getNotification called. State: " + state.getState() + ", MediaId: " + (metadata != null ? metadata.getDescription().getMediaId() : "null"));
        boolean isPlaying = state.getState() == PlaybackStateCompat.STATE_PLAYING;
        MediaDescriptionCompat description = metadata.getDescription();
        NotificationCompat.Builder builder = buildNotification(state, token, isPlaying, description);
        Log.d(TAG, "getNotification: Notification built successfully.");
        return builder.build();
    }

    /**
     * 内部辅助方法，用于构建通知的 {@link NotificationCompat.Builder}。
     *
     * @param state       当前的播放状态 ({@link PlaybackStateCompat})。
     * @param token       媒体会话的 {@link MediaSessionCompat.Token}。
     * @param isPlaying   一个布尔值，指示当前是否正在播放。
     * @param description 当前媒体的描述信息 ({@link MediaDescriptionCompat})。
     * @return 配置好的 {@link NotificationCompat.Builder} 实例。
     */
    private NotificationCompat.Builder buildNotification(@NonNull PlaybackStateCompat state,
                                                         MediaSessionCompat.Token token,
                                                         boolean isPlaying,
                                                         MediaDescriptionCompat description) {
        Log.d(TAG, "buildNotification called. isPlaying: " + isPlaying + ", Title: " + description.getTitle());
        // 在 Android O (API 26) 及更高版本上，必须创建通知渠道。
        if (isAndroidOOrHigher()) {
            createChannel();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, CHANNEL_ID);
        builder
                // 设置通知样式为媒体样式
                .setStyle(
                        new androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(token) //关联 MediaSession
                                .setShowActionsInCompactView(0, 1, 2) // 在紧凑视图中显示的操作按钮索引 (上一首, 播放/暂停, 下一首)
                                // 为了向后兼容 Android L 及更早版本
                                .setShowCancelButton(true) // 显示取消按钮 (通常是停止按钮)
                                .setCancelButtonIntent( // 取消按钮的 PendingIntent
                                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                mService,
                                                PlaybackStateCompat.ACTION_STOP))) // 对应停止播放动作
                // 设置通知背景颜色
                .setColor(ContextCompat.getColor(mService, R.color.notification_bg))
                // 设置小图标 (状态栏图标)
                .setSmallIcon(R.drawable.ic_stat_image_audiotrack)
                // 设置点击通知主体时的 PendingIntent (通常打开应用的播放界面)
                .setContentIntent(createContentIntent())
                // 设置通知标题 (通常是歌曲名称)
                .setContentTitle(description.getTitle())
                // 设置通知副标题 (通常是艺术家名称)
                .setContentText(description.getSubtitle())
                // 设置大图标 (通常是专辑封面)
                .setLargeIcon(MusicLibrary.getAlbumBitmap(mService, description.getMediaId()))
                // 设置当通知被删除时触发的 PendingIntent (例如，当播放暂停且通知可以被清除时)
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService, PlaybackStateCompat.ACTION_STOP))
                // 设置通知在锁屏上的可见性 (即使隐藏了敏感内容也显示控件)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // 如果“上一首”操作可用，则添加上一首按钮
        if ((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            builder.addAction(mPrevAction);
            Log.d(TAG, "buildNotification: Added Previous action.");
        } else {
            Log.d(TAG, "buildNotification: Previous action not available/added.");
        }

        // 根据播放状态添加“播放”或“暂停”按钮
        builder.addAction(isPlaying ? mPauseAction : mPlayAction);
        Log.d(TAG, "buildNotification: Added " + (isPlaying ? "Pause" : "Play") + " action.");

        // 如果“下一首”操作可用，则添加下一首按钮
        if ((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            builder.addAction(mNextAction);
            Log.d(TAG, "buildNotification: Added Next action.");
        } else {
            Log.d(TAG, "buildNotification: Next action not available/added.");
        }
        Log.d(TAG, "buildNotification: Notification builder configured.");
        return builder;
    }

    /**
     * 创建通知渠道。此方法在 Android O (API 26) 之前的版本上无效。
     * 通知渠道允许用户对不同类型的通知进行精细控制。
     */
    @RequiresApi(Build.VERSION_CODES.O) // 确保此方法仅在 API 26 及更高版本上调用
    private void createChannel() {
        Log.d(TAG, "createChannel called (for Android O+).");
        // 检查渠道是否已存在，避免重复创建
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            // 用户可见的渠道名称
            CharSequence name = "MediaSession";
            // 用户可见的渠道描述
            String description = "MediaSession and MediaPlayer";
            // 通知的重要性级别 (IMPORTANCE_LOW 表示通知不会发出声音，但会显示在通知栏)
            // 对于媒体播放，通常使用 IMPORTANCE_LOW 或 IMPORTANCE_DEFAULT，避免打扰用户
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            // 配置通知渠道的属性
            mChannel.setDescription(description); // 设置描述
            mChannel.enableLights(true); // 是否显示指示灯 (如果设备支持)
            mChannel.setLightColor(Color.RED); // 指示灯颜色
            mChannel.enableVibration(true); // 是否振动
            mChannel.setVibrationPattern( // 振动模式
                    new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            // 不应在渠道级别设置 setSound(null)，因为 IMPORTANCE_LOW 已经处理了声音。
            // 如果重要性更高，可以通过 mChannel.setSound(null, null) 来禁用声音。

            mNotificationManager.createNotificationChannel(mChannel);
            Log.d(TAG, "createChannel: New notification channel created with ID: " + CHANNEL_ID);
        } else {
            Log.d(TAG, "createChannel: Notification channel with ID: " + CHANNEL_ID + " already exists.");
        }
    }

    /**
     * 检查当前 Android 版本是否为 Oreo (API 26) 或更高。
     *
     * @return 如果是 Android O 或更高版本，则返回 true；否则返回 false。
     */
    private boolean isAndroidOOrHigher() {
        boolean isOreoOrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        Log.d(TAG, "isAndroidOOrHigher called, result: " + isOreoOrHigher);
        return isOreoOrHigher;
    }

    /**
     * 创建一个 {@link PendingIntent}，当用户点击通知主体时，该 PendingIntent 会被触发。
     * 通常，这会打开应用程序的播放界面 (例如 {@link MainActivity})。
     *
     * @return 配置好的 {@link PendingIntent}。
     */
    private PendingIntent createContentIntent() {
        Log.d(TAG, "createContentIntent called.");
        // 创建一个打开 MainActivity 的 Intent
        Intent openUI = new Intent(mService, MainActivity.class);
        // 设置标志，确保如果 MainActivity 已经在任务栈顶，则不会创建新的实例
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // 创建 PendingIntent
        // PendingIntent.FLAG_CANCEL_CURRENT: 如果先前存在具有相同 requestCode 的 PendingIntent，则先取消它，然后生成新的。
        // 对于 Android 12 (API 31) 及更高版本，需要明确指定可变性标志。
        // 如果不需要修改 Intent 中的 extras，可以使用 PendingIntent.FLAG_IMMUTABLE。
        // 如果需要修改，则使用 PendingIntent.FLAG_MUTABLE。
        // MediaButtonReceiver.buildMediaButtonPendingIntent 内部处理了可变性。
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE; // 对于打开 Activity 的 Intent，通常是不可变的
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                mService, REQUEST_CODE, openUI, flags);
        Log.d(TAG, "createContentIntent: PendingIntent created to open MainActivity.");
        return pendingIntent;
    }
}
