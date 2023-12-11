package com.sdk;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.util.Log;

import android.util.Base64;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableMap;  // 이 부분 추가
import com.facebook.react.bridge.ReadableArray;  // 이 부분 추가
import com.facebook.react.bridge.ReadableType;  // 추가
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import ai.onnxruntime.OrtException;

public class SlieroVadDetectorModule extends ReactContextBaseJavaModule {

    private SlieroVadDetector vadDetector;
    private String modelPath; // 저장된 파일의 경로를 저장하는 변수

    public SlieroVadDetectorModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "SlieroVadDetector";
    }

    @ReactMethod
    public void initialize(String modelPath, float startThreshold, float endThreshold, int samplingRate,
                           int minSilenceDurationMs, int speechPadMs, Callback callback) {
        this.modelPath = modelPath; // 모듈의 modelPath에 저장

        try {
            // 파일을 내부 저장소로 복사
            copyModelToInternalStorage(modelPath);

            // 복사된 파일의 경로를 사용하여 초기화
            vadDetector = new SlieroVadDetector(getReactApplicationContext().getFilesDir() + File.separator + modelPath,
                    startThreshold, endThreshold, samplingRate, minSilenceDurationMs, speechPadMs);

            callback.invoke(null, modelPath); // 성공 시 콜백 호출
        } catch (OrtException | IOException e) {
            e.printStackTrace();
            callback.invoke(e.getMessage(), null);
        }
    }

    @ReactMethod
    public void getMessage(Callback callback) {
        if (modelPath != null) {
            callback.invoke(null, modelPath);
        } else {
            callback.invoke("Message not available", null);
        }
    }

    @ReactMethod
    public void apply(ReadableArray audioData, boolean returnSeconds, Callback successCallback, Callback errorCallback) {
        try {
            int size = audioData.size();
            short[] audioArray = new short[size];

            for (int i = 0; i < size; i++) {
                int value = audioData.getInt(i);
                audioArray[i] = (short) value;
            }

            // short 배열을 byte 배열로 변환
            byte[] audioBytes = convertShortArrayToByteArray(audioArray);

            // 직접 VAD를 수행
            if (vadDetector != null) {
//                Map<String, Double> result = vadDetector.apply(audioBytes, returnSeconds);
                String result = vadDetector.apply(audioBytes, returnSeconds);

//                // 여기서 result를 자바스크립트로 전달하는 코드 작성
//                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                        .emit("VadResult", convertResultToWritableMap(result));

                // 성공 콜백 호출
//                successCallback.invoke("success", convertResultToWritableMap(result));

//                // Create a WritableMap
//                WritableMap params = Arguments.createMap();
//
//                // Put the float value into the map
//                params.putDouble("floatValue", (double) result);

                successCallback.invoke("success", result);

//                if (!result.isEmpty()) {
//                    // Emit an event to send the result to React Native
//                    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                            .emit("VadResult", result);
//                    // 성공 콜백 호출
//                    successCallback.invoke("success");
                } else {
                    Log.e("SlieroVadDetectorModule", "VadDetector is not initialized.");
                    errorCallback.invoke("VadDetector is not initialized.");
                }
            } catch (Exception e) {
                Log.e("SlieroVadDetectorModule", "VadDetector is not initialized.");
                errorCallback.invoke("VadDetector is not initialized.");
            }
//        } catch (Exception e) {
//            Log.e("SlieroVadDetectorModule", "Error in apply: " + e.getMessage());
//            // 오류 콜백 호출
//            errorCallback.invoke("error", e.getMessage());
//        }
    }

    private WritableMap convertResultToWritableMap(Map<String, Double> result) {
        WritableMap writableMap = Arguments.createMap();
        for (Map.Entry<String, Double> entry : result.entrySet()) {
            writableMap.putDouble(entry.getKey(), entry.getValue());
        }
        return writableMap;
    }

    @ReactMethod
    public void processAudioData(ReadableArray audioData, Callback successCallback, Callback errorCallback) {
        try {
            int size = audioData.size();
            short[] audioArray = new short[size];

            for (int i = 0; i < size; i++) {
                int value = audioData.getInt(i);
                audioArray[i] = (short) value;
            }

            // short 배열을 byte 배열로 변환
            byte[] audioBytes = convertShortArrayToByteArray(audioArray);

            // WritableArray를 생성하고 오디오 데이터를 추가
            WritableArray resultArray = Arguments.createArray();
            for (byte value : audioBytes) {
                resultArray.pushInt(value);
            }

            // 성공 콜백 호출
            successCallback.invoke("success", resultArray);
        } catch (Exception e) {
            // 오류 콜백 호출
            errorCallback.invoke("error", e.getMessage());
        }
    }

    private byte[] convertShortArrayToByteArray(short[] shortArray) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(shortArray.length * 2); // 2 bytes per short
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN); // 리틀 엔디안으로 설정

        for (short value : shortArray) {
            byteBuffer.putShort(value);
        }

        return byteBuffer.array();
    }

    @ReactMethod
    public void close() {
        try {
            if (vadDetector != null) {
                vadDetector.close();
            } else {
                Log.e("SlieroVadDetectorModule", "VadDetector is not initialized.");
            }
        } catch (OrtException e) {
            e.printStackTrace();
        }
    }

    private void copyModelToInternalStorage(String modelPath) throws IOException {
        // 파일이 이미 복사되었는지 확인
        File internalFile = new File(getReactApplicationContext().getFilesDir(), modelPath);
        if (!internalFile.exists()) {
            // 파일이 없으면 복사 수행
            try (InputStream inputStream = getReactApplicationContext().getResources().openRawResource(R.raw.silero_vad);
                 OutputStream outputStream = new FileOutputStream(internalFile)) {
                byte[] buffer = new byte[4 * 1024]; // 4k buffer
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        }
    }
}
