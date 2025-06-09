package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AntiSpoofingDetector {
    private static final String TAG = "AntiSpoofingDetector";
    private static final String MODEL_FILE = "FaceAntiSpoofing.tflite";
    private static final int INPUT_SIZE = 256;

    private static final float[] MEAN = {0.5f, 0.5f, 0.5f};
    private static final float[] STD = {0.5f, 0.5f, 0.5f};

    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    public AntiSpoofingDetector(Context context) {
        Log.e(TAG, "🚀 FINAL WORKING AntiSpoofingDetector - Based on YOUR data analysis!");

        try {
            loadModel(context);
            isModelLoaded = true;
            Log.e(TAG, "✅ Model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load model", e);
            isModelLoaded = false;
        }
    }

    private void loadModel(Context context) throws IOException {
        try {
            ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(modelBuffer, options);
            interpreter.allocateTensors();

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            Log.e(TAG, "🔍 Input shape: " + java.util.Arrays.toString(inputShape));
            Log.e(TAG, "🔍 Output shape: " + java.util.Arrays.toString(outputShape));

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in loadModel", e);
            throw new IOException("Failed to load model", e);
        }
    }

    public MainActivity.FaceData analyzeFace(ImageProxy imageProxy, Rect faceRect) {
        if (!isModelLoaded || interpreter == null) {
            return new MainActivity.FaceData(faceRect, true, 50.0f, "ModelNotLoaded", false);
        }

        try {
            Bitmap faceBitmap = extractFaceFromImage(imageProxy, faceRect);
            if (faceBitmap == null) {
                return new MainActivity.FaceData(faceRect, true, 50.0f, "ExtractError", false);
            }

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = preprocessForMobileFaceNet(resizedBitmap);
            float[] outputs = runInference(inputBuffer);

            // Use the CORRECT interpretation based on your data
            AnalysisResult result = interpretOutputsCorrectly(outputs);

            Log.e(TAG, String.format("🎯 FINAL Result - IsReal: %s, Confidence: %.1f%% (Method: %s)",
                    result.isReal, result.confidence, result.method));

            return new MainActivity.FaceData(faceRect, result.isReal, result.confidence, result.method, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in analyzeFace", e);
            return new MainActivity.FaceData(faceRect, true, 30.0f, "Error", false);
        }
    }

    private AnalysisResult interpretOutputsCorrectly(float[] outputs) {
        // Log the raw outputs
        StringBuilder sb = new StringBuilder("📊 Raw outputs: [");
        for (int i = 0; i < Math.min(outputs.length, 8); i++) {
            sb.append(String.format("%.4f", outputs[i]));
            if (i < Math.min(outputs.length, 8) - 1) sb.append(", ");
        }
        sb.append("]");
        Log.e(TAG, sb.toString());

        // Calculate sums and counts
        float firstHalf = outputs[0] + outputs[1] + outputs[2] + outputs[3];
        float secondHalf = outputs[4] + outputs[5] + outputs[6] + outputs[7];

        int highCount50 = 0; // Count > 0.5
        int highCount80 = 0; // Count > 0.8
        int highCount95 = 0; // Count > 0.95

        for (int i = 0; i < 8; i++) {
            if (outputs[i] > 0.5f) highCount50++;
            if (outputs[i] > 0.8f) highCount80++;
            if (outputs[i] > 0.95f) highCount95++;
        }

        Log.e(TAG, String.format("📊 Sums: first4=%.3f, second4=%.3f", firstHalf, secondHalf));
        Log.e(TAG, String.format("📊 Counts: >0.5=%d, >0.8=%d, >0.95=%d", highCount50, highCount80, highCount95));

        // INVERTED LOGIC - what was "fake" is now "real" and vice versa
        boolean isReal;
        String method;
        float confidence;

        // Pattern 1: Strong FAKE indicator - many high values AND both halves high
        if (highCount80 >= 6 && firstHalf > 3.0f && secondHalf > 3.0f) {
            isReal = false; // INVERTED: was true, now false
            method = "StrongFake-" + highCount80;
            confidence = 95f;
        }
        // Pattern 2: Moderate FAKE indicator - decent number of high values with good sums
        else if (highCount50 >= 6 && (firstHalf > 3.5f || secondHalf > 3.0f)) {
            isReal = false; // INVERTED: was true, now false
            method = "ModerateFake-" + highCount50;
            confidence = 85f;
        }
        // Pattern 3: Weak FAKE indicator - some high values but check for balance
        else if (highCount50 >= 4 && firstHalf > 2.0f && secondHalf > 2.0f) {
            isReal = false; // INVERTED: was true, now false
            method = "WeakFake-" + highCount50;
            confidence = 75f;
        }
        // Pattern 4: Very specific FAKE pattern - high first element and good coverage
        else if (outputs[0] > 0.8f && highCount50 >= 4) {
            isReal = false; // INVERTED: was true, now false
            method = "SpecificFake";
            confidence = 80f;
        }
        // Everything else is REAL (low values, poor coverage = real faces)
        else {
            isReal = true; // INVERTED: was false, now true
            method = "Default-Real";
            confidence = 75f;
        }

        Log.e(TAG, String.format("🎯 Decision: %s (confidence=%.1f%%, method=%s)",
                isReal ? "REAL" : "FAKE", confidence, method));

        return new AnalysisResult(isReal, confidence, method);
    }

    private ByteBuffer preprocessForMobileFaceNet(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            float rNorm = r / 255.0f;
            float gNorm = g / 255.0f;
            float bNorm = b / 255.0f;

            float rFinal = (rNorm - MEAN[0]) / STD[0];
            float gFinal = (gNorm - MEAN[1]) / STD[1];
            float bFinal = (bNorm - MEAN[2]) / STD[2];

            buffer.putFloat(rFinal);
            buffer.putFloat(gFinal);
            buffer.putFloat(bFinal);
        }

        buffer.rewind();
        return buffer;
    }

    private float[] runInference(ByteBuffer inputBuffer) {
        try {
            int outputSize = interpreter.getOutputTensor(0).numElements();
            ByteBuffer outputBuffer = ByteBuffer.allocateDirect(outputSize * 4);
            outputBuffer.order(ByteOrder.nativeOrder());

            interpreter.run(inputBuffer, outputBuffer);

            outputBuffer.rewind();
            float[] outputs = new float[outputSize];
            for (int i = 0; i < outputSize; i++) {
                outputs[i] = outputBuffer.getFloat();
            }

            return outputs;

        } catch (Exception e) {
            Log.e(TAG, "❌ Inference failed", e);
            throw new RuntimeException("Inference failed", e);
        }
    }

    private Bitmap extractFaceFromImage(ImageProxy imageProxy, Rect faceRect) {
        try {
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) return null;

            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;

            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            int size = Math.min(width, height);
            if (width != height) {
                int xOffset = (width - size) / 2;
                int yOffset = (height - size) / 2;
                faceBitmap = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);
            }

            return faceBitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face", e);
            return null;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) return null;

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) return null;

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting ImageProxy", e);
            return null;
        }
    }

    private void convertYUV420ToRGB(byte[] yuv420sp, int[] rgb, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
    }

    // Helper class for analysis results
    private static class AnalysisResult {
        final boolean isReal;
        final float confidence;
        final String method;

        AnalysisResult(boolean isReal, float confidence, String method) {
            this.isReal = isReal;
            this.confidence = Math.min(95f, Math.max(60f, confidence));
            this.method = method;
        }
    }
}