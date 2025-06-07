package com.lazy.mediasessiontest.client;


import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import android.os.Bundle;

/**
 * MediaBrowserHelper 是一个辅助类，用于简化 MediaBrowserCompat 的使用。
 * 它处理与 MediaBrowserServiceCompat 的连接、断开连接，以及基本的媒体浏览功能，
 * 并提供了简化的回调接口。
 * 主要职责：
 * 1. 管理 MediaBrowserCompat 与 MediaBrowserServiceCompat 之间的连接生命周期。
 * 2. 在连接成功后，创建并提供 MediaControllerCompat 实例。
 * 3. 封装 MediaControllerCompat.Callback 和 MediaBrowserCompat.SubscriptionCallback 的处理逻辑。
 * 4. 向外部（通常是UI层）提供媒体元数据和播放状态的更新。
 */
public class MediaBrowserHelper {

    private static final String TAG = "MyMediaSeeionTestMediaBrowserHelper";

    /**
     * Android 应用上下文。
     */
    private final Context mContext;
    /**
     * 目标 MediaBrowserServiceCompat 服务的 Class 对象。
     * MediaBrowserHelper 将会连接到这个服务。
     */
    private final Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass;

    /**
     * 存储注册的 MediaControllerCompat.Callback 实例列表。
     * 当媒体元数据或播放状态改变时，会通知列表中的所有回调。
     */
    private final List<MediaControllerCompat.Callback> mCallbackList = new ArrayList<>();
    /**
     * MediaBrowserCompat 与 MediaBrowserServiceCompat 的连接状态回调。
     * 处理连接成功、失败、挂起等事件。
     */
    private final MediaBrowserConnectionCallback mMediaBrowserConnectionCallback;
    /**
     * MediaControllerCompat 的回调实例。
     * 用于接收来自 MediaSession 的元数据和播放状态更新，并分发给 mCallbackList 中的所有回调。
     */
    private final MediaControllerCallback mMediaControllerCallback;
    /**
     * MediaBrowserCompat 的订阅回调。
     * 当通过 MediaBrowserCompat 订阅的媒体内容（例如播放列表）发生变化时被调用。
     */
    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback;

    /**
     * MediaBrowserCompat 实例，用于浏览 MediaBrowserServiceCompat 提供的媒体内容。
     * 它的主要功能是连接到服务并获取 MediaSessionToken，以便创建 MediaControllerCompat。
     */
    private MediaBrowserCompat mMediaBrowser;
    /**
     * MediaControllerCompat 实例，用于向 MediaSession 发送播放控制命令，
     * 并接收来自 MediaSession 的状态更新。
     * 在成功连接到 MediaBrowserServiceCompat 后创建。
     */
    @Nullable
    private MediaControllerCompat mMediaController;

    /**
     * MediaBrowserHelper 的构造函数。
     *
     * @param context      应用程序上下文。
     * @param serviceClass 要连接的 MediaBrowserServiceCompat 的 Class 对象。
     */
    public MediaBrowserHelper(Context context,
                              Class<? extends MediaBrowserServiceCompat> serviceClass) {
        Log.d(TAG, "MediaBrowserHelper constructor called");
        mContext = context;
        mMediaBrowserServiceClass = serviceClass;
        // 初始化各种回调处理类
        mMediaBrowserConnectionCallback = new MediaBrowserConnectionCallback();
        mMediaControllerCallback = new MediaControllerCallback();
        mMediaBrowserSubscriptionCallback = new MediaBrowserSubscriptionCallback();
    }

    /**
     * 启动 MediaBrowserHelper，尝试连接到 MediaBrowserServiceCompat。
     * 通常在 Activity 或 Fragment 的 onStart() 生命周期方法中调用。
     * 如果 mMediaBrowser 为 null，会创建一个新的实例并发起连接。
     */
    public void onStart() {
        Log.d(TAG, "onStart called - Attempting to connect MediaBrowser");
        if (mMediaBrowser == null) {
            // 创建 MediaBrowserCompat 实例
            mMediaBrowser = new MediaBrowserCompat(
                    mContext,
                    new ComponentName(mContext, mMediaBrowserServiceClass), // 指定要连接的服务
                    mMediaBrowserConnectionCallback, // 设置连接回调
                    null); // Bundle for root hints, null if not needed
            // 发起连接请求
            mMediaBrowser.connect();
            Log.d(TAG, "onStart: New MediaBrowser created and connect() called.");
        } else if (!mMediaBrowser.isConnected()) {
            // 如果 MediaBrowser 已存在但未连接，则尝试重新连接
            mMediaBrowser.connect();
            Log.d(TAG, "onStart: Existing MediaBrowser not connected, connect() called.");
        } else {
            Log.d(TAG, "onStart: MediaBrowser already created and connected.");
        }
    }

    /**
     * 停止 MediaBrowserHelper，断开与 MediaBrowserServiceCompat 的连接。
     * 通常在 Activity 或 Fragment 的 onStop() 生命周期方法中调用。
     * 会注销 MediaControllerCompat 的回调并断开 MediaBrowserCompat 的连接。
     */
    public void onStop() {
        Log.d(TAG, "onStop called - Disconnecting MediaBrowser and releasing resources");
        // 注销 MediaControllerCompat 的回调
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
            mMediaController = null; // 释放 MediaControllerCompat 实例
            Log.d(TAG, "onStop: MediaController unregistered and nulled.");
        }
        // 断开 MediaBrowserCompat 的连接
        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null; // 释放 MediaBrowserCompat 实例
            Log.d(TAG, "onStop: MediaBrowser disconnected and nulled.");
        }
        // 重置状态，通知回调播放状态为空
        resetState();
        Log.d(TAG, "onStop: State reset. MediaBrowserHelper stopped.");
    }

    /**
     * 当 MediaBrowserCompat 成功连接到 MediaBrowserServiceCompat 后调用。
     * 子类可以重写此方法以在连接建立后执行特定操作，例如订阅媒体内容或更新UI。
     *
     * @param mediaController 与连接的 MediaSession关联的 MediaControllerCompat 实例。
     */
    protected void onConnected(@NonNull MediaControllerCompat mediaController) {
        Log.d(TAG, "onConnected called with MediaController: " + mediaController.getPackageName());
        // 默认实现为空，子类可以重写
    }

    /**
     * 当通过 MediaBrowserCompat 加载可浏览的媒体项的子项列表后调用。
     * 子类可以重写此方法以处理加载到的媒体项列表，例如更新UI显示。
     *
     * @param parentId 父媒体项的 ID。
     * @param children 子媒体项的列表（可能为空）。
     */
    protected void onChildrenLoaded(@NonNull String parentId,
                                    @NonNull List<MediaBrowserCompat.MediaItem> children) {
        Log.d(TAG, "onChildrenLoaded called for parentId: " + parentId + ", children count: " + children.size());
        // 默认实现为空，子类可以重写
    }

    /**
     * 当与 MediaBrowserServiceCompat 的连接丢失时调用。
     * 子类可以重写此方法以处理连接断开的情况，例如提示用户或尝试重新连接。
     */
    protected void onDisconnected() {
        Log.d(TAG, "onDisconnected called");
        // 默认实现为空，子类可以重写
    }

    /**
     * 获取当前关联的 MediaControllerCompat 实例。
     *
     * @return 当前的 MediaControllerCompat 实例。
     * @throws IllegalStateException 如果 MediaControllerCompat 尚未初始化（即未连接或连接已断开）。
     */
    @NonNull
    public final MediaControllerCompat getMediaController() {
        Log.d(TAG, "getMediaController called");
        if (mMediaController == null) {
            Log.e(TAG, "getMediaController: MediaController is null! Ensure MediaBrowser is connected.");
            throw new IllegalStateException("MediaController is null!");
        }
        return mMediaController;
    }

    /**
     * 重置状态，主要用于通知所有注册的回调播放状态已改变为 null。
     * 这通常在断开连接或会话销毁时调用，以清除UI上的旧状态。
     */
    private void resetState() {
        Log.d(TAG, "resetState called - Notifying callbacks with null PlaybackState");
        performOnAllCallbacks(new CallbackCommand() {
            @Override
            public void perform(@NonNull MediaControllerCompat.Callback callback) {
                // 通知回调播放状态已改变，传入 null 表示清除状态
                callback.onPlaybackStateChanged(null);
            }
        });
        Log.d(TAG, "resetState: PlaybackStateChanged(null) dispatched to all callbacks.");
    }

    /**
     * 获取 MediaControllerCompat.TransportControls 实例。
     * TransportControls 用于向 MediaSession 发送播放控制命令，如播放、暂停、跳过等。
     * UI层通过此对象与媒体播放进行交互。
     *
     * @return MediaControllerCompat.TransportControls 实例。
     * @throws IllegalStateException 如果 MediaControllerCompat 尚未初始化。
     */
    public MediaControllerCompat.TransportControls getTransportControls() {
        Log.d(TAG, "getTransportControls called");
        if (mMediaController == null) {
            Log.e(TAG, "getTransportControls: MediaController is null! Cannot get TransportControls.");
            throw new IllegalStateException("MediaController is null!");
        }
        return mMediaController.getTransportControls();
    }

    /**
     * 注册一个 MediaControllerCompat.Callback 实例。
     * 注册的回调将接收媒体元数据和播放状态的更新。
     * 如果在注册时 MediaControllerCompat 已经连接并有有效数据，会立即回调一次以同步当前状态。
     *
     * @param callback 要注册的 MediaControllerCompat.Callback 实例。
     */
    public void registerCallback(MediaControllerCompat.Callback callback) {
        Log.d(TAG, "registerCallback called for callback: " + callback.getClass().getSimpleName());
        if (callback != null) {
            mCallbackList.add(callback); // 将回调添加到列表中

            // 如果 MediaController 已经存在，立即用当前状态更新新注册的回调
            if (mMediaController != null) {
                final MediaMetadataCompat metadata = mMediaController.getMetadata();
                if (metadata != null) {
                    Log.d(TAG, "registerCallback: Initial metadata update for new callback.");
                    callback.onMetadataChanged(metadata);
                }
                final PlaybackStateCompat playbackState = mMediaController.getPlaybackState();
                if (playbackState != null) {
                    Log.d(TAG, "registerCallback: Initial playback state update for new callback.");
                    callback.onPlaybackStateChanged(playbackState);
                }
            } else {
                Log.d(TAG, "registerCallback: MediaController is null, no initial update for new callback.");
            }
        } else {
            Log.w(TAG, "registerCallback: Attempted to register a null callback.");
        }
    }

    /**
     * 对 mCallbackList 中的所有 MediaControllerCompat.Callback 实例执行一个命令。
     *
     * @param command 要在每个回调上执行的 CallbackCommand。
     */
    private void performOnAllCallbacks(@NonNull CallbackCommand command) {
        Log.d(TAG, "performOnAllCallbacks called for command: " + command.getClass().getSimpleName());
        for (MediaControllerCompat.Callback callback : mCallbackList) {
            if (callback != null) {
                command.perform(callback);
            }
        }
    }

    /**
     * 一个简单的接口，定义了在 MediaControllerCompat.Callback 上执行的操作。
     * 用于 {@link #performOnAllCallbacks(CallbackCommand)} 方法。
     */
    private interface CallbackCommand {
        /**
         * 在给定的 MediaControllerCompat.Callback 实例上执行具体操作。
         * @param callback 要操作的 MediaControllerCompat.Callback 实例。
         */
        void perform(@NonNull MediaControllerCompat.Callback callback);
    }

    /**
     * MediaBrowserCompat.ConnectionCallback 的实现。
     * 处理 MediaBrowserCompat 与 MediaBrowserServiceCompat 之间的连接事件。
     */
    private class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

        /**
         * 当 MediaBrowserCompat 成功连接到 MediaBrowserServiceCompat 时调用。
         * 此方法在 {@link MediaBrowserHelper#onStart()} 调用 connect() 后异步触发。
         */
        @Override
        public void onConnected() {
            Log.d(TAG, "MediaBrowserConnectionCallback.onConnected: MediaBrowser connected successfully.");
            try {
                // 连接成功后，获取 MediaSessionToken 并创建 MediaControllerCompat
                mMediaController =
                        new MediaControllerCompat(mContext, mMediaBrowser.getSessionToken());
                // 注册 mMediaControllerCallback 以接收来自 MediaSession 的更新
                mMediaController.registerCallback(mMediaControllerCallback);
                Log.d(TAG, "MediaBrowserConnectionCallback.onConnected: MediaController created and callback registered.");

                // 将当前 MediaSession 的状态同步到UI（通过 mMediaControllerCallback 间接触发 mCallbackList）
                // 这确保了即使在连接建立时已有播放状态或元数据，UI也能正确显示
                mMediaControllerCallback.onMetadataChanged(mMediaController.getMetadata());
                mMediaControllerCallback.onPlaybackStateChanged(mMediaController.getPlaybackState());

                // 调用外部的 onConnected 回调，允许子类或使用者执行连接后的操作
                MediaBrowserHelper.this.onConnected(mMediaController);

                // 订阅媒体内容的根节点，以便接收媒体项列表
                // mMediaBrowser.getRoot() 获取的是在 MediaBrowserServiceCompat 的 onGetRoot() 中返回的 rootId
                if (mMediaBrowser.getRoot() != null) {
                    mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);
                    Log.d(TAG, "MediaBrowserConnectionCallback.onConnected: Subscribed to MediaBrowser root: " + mMediaBrowser.getRoot());
                } else {
                    Log.w(TAG, "MediaBrowserConnectionCallback.onConnected: MediaBrowser root is null, cannot subscribe.");
                }

            } catch (RemoteException e) {
                Log.e(TAG, "MediaBrowserConnectionCallback.onConnected: RemoteException while creating MediaController: " + e.toString());
                throw new RuntimeException(e);
            }
        }

        /**
         * 当 MediaBrowserCompat 连接失败时调用。
         */
        @Override
        public void onConnectionFailed() {
            Log.w(TAG, "MediaBrowserConnectionCallback.onConnectionFailed: Connection to MediaBrowserService failed.");
            // 可以在这里通知用户连接失败
            MediaBrowserHelper.this.onDisconnected(); // 触发外部的断开连接回调
        }

        /**
         * 当与 MediaBrowserServiceCompat 的连接意外断开或服务崩溃时调用。
         */
        @Override
        public void onConnectionSuspended() {
            Log.w(TAG, "MediaBrowserConnectionCallback.onConnectionSuspended: Connection to MediaBrowserService suspended.");
            // 服务连接挂起，通常意味着服务崩溃或被系统杀死
            // 注销旧的 MediaController 回调
            if (mMediaController != null) {
                mMediaController.unregisterCallback(mMediaControllerCallback);
                mMediaController = null;
            }
            // 重置状态并通知外部连接已断开
            resetState();
            MediaBrowserHelper.this.onDisconnected();
        }
    }

    /**
     * MediaBrowserCompat.SubscriptionCallback 的实现。
     * 当订阅的媒体内容（例如播放列表的子项）加载完成或发生错误时调用。
     */
    public class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {

        /**
         * 当订阅的父媒体项的子项列表加载完成时调用。
         *
         * @param parentId 父媒体项的 ID。
         * @param children 加载到的子媒体项列表。
         */
        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                                     @NonNull List<MediaBrowserCompat.MediaItem> children) {
            Log.d(TAG, "MediaBrowserSubscriptionCallback.onChildrenLoaded for parentId: " + parentId + ", children count: " + children.size());
            // 将加载到的子项列表传递给外部的 onChildrenLoaded 回调
            MediaBrowserHelper.this.onChildrenLoaded(parentId, children);
        }

        /**
         * 当订阅媒体项发生错误时调用。
         * @param parentId 发生错误的父媒体项的 ID。
         */
        @Override
        public void onError(@NonNull String parentId) {
            Log.e(TAG, "MediaBrowserSubscriptionCallback.onError for parentId: " + parentId);
            // 可以通知用户加载媒体内容失败
        }

        /**
         * 当订阅媒体项发生错误时调用 (带 options 的版本)。
         * @param parentId 发生错误的父媒体项的 ID。
         * @param options 订阅时传入的选项。
         */
        @Override
        public void onError(@NonNull String parentId, @NonNull Bundle options) {
            Log.e(TAG, "MediaBrowserSubscriptionCallback.onError for parentId: " + parentId + " with options: " + options.toString());
            // 可以通知用户加载媒体内容失败
        }
    }

    /**
     * MediaControllerCompat.Callback 的实现。
     * 接收来自 MediaSession 的状态更新（元数据、播放状态、会话事件），
     * 并通过 {@link #performOnAllCallbacks(CallbackCommand)} 将这些更新分发给所有注册的外部回调。
     */
    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        /**
         * 当媒体元数据（如歌曲标题、艺术家、专辑封面等）发生改变时调用。
         *
         * @param metadata 最新的 MediaMetadataCompat 对象，如果为 null 表示没有元数据。
         */
        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {
            String title = (metadata == null || metadata.getDescription() == null) ? "null" : String.valueOf(metadata.getDescription().getTitle());
            Log.d(TAG, "MediaControllerCallback.onMetadataChanged: Metadata changed, title: " + title);
            // 将元数据更新分发给所有注册的外部回调
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onMetadataChanged(metadata);
                }
            });
        }

        /**
         * 当播放状态（如播放、暂停、缓冲、错误等）发生改变时调用。
         *
         * @param state 最新的 PlaybackStateCompat 对象，如果为 null 表示播放状态未知或已清除。
         */
        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {
            String stateDesc = (state == null) ? "null" : String.valueOf(state.getState());
            Log.d(TAG, "MediaControllerCallback.onPlaybackStateChanged: Playback state changed to: " + stateDesc);
            // 将播放状态更新分发给所有注册的外部回调
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onPlaybackStateChanged(state);
                }
            });
        }

        /**
         * 当 MediaSession 被销毁时调用。
         * 这通常意味着媒体播放服务已停止或媒体会话不再可用。
         */
        @Override
        public void onSessionDestroyed() {
            Log.w(TAG, "MediaControllerCallback.onSessionDestroyed: MediaSession has been destroyed.");
            // MediaSession 已销毁，重置状态
            resetState();
            // 明确通知播放状态为 null
            onPlaybackStateChanged(null);
            // 通知外部连接已断开（因为会话销毁通常意味着服务不再可用）
            MediaBrowserHelper.this.onDisconnected();
        }

        /**
         * 当会话事件发生时调用。
         * @param event 事件名称。
         * @param extras 事件相关的额外数据。
         */
        @Override
        public void onSessionEvent(@NonNull String event, @Nullable Bundle extras) {
            Log.d(TAG, "MediaControllerCallback.onSessionEvent: Received event: " + event);
            performOnAllCallbacks(new CallbackCommand() {
                @Override
                public void perform(@NonNull MediaControllerCompat.Callback callback) {
                    callback.onSessionEvent(event, extras);
                }
            });
        }

        // 其他 MediaControllerCompat.Callback 的回调方法可以根据需要添加和实现，
        // 例如 onQueueChanged, onQueueTitleChanged, onExtrasChanged, onAudioInfoChanged 等。
    }
}
