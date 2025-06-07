package com.lazy.mediasessiontest.client;

import android.service.notification.NotificationListenerService;
import android.util.Log; // Already present

/**
 * MediaNotificationListenerService 是一个通知监听服务。
 * 它的主要职责是监听系统中的通知。在媒体播放的场景下，
 * 这个服务是 {@link MediaSessionManager} 获取活跃媒体会话信息的关键。
 *
 * <p><b>工作原理：</b></p>
 * Android 系统允许应用注册为通知监听器。一旦注册并获得用户授权，
 * 该服务就能接收到系统中所有应用发出的通知（或根据配置过滤）。
 * {@link android.media.session.MediaSessionManager#getActiveSessions(ComponentName)}
 * 方法需要一个 {@link ComponentName} 指向一个已启用的通知监听服务，
 * 以此来验证请求方有权限访问媒体会话信息。
 *
 * <p><b>权限要求：</b></p>
 * 用户必须在系统设置中手动为此服务授予“通知访问权限”。
 * 如果没有此权限，{@link android.media.session.MediaSessionManager#getActiveSessions(ComponentName)}
 * 将无法获取媒体会话列表，通常会抛出 {@link SecurityException}。
 *
 * <p><b>用途：</b></p>
 * 虽然这个服务本身在这个示例项目中没有实现复杂的通知处理逻辑
 * （如解析通知内容、响应通知操作等），但它的存在和启用是
 * {@link MediaSessionManager} 能够查询其他应用媒体会话的前提。
 *
 * <p><b>声明周期：</b></p>
 * 这是一个标准的 Android Service，其生命周期由系统管理。
 * 它会在需要时被创建 ({@link #onCreate()})，并在不再需要或系统资源紧张时被销毁 ({@link #onDestroy()})。
 *
 * <p><b>配置：</b></p>
 * 此服务需要在 AndroidManifest.xml 文件中声明，并指定绑定到
 * {@code android.service.notification.NotificationListenerService} action 的 intent-filter，
 * 同时声明 {@code android.permission.BIND_NOTIFICATION_LISTENER_SERVICE} 权限。
 * 例如：
 * <pre>
 * {@code
 * <service android:name=".client.MediaNotificationListenerService"
 *          android:label="@string/service_name"
 *          android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *          android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.service.notification.NotificationListenerService" />
 *     </intent-filter>
 * </service>
 * }
 * </pre>
 */
public class MediaNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyMediaSeeionTestMediaNotificationListenerService"; // Updated TAG

    /**
     * 当服务首次被创建时调用。
     * 这是执行一次性初始化设置的地方。
     * 在这个实现中，主要用于日志记录。
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate called - Service is being created.");
        super.onCreate();
        // 可以在这里进行服务的初始化操作，例如注册广播接收器等。
        Log.d(TAG, "NotificationListenerService Created"); // Existing log
    }

    /**
     * 当服务即将被销毁时调用。
     * 这是执行所有清理工作的地方，例如注销广播接收器、释放资源等。
     * 在这个实现中，主要用于日志记录。
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called - Service is being destroyed.");
        super.onDestroy();
        // 可以在这里进行服务的清理操作。
        Log.d(TAG, "NotificationListenerService Destroyed"); // Existing log
    }

    // NotificationListenerService 还提供了其他可以重写的回调方法，
    // 例如 onNotificationPosted(StatusBarNotification sbn) 和 onNotificationRemoved(StatusBarNotification sbn)，
    // 这些方法分别在有新通知发布或通知被移除时调用。
    // 在这个项目中，我们主要依赖此服务被启用，以便 MediaSessionManager 可以查询会话，
    // 而不是直接处理通知本身。
}