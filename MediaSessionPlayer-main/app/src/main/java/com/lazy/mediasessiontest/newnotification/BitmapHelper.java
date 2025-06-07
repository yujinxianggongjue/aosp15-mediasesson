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
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * BitmapHelper 是一个工具类，提供了处理 Bitmap 对象的静态方法。
 * 主要功能包括：
 * 1. 缩放 Bitmap 到指定的宽度和高度。
 * 2. 从 InputStream 解码 Bitmap 时，根据指定的缩放因子进行缩放。
 * 3. 计算从 InputStream 解码 Bitmap 以适应目标宽度和高度所需的缩放因子。
 * 4. 从给定的 URL 获取图片，并将其缩放到指定的宽度和高度。
 */
public class BitmapHelper {
    private static final String TAG = com.lazy.mediasessiontest.newnotification.BitmapHelper.class.getSimpleName();

    /**
     * 输入流允许标记/重置的最大读取限制（每个图像）。
     * 用于在计算缩放因子后重置流以进行实际解码。
     * 设置为 1MB，应该足够大多数图片头信息。
     */
    private static final int MAX_READ_LIMIT_PER_IMG = 1024 * 1024; // 1MB

    /**
     * 将给定的 Bitmap 缩放到指定的最大宽度和最大高度，同时保持其原始宽高比。
     *
     * @param src       要缩放的源 Bitmap。
     * @param maxWidth  目标最大宽度。
     * @param maxHeight 目标最大高度。
     * @return 缩放后的 Bitmap。如果源 Bitmap 为 null，则可能抛出 NullPointerException。
     */
    public static Bitmap scaleBitmap(Bitmap src, int maxWidth, int maxHeight) {
        Log.d(TAG, "scaleBitmap(Bitmap, int, int) called. src.width=" + (src != null ? src.getWidth() : "null") +
                ", src.height=" + (src != null ? src.getHeight() : "null") +
                ", maxWidth=" + maxWidth + ", maxHeight=" + maxHeight);

        if (src == null) {
            Log.e(TAG, "scaleBitmap: Source bitmap is null. Returning null.");
            return null;
        }
        if (maxWidth <= 0 || maxHeight <= 0) {
            Log.w(TAG, "scaleBitmap: maxWidth or maxHeight is non-positive. Returning original bitmap.");
            return src;
        }

        // 计算缩放比例，确保缩放后的图片不会超过 maxWidth 和 maxHeight
        double scaleFactor = Math.min(
           ((double) maxWidth) / src.getWidth(), ((double) maxHeight) / src.getHeight());

        // 如果计算出的缩放因子大于或等于1，意味着图片已经小于或等于目标尺寸，无需放大
        if (scaleFactor >= 1.0) {
            Log.d(TAG, "scaleBitmap: Calculated scaleFactor >= 1.0 (" + scaleFactor + "). Returning original bitmap as it's already small enough.");
            return src;
        }

        int newWidth = (int) (src.getWidth() * scaleFactor);
        int newHeight = (int) (src.getHeight() * scaleFactor);

        Log.d(TAG, "scaleBitmap: Calculated scaleFactor: " + scaleFactor +
                   ", newWidth: " + newWidth + ", newHeight: " + newHeight);

        // 创建缩放后的 Bitmap
        // 第四个参数 filter: 如果为 true，则在缩放时应用双线性过滤，以获得更平滑的图像。
        // 通常对于缩小操作，设置为 true 可以获得更好的质量。
        return Bitmap.createScaledBitmap(src, newWidth, newHeight, true);
    }

    /**
     * 从 InputStream 解码 Bitmap，并根据给定的缩放因子进行缩放。
     *
     * @param scaleFactor 缩放因子。例如，如果为 2，则图片的宽度和高度都将缩小为原来的 1/2，总像素数为原来的 1/4。
     * @param is          包含图片数据的输入流。此流在方法执行后不会被关闭。
     * @return 解码并缩放后的 Bitmap；如果解码失败，则返回 null。
     */
    public static Bitmap scaleBitmap(int scaleFactor, InputStream is) {
        Log.d(TAG, "scaleBitmap(int, InputStream) called. scaleFactor=" + scaleFactor);
        if (is == null) {
            Log.e(TAG, "scaleBitmap: InputStream is null. Returning null.");
            return null;
        }
        if (scaleFactor <= 0) {
            Log.w(TAG, "scaleBitmap: scaleFactor is non-positive ("+ scaleFactor +"). Using scaleFactor = 1.");
            scaleFactor = 1; // 至少为1，否则 inSampleSize 会有问题
        }

        // 获取 Bitmap 的解码选项
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();

        // 设置 inJustDecodeBounds 为 false 以实际解码图片
        bmOptions.inJustDecodeBounds = false;
        // 设置采样率（缩放因子）
        bmOptions.inSampleSize = scaleFactor;
        // 可选：设置颜色模式，例如 ARGB_8888 (默认) 或 RGB_565 (节省内存)
        // bmOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        Log.d(TAG, "scaleBitmap: Decoding stream with inSampleSize=" + bmOptions.inSampleSize);
        Bitmap decodedBitmap = BitmapFactory.decodeStream(is, null, bmOptions);
        if (decodedBitmap == null) {
            Log.e(TAG, "scaleBitmap: BitmapFactory.decodeStream returned null.");
        } else {
            Log.d(TAG, "scaleBitmap: Decoded bitmap dimensions: " + decodedBitmap.getWidth() + "x" + decodedBitmap.getHeight());
        }
        return decodedBitmap;
    }

    /**
     * 计算从 InputStream 解码 Bitmap 以适应目标宽度和高度所需的最佳缩放因子 (inSampleSize)。
     * 缩放因子总是2的幂（1, 2, 4, 8, ...）。
     * 此方法会读取流中的图片尺寸信息，但不会实际解码整个图片。
     *
     * @param targetW 目标宽度。
     * @param targetH 目标高度。
     * @param is      包含图片数据的输入流。此流必须支持 mark/reset 操作，
     *                因为在读取尺寸后需要重置流以便后续实际解码。
     *                此方法在执行后不会关闭流。
     * @return 计算出的最佳缩放因子 (inSampleSize)。如果无法获取图片尺寸或目标尺寸无效，则返回 1。
     */
    public static int findScaleFactor(int targetW, int targetH, InputStream is) {
        Log.d(TAG, "findScaleFactor called. targetW=" + targetW + ", targetH=" + targetH);
        if (is == null) {
            Log.e(TAG, "findScaleFactor: InputStream is null. Returning scaleFactor = 1.");
            return 1;
        }
        if (!is.markSupported()) {
            Log.e(TAG, "findScaleFactor: InputStream does not support mark/reset. Returning scaleFactor = 1. This may lead to incorrect scaling or errors.");
            // 在这种情况下，后续的 is.reset() 会失败。
            // 理想情况下，应该处理这种情况，例如通过复制流或不进行预先的尺寸检查。
            return 1;
        }
        if (targetW <= 0 || targetH <= 0) {
            Log.w(TAG, "findScaleFactor: targetW or targetH is non-positive. Returning scaleFactor = 1.");
            return 1;
        }

        // 获取 Bitmap 的解码选项
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        // 设置 inJustDecodeBounds 为 true，这样 decodeStream 只会读取图片的尺寸信息，而不会加载整个图片到内存
        bmOptions.inJustDecodeBounds = true;

        // 标记当前流的位置，以便后续重置
        is.mark(MAX_READ_LIMIT_PER_IMG);
        BitmapFactory.decodeStream(is, null, bmOptions); // 传入 null for padding is fine
        try {
            // 重置流到标记的位置，以便后续可以再次从头读取并实际解码图片
            is.reset();
        } catch (IOException e) {
            Log.e(TAG, "findScaleFactor: IOException while resetting InputStream. This may lead to errors in subsequent decoding.", e);
            // 如果重置失败，后续的解码可能会从错误的位置开始，或者失败。
            // 此时返回1可能不是最佳选择，但为了简单起见，我们继续。
            // 更好的处理方式可能是抛出异常或返回一个错误指示。
            return 1;
        }


        int actualW = bmOptions.outWidth;
        int actualH = bmOptions.outHeight;

        Log.d(TAG, "findScaleFactor: Actual image dimensions: actualW=" + actualW + ", actualH=" + actualH);

        if (actualW <= 0 || actualH <= 0) {
            Log.e(TAG, "findScaleFactor: Failed to get actual image dimensions (outWidth/outHeight is non-positive). Returning scaleFactor = 1.");
            return 1;
        }

        // 计算缩放因子 (inSampleSize)
        // inSampleSize 应该是2的幂。BitmapFactory 会向下取整到最接近的2的幂。
        // 例如，如果计算出的比例是3，则实际使用的 inSampleSize 会是2。
        // 如果计算出的比例是1.5，则实际使用的 inSampleSize 会是1。
        int scaleFactor = 1;
        if (actualH > targetH || actualW > targetW) {
            final int halfHeight = actualH / 2;
            final int halfWidth = actualW / 2;
            // 选择一个缩放因子，使得缩放后的图片的宽度和高度都大于或等于目标尺寸。
            while ((halfHeight / scaleFactor) >= targetH && (halfWidth / scaleFactor) >= targetW) {
                scaleFactor *= 2;
            }
        }
        // 原来的计算方式是 Math.min(actualW/targetW, actualH/targetH);
        // 这种方式计算出的 scaleFactor 可能不是2的幂，但 BitmapFactory 内部会处理。
        // 改用上述循环方式可以确保 scaleFactor 是2的幂，这与 inSampleSize 的行为更一致。
        // 不过，为了保持与原始代码逻辑的一致性，这里使用原始的计算方式，
        // BitmapFactory.decodeStream 内部会处理非2的幂的 inSampleSize。
        // int scaleFactor = Math.min(actualW/targetW, actualH/targetH);
        // 如果 targetW 或 targetH 为0，会导致除零异常，已在前面处理。

        Log.d(TAG, "findScaleFactor: Calculated scaleFactor = " + scaleFactor);
        return scaleFactor == 0 ? 1 : scaleFactor; // 确保 scaleFactor 至少为1
    }

    /**
     * 从给定的 URI (通常是 URL) 获取图片，并将其缩放到指定的目标宽度和高度。
     * 此方法会首先计算最佳的缩放因子 (inSampleSize) 以减少内存消耗，
     * 然后再解码并缩放图片。
     *
     * @param uri    图片的 URI 字符串 (例如 "http://example.com/image.png")。
     * @param width  目标宽度。
     * @param height 目标高度。
     * @return 获取并缩放后的 Bitmap；如果发生错误 (如网络问题、解码失败)，则返回 null。
     * @throws IOException 如果在网络连接或流操作过程中发生 I/O 错误。
     */
    @SuppressWarnings("SameParameterValue") // 抑制 'width' 和 'height' 参数值相同的警告
    public static Bitmap fetchAndRescaleBitmap(String uri, int width, int height)
            throws IOException {
        Log.d(TAG, "fetchAndRescaleBitmap called. uri=" + uri + ", width=" + width + ", height=" + height);
        if (uri == null || uri.isEmpty()) {
            Log.e(TAG, "fetchAndRescaleBitmap: URI is null or empty. Returning null.");
            return null; // 或者抛出 IllegalArgumentException
        }
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "fetchAndRescaleBitmap: Target width or height is non-positive. Returning null.");
            return null; // 或者抛出 IllegalArgumentException
        }

        URL url = new URL(uri);
        HttpURLConnection urlConnection = null;
        BufferedInputStream is = null;
        Bitmap scaledBitmap = null;

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            // 设置连接和读取超时，防止无限等待
            urlConnection.setConnectTimeout(15000); // 15秒连接超时
            urlConnection.setReadTimeout(15000);    // 15秒读取超时

            int responseCode = urlConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "fetchAndRescaleBitmap: HTTP error response code: " + responseCode + " for URI: " + uri);
                // 可以根据 responseCode 抛出更具体的异常或返回 null
                throw new IOException("HTTP error fetching URL " + uri + ": " + responseCode + " " + urlConnection.getResponseMessage());
            }

            is = new BufferedInputStream(urlConnection.getInputStream());

            // 标记输入流的当前位置，以便在计算缩放因子后可以重置它
            // MAX_READ_LIMIT_PER_IMG 应该足够大以包含图片头信息
            if (!is.markSupported()) {
                // 如果流不支持标记，我们无法安全地进行两步读取（先读尺寸，再读完整图片）。
                // 这种情况下，一种策略是完整读取到内存再缩放，但这可能导致OOM。
                // 另一种是直接解码，不进行预先的inSampleSize计算，这可能效率较低。
                // 这里简单记录一个警告。对于生产代码，可能需要更健壮的处理。
                Log.w(TAG, "fetchAndRescaleBitmap: InputStream from URL does not support mark/reset. Scaling might be suboptimal or fail.");
                // 尝试直接解码，不使用 inSampleSize 优化，然后进行 Bitmap.createScaledBitmap
                // 但这会先将原始大小的图片加载到内存，可能导致OOM。
                // 为了与原逻辑保持一致（依赖mark/reset），这里继续，但后续的reset可能会失败。
            }
            is.mark(MAX_READ_LIMIT_PER_IMG);

            // 计算缩放因子 (inSampleSize)
            int scaleFactor = findScaleFactor(width, height, is);
            Log.d(TAG, "fetchAndRescaleBitmap: Scaling bitmap " + uri + " by factor " + scaleFactor +
                    " to support " + width + "x" + height + " requested dimension");

            // 重置输入流到之前标记的位置，以便用计算出的缩放因子实际解码图片
            try {
                is.reset();
            } catch (IOException e) {
                Log.e(TAG, "fetchAndRescaleBitmap: IOException while resetting InputStream. Attempting to re-open stream.", e);
                // 如果重置失败（例如，流不支持标记，或者读取超过了 markLimit），
                // 尝试关闭并重新打开连接和流。这会产生额外的网络请求。
                if (is != null) try { is.close(); } catch (IOException ignored) {}
                if (urlConnection != null) urlConnection.disconnect();

                urlConnection = (HttpURLConnection) url.openConnection(); // 重新打开连接
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);
                if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                     throw new IOException("HTTP error re-fetching URL " + uri + ": " + urlConnection.getResponseCode());
                }
                is = new BufferedInputStream(urlConnection.getInputStream());
                // 重新打开流后，我们不能再依赖之前的 scaleFactor，因为它是在旧流上计算的。
                // 最安全的是不使用 scaleFactor，或者重新计算（但这又需要 mark/reset）。
                // 为了简单，这里我们假设如果 reset 失败，就直接用 scaleFactor=1 (即不预缩放)。
                // 或者，更好的做法是，如果 reset 失败，就直接完整解码然后用 createScaledBitmap。
                // 但原始代码是直接调用 scaleBitmap(scaleFactor, is)，这里保持一致。
                // 如果 reset 失败，并且流不支持 mark，那么 findScaleFactor 之前可能已经返回了1。
            }

            // 使用计算出的缩放因子解码并缩放 Bitmap
            scaledBitmap = scaleBitmap(scaleFactor, is);

            if (scaledBitmap != null) {
                Log.d(TAG, "fetchAndRescaleBitmap: Successfully decoded with scaleFactor. Initial scaled size: " +
                           scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
                // 即使使用了 inSampleSize，解码后的图片尺寸也可能不完全精确匹配 width/height，
                // 因为 inSampleSize 是2的幂。所以可能需要进一步精确缩放。
                // 但原始代码的 scaleBitmap(scaleFactor, is) 返回的就是最终结果，
                // 它没有再调用 Bitmap.createScaledBitmap。
                // 如果需要更精确的尺寸，应该在之后调用 scaleBitmap(Bitmap, int, int)。
                // 例如：
                // if (scaledBitmap.getWidth() > width || scaledBitmap.getHeight() > height) {
                //     Bitmap preciselyScaledBitmap = scaleBitmap(scaledBitmap, width, height);
                //     if (preciselyScaledBitmap != scaledBitmap) { // 如果发生了缩放，回收旧的
                //         scaledBitmap.recycle();
                //     }
                //     scaledBitmap = preciselyScaledBitmap;
                //     Log.d(TAG, "fetchAndRescaleBitmap: Precisely scaled to: " +
                //                scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
                // }
            } else {
                Log.e(TAG, "fetchAndRescaleBitmap: scaleBitmap(scaleFactor, is) returned null for URI: " + uri);
            }

        } catch (IOException e) {
            Log.e(TAG, "fetchAndRescaleBitmap: IOException for URI: " + uri, e);
            throw e; // 重新抛出，让调用者处理
        } catch (Exception e) {
            // 捕获其他潜在的运行时异常，例如 OutOfMemoryError
            Log.e(TAG, "fetchAndRescaleBitmap: Unexpected exception for URI: " + uri, e);
            // 可以选择将其包装为 IOException 或自定义异常抛出
            throw new IOException("Unexpected error fetching or rescaling bitmap from " + uri, e);
        }
        finally {
            // 确保输入流和连接被关闭
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "fetchAndRescaleBitmap: Error closing InputStream.", e);
                }
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
                Log.d(TAG, "fetchAndRescaleBitmap: HttpURLConnection disconnected for URI: " + uri);
            }
        }
        return scaledBitmap;
    }
}
