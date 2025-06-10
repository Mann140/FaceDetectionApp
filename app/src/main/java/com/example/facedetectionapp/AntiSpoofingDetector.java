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
        Log.e(TAG, "🚀 FIXED AntiSpoofingDetector - Corrected Real/Fake Logic!");

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

            // Use the CORRECTED interpretation - Real/Fake logic fixed
            AnalysisResult result = interpretOutputsCorrectly(outputs);

            Log.e(TAG, String.format("🎯 CORRECTED Result - IsReal: %s, Confidence: %.1f%% (Method: %s)",
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

        // Enhanced pattern analysis with more granular thresholds
        float firstHalf = outputs[0] + outputs[1] + outputs[2] + outputs[3];
        float secondHalf = outputs[4] + outputs[5] + outputs[6] + outputs[7];
        float totalSum = firstHalf + secondHalf;
        float average = totalSum / 8.0f;

        // Calculate statistics with refined thresholds
        float minValue = Float.MAX_VALUE, maxValue = Float.MIN_VALUE;
        int veryHighCount = 0;    // Count >0.8
        int highCount = 0;        // Count 0.5-0.8
        int moderateCount = 0;    // Count 0.1-0.5
        int lowCount = 0;         // Count 0.0-0.1
        int negativeCount = 0;    // Count <0.0

        for (int i = 0; i < 8; i++) {
            minValue = Math.min(minValue, outputs[i]);
            maxValue = Math.max(maxValue, outputs[i]);

            if (outputs[i] > 0.8f) veryHighCount++;
            else if (outputs[i] > 0.5f) highCount++;
            else if (outputs[i] > 0.1f) moderateCount++;
            else if (outputs[i] >= 0.0f) lowCount++;
            else negativeCount++;
        }

        float overallRange = maxValue - minValue;
        float standardDeviation = calculateStandardDeviation(outputs, average);

        Log.e(TAG, String.format("📊 Enhanced Analysis: sum=%.3f, avg=%.3f, range=%.3f, std=%.3f",
                totalSum, average, overallRange, standardDeviation));
        Log.e(TAG, String.format("📊 Refined Counts: veryHigh=%d, high=%d, moderate=%d, low=%d, neg=%d",
                veryHighCount, highCount, moderateCount, lowCount, negativeCount));

        // Score-based approach with stronger fake detection for images
        float realScore = 0f;
        float fakeScore = 0f;
        String method = "";

        // Factor 1: Overall level analysis (images tend to have higher averages)
        if (average > 0.9f) {
            fakeScore += 4f; // Very high average strongly suggests fake (image)
            method += "VeryHighAvg ";
        } else if (average > 0.75f) {
            fakeScore += 2.5f; // High average suggests fake (image)
            method += "HighAvg ";
        } else if (average > 0.5f) {
            fakeScore += 1f; // Moderately high average slightly suggests fake
            method += "ModAvg ";
        } else if (average > 0.2f) {
            realScore += 1.5f; // Moderate average suggests real
            method += "MidAvg ";
        } else {
            realScore += 3f; // Low average strongly suggests real
            method += "LowAvg ";
        }

        // Factor 2: Consistency analysis (images tend to be more uniform)
        if (standardDeviation < 0.03f) {
            fakeScore += 3f; // Very uniform strongly suggests fake (image)
            method += "VeryUniform ";
        } else if (standardDeviation < 0.08f) {
            fakeScore += 2f; // Somewhat uniform suggests fake (image)
            method += "Uniform ";
        } else if (standardDeviation < 0.2f) {
            fakeScore += 0.5f; // Slightly uniform leans fake
            method += "SlightUniform ";
        } else if (standardDeviation > 0.5f) {
            realScore += 3f; // High variation strongly suggests real
            method += "HighVar ";
        } else {
            realScore += 1.5f; // Moderate variation suggests real
            method += "ModVar ";
        }

        // Factor 3: Distribution analysis (images show specific patterns)
        if (veryHighCount >= 7) {
            fakeScore += 3f; // Almost all high values strongly suggest fake (image)
            method += "AlmostAllHigh ";
        } else if (veryHighCount >= 5) {
            fakeScore += 2f; // Many very high values suggest fake (image)
            method += "ManyHigh ";
        } else if (veryHighCount >= 3) {
            fakeScore += 1f; // Some very high values suggest fake
            method += "SomeHigh ";
        }

        if (negativeCount >= 4 || lowCount >= 5) {
            realScore += 3f; // Many low/negative values strongly suggest real
            method += "ManyLow ";
        } else if (negativeCount >= 2 || lowCount >= 3) {
            realScore += 2f; // Some low/negative values suggest real
            method += "SomeLow ";
        } else if (negativeCount >= 1 || lowCount >= 1) {
            realScore += 1f; // Few low/negative values suggest real
            method += "FewLow ";
        }

        // Factor 4: Range analysis (images tend to have smaller ranges)
        if (overallRange < 0.05f) {
            fakeScore += 2.5f; // Very small range strongly suggests fake (image)
            method += "VerySmallRange ";
        } else if (overallRange < 0.15f) {
            fakeScore += 1.5f; // Small range suggests fake (image)
            method += "SmallRange ";
        } else if (overallRange > 1.0f) {
            realScore += 2f; // Large range suggests real
            method += "LargeRange ";
        } else if (overallRange > 0.5f) {
            realScore += 1f; // Medium range suggests real
            method += "MediumRange ";
        }

        // Factor 5: Pattern analysis
        float halfDifference = Math.abs(firstHalf - secondHalf);
        if (halfDifference > 2.5f) {
            realScore += 2f; // Very asymmetric halves suggest real
            method += "VeryAsymmetric ";
        } else if (halfDifference > 1.0f) {
            realScore += 1f; // Asymmetric halves suggest real
            method += "Asymmetric ";
        } else if (halfDifference < 0.2f) {
            fakeScore += 1f; // Very symmetric suggests fake (image)
            method += "VerySymmetric ";
        }

        // Factor 6: Special image detection patterns
        // Images often have all values in a narrow high band
        if (veryHighCount + highCount >= 6 && overallRange < 0.2f && average > 0.7f) {
            fakeScore += 2.5f; // Classic image pattern
            method += "ImagePattern ";
        }

        // Real faces often have some variation and lower values
        if ((negativeCount + lowCount >= 2) && standardDeviation > 0.1f) {
            realScore += 1.5f; // Real face variation pattern
            method += "RealPattern ";
        }

        // Decision making with adjusted thresholds for better fake detection
        float totalScore = realScore + fakeScore;
        float fakePercentage = totalScore > 0 ? (fakeScore / totalScore) * 100f : 50f;

        boolean isReal;
        float confidence;

        // Adjusted thresholds - more sensitive to fake detection
        if (fakePercentage >= 60f) {
            isReal = false; // Fake
            confidence = Math.min(95f, 65f + (fakePercentage - 60f) * 0.8f);
            method = "Fake-" + method.trim();
        } else if (fakePercentage <= 40f) {
            isReal = true; // Real
            confidence = Math.min(95f, 65f + (60f - fakePercentage) * 0.8f);
            method = "Real-" + method.trim();
        } else {
            // Uncertain zone - lean towards fake for images (opposite of before)
            isReal = false;
            confidence = 50f + Math.abs(fakePercentage - 50f) * 0.4f;
            method = "Uncertain-Fake-" + method.trim();
        }

        Log.e(TAG, String.format("🎯 IMAGE-AWARE Decision: %s (realScore=%.1f, fakeScore=%.1f, fakePct=%.1f%%, confidence=%.1f%%)",
                isReal ? "REAL" : "FAKE", realScore, fakeScore, fakePercentage, confidence));
        Log.e(TAG, String.format("🔍 Method: %s", method));

        return new AnalysisResult(isReal, confidence, method);
    }

    private float calculateStandardDeviation(float[] values, float mean) {
        float sumSquaredDiffs = 0f;
        for (float value : values) {
            float diff = value - mean;
            sumSquaredDiffs += diff * diff;
        }
        return (float) Math.sqrt(sumSquaredDiffs / values.length);
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