/*
 * Copyright (C) 2008 ZXing authors
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;

import com.dtr.zxing.activity.CaptureActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 在这个线程子类中处理耗时的解码工作
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class DecodeThread extends Thread {

    public static final String BARCODE_BITMAP = "barcode_bitmap";

    public static final int BARCODE_MODE = 0X100;
    public static final int QRCODE_MODE = 0X200;
    public static final int ALL_MODE = 0X300;

    private final CaptureActivity activity;
    private final Map<DecodeHintType, Object> hints;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;

    public DecodeThread(CaptureActivity activity, int decodeMode) {

        this.activity = activity;
        // 线程间同步锁
        handlerInitLatch = new CountDownLatch(1);

        hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        // 开始添加需要解码的类型
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));

        switch (decodeMode) {
            case BARCODE_MODE:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                break;

            case QRCODE_MODE:
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            case ALL_MODE:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            default:
                break;
        }

        // 添加这些解码参数到hints中
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
    }

    /**
     * 获取handler 并且让这个线程阻塞等待handler构造函数完成后返回
     *
     * @return
     */
    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return handler;
    }

    /**
     * 启动这个线程 线程只是一个容器 线程内干活的是DecodeHandler
     */
    @Override
    public void run() {
        Looper.prepare();
        handler = new DecodeHandler(activity, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
