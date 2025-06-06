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
    private static final int INPUT_SIZE = 256; // Model expects 256x256, not 224

    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    public AntiSpoofingDetector(Context context) {
        Log.d(TAG, "🚀 Initializing AntiSpoofingDetector with real TensorFlow model...");
        try {
            loadModel(context);
            isModelLoaded = true;
            Log.d(TAG, "✅ Real TensorFlow FaceAntiSpoofing model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load real TensorFlow model", e);
            isModelLoaded = false;
        }
    }

    private void loadModel(Context context) throws IOException {
        Log.d(TAG, "📂 Loading real TensorFlow model...");

        try {
            ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);
            Log.d(TAG, "📊 Model buffer size: " + modelBuffer.capacity() + " bytes");

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(modelBuffer, options);

            // Allocate tensors
            interpreter.allocateTensors();

            // Log detailed tensor information
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            Log.d(TAG, "🔍 Input tensor details:");
            Log.d(TAG, "   Shape: " + java.util.Arrays.toString(inputShape));
            Log.d(TAG, "   Data type: " + interpreter.getInputTensor(0).dataType());
            Log.d(TAG, "   Num elements: " + interpreter.getInputTensor(0).numElements());
            Log.d(TAG, "   Byte size: " + interpreter.getInputTensor(0).numBytes());

            Log.d(TAG, "🔍 Output tensor details:");
            Log.d(TAG, "   Shape: " + java.util.Arrays.toString(outputShape));
            Log.d(TAG, "   Data type: " + interpreter.getOutputTensor(0).dataType());
            Log.d(TAG, "   Num elements: " + interpreter.getOutputTensor(0).numElements());
            Log.d(TAG, "   Byte size: " + interpreter.getOutputTensor(0).numBytes());

            // Calculate expected input size
            if (inputShape.length >= 3) {
                int height = inputShape[1];
                int width = inputShape[2];
                int channels = inputShape.length > 3 ? inputShape[3] : 1;
                int expectedBytes = height * width * channels * 4; // 4 bytes per float

                Log.d(TAG, "🔍 Calculated expected input:");
                Log.d(TAG, "   Dimensions: " + height + "x" + width + "x" + channels);
                Log.d(TAG, "   Expected bytes: " + expectedBytes);

                if (expectedBytes != interpreter.getInputTensor(0).numBytes()) {
                    Log.w(TAG, "⚠️ Size mismatch: calculated=" + expectedBytes +
                            ", actual=" + interpreter.getInputTensor(0).numBytes());
                }
            }

            Log.d(TAG, "✅ Real TensorFlow tensors allocated successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading real TensorFlow model", e);
            throw new IOException("Failed to load real TensorFlow model", e);
        }
    }

    public MainActivity.FaceData analyzeFace(ImageProxy imageProxy, Rect faceRect) {
        Log.d(TAG, "🔍 Starting real TensorFlow face analysis - Model loaded: " + isModelLoaded);

        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "⚠️ Real model not loaded, using fallback");
            return new MainActivity.FaceData(faceRect, true, 50.0f, "ModelNotLoaded", false);
        }

        try {
            // Extract face region
            Bitmap faceBitmap = extractFaceFromImage(imageProxy, faceRect);
            if (faceBitmap == null) {
                Log.w(TAG, "⚠️ Failed to extract face bitmap");
                return new MainActivity.FaceData(faceRect, true, 50.0f, "ExtractError", false);
            }

            Log.d(TAG, "✅ Face bitmap extracted: " + faceBitmap.getWidth() + "x" + faceBitmap.getHeight());

            // Resize bitmap
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);
            Log.d(TAG, "✅ Bitmap resized to: " + resizedBitmap.getWidth() + "x" + resizedBitmap.getHeight());

            // Convert to FLOAT32 with 4 channels (RGBA)
            ByteBuffer inputBuffer = bitmapToFloat32Buffer(resizedBitmap);

            // Run inference
            float spoofScore = runInference(inputBuffer);

            // Determine result
            boolean isReal = spoofScore < 0.5f;
            float confidence = Math.abs(spoofScore - (isReal ? 0f : 1f)) * 100f;

            Log.d(TAG, String.format("🎯 Real TensorFlow analysis complete - Score: %.3f, IsReal: %s, Confidence: %.1f%%",
                    spoofScore, isReal, confidence));

            return new MainActivity.FaceData(faceRect, isReal, confidence, "TensorFlow", false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error during real TensorFlow analysis", e);
            return new MainActivity.FaceData(faceRect, true, 30.0f, "TensorFlowError", false);
        }
    }

    private ByteBuffer bitmapToFloat32Buffer(Bitmap bitmap) {
        Log.d(TAG, "🔄 Converting bitmap to FLOAT32 buffer for TensorFlow...");

        // Get the exact size the model expects
        int expectedBytes = interpreter.getInputTensor(0).numBytes();
        int[] inputShape = interpreter.getInputTensor(0).shape();

        Log.d(TAG, "📊 Model input shape: " + java.util.Arrays.toString(inputShape));
        Log.d(TAG, "📊 Model expects exactly: " + expectedBytes + " bytes");

        // Create buffer with the exact size the model expects
        ByteBuffer buffer = ByteBuffer.allocateDirect(expectedBytes);
        buffer.order(ByteOrder.nativeOrder());

        // Determine how many channels the model expects
        int channels = inputShape.length > 3 ? inputShape[3] : 3;
        Log.d(TAG, "📊 Model expects " + channels + " channels");

        // Get pixels from 256x256 bitmap
        int[] pixels = new int[256 * 256];
        bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256);

        Log.d(TAG, "📊 Processing " + pixels.length + " pixels for " + channels + " channels");

        // Convert pixels based on how many channels the model expects
        if (channels == 3) {
            // RGB format
            for (int pixel : pixels) {
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                buffer.putFloat((r / 127.5f) - 1.0f);
                buffer.putFloat((g / 127.5f) - 1.0f);
                buffer.putFloat((b / 127.5f) - 1.0f);
            }
        } else if (channels == 4) {
            // RGBA format
            for (int pixel : pixels) {
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int a = (pixel >> 24) & 0xFF;

                buffer.putFloat((r / 127.5f) - 1.0f);
                buffer.putFloat((g / 127.5f) - 1.0f);
                buffer.putFloat((b / 127.5f) - 1.0f);
                buffer.putFloat((a / 127.5f) - 1.0f);
            }
        } else {
            Log.e(TAG, "❌ Unsupported channel count: " + channels);
            throw new RuntimeException("Unsupported channel count: " + channels);
        }

        buffer.rewind();
        Log.d(TAG, "📊 Final buffer: " + buffer.capacity() + " bytes (expected: " + expectedBytes + ")");

        if (buffer.capacity() != expectedBytes) {
            Log.e(TAG, "❌ Buffer size mismatch! Expected: " + expectedBytes + ", Got: " + buffer.capacity());
            throw new RuntimeException("Buffer size mismatch");
        }

        return buffer;
    }

    private float runInference(ByteBuffer inputBuffer) {
        Log.d(TAG, "🧠 Running real TensorFlow inference...");

        try {
            Log.d(TAG, "📊 Input buffer size: " + inputBuffer.capacity() + " bytes");

            // Prepare output buffer for 8 float values
            ByteBuffer outputBuffer = ByteBuffer.allocateDirect(8 * 4);
            outputBuffer.order(ByteOrder.nativeOrder());

            // Run inference using the standard interpreter API
            interpreter.run(inputBuffer, outputBuffer);

            // Read results
            outputBuffer.rewind();
            float[] outputs = new float[8];
            for (int i = 0; i < 8; i++) {
                outputs[i] = outputBuffer.getFloat();
            }

            // Log all outputs
            StringBuilder sb = new StringBuilder("📊 TensorFlow model outputs: [");
            for (int i = 0; i < outputs.length; i++) {
                sb.append(String.format("%.3f", outputs[i]));
                if (i < outputs.length - 1) sb.append(", ");
            }
            sb.append("]");
            Log.d(TAG, sb.toString());

            // Convert to single score
            float score = convertToScore(outputs);
            Log.d(TAG, "📊 Final TensorFlow score: " + score);
            return score;

        } catch (Exception e) {
            Log.e(TAG, "❌ Real TensorFlow inference failed", e);
            throw new RuntimeException("Real TensorFlow inference failed", e);
        }
    }

    private float convertToScore(float[] outputs) {
        if (outputs.length == 1) {
            return outputs[0];
        } else if (outputs.length == 2) {
            // Typical binary classification: [fake_score, real_score]
            // Apply softmax and return fake probability
            float fakeLogit = outputs[0];
            float realLogit = outputs[1];
            float maxLogit = Math.max(fakeLogit, realLogit);

            float fakeExp = (float) Math.exp(fakeLogit - maxLogit);
            float realExp = (float) Math.exp(realLogit - maxLogit);
            float sum = fakeExp + realExp;

            float fakeProb = fakeExp / sum;
            Log.d(TAG, String.format("📊 Binary classification: fake=%.3f, real=%.3f → fake_prob=%.3f",
                    fakeLogit, realLogit, fakeProb));
            return fakeProb;

        } else if (outputs.length == 8) {
            Log.d(TAG, "📊 8-class output detected - applying improved analysis");

            // Try different approaches for 8-class output

            // Method 1: Softmax across all 8 classes, then group
            float[] softmax = applySoftmax(outputs);

            // Log softmax values
            StringBuilder sb = new StringBuilder("📊 Softmax probabilities: [");
            for (int i = 0; i < softmax.length; i++) {
                sb.append(String.format("%.3f", softmax[i]));
                if (i < softmax.length - 1) sb.append(", ");
            }
            sb.append("]");
            Log.d(TAG, sb.toString());

            // Strategy: Sum first 4 classes as "fake", last 4 as "real"
            float fakeSum = softmax[0] + softmax[1] + softmax[2] + softmax[3];
            float realSum = softmax[4] + softmax[5] + softmax[6] + softmax[7];

            Log.d(TAG, String.format("📊 Class grouping: fake_classes_sum=%.3f, real_classes_sum=%.3f",
                    fakeSum, realSum));

            // Normalize to get fake probability
            float totalSum = fakeSum + realSum;
            float fakeProb = totalSum > 0 ? fakeSum / totalSum : 0.5f;

            Log.d(TAG, String.format("📊 Final fake probability: %.3f", fakeProb));
            return fakeProb;

        } else {
            Log.w(TAG, "⚠️ Unknown TensorFlow output size " + outputs.length + ", using first value");
            return outputs[0];
        }
    }

    private float[] applySoftmax(float[] logits) {
        // Find max for numerical stability
        float max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > max) max = logits[i];
        }

        // Calculate exp and sum
        float[] exp = new float[logits.length];
        float sum = 0;
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }

        // Normalize
        float[] softmax = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            softmax[i] = exp[i] / sum;
        }

        return softmax;
    }

    private Bitmap extractFaceFromImage(ImageProxy imageProxy, Rect faceRect) {
        try {
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) return null;

            // Add padding around the face
            int padding = Math.max(10, Math.min(faceRect.width(), faceRect.height()) / 20);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;

            // Extract face region and make it square
            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);
            int size = Math.min(width, height);
            int xOffset = (width - size) / 2;
            int yOffset = (height - size) / 2;

            return Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face from image", e);
            return null;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.w(TAG, "⚠️ ImageProxy.getImage() returned null");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // Convert YUV420 to RGB
            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.w(TAG, "⚠️ Not enough image planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.w(TAG, "⚠️ Empty image buffers");
                return null;
            }

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Mirror for front camera
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting ImageProxy to bitmap", e);
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
        Log.d(TAG, "🔄 Closing real TensorFlow AntiSpoofingDetector...");
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
        Log.d(TAG, "✅ Real TensorFlow AntiSpoofingDetector closed");
    }
}