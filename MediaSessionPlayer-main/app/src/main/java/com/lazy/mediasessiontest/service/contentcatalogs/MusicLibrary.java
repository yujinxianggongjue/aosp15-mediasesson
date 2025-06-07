package com.lazy.mediasessiontest.service.contentcatalogs;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log; // Added

import com.lazy.mediasessiontest.BuildConfig;
import com.lazy.mediasessiontest.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * MusicLibrary 类提供了一个静态的音乐曲目集合。
 * 这个类用于演示媒体浏览服务如何提供媒体元数据。
 * 音乐数据是硬编码在类中的，主要用于示例目的。
 * <p>
 * 主要功能包括：
 * <ul>
 *     <li>存储和管理一组 {@link MediaMetadataCompat} 对象，代表音乐曲目。</li>
 *     <li>提供方法来获取音乐的根ID、专辑封面URI、音乐文件名、专辑封面资源ID和Bitmap。</li>
 *     <li>将音乐元数据转换为 {@link MediaBrowserCompat.MediaItem} 列表，供媒体浏览器使用。</li>
 *     <li>在获取元数据时动态加载专辑封面 Bitmap，以节省内存。</li>
 * </ul>
 *
 * @author xu
 * @date 2021/2/24 16:49
 * @description 提供MediaMetadata 元数据
 */
public class MusicLibrary {
    private static final String TAG = "MyMediaSeeionTestMusicLibrary"; // 日志标签

    /**
     * 使用 TreeMap 存储音乐元数据。
     * 键是媒体ID (String)，值是 {@link MediaMetadataCompat} 对象。
     * TreeMap 用于保持媒体ID的有序性，尽管在这个特定实现中顺序可能不那么重要。
     *
     * <p>常用的 MediaMetadataCompat 键包括：</p>
     * <ul>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_MEDIA_ID}: 媒体内容的唯一ID。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_TITLE}: 标题。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ARTIST}: 艺术家。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DURATION}: 媒体时长（毫秒）。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM}: 专辑标题。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_AUTHOR}: 作者（通常用于书籍或播客）。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_WRITER}: 作词者。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_COMPOSER}: 作曲家。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_GENRE}: 流派。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_TRACK_NUMBER}: 音轨号。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_NUM_TRACKS}: 专辑中的总音轨数。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART_URI}: 专辑封面图片的URI。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_ICON_URI}: 用于显示的图标URI。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_MEDIA_URI}: 媒体内容的URI（例如，指向音频文件的路径或URL）。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART}: 专辑封面的Bitmap对象。</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_ICON}: 用于显示的图标Bitmap对象。</li>
     * </ul>
     */
    private static final TreeMap<String, MediaMetadataCompat> music = new TreeMap<>();
    /**
     * 存储媒体ID到专辑封面 drawable 资源ID的映射。
     * 键是媒体ID (String)，值是 drawable 资源的整数ID。
     */
    private static final HashMap<String, Integer> albumRes = new HashMap<>();
    /**
     * 存储媒体ID到音乐文件名的映射。
     * 键是媒体ID (String)，值是音乐文件名 (例如 "jazz_in_paris.mp3")。
     * 这些文件名通常对应于 assets 目录下的文件。
     */
    private static final HashMap<String, String> musicFileName = new HashMap<>();

    /**
     * 静态初始化块，用于在类加载时创建和填充音乐库的元数据。
     */
    static {
        Log.d(TAG, "Static initializer block started - populating music library.");
        createMediaMetadataCompat(
                "Jazz_In_Paris", // mediaId
                "Jazz in Paris", // title
                "Media Right Productions", // artist
                "Jazz & Blues", // album
                "Jazz", // genre
                103, // duration
                TimeUnit.SECONDS, // durationUnit
                "jazz_in_paris.mp3", // musicFilename
                R.drawable.album_jazz_blues, // albumArtResId (drawable resource)
                "album_jazz_blues"); // albumArtResName (for URI)

        createMediaMetadataCompat(
                "The_Coldest_Shoulder",
                "The Coldest Shoulder",
                "The 126ers",
                "Youtube Audio Library Rock 2",
                "Rock",
                160,
                TimeUnit.SECONDS,
                "the_coldest_shoulder.mp3",
                R.drawable.album_youtube_audio_library_rock_2,
                "album_youtube_audio_library_rock_2");
        Log.d(TAG, "Static initializer block finished. Music library populated with " + music.size() + " items.");
    }

    /**
     * 获取媒体浏览器层次结构的根ID。
     *
     * @return 代表音乐库根的字符串ID。
     */
    public static String getRoot() {
        Log.d(TAG, "getRoot() called, returning: \"root\"");
        return "root";
    }

    /**
     * 根据专辑封面的 drawable 资源名称构建其 URI。
     * URI 格式为 "android.resource://[application_id]/drawable/[albumArtResName]"。
     *
     * @param albumArtResName 专辑封面 drawable 资源的名称 (例如 "album_jazz_blues")。
     * @return 专辑封面的 ContentResolver URI 字符串。
     */
    private static String getAlbumArtUri(String albumArtResName) {
        String uri = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                BuildConfig.APPLICATION_ID + "/drawable/" + albumArtResName;
        Log.d(TAG, "getAlbumArtUri called for '" + albumArtResName + "', returning URI: " + uri);
        return uri;
    }

    /**
     * 根据媒体ID获取对应的音乐文件名。
     *
     * @param mediaId 媒体内容的唯一ID。
     * @return 如果找到，则返回音乐文件名；否则返回 null。
     */
    public static String getMusicFilename(String mediaId) {
        Log.d(TAG, "getMusicFilename called for mediaId: " + mediaId);
        String filename = musicFileName.get(mediaId); // 使用 .get() 更简洁
        if (filename != null) {
            Log.d(TAG, "Music filename found: " + filename);
        } else {
            Log.w(TAG, "Music filename not found for mediaId: " + mediaId);
        }
        return filename;
    }

    /**
     * 根据媒体ID获取对应的专辑封面 drawable 资源ID。
     *
     * @param mediaId 媒体内容的唯一ID。
     * @return 如果找到，则返回 drawable 资源的整数ID；否则返回 0。
     */
    private static int getAlbumRes(String mediaId) {
        Log.d(TAG, "getAlbumRes called for mediaId: " + mediaId);
        Integer resId = albumRes.get(mediaId); // 使用 Integer 避免自动拆箱可能导致的 NullPointerException
        if (resId != null) {
            Log.d(TAG, "Album resource ID found: " + resId);
            return resId;
        } else {
            Log.w(TAG, "Album resource ID not found for mediaId: " + mediaId + ", returning 0.");
            return 0; // 返回0表示未找到或无效资源ID
        }
    }

    /**
     * 根据媒体ID获取专辑封面的 Bitmap 对象。
     *
     * @param context 上下文对象，用于访问资源。
     * @param mediaId 媒体内容的唯一ID。
     * @return 如果成功加载，则返回专辑封面的 Bitmap 对象；否则返回 null (例如，如果资源ID无效)。
     */
    public static Bitmap getAlbumBitmap(Context context, String mediaId) {
        Log.d(TAG, "getAlbumBitmap called for mediaId: " + mediaId);
        int albumResource = MusicLibrary.getAlbumRes(mediaId);
        if (albumResource == 0) {
            Log.e(TAG, "getAlbumBitmap: Album resource ID is 0 for mediaId: " + mediaId + ". Cannot load bitmap.");
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), albumResource);
        if (bitmap != null) {
            Log.d(TAG, "Album bitmap loaded successfully for mediaId: " + mediaId);
        } else {
            Log.e(TAG, "getAlbumBitmap: BitmapFactory.decodeResource returned null for mediaId: " + mediaId + ", resourceId: " + albumResource);
        }
        return bitmap;
    }

    /**
     * 获取音乐库中所有可播放媒体项目的列表。
     * 每个项目都是一个 {@link MediaBrowserCompat.MediaItem} 对象。
     *
     * @return 包含所有音乐项目的列表。
     */
    public static List<MediaBrowserCompat.MediaItem> getMediaItems() {
        Log.d(TAG, "getMediaItems called. Fetching all media items.");
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (MediaMetadataCompat metadata : music.values()) {
            result.add(
                    new MediaBrowserCompat.MediaItem(
                            metadata.getDescription(), // MediaDescription 从 MediaMetadataCompat 生成
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)); // 标记为可播放
        }
        Log.d(TAG, "getMediaItems: Returning " + result.size() + " media items.");
        return result;
    }

    /**
     * 根据媒体ID获取完整的 {@link MediaMetadataCompat} 对象，包括专辑封面 Bitmap。
     * 这个方法会动态加载专辑封面 Bitmap 并将其添加到元数据中。
     * 这样做是为了避免在初始加载所有元数据时就将所有图片加载到内存中，从而节省内存。
     *
     * @param context 上下文对象，用于加载专辑封面 Bitmap。
     * @param mediaId 媒体内容的唯一ID。
     * @return 包含完整信息的 {@link MediaMetadataCompat} 对象；如果未找到对应 mediaId 的元数据，则返回 null。
     */
    public static MediaMetadataCompat getMetadata(Context context, String mediaId) {
        Log.d(TAG, "getMetadata called for mediaId: " + mediaId);
        MediaMetadataCompat metadataWithoutBitmap = music.get(mediaId);
        if (metadataWithoutBitmap == null) {
            Log.w(TAG, "getMetadata: No metadata found in 'music' map for mediaId: " + mediaId);
            return null;
        }

        Bitmap albumArt = getAlbumBitmap(context, mediaId);
        if (albumArt == null) {
            Log.w(TAG, "getMetadata: Failed to load album art for mediaId: " + mediaId);
            // 即使没有专辑封面，也可能希望返回其他元数据
        }

        // 由于 MediaMetadataCompat 是不可变的，我们需要创建一个新的 Builder 来添加专辑封面 Bitmap。
        // 我们不在初始时就为所有项目设置它，以避免不必要的内存占用。
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

        // 从原始元数据复制所有字符串类型的字段
        for (String key :
                new String[]{
                        MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                        MediaMetadataCompat.METADATA_KEY_ALBUM,
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        MediaMetadataCompat.METADATA_KEY_GENRE,
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        // 确保复制所有在 createMediaMetadataCompat 中设置的 URI 字段
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI
                }) {
            builder.putString(key, metadataWithoutBitmap.getString(key));
        }
        // 复制时长字段
        builder.putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                metadataWithoutBitmap.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));

        // 添加专辑封面 Bitmap
        if (albumArt != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
            // 通常，如果 METADATA_KEY_ALBUM_ART 可用，它也可以用作 METADATA_KEY_DISPLAY_ICON
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt);
        }
        Log.d(TAG, "getMetadata: Built new MediaMetadataCompat with album art for mediaId: " + mediaId);
        return builder.build();
    }

    /**
     * 创建一个新的 {@link MediaMetadataCompat} 对象并将其添加到音乐库中。
     * 同时，将相关的专辑封面资源ID和音乐文件名存储在相应的映射中。
     *
     * @param mediaId           媒体内容的唯一ID。
     * @param title             标题。
     * @param artist            艺术家。
     * @param album             专辑标题。
     * @param genre             流派。
     * @param duration          时长。
     * @param durationUnit      时长的单位 (例如 TimeUnit.SECONDS)。
     * @param musicFilename     音乐文件名 (用于从 assets 加载)。
     * @param albumArtResId     专辑封面 drawable 资源的整数ID。
     * @param albumArtResName   专辑封面 drawable 资源的名称 (用于构建URI)。
     */
    private static void createMediaMetadataCompat(
            String mediaId,
            String title,
            String artist,
            String album,
            String genre,
            long duration,
            TimeUnit durationUnit,
            String musicFilename,
            int albumArtResId,
            String albumArtResName) {
        Log.d(TAG, "createMediaMetadataCompat called for mediaId: " + mediaId + ", title: " + title);
        music.put(
                mediaId,
                new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                TimeUnit.MILLISECONDS.convert(duration, durationUnit)) // 转换为毫秒
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, // 专辑封面的URI
                                getAlbumArtUri(albumArtResName))
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, // 显示图标的URI
                                getAlbumArtUri(albumArtResName))
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        // 注意：METADATA_KEY_MEDIA_URI (实际音频文件的URI) 没有在这里设置，
                        // 它通常在播放器准备播放时根据 musicFilename 动态构建。
                        // 例如，如果文件在 assets 中，URI 可能是 "asset:///filename.mp3"
                        // 或者，如果期望从网络流式传输，则可能是 HTTP URL。
                        .build());
        albumRes.put(mediaId, albumArtResId);
        musicFileName.put(mediaId, musicFilename);
        Log.d(TAG, "createMediaMetadataCompat: Successfully created and stored metadata for mediaId: " + mediaId);
    }
}
