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

            // Calculate face distance/size metrics for context
            int imageWidth = imageProxy.getWidth();
            int imageHeight = imageProxy.getHeight();
            float faceArea = faceRect.width() * faceRect.height();
            float imageArea = imageWidth * imageHeight;
            float faceToImageRatio = faceArea / imageArea;

            Log.e(TAG, String.format("📏 Face metrics: area=%.0f, imageArea=%.0f, ratio=%.3f, faceSize=%dx%d",
                    faceArea, imageArea, faceToImageRatio, faceRect.width(), faceRect.height()));

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = preprocessForMobileFaceNet(resizedBitmap);
            float[] outputs = runInference(inputBuffer);

            // Use distance-aware interpretation
            AnalysisResult result = interpretOutputsWithDistanceAwareness(outputs, faceToImageRatio, faceRect.width());

            Log.e(TAG, String.format("🎯 DISTANCE-AWARE Result - IsReal: %s, Confidence: %.1f%% (Method: %s)",
                    result.isReal, result.confidence, result.method));

            return new MainActivity.FaceData(faceRect, result.isReal, result.confidence, result.method, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in analyzeFace", e);
            return new MainActivity.FaceData(faceRect, true, 30.0f, "Error", false);
        }
    }

    private AnalysisResult interpretOutputsWithDistanceAwareness(float[] outputs, float faceToImageRatio, int faceWidth) {
        // Log the raw outputs
        StringBuilder sb = new StringBuilder("📊 Raw outputs: [");
        for (int i = 0; i < Math.min(outputs.length, 8); i++) {
            sb.append(String.format("%.4f", outputs[i]));
            if (i < Math.min(outputs.length, 8) - 1) sb.append(", ");
        }
        sb.append("]");
        Log.e(TAG, sb.toString());

        // Enhanced pattern analysis with distance context
        float firstHalf = outputs[0] + outputs[1] + outputs[2] + outputs[3];
        float secondHalf = outputs[4] + outputs[5] + outputs[6] + outputs[7];
        float totalSum = firstHalf + secondHalf;
        float average = totalSum / 8.0f;

        // Distance categorization for adaptive thresholds
        String distanceCategory;
        float distanceMultiplier;

        if (faceToImageRatio > 0.15f || faceWidth > 200) {
            distanceCategory = "CLOSE";
            distanceMultiplier = 1.0f; // Standard detection
        } else if (faceToImageRatio > 0.08f || faceWidth > 120) {
            distanceCategory = "MEDIUM";
            distanceMultiplier = 1.2f; // Slightly more sensitive to fake
        } else if (faceToImageRatio > 0.04f || faceWidth > 80) {
            distanceCategory = "FAR";
            distanceMultiplier = 1.5f; // More aggressive fake detection
        } else {
            distanceCategory = "VERY_FAR";
            distanceMultiplier = 2.0f; // Very aggressive fake detection
        }

        Log.e(TAG, String.format("📏 Distance: %s (ratio=%.3f, width=%d, multiplier=%.1f)",
                distanceCategory, faceToImageRatio, faceWidth, distanceMultiplier));

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

        // Score-based approach with distance-aware fake detection for images
        float realScore = 0f;
        float fakeScore = 0f;
        String method = distanceCategory + "-";

        // Factor 1: Overall level analysis with distance compensation
        float avgThreshold1 = 0.9f - (distanceMultiplier - 1.0f) * 0.1f; // Lower threshold for far distances
        float avgThreshold2 = 0.75f - (distanceMultiplier - 1.0f) * 0.1f;
        float avgThreshold3 = 0.5f - (distanceMultiplier - 1.0f) * 0.05f;

        if (average > avgThreshold1) {
            fakeScore += 4f * distanceMultiplier; // Distance-adjusted fake detection
            method += "VeryHighAvg ";
        } else if (average > avgThreshold2) {
            fakeScore += 2.5f * distanceMultiplier;
            method += "HighAvg ";
        } else if (average > avgThreshold3) {
            fakeScore += 1f * Math.min(distanceMultiplier, 1.3f); // Cap multiplier for moderate avg
            method += "ModAvg ";
        } else if (average > 0.2f) {
            realScore += 1.5f; // Moderate average suggests real
            method += "MidAvg ";
        } else {
            realScore += 3f; // Low average strongly suggests real
            method += "LowAvg ";
        }

        // Factor 2: Consistency analysis with distance awareness
        float stdThreshold1 = 0.03f + (distanceMultiplier - 1.0f) * 0.01f; // Relax for far distances
        float stdThreshold2 = 0.08f + (distanceMultiplier - 1.0f) * 0.02f;

        if (standardDeviation < stdThreshold1) {
            fakeScore += 3f * distanceMultiplier; // Very uniform suggests fake
            method += "VeryUniform ";
        } else if (standardDeviation < stdThreshold2) {
            fakeScore += 2f * distanceMultiplier; // Somewhat uniform suggests fake
            method += "Uniform ";
        } else if (standardDeviation < 0.2f) {
            fakeScore += 0.5f * Math.min(distanceMultiplier, 1.2f); // Slightly uniform leans fake
            method += "SlightUniform ";
        } else if (standardDeviation > 0.5f) {
            realScore += 3f; // High variation strongly suggests real
            method += "HighVar ";
        } else {
            realScore += 1.5f; // Moderate variation suggests real
            method += "ModVar ";
        }

        // Factor 3: Distribution analysis with distance compensation
        int highCountThreshold = (int)(7 - (distanceMultiplier - 1.0f) * 1); // Lower threshold for far distances

        if (veryHighCount >= Math.max(6, highCountThreshold)) {
            fakeScore += 3f * distanceMultiplier; // Many high values suggest fake
            method += "AlmostAllHigh ";
        } else if (veryHighCount >= Math.max(4, highCountThreshold - 2)) {
            fakeScore += 2f * distanceMultiplier;
            method += "ManyHigh ";
        } else if (veryHighCount >= Math.max(2, highCountThreshold - 4)) {
            fakeScore += 1f * Math.min(distanceMultiplier, 1.3f);
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

        // Factor 4: Range analysis with distance awareness
        float rangeThreshold1 = 0.05f + (distanceMultiplier - 1.0f) * 0.02f; // Relax for distance
        float rangeThreshold2 = 0.15f + (distanceMultiplier - 1.0f) * 0.03f;

        if (overallRange < rangeThreshold1) {
            fakeScore += 2.5f * distanceMultiplier; // Very small range suggests fake
            method += "VerySmallRange ";
        } else if (overallRange < rangeThreshold2) {
            fakeScore += 1.5f * distanceMultiplier; // Small range suggests fake
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
            fakeScore += 1f * Math.min(distanceMultiplier, 1.3f); // Very symmetric suggests fake
            method += "VerySymmetric ";
        }

        // Factor 6: Distance-specific image detection patterns
        float adjustedAverage = average + (distanceMultiplier - 1.0f) * 0.05f; // Compensate for distance
        if (veryHighCount + highCount >= Math.max(5, 7 - (int)distanceMultiplier) &&
                overallRange < rangeThreshold2 && adjustedAverage > 0.6f) {
            fakeScore += 2.5f * distanceMultiplier; // Distance-adjusted image pattern
            method += "DistanceImagePattern ";
        }

        // Factor 7: Special handling for far distances
        if (distanceMultiplier >= 1.5f) {
            // At far distances, even moderate uniformity suggests images
            if (veryHighCount >= 3 && standardDeviation < 0.15f) {
                fakeScore += 1.5f; // Additional fake score for far distance uniformity
                method += "FarDistanceUniform ";
            }

            // At very far distances, bias toward fake unless clear real indicators
            if (distanceMultiplier >= 2.0f && negativeCount == 0 && lowCount <= 1) {
                fakeScore += 1f; // Very far distance bias
                method += "VeryFarBias ";
            }
        }

        // Real faces often have some variation and lower values
        if ((negativeCount + lowCount >= 2) && standardDeviation > 0.1f) {
            realScore += 1.5f; // Real face variation pattern
            method += "RealPattern ";
        }

        // Decision making with distance-adjusted thresholds
        float totalScore = realScore + fakeScore;
        float fakePercentage = totalScore > 0 ? (fakeScore / totalScore) * 100f : 50f;

        boolean isReal;
        float confidence;

        // Distance-adjusted decision thresholds
        float fakeThreshold = Math.max(55f, 60f - (distanceMultiplier - 1.0f) * 8f); // Lower threshold for far
        float realThreshold = Math.min(45f, 40f + (distanceMultiplier - 1.0f) * 5f); // Higher threshold for far

        if (fakePercentage >= fakeThreshold) {
            isReal = false; // Fake
            confidence = Math.min(95f, 65f + (fakePercentage - fakeThreshold) * 0.8f);
            method = "Fake-" + method.trim();
        } else if (fakePercentage <= realThreshold) {
            isReal = true; // Real
            confidence = Math.min(95f, 65f + (fakeThreshold - fakePercentage) * 0.8f);
            method = "Real-" + method.trim();
        } else {
            // Uncertain zone - bias based on distance
            if (distanceMultiplier >= 1.3f) {
                isReal = false; // Lean fake for distant images
                confidence = 50f + (fakePercentage - 50f) * 0.3f;
                method = "UncertainFar-Fake-" + method.trim();
            } else {
                isReal = true; // Lean real for close images
                confidence = 50f + (50f - fakePercentage) * 0.3f;
                method = "UncertainClose-Real-" + method.trim();
            }
        }

        Log.e(TAG, String.format("🎯 DISTANCE-AWARE Decision: %s (realScore=%.1f, fakeScore=%.1f, fakePct=%.1f%%, confidence=%.1f%%, thresholds: fake>%.1f, real<%.1f)",
                isReal ? "REAL" : "FAKE", realScore, fakeScore, fakePercentage, confidence, fakeThreshold, realThreshold));
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