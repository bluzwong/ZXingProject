/*
 * Copyright (C) 2010 ZXing authors
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

package com.dtr.zxing.decode;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.dtr.zxing.R;
import com.dtr.zxing.activity.CaptureActivity;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class DecodeHandler extends Handler {

    private final CaptureActivity activity;
    /**
     * 可能是解码bitmap -> Result 的类
     */
    private final MultiFormatReader multiFormatReader;
    private boolean running = true;

    public DecodeHandler(CaptureActivity activity, Map<DecodeHintType, Object> hints) {
        // hints内的东西可能是解码所需的参数
        multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message message) {
        if (!running) {
            return;
        }
        switch (message.what) {
            case R.id.decode:
                decode((byte[]) message.obj, message.arg1, message.arg2);
                break;
            case R.id.quit:
                running = false;
                Looper.myLooper().quit();
                break;
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it
     * took. For efficiency, reuse the same reader objects from one decode to
     * the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    /**
     * 解码data 并且发送到activity，该方法会阻塞 一定要在非主线程运行
     *
     * @param data
     * @param width
     * @param height
     */
    private void decode(byte[] data, int width, int height) {
        Size size = activity.getCameraManager().getPreviewSize();

        // 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
        byte[] rotatedData = new byte[data.length];
        for (int y = 0; y < size.height; y++) {
            for (int x = 0; x < size.width; x++)
                rotatedData[x * size.height + size.height - y - 1] = data[x + y * size.width];
        }

        // 宽高也要调整
        int tmp = size.width;
        size.width = size.height;
        size.height = tmp;

        // 用data生成bitmap
        Result rawResult = null;
        PlanarYUVLuminanceSource source = buildLuminanceSource(rotatedData, size.width, size.height);
        if (source != null) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                // 把bitmap进行解码
                rawResult = multiFormatReader.decodeWithState(bitmap);
            } catch (ReaderException re) {
                // continue
            } finally {
                multiFormatReader.reset();
            }
        }

        // 将解码后的结果尝试发送到activity
        // fixme 耦合程度很高
        Handler handler = activity.getHandler();
        if (rawResult != null) {
            // Don't log the barcode contents for security.
            if (handler != null) {
                // message中放入解码后的结果
                Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
                Bundle bundle = new Bundle();
                bundleThumbnail(source, bundle);
                message.setData(bundle);
                message.sendToTarget();
            }
        } else {
            // 没有解码出结果
            if (handler != null) {
                Message message = Message.obtain(handler, R.id.decode_failed);
                message.sendToTarget();
            }
        }

    }

    /**
     * 将一个 source 序列化 并放入bundle
     *
     * @param source
     * @param bundle
     */
    private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
        int[] pixels = source.renderThumbnail();
        int width = source.getThumbnailWidth();
        int height = source.getThumbnailHeight();
        Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
        bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    /**
     * 根据预览的data，宽高构造一个 PlanarYUVLuminanceSource
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = activity.getCropRect();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
    }

}
