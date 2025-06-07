/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lazy.mediasessiontest.newnotification;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;

/**
 * AlbumArtCache 类实现了一个专辑封面图片的内存缓存机制，并支持异步加载。
 * 它使用 LruCache 来存储 Bitmap 对象，以优化内存使用和加载性能。
 * 主要功能包括：
 * 1. 缓存大尺寸专辑图和小尺寸图标。
 * 2. 异步从 URL 获取图片并进行缩放。
 * 3. 提供获取缓存图片的方法。
 * 4. 使用单例模式确保全局只有一个缓存实例。
 */
public final class AlbumArtCache {
    private static final String TAG = com.lazy.mediasessiontest.newnotification.AlbumArtCache.class.getSimpleName();

    /**
     * 专辑封面缓存的最大内存占用，单位为字节 (12MB)。
     */
    private static final int MAX_ALBUM_ART_CACHE_SIZE = 12 * 1024 * 1024;  // 12 MB
    /**
     * 从网络获取并缩放后的大尺寸专辑封面的最大宽度，单位为像素。
     */
    private static final int MAX_ART_WIDTH = 800;  // pixels
    /**
     * 从网络获取并缩放后的大尺寸专辑封面的最大高度，单位为像素。
     */
    private static final int MAX_ART_HEIGHT = 480;  // pixels

    /**
     * 作为图标使用的小尺寸专辑封面的最大宽度，单位为像素。
     * 通常用于 MediaDescription.getIconBitmap()。
     * 这个尺寸不宜过大，因为 MediaDescription 对象应该保持轻量级。
     * 如果设置得太大并尝试序列化 MediaDescription，可能会导致 FAILED BINDER TRANSACTION 错误。
     */
    private static final int MAX_ART_WIDTH_ICON = 128;  // pixels
    /**
     * 作为图标使用的小尺寸专辑封面的最大高度，单位为像素。
     */
    private static final int MAX_ART_HEIGHT_ICON = 128;  // pixels

    /**
     * 存储在 Bitmap 数组中大尺寸图片的索引。
     */
    private static final int BIG_BITMAP_INDEX = 0;
    /**
     * 存储在 Bitmap 数组中小尺寸图标的索引。
     */
    private static final int ICON_BITMAP_INDEX = 1;

    /**
     * LruCache 实例，用于存储专辑封面。
     * 键 (String) 是图片的 URL，值 (Bitmap[]) 是一个包含大图和图标的数组。
     */
    private final LruCache<String, Bitmap[]> mCache;

    /**
     * AlbumArtCache 的单例实例。
     */
    private static final com.lazy.mediasessiontest.newnotification.AlbumArtCache sInstance = new com.lazy.mediasessiontest.newnotification.AlbumArtCache();

    /**
     * 获取 AlbumArtCache 的单例实例。
     *
     * @return AlbumArtCache 的单例实例。
     */
    public static com.lazy.mediasessiontest.newnotification.AlbumArtCache getInstance() {
        Log.d(TAG, "getInstance() called");
        return sInstance;
    }

    /**
     * 私有构造函数，用于实现单例模式。
     * 初始化 LruCache，设置其最大大小。
     * 缓存大小被限制在 MAX_ALBUM_ART_CACHE_SIZE 和 JVM 最大可用内存的1/4之间，
     * 并且不超过 Integer.MAX_VALUE。
     */
    private AlbumArtCache() {
        Log.d(TAG, "AlbumArtCache constructor called - initializing LruCache.");
        // Holds no more than MAX_ALBUM_ART_CACHE_SIZE bytes, bounded by maxmemory/4 and
        // Integer.MAX_VALUE:
        // 计算缓存的最大大小，取 MAX_ALBUM_ART_CACHE_SIZE 和 (运行时最大内存的1/4) 中的较小值。
        // 这样做是为了避免缓存占用过多内存导致应用 OOM。
        int maxSize = Math.min(MAX_ALBUM_ART_CACHE_SIZE,
            (int) (Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 4)));
        Log.d(TAG, "AlbumArtCache constructor: Calculated LruCache maxSize = " + maxSize + " bytes.");

        mCache = new LruCache<String, Bitmap[]>(maxSize) {
            /**
             * 计算缓存中每个条目的大小。
             * 对于 Bitmap 数组，大小是大图和小图标的字节数之和。
             *
             * @param key   缓存条目的键 (图片URL)。
             * @param value 缓存条目的值 (Bitmap数组)。
             * @return 该条目占用的字节数。
             */
            @Override
            protected int sizeOf(String key, Bitmap[] value) {
                int size = value[BIG_BITMAP_INDEX].getByteCount()
                           + value[ICON_BITMAP_INDEX].getByteCount();
                // Log.d(TAG, "LruCache.sizeOf: key=" + key + ", size=" + size); // 频繁日志，按需开启
                return size;
            }
        };
        Log.d(TAG, "AlbumArtCache constructor: LruCache initialized.");
    }

    /**
     * 从缓存中获取大尺寸的专辑封面图片。
     *
     * @param artUrl 图片的 URL，作为缓存的键。
     * @return 如果缓存中存在对应的大尺寸图片，则返回 Bitmap 对象；否则返回 null。
     */
    public Bitmap getBigImage(String artUrl) {
        Log.d(TAG, "getBigImage() called for artUrl: " + artUrl);
        Bitmap[] result = mCache.get(artUrl);
        if (result == null) {
            Log.d(TAG, "getBigImage: Image not found in cache for artUrl: " + artUrl);
            return null;
        }
        Log.d(TAG, "getBigImage: Image found in cache for artUrl: " + artUrl);
        return result[BIG_BITMAP_INDEX];
    }

    /**
     * 从缓存中获取小尺寸的专辑封面图标。
     *
     * @param artUrl 图片的 URL，作为缓存的键。
     * @return 如果缓存中存在对应的小尺寸图标，则返回 Bitmap 对象；否则返回 null。
     */
    public Bitmap getIconImage(String artUrl) {
        Log.d(TAG, "getIconImage() called for artUrl: " + artUrl);
        Bitmap[] result = mCache.get(artUrl);
        if (result == null) {
            Log.d(TAG, "getIconImage: Icon not found in cache for artUrl: " + artUrl);
            return null;
        }
        Log.d(TAG, "getIconImage: Icon found in cache for artUrl: " + artUrl);
        return result[ICON_BITMAP_INDEX];
    }

    /**
     * 异步获取专辑封面图片。
     * 首先检查缓存中是否存在该图片。如果存在，则直接通过监听器回调返回。
     * 如果不存在，则启动一个 AsyncTask 从给定的 URL 下载并缩放图片，
     * 然后将结果存入缓存，并通过监听器回调返回。
     *
     * <p><b>警告:</b> 为了简单起见，这个实现没有正确处理并发的多线程获取请求。
     * 并发请求可能会导致冗余的网络请求和图片缩放操作。
     * 对于生产级别的应用，建议使用成熟的图片加载库，如 Glide 或 Picasso。</p>
     *
     * @param artUrl   图片的 URL。
     * @param listener 获取完成后的回调监听器。
     */
    public void fetch(final String artUrl, final FetchListener listener) {
        Log.d(TAG, "fetch() called for artUrl: " + artUrl);
        // WARNING: for the sake of simplicity, simultaneous multi-thread fetch requests
        // are not handled properly: they may cause redundant costly operations, like HTTP
        // requests and bitmap rescales. For production-level apps, we recommend you use
        // a proper image loading library, like Glide.

        // 首先尝试从缓存中获取
        Bitmap[] bitmap = mCache.get(artUrl);
        if (bitmap != null) {
            Log.d(TAG, "fetch: album art is in cache, using it for artUrl: " + artUrl);
            listener.onFetched(artUrl, bitmap[BIG_BITMAP_INDEX], bitmap[ICON_BITMAP_INDEX]);
            return;
        }

        Log.d(TAG, "fetch: album art not in cache, starting AsyncTask to fetch artUrl: " + artUrl);

        // 如果缓存中没有，则启动 AsyncTask 进行网络获取
        new AsyncTask<Void, Void, Bitmap[]>() {
            /**
             * 在后台线程执行图片获取和处理操作。
             *
             * @param params AsyncTask 参数 (未使用)。
             * @return 一个包含大图和图标的 Bitmap 数组；如果发生错误则返回 null。
             */
            @Override
            protected Bitmap[] doInBackground(Void... params) {
                Log.d(TAG, "AsyncTask.doInBackground: Starting to fetch and rescale bitmap for artUrl: " + artUrl);
                Bitmap[] bitmaps;
                try {
                    // 从 URL 获取原始 Bitmap 并缩放到指定的最大尺寸
                    Bitmap bitmap = BitmapHelper.fetchAndRescaleBitmap(artUrl,
                        MAX_ART_WIDTH, MAX_ART_HEIGHT);
                    if (bitmap == null) {
                        Log.e(TAG, "AsyncTask.doInBackground: fetchAndRescaleBitmap returned null for artUrl: " + artUrl);
                        return null;
                    }
                    // 将获取到的大图缩放为小图标尺寸
                    Bitmap icon = BitmapHelper.scaleBitmap(bitmap,
                        MAX_ART_WIDTH_ICON, MAX_ART_HEIGHT_ICON);
                    if (icon == null) {
                        Log.e(TAG, "AsyncTask.doInBackground: scaleBitmap for icon returned null for artUrl: " + artUrl);
                        // 即使图标创建失败，也可能仍希望缓存大图
                        // 但当前逻辑是如果任一失败则整体失败
                        return null;
                    }
                    bitmaps = new Bitmap[]{bitmap, icon};
                    // 将获取到的图片存入缓存
                    mCache.put(artUrl, bitmaps);
                    Log.d(TAG, "AsyncTask.doInBackground: Successfully fetched and cached bitmaps for artUrl: " + artUrl +
                               ". Cache size = " + mCache.size());
                } catch (IOException e) {
                    Log.e(TAG, "AsyncTask.doInBackground: IOException while fetching artUrl: " + artUrl, e);
                    return null;
                } catch (Exception e) {
                    Log.e(TAG, "AsyncTask.doInBackground: Unexpected exception while fetching artUrl: " + artUrl, e);
                    return null;
                }
                return bitmaps;
            }

            /**
             * 在 UI 线程执行，当后台操作完成后调用。
             *
             * @param bitmaps 后台操作返回的 Bitmap 数组；如果发生错误则为 null。
             */
            @Override
            protected void onPostExecute(Bitmap[] bitmaps) {
                Log.d(TAG, "AsyncTask.onPostExecute: called for artUrl: " + artUrl);
                if (bitmaps == null) {
                    Log.e(TAG, "AsyncTask.onPostExecute: Received null bitmaps, calling listener.onError for artUrl: " + artUrl);
                    listener.onError(artUrl, new IllegalArgumentException("Got null bitmaps from AsyncTask"));
                } else {
                    Log.d(TAG, "AsyncTask.onPostExecute: Received valid bitmaps, calling listener.onFetched for artUrl: " + artUrl);
                    listener.onFetched(artUrl,
                        bitmaps[BIG_BITMAP_INDEX], bitmaps[ICON_BITMAP_INDEX]);
                }
            }
        }.execute(); // 启动 AsyncTask
    }

    /**
     * 专辑封面获取监听器抽象类。
     * 用于在异步获取操作完成后回调通知调用者。
     */
    public static abstract class FetchListener {
        /**
         * 当专辑封面成功获取并处理后调用。
         *
         * @param artUrl    图片的 URL。
         * @param bigImage  获取到的大尺寸专辑封面图。
         * @param iconImage 获取到的小尺寸专辑封面图标。
         */
        public abstract void onFetched(String artUrl, Bitmap bigImage, Bitmap iconImage);

        /**
         * 当获取专辑封面过程中发生错误时调用。
         * 默认实现会记录错误日志。
         *
         * @param artUrl 图片的 URL。
         * @param e      发生的异常。
         */
        public void onError(String artUrl, Exception e) {
            Log.e(TAG, "FetchListener.onError: Error while downloading/processing artUrl: " + artUrl, e);
        }
    }
}
