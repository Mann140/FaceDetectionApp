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
import java.util.ArrayList;
import java.util.List;

public class AntiSpoofingDetector {
    private static final String TAG = "AntiSpoofingDetector";
    private static final String MODEL_FILE = "FaceAntiSpoofing.tflite";
    private static final int INPUT_SIZE = 256;

    private static final float[] MEAN = {0.5f, 0.5f, 0.5f};
    private static final float[] STD = {0.5f, 0.5f, 0.5f};

    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    // Temporal consistency for better accuracy
    private List<AnalysisResult> recentResults = new ArrayList<>();
    private static final int TEMPORAL_WINDOW = 3; // Average over 3 frames
    private static final int MIN_FACE_SIZE = 80; // Minimum reliable face size

    public AntiSpoofingDetector(Context context) {
        Log.e(TAG, "🚀 IMPROVED AntiSpoofingDetector - Enhanced Small Face Detection!");

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

            // Check if face is too small for reliable detection
            if (faceRect.width() < MIN_FACE_SIZE || faceRect.height() < MIN_FACE_SIZE) {
                Log.e(TAG, "⚠️ Face too small for reliable detection - defaulting to FAKE for security");
                return new MainActivity.FaceData(faceRect, false, 85.0f, "TooSmall-DefaultFake", false);
            }

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = preprocessForMobileFaceNet(resizedBitmap);
            float[] outputs = runInference(inputBuffer);

            // Use improved interpretation with temporal consistency
            AnalysisResult result = interpretOutputsWithImprovedLogic(outputs, faceToImageRatio, faceRect.width());

            // Add to temporal window and get smoothed result
            AnalysisResult finalResult = applyTemporalSmoothing(result);

            Log.e(TAG, String.format("🎯 FINAL Result - IsReal: %s, Confidence: %.1f%% (Method: %s)",
                    finalResult.isReal, finalResult.confidence, finalResult.method));

            return new MainActivity.FaceData(faceRect, finalResult.isReal, finalResult.confidence, finalResult.method, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in analyzeFace", e);
            return new MainActivity.FaceData(faceRect, false, 75.0f, "Error-DefaultFake", false);
        }
    }

    private AnalysisResult interpretOutputsWithImprovedLogic(float[] outputs, float faceToImageRatio, int faceWidth) {
        // Log the raw outputs
        StringBuilder sb = new StringBuilder("📊 Raw outputs: [");
        for (int i = 0; i < Math.min(outputs.length, 8); i++) {
            sb.append(String.format("%.4f", outputs[i]));
            if (i < Math.min(outputs.length, 8) - 1) sb.append(", ");
        }
        sb.append("]");
        Log.e(TAG, sb.toString());

        // Enhanced pattern analysis with more aggressive small face detection
        float firstHalf = outputs[0] + outputs[1] + outputs[2] + outputs[3];
        float secondHalf = outputs[4] + outputs[5] + outputs[6] + outputs[7];
        float totalSum = firstHalf + secondHalf;
        float average = totalSum / 8.0f;

        // More aggressive distance categorization
        String distanceCategory;
        float distanceMultiplier;

        if (faceToImageRatio > 0.20f || faceWidth > 250) {
            distanceCategory = "CLOSE";
            distanceMultiplier = 1.0f;
        } else if (faceToImageRatio > 0.12f || faceWidth > 150) {
            distanceCategory = "MEDIUM";
            distanceMultiplier = 1.3f;
        } else if (faceToImageRatio > 0.06f || faceWidth > 100) {
            distanceCategory = "FAR";
            distanceMultiplier = 1.8f;
        } else {
            distanceCategory = "VERY_FAR";
            distanceMultiplier = 2.5f; // More aggressive for very small faces
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

        // More aggressive scoring for small faces
        float realScore = 0f;
        float fakeScore = 0f;
        String method = distanceCategory + "-";

        // Factor 1: Average analysis - more aggressive for small faces
        float avgThreshold1 = Math.max(0.6f, 0.9f - (distanceMultiplier - 1.0f) * 0.15f);
        float avgThreshold2 = Math.max(0.5f, 0.75f - (distanceMultiplier - 1.0f) * 0.12f);
        float avgThreshold3 = Math.max(0.3f, 0.5f - (distanceMultiplier - 1.0f) * 0.08f);

        if (average > avgThreshold1) {
            fakeScore += 5f * distanceMultiplier; // Increased penalty
            method += "VeryHighAvg ";
        } else if (average > avgThreshold2) {
            fakeScore += 3f * distanceMultiplier; // Increased penalty
            method += "HighAvg ";
        } else if (average > avgThreshold3) {
            fakeScore += 1.5f * Math.min(distanceMultiplier, 1.5f);
            method += "ModAvg ";
        } else if (average > 0.15f) {
            realScore += 2f; // Slightly increased real score
            method += "MidAvg ";
        } else {
            realScore += 4f; // Increased real score
            method += "LowAvg ";
        }

        // Factor 2: Uniformity analysis - more sensitive for small faces
        float stdThreshold1 = Math.max(0.02f, 0.03f + (distanceMultiplier - 1.0f) * 0.005f);
        float stdThreshold2 = Math.max(0.05f, 0.08f + (distanceMultiplier - 1.0f) * 0.015f);

        if (standardDeviation < stdThreshold1) {
            fakeScore += 4f * distanceMultiplier; // Increased penalty for uniformity
            method += "VeryUniform ";
        } else if (standardDeviation < stdThreshold2) {
            fakeScore += 2.5f * distanceMultiplier;
            method += "Uniform ";
        } else if (standardDeviation < 0.15f) {
            fakeScore += 1f * Math.min(distanceMultiplier, 1.3f);
            method += "SlightUniform ";
        } else if (standardDeviation > 0.4f) {
            realScore += 3.5f; // Increased real score for high variation
            method += "HighVar ";
        } else {
            realScore += 2f;
            method += "ModVar ";
        }

        // Factor 3: Distribution analysis - more aggressive thresholds
        int highCountThreshold = Math.max(4, (int)(8 - (distanceMultiplier - 1.0f) * 2));

        if (veryHighCount >= Math.max(5, highCountThreshold)) {
            fakeScore += 4f * distanceMultiplier; // Increased penalty
            method += "AlmostAllHigh ";
        } else if (veryHighCount >= Math.max(3, highCountThreshold - 2)) {
            fakeScore += 3f * distanceMultiplier; // Increased penalty
            method += "ManyHigh ";
        } else if (veryHighCount >= Math.max(2, highCountThreshold - 4)) {
            fakeScore += 1.5f * Math.min(distanceMultiplier, 1.5f);
            method += "SomeHigh ";
        }

        if (negativeCount >= 4 || lowCount >= 5) {
            realScore += 4f; // Increased real score
            method += "ManyLow ";
        } else if (negativeCount >= 2 || lowCount >= 3) {
            realScore += 2.5f;
            method += "SomeLow ";
        } else if (negativeCount >= 1 || lowCount >= 1) {
            realScore += 1.5f;
            method += "FewLow ";
        }

        // Factor 4: Range analysis
        float rangeThreshold1 = Math.max(0.03f, 0.05f + (distanceMultiplier - 1.0f) * 0.015f);
        float rangeThreshold2 = Math.max(0.08f, 0.15f + (distanceMultiplier - 1.0f) * 0.025f);

        if (overallRange < rangeThreshold1) {
            fakeScore += 3f * distanceMultiplier;
            method += "VerySmallRange ";
        } else if (overallRange < rangeThreshold2) {
            fakeScore += 2f * distanceMultiplier;
            method += "SmallRange ";
        } else if (overallRange > 0.8f) {
            realScore += 2.5f;
            method += "LargeRange ";
        } else if (overallRange > 0.4f) {
            realScore += 1.5f;
            method += "MediumRange ";
        }

        // Factor 5: Small face bias - be more suspicious of small faces
        if (distanceMultiplier >= 2.0f) {
            if (veryHighCount + highCount >= 4 && standardDeviation < 0.2f) {
                fakeScore += 2f; // Additional penalty for small faces with suspicious patterns
                method += "SmallFaceBias ";
            }

            // Very aggressive for tiny faces
            if (distanceMultiplier >= 2.5f && average > 0.4f && veryHighCount >= 3) {
                fakeScore += 2f; // Extra penalty for very small faces
                method += "TinyFacePenalty ";
            }
        }

        // Decision making with more conservative thresholds for small faces
        float totalScore = realScore + fakeScore;
        float fakePercentage = totalScore > 0 ? (fakeScore / totalScore) * 100f : 50f;

        boolean isReal;
        float confidence;

        // More conservative thresholds (easier to classify as fake)
        float fakeThreshold = Math.max(40f, 60f - (distanceMultiplier - 1.0f) * 12f); // Lower threshold
        float realThreshold = Math.min(35f, 40f + (distanceMultiplier - 1.0f) * 8f);  // Higher threshold

        if (fakePercentage >= fakeThreshold) {
            isReal = false; // Fake
            confidence = Math.min(95f, 70f + (fakePercentage - fakeThreshold) * 0.6f);
            method = "Fake-" + method.trim();
        } else if (fakePercentage <= realThreshold) {
            isReal = true; // Real
            confidence = Math.min(95f, 70f + (fakeThreshold - fakePercentage) * 0.6f);
            method = "Real-" + method.trim();
        } else {
            // Uncertain zone - for small faces, default to fake for security
            if (distanceMultiplier >= 1.8f) {
                isReal = false; // More conservative for distant faces
                confidence = 65f;
                method = "UncertainSmall-Fake-" + method.trim();
            } else {
                isReal = true;
                confidence = 60f;
                method = "UncertainLarge-Real-" + method.trim();
            }
        }

        Log.e(TAG, String.format("🎯 IMPROVED Decision: %s (realScore=%.1f, fakeScore=%.1f, fakePct=%.1f%%, confidence=%.1f%%, thresholds: fake>%.1f, real<%.1f)",
                isReal ? "REAL" : "FAKE", realScore, fakeScore, fakePercentage, confidence, fakeThreshold, realThreshold));
        Log.e(TAG, String.format("🔍 Method: %s", method));

        return new AnalysisResult(isReal, confidence, method);
    }

    private AnalysisResult applyTemporalSmoothing(AnalysisResult currentResult) {
        // Add current result to temporal window
        recentResults.add(currentResult);

        // Keep only recent results
        if (recentResults.size() > TEMPORAL_WINDOW) {
            recentResults.remove(0);
        }

        // If we don't have enough samples yet, return current result
        if (recentResults.size() < 2) {
            return currentResult;
        }

        // Calculate temporal average
        float avgConfidence = 0f;
        int realCount = 0;

        for (AnalysisResult result : recentResults) {
            avgConfidence += result.confidence;
            if (result.isReal) realCount++;
        }

        avgConfidence /= recentResults.size();

        // Use majority vote for classification, but be conservative (bias toward fake)
        boolean isReal = realCount > (recentResults.size() / 2);

        // For small faces, require stronger evidence for "real" classification
        if (currentResult.method.contains("VERY_FAR") || currentResult.method.contains("FAR")) {
            // Require 2/3 majority for real classification on small faces
            isReal = realCount >= Math.ceil(recentResults.size() * 0.67);
        }

        String method = "Temporal-" + currentResult.method;

        Log.e(TAG, String.format("🕐 Temporal Analysis: %d samples, %d real votes, final: %s (%.1f%%)",
                recentResults.size(), realCount, isReal ? "REAL" : "FAKE", avgConfidence));

        return new AnalysisResult(isReal, Math.min(95f, Math.max(60f, avgConfidence)), method);
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
        recentResults.clear();
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