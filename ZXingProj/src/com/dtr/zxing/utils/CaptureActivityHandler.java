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

package com.dtr.zxing.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.dtr.zxing.R;
import com.dtr.zxing.activity.CaptureActivity;
import com.dtr.zxing.camera.CameraManager;
import com.dtr.zxing.decode.DecodeThread;
import com.google.zxing.Result;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 * 在扫描二维码activity内使用的handler
 * 这个类并不能算工具类 是和activity耦合的一个类
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class CaptureActivityHandler extends Handler {

    private final CaptureActivity activity;
    private final DecodeThread decodeThread;
    private final CameraManager cameraManager;
    private State state;

    /**
     * handler的状态
     */
    private enum State {
        PREVIEW, SUCCESS, DONE
    }

    public CaptureActivityHandler(CaptureActivity activity, CameraManager cameraManager, int decodeMode) {
        this.activity = activity;
        // 启动解码的工作线程
        decodeThread = new DecodeThread(activity, decodeMode);
        decodeThread.start();
        // 状态设为success
        state = State.SUCCESS;

        // Start ourselves capturing previews and decoding.
        // 启动预览并且开始解码
        this.cameraManager = cameraManager;
        cameraManager.startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.restart_preview:
                // 重启预览并且解码
                restartPreviewAndDecode();
                break;
            case R.id.decode_succeeded:
                // 解码成功了 发送数据到activity
                state = State.SUCCESS;
                Bundle bundle = message.getData();

                activity.handleDecode((Result) message.obj, bundle);
                break;
            case R.id.decode_failed:
                // 解码失败 状态改为preview 开始另一个预览
                // fixme 这里可能需要开另外一个handler
                // We're decoding as fast as possible, so when one decode fails,
                // start another.
                state = State.PREVIEW;
                cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
                break;
            case R.id.return_scan_result:
                // 关闭activity 并且return 一个result
                activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
                activity.finish();
                break;
        }
    }

    /**
     * 同步的方式退出
     */
    public void quitSynchronously() {
        // 状态设为done
        state = State.DONE;
        // 关闭camera
        cameraManager.stopPreview();
        // 发送quit到handler
        Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
        quit.sendToTarget();
        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            // 阻塞直到工作线程结束
            decodeThread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }

        // 清空本handler内的成功失败消息
        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.decode_succeeded);
        removeMessages(R.id.decode_failed);
    }

    /**
     * 重启预览和解码
     */
    private void restartPreviewAndDecode() {
        // 如果当前状态时success则改为preview 并且开始获取预览
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            // 发送decode请求给handler 这个handler实际就是DecodeHandler
            cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
        }
    }

}
