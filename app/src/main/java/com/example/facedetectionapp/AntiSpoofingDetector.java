package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.face.Face;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AntiSpoofingDetector {
    private static final String TAG = "AntiSpoofingDetector";

    private Context context;

    // Detection parameters
    private static final float FAKE_THRESHOLD = 60.0f;
    private static final float REAL_THRESHOLD = 35.0f;
    private static final int MIN_FACE_AREA = 10000;
    private static final float MIN_FACE_RATIO = 0.15f;
    private static final float MAX_FACE_RATIO = 0.8f;

    // Temporal analysis
    private List<DetectionResult> recentResults = new ArrayList<>();
    private static final int MAX_TEMPORAL_SAMPLES = 5;

    public AntiSpoofingDetector(Context context) {
        this.context = context;
        Log.d(TAG, "🔒 AntiSpoofingDetector initialized");
    }

    /**
     * Simple method for MainActivity compatibility
     */
    public boolean isRealFace(ImageProxy imageProxy, Face face) {
        try {
            Log.d(TAG, "🔒 Starting anti-spoofing detection...");

            // Perform comprehensive anti-spoofing detection
            AntiSpoofingResult result = detectAntiSpoofing(imageProxy, face);

            return result.isReal;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in simple anti-spoofing check", e);
            return false; // Fail safe
        }
    }

    /**
     * Comprehensive anti-spoofing detection
     */
    public AntiSpoofingResult detectAntiSpoofing(ImageProxy imageProxy, Face face) {
        try {
            Log.d(TAG, "🔒 Starting comprehensive anti-spoofing detection...");

            // Extract face metrics
            FaceMetrics metrics = extractFaceMetrics(imageProxy, face);
            if (metrics == null) {
                return new AntiSpoofingResult(false, 0.0f, "Failed to extract face metrics");
            }

            // Perform basic validation checks
            if (!performBasicValidation(metrics)) {
                return new AntiSpoofingResult(false, 0.1f, "Failed basic validation");
            }

            // Extract face region for analysis
            Bitmap faceBitmap = extractFaceBitmap(imageProxy, face);
            if (faceBitmap == null) {
                return new AntiSpoofingResult(false, 0.1f, "Failed to extract face bitmap");
            }

            // Perform texture analysis
            TextureAnalysis textureResult = analyzeTexture(faceBitmap, metrics);

            // Determine distance category
            String distanceCategory = determineDistanceCategory(metrics.ratio, metrics.width);

            // Make final decision
            DetectionResult currentResult = makeDetectionDecision(textureResult, metrics, distanceCategory);

            // Add temporal analysis
            AntiSpoofingResult finalResult = performTemporalAnalysis(currentResult);

            Log.e(TAG, "🎯 FINAL Result - IsReal: " + finalResult.isReal +
                    ", Confidence: " + String.format("%.1f%%", finalResult.confidence * 100) +
                    " (Method: " + finalResult.method + ")");

            return finalResult;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in anti-spoofing detection", e);
            return new AntiSpoofingResult(false, 0.0f, "Detection error: " + e.getMessage());
        }
    }

    /**
     * Extract face metrics from the detected face
     */
    private FaceMetrics extractFaceMetrics(ImageProxy imageProxy, Face face) {
        try {
            Rect bounds = face.getBoundingBox();
            int imageArea = imageProxy.getWidth() * imageProxy.getHeight();
            int faceArea = bounds.width() * bounds.height();
            float ratio = (float) faceArea / imageArea;

            FaceMetrics metrics = new FaceMetrics();
            metrics.area = faceArea;
            metrics.imageArea = imageArea;
            metrics.ratio = ratio;
            metrics.width = bounds.width();
            metrics.height = bounds.height();
            metrics.faceSize = bounds.width() + "x" + bounds.height();

            Log.e(TAG, "📏 Face metrics: area=" + faceArea + ", imageArea=" + imageArea +
                    ", ratio=" + String.format("%.3f", ratio) + ", faceSize=" + metrics.faceSize);

            return metrics;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face metrics", e);
            return null;
        }
    }

    /**
     * Perform basic validation checks
     */
    private boolean performBasicValidation(FaceMetrics metrics) {
        // Check minimum face area
        if (metrics.area < MIN_FACE_AREA) {
            Log.e(TAG, "❌ Face too small: " + metrics.area + " < " + MIN_FACE_AREA);
            return false;
        }

        // Check face ratio bounds
        if (metrics.ratio < MIN_FACE_RATIO || metrics.ratio > MAX_FACE_RATIO) {
            Log.e(TAG, "❌ Face ratio out of bounds: " + metrics.ratio);
            return false;
        }

        // Check aspect ratio (face should be roughly square-ish)
        float aspectRatio = (float) metrics.width / metrics.height;
        if (aspectRatio < 0.5f || aspectRatio > 2.0f) {
            Log.e(TAG, "❌ Invalid aspect ratio: " + aspectRatio);
            return false;
        }

        return true;
    }

    /**
     * Extract face bitmap from image
     */
    private Bitmap extractFaceBitmap(ImageProxy imageProxy, Face face) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) {
                return null;
            }

            // Extract face region
            Rect bounds = face.getBoundingBox();
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(fullBitmap.getWidth(), bounds.right);
            int bottom = Math.min(fullBitmap.getHeight(), bounds.bottom);

            if (right <= left || bottom <= top) {
                return null;
            }

            return Bitmap.createBitmap(fullBitmap, left, top, right - left, bottom - top);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face bitmap", e);
            return null;
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);

            // Copy UV planes
            byte[] uvBuffer = new byte[uSize];
            uBuffer.get(uvBuffer);
            byte[] vArray = new byte[vSize];
            vBuffer.get(vArray);

            for (int i = 0; i < uSize; i++) {
                nv21[ySize + i * 2] = vArray[i];
                nv21[ySize + i * 2 + 1] = uvBuffer[i];
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()),
                    100, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Analyze texture patterns in the face bitmap
     */
    private TextureAnalysis analyzeTexture(Bitmap faceBitmap, FaceMetrics metrics) {
        try {
            int width = faceBitmap.getWidth();
            int height = faceBitmap.getHeight();
            int[] pixels = new int[width * height];
            faceBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // Simulate ML model outputs (replace with actual model inference)
            float[] rawOutputs = simulateMLModelOutputs(pixels);

            Log.e(TAG, "📊 Raw outputs: " + formatFloatArray(rawOutputs));

            // Analyze the outputs
            TextureAnalysis analysis = new TextureAnalysis();
            analysis.rawOutputs = rawOutputs;
            analysis.sum = 0;
            analysis.avg = 0;
            analysis.range = 0;
            analysis.std = 0;

            // Calculate statistics
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            for (float output : rawOutputs) {
                analysis.sum += output;
                min = Math.min(min, output);
                max = Math.max(max, output);
            }

            analysis.avg = analysis.sum / rawOutputs.length;
            analysis.range = max - min;

            // Calculate standard deviation
            float variance = 0;
            for (float output : rawOutputs) {
                variance += (output - analysis.avg) * (output - analysis.avg);
            }
            analysis.std = (float) Math.sqrt(variance / rawOutputs.length);

            Log.e(TAG, "📊 Enhanced Analysis: sum=" + String.format("%.3f", analysis.sum) +
                    ", avg=" + String.format("%.3f", analysis.avg) +
                    ", range=" + String.format("%.3f", analysis.range) +
                    ", std=" + String.format("%.3f", analysis.std));

            return analysis;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in texture analysis", e);
            return new TextureAnalysis();
        }
    }

    /**
     * Simulate ML model outputs (replace with actual model)
     */
    private float[] simulateMLModelOutputs(int[] pixels) {
        // This simulates the ML model outputs you saw in your logs
        // Replace this with actual TensorFlow Lite or ML Kit model inference

        float[] outputs = new float[8];

        // Analyze pixel characteristics
        long sumR = 0, sumG = 0, sumB = 0;
        for (int pixel : pixels) {
            sumR += (pixel >> 16) & 0xFF;
            sumG += (pixel >> 8) & 0xFF;
            sumB += pixel & 0xFF;
        }

        float avgR = (float) sumR / pixels.length / 255.0f;
        float avgG = (float) sumG / pixels.length / 255.0f;
        float avgB = (float) sumB / pixels.length / 255.0f;

        // Generate outputs based on characteristics (this is simplified)
        outputs[0] = (avgR - 0.5f) * 0.02f;  // Simulated texture feature
        outputs[1] = (avgG - 0.5f) * 0.02f;  // Simulated color feature
        outputs[2] = (avgB - 0.5f) * 0.02f;  // Simulated brightness feature
        outputs[3] = -0.05f + (float) Math.random() * 0.02f; // Simulated edge feature
        outputs[4] = 0.01f + (float) Math.random() * 0.04f;  // Simulated pattern feature
        outputs[5] = 0.003f + (float) Math.random() * 0.01f; // Simulated noise feature
        outputs[6] = -0.001f + (float) Math.random() * 0.01f; // Simulated gradient feature
        outputs[7] = 0.2f + (float) Math.random() * 0.4f;     // Main liveness score

        return outputs;
    }

    /**
     * Determine distance category based on face size
     */
    private String determineDistanceCategory(float ratio, int width) {
        String category;
        if (ratio > 0.25f || width > 280) {
            category = "CLOSE";
        } else if (ratio > 0.18f || width > 220) {
            category = "MEDIUM";
        } else {
            category = "FAR";
        }

        Log.e(TAG, "📏 Distance: " + category + " (ratio=" + String.format("%.3f", ratio) +
                ", width=" + width + ", multiplier=" + getDistanceMultiplier(category) + ")");

        return category;
    }

    /**
     * Get distance multiplier for threshold adjustment
     */
    private float getDistanceMultiplier(String category) {
        switch (category) {
            case "CLOSE": return 1.0f;
            case "MEDIUM": return 1.3f;
            case "FAR": return 1.6f;
            default: return 1.0f;
        }
    }

    /**
     * Make detection decision based on analysis
     */
    private DetectionResult makeDetectionDecision(TextureAnalysis analysis, FaceMetrics metrics, String distanceCategory) {
        try {
            // Count outputs in different ranges
            int veryHigh = 0, high = 0, moderate = 0, low = 0, neg = 0;

            for (float output : analysis.rawOutputs) {
                if (output > 0.1f) veryHigh++;
                else if (output > 0.05f) high++;
                else if (output > 0.02f) moderate++;
                else if (output > 0.0f) low++;
                else neg++;
            }

            Log.e(TAG, "📊 Refined Counts: veryHigh=" + veryHigh + ", high=" + high +
                    ", moderate=" + moderate + ", low=" + low + ", neg=" + neg);

            // Calculate scores
            float realScore = (low * 2.0f) + (moderate * 1.0f) + (neg * 0.5f);
            float fakeScore = (veryHigh * 3.0f) + (high * 2.0f);
            float fakePct = fakeScore / (realScore + fakeScore + 0.001f) * 100;

            // Determine thresholds based on distance
            float fakeThreshold = FAKE_THRESHOLD;
            float realThreshold = REAL_THRESHOLD;

            if ("MEDIUM".equals(distanceCategory)) {
                fakeThreshold *= 0.94f; // 56.4
            } else if ("FAR".equals(distanceCategory)) {
                fakeThreshold *= 0.88f; // 52.8
            }

            // Make decision
            boolean isReal;
            float confidence;
            String method;

            if (fakePct > fakeThreshold) {
                isReal = false;
                confidence = Math.min(0.99f, fakePct / 100.0f);
                method = "Fake-" + distanceCategory + "-HighFake";
            } else if (fakePct < realThreshold) {
                isReal = true;
                confidence = Math.max(0.85f, (100 - fakePct) / 100.0f);
                method = createRealMethod(distanceCategory, analysis);
            } else {
                // Uncertain zone - use additional heuristics
                isReal = realScore > fakeScore;
                confidence = 0.75f;
                method = "Uncertain-" + distanceCategory;
            }

            Log.e(TAG, "🎯 IMPROVED Decision: " + (isReal ? "REAL" : "FAKE") +
                    " (realScore=" + String.format("%.1f", realScore) +
                    ", fakeScore=" + String.format("%.1f", fakeScore) +
                    ", fakePct=" + String.format("%.1f%%", fakePct) +
                    ", confidence=" + String.format("%.1f%%", confidence * 100) +
                    ", thresholds: fake>" + String.format("%.1f", fakeThreshold) +
                    ", real<" + String.format("%.1f", realThreshold) + ")");

            Log.e(TAG, "🔍 Method: " + method);

            DetectionResult result = new DetectionResult();
            result.isReal = isReal;
            result.confidence = confidence;
            result.method = method;
            result.timestamp = System.currentTimeMillis();

            return result;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error making detection decision", e);
            DetectionResult result = new DetectionResult();
            result.isReal = false;
            result.confidence = 0.0f;
            result.method = "Error";
            result.timestamp = System.currentTimeMillis();
            return result;
        }
    }

    /**
     * Create method string for real face detection
     */
    private String createRealMethod(String distanceCategory, TextureAnalysis analysis) {
        StringBuilder method = new StringBuilder("Real-").append(distanceCategory);

        if (analysis.avg < 0.03f) method.append("-LowAvg");
        else if (analysis.avg < 0.06f) method.append("-MedAvg");
        else method.append("-HighAvg");

        if (analysis.std < 0.1f) method.append(" Uniform");
        else if (analysis.std < 0.15f) method.append(" SlightUniform");
        else method.append(" ModVar");

        // Count low values
        int lowCount = 0;
        for (float output : analysis.rawOutputs) {
            if (output > 0.0f && output < 0.05f) lowCount++;
        }

        if (lowCount >= 4) method.append(" ManyLow");
        else if (lowCount >= 2) method.append(" SomeLow");

        if (analysis.range > 0.3f) method.append(" MediumRange");

        return method.toString();
    }

    /**
     * Perform temporal analysis across multiple frames
     */
    private AntiSpoofingResult performTemporalAnalysis(DetectionResult currentResult) {
        try {
            // Add current result to history
            recentResults.add(currentResult);

            // Keep only recent results
            while (recentResults.size() > MAX_TEMPORAL_SAMPLES) {
                recentResults.remove(0);
            }

            if (recentResults.size() < 2) {
                // Not enough samples for temporal analysis
                return new AntiSpoofingResult(currentResult.isReal, currentResult.confidence, currentResult.method);
            }

            // Count real votes
            int realVotes = 0;
            float avgConfidence = 0;

            for (DetectionResult result : recentResults) {
                if (result.isReal) realVotes++;
                avgConfidence += result.confidence;
            }

            avgConfidence /= recentResults.size();

            boolean finalDecision = realVotes > (recentResults.size() / 2);
            float finalConfidence = avgConfidence;

            // Boost confidence if consistent results
            if (realVotes == recentResults.size() || realVotes == 0) {
                finalConfidence = Math.min(0.98f, finalConfidence * 1.1f);
            }

            String finalMethod = "Temporal-" + currentResult.method;

            Log.e(TAG, "🕐 Temporal Analysis: " + recentResults.size() + " samples, " +
                    realVotes + " real votes, final: " + (finalDecision ? "REAL" : "FAKE") +
                    " (" + String.format("%.1f%%", finalConfidence * 100) + ")");

            return new AntiSpoofingResult(finalDecision, finalConfidence, finalMethod);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in temporal analysis", e);
            return new AntiSpoofingResult(currentResult.isReal, currentResult.confidence, currentResult.method);
        }
    }

    /**
     * Format float array for logging
     */
    private String formatFloatArray(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(String.format("%.4f", array[i]));
            if (i < array.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Cleanup resources
     */
    public void close() {
        try {
            Log.d(TAG, "🔒 Closing AntiSpoofingDetector");
            recentResults.clear();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error closing AntiSpoofingDetector", e);
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Anti-spoofing result class
     */
    public static class AntiSpoofingResult {
        public boolean isReal;
        public float confidence;
        public String method;

        public AntiSpoofingResult(boolean isReal, float confidence, String method) {
            this.isReal = isReal;
            this.confidence = confidence;
            this.method = method;
        }

        @Override
        public String toString() {
            return "AntiSpoofingResult{isReal=" + isReal +
                    ", confidence=" + String.format("%.1f%%", confidence * 100) +
                    ", method='" + method + "'}";
        }
    }

    /**
     * Face metrics data class
     */
    private static class FaceMetrics {
        public int area;
        public int imageArea;
        public float ratio;
        public int width;
        public int height;
        public String faceSize;
    }

    /**
     * Texture analysis data class
     */
    private static class TextureAnalysis {
        public float[] rawOutputs;
        public float sum;
        public float avg;
        public float range;
        public float std;

        public TextureAnalysis() {
            this.rawOutputs = new float[8];
        }
    }

    /**
     * Detection result data class
     */
    private static class DetectionResult {
        public boolean isReal;
        public float confidence;
        public String method;
        public long timestamp;
    }
}