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

    // Enhanced configuration
    private static final int MAX_HISTORY_SIZE = 20;
    private static final int MIN_FRAMES_FOR_TEMPORAL = 5;
    private static final int MIN_FACE_SIZE = 90;
    private static final float STATIC_CONFIDENCE_THRESHOLD = 0.85f;
    private static final float LIVENESS_CONFIDENCE_THRESHOLD = 0.75f;

    // Cache management fields
    private long lastDetectionTime = 0;
    private static final long CACHE_CLEAR_TIMEOUT = 2000; // 2 seconds
    private static final long AUTO_CLEAR_TIMEOUT = 5000; // 5 seconds for automatic clearing

    // Detection components
    private final List<DetectionResult> detectionHistory = new ArrayList<>();
    private final StaticImageAnalyzer staticAnalyzer = new StaticImageAnalyzer();
    private final LivenessAnalyzer livenessAnalyzer = new LivenessAnalyzer();
    private final TemporalAnalyzer temporalAnalyzer = new TemporalAnalyzer();

    public AntiSpoofingDetector(Context context) {
        Log.e(TAG, "🚀 AntiSpoofingDetector - Consistent Detection v5.3 with Auto Cache Clearing");

        try {
            loadModel(context);
            isModelLoaded = true;
            Log.e(TAG, "✅ Model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load model: " + e.getMessage(), e);
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

    // ========== CACHE MANAGEMENT METHODS ==========

    /**
     * Clears all cached data when no faces are detected
     */
    public void clearCache() {
        Log.e(TAG, "🧹 Clearing anti-spoofing cache - no faces detected");

        // Clear detection history
        detectionHistory.clear();

        // Clear liveness analyzer recent outputs
        if (livenessAnalyzer != null) {
            livenessAnalyzer.clearCache();
        }

        Log.e(TAG, String.format("✅ Cache cleared successfully - History: %d, Liveness: %d",
                detectionHistory.size(),
                livenessAnalyzer != null ? livenessAnalyzer.getCacheSize() : 0));
    }

    /**
     * Updates the timestamp of the last face detection
     */
    public void updateLastDetectionTime() {
        lastDetectionTime = System.currentTimeMillis();
    }

    /**
     * Clears cache if too much time has passed since last detection
     * Call this periodically or on each frame to auto-clear stale data
     */
    public void clearCacheIfStale() {
        long currentTime = System.currentTimeMillis();
        if (lastDetectionTime > 0 && (currentTime - lastDetectionTime) > AUTO_CLEAR_TIMEOUT) {
            Log.e(TAG, String.format("⏰ Auto-clearing stale cache after %dms",
                    currentTime - lastDetectionTime));
            clearCache();
            lastDetectionTime = 0; // Reset timer
        }
    }

    /**
     * Forces an immediate cache clear and resets all timers
     */
    public void forceClearCache() {
        Log.e(TAG, "🔄 Force clearing cache");
        clearCache();
        lastDetectionTime = 0;
    }

    /**
     * Gets the current cache status for debugging
     */
    public String getCacheStatus() {
        long timeSinceDetection = lastDetectionTime > 0 ?
                System.currentTimeMillis() - lastDetectionTime : -1;

        return String.format("H:%d L:%d T:%dms",
                detectionHistory.size(),
                livenessAnalyzer != null ? livenessAnalyzer.getCacheSize() : 0,
                timeSinceDetection);
    }

    /**
     * Checks if the cache has meaningful data
     */
    public boolean hasCachedData() {
        return !detectionHistory.isEmpty() ||
                (livenessAnalyzer != null && livenessAnalyzer.getCacheSize() > 0);
    }

    /**
     * Gets detailed cache information for debugging
     */
    public String getDetailedCacheInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== CACHE STATUS ===\n");
        info.append(String.format("Detection History: %d entries\n", detectionHistory.size()));

        if (livenessAnalyzer != null) {
            info.append(String.format("Liveness Cache: %d entries\n", livenessAnalyzer.getCacheSize()));
        }

        if (lastDetectionTime > 0) {
            long age = System.currentTimeMillis() - lastDetectionTime;
            info.append(String.format("Last Detection: %dms ago\n", age));
            info.append(String.format("Auto-clear in: %dms\n", Math.max(0, AUTO_CLEAR_TIMEOUT - age)));
        } else {
            info.append("Last Detection: Never\n");
        }

        info.append("==================");
        return info.toString();
    }

    // ========== MAIN ANALYSIS METHODS ==========

    public MainActivity.FaceData analyzeFace(ImageProxy imageProxy, Rect faceRect) {
        Log.e(TAG, "🚀 Starting face analysis...");

        if (!isModelLoaded || interpreter == null) {
            Log.e(TAG, "❌ Model not loaded!");
            return new MainActivity.FaceData(faceRect, false, 85.0f, "ModelNotLoaded", false);
        }

        try {
            Bitmap faceBitmap = extractFaceFromImage(imageProxy, faceRect);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap!");
                return new MainActivity.FaceData(faceRect, false, 80.0f, "ExtractError", false);
            }

            // Calculate face metrics
            int imageWidth = imageProxy.getWidth();
            int imageHeight = imageProxy.getHeight();
            float faceArea = faceRect.width() * faceRect.height();
            float imageArea = imageWidth * imageHeight;

            Log.e(TAG, String.format("📊 Face: %dx%d, ratio=%.3f",
                    faceRect.width(), faceRect.height(), faceArea / imageArea));

            // Size check
            if (faceRect.width() < MIN_FACE_SIZE || faceRect.height() < MIN_FACE_SIZE) {
                Log.e(TAG, "⚠️ Face too small - defaulting to FAKE");
                return new MainActivity.FaceData(faceRect, false, 85.0f, "TooSmall", false);
            }

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = preprocessForMobileFaceNet(resizedBitmap);
            float[] outputs = runInference(inputBuffer);

            Log.e(TAG, "✅ ML inference complete");

            // Use enhanced detection logic
            DetectionResult result = detectAntiSpoofing(outputs, (int)faceArea, (int)imageArea, faceRect.width(), faceRect.height());

            Log.e(TAG, String.format("🎯 RESULT: %s (%.1f%% - %s)",
                    result.isReal ? "REAL" : "FAKE", result.confidence, result.method));

            return new MainActivity.FaceData(faceRect, result.isReal, result.confidence, result.method, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage(), e);
            return new MainActivity.FaceData(faceRect, false, 80.0f, "Error", false);
        }
    }

    /**
     * Main detection method - analyzes face anti-spoofing with multiple layers
     */
    public DetectionResult detectAntiSpoofing(float[] rawOutputs, int faceArea, int imageArea, int faceWidth, int faceHeight) {
        Log.e(TAG, "🔍 Starting Advanced Anti-Spoofing Analysis...");

        // Log raw outputs first
        StringBuilder sb = new StringBuilder("📊 Raw outputs: [");
        for (int i = 0; i < Math.min(rawOutputs.length, 8); i++) {
            sb.append(String.format("%.4f", rawOutputs[i]));
            if (i < Math.min(rawOutputs.length, 8) - 1) sb.append(", ");
        }
        sb.append("]");
        Log.e(TAG, sb.toString());

        // Step 1: Calculate face metrics
        FaceMetrics metrics = calculateFaceMetrics(faceArea, imageArea, faceWidth, faceHeight);
        logFaceMetrics(metrics);

        // Step 2: Perform static image analysis
        StaticAnalysis staticAnalysis = staticAnalyzer.analyzeForStaticPatterns(rawOutputs, metrics);
        logStaticAnalysis(staticAnalysis);

        // Step 3: Perform liveness analysis
        LivenessAnalysis livenessAnalysis = livenessAnalyzer.analyzeLiveness(rawOutputs, metrics);
        logLivenessAnalysis(livenessAnalysis);

        // Step 4: Initial classification
        DetectionResult initialResult = performInitialClassification(rawOutputs, metrics, staticAnalysis, livenessAnalysis);
        Log.e(TAG, String.format("🔄 Initial Result: %s (%.1f%% - %s)",
                initialResult.isReal ? "REAL" : "FAKE", initialResult.confidence, initialResult.method));

        // Step 5: Store result and perform temporal analysis
        detectionHistory.add(initialResult);
        if (detectionHistory.size() > MAX_HISTORY_SIZE) {
            detectionHistory.remove(0);
        }

        Log.e(TAG, String.format("📚 Detection History: %d entries", detectionHistory.size()));

        // Step 6: Apply temporal analysis if enough history
        DetectionResult finalResult = temporalAnalyzer.applyTemporalAnalysis(initialResult, detectionHistory);

        logFinalResult(finalResult);
        return finalResult;
    }

    private FaceMetrics calculateFaceMetrics(int faceArea, int imageArea, int faceWidth, int faceHeight) {
        FaceMetrics metrics = new FaceMetrics();
        metrics.faceArea = faceArea;
        metrics.imageArea = imageArea;
        metrics.ratio = (float) faceArea / imageArea;
        metrics.width = faceWidth;
        metrics.height = faceHeight;

        // Enhanced distance categorization
        if (metrics.ratio < 0.08f) {
            metrics.distanceCategory = "VERY_FAR";
            metrics.distanceMultiplier = 2.5f;
        } else if (metrics.ratio < 0.15f) {
            metrics.distanceCategory = "FAR";
            metrics.distanceMultiplier = 2.0f;
        } else if (metrics.ratio < 0.25f) {
            metrics.distanceCategory = "MEDIUM";
            metrics.distanceMultiplier = 1.5f;
        } else if (metrics.ratio < 0.40f) {
            metrics.distanceCategory = "CLOSE";
            metrics.distanceMultiplier = 1.2f;
        } else {
            metrics.distanceCategory = "VERY_CLOSE";
            metrics.distanceMultiplier = 1.0f;
        }

        return metrics;
    }

    private DetectionResult performInitialClassification(float[] rawOutputs, FaceMetrics metrics,
                                                         StaticAnalysis staticAnalysis, LivenessAnalysis livenessAnalysis) {

        Log.e(TAG, "🔍 Starting Initial Classification...");

        // Priority 1: Strong static image detection
        if (staticAnalysis.isStaticImage && staticAnalysis.staticConfidence > STATIC_CONFIDENCE_THRESHOLD) {
            Log.e(TAG, String.format("🚨 STATIC IMAGE DETECTED! Confidence: %.1f%%, Pattern: %s",
                    staticAnalysis.staticConfidence * 100, staticAnalysis.pattern));
            return new DetectionResult(false, staticAnalysis.staticConfidence * 100,
                    "StaticImageDetected-" + staticAnalysis.pattern);
        }

        // Priority 2: Strong liveness indicators
        if (livenessAnalysis.showsLiveness && livenessAnalysis.livenessScore > LIVENESS_CONFIDENCE_THRESHOLD) {
            Log.e(TAG, String.format("✅ LIVENESS DETECTED! Score: %.1f%%, Indicators: %s",
                    livenessAnalysis.livenessScore * 100, livenessAnalysis.livenessIndicators));
            return new DetectionResult(true, livenessAnalysis.livenessScore * 100,
                    "LivenessDetected-" + livenessAnalysis.livenessIndicators);
        }

        Log.e(TAG, "⚖️ Proceeding to Statistical Classification...");

        // Priority 3: Statistical analysis with adaptive thresholds
        return performStatisticalClassification(rawOutputs, metrics, staticAnalysis, livenessAnalysis);
    }

    private DetectionResult performStatisticalClassification(float[] rawOutputs, FaceMetrics metrics,
                                                             StaticAnalysis staticAnalysis, LivenessAnalysis livenessAnalysis) {

        Log.e(TAG, "📊 Starting Statistical Classification...");

        // Calculate enhanced statistical features
        float[] stats = calculateEnhancedStatistics(rawOutputs);
        int[] counts = countValueDistribution(rawOutputs);

        Log.e(TAG, String.format("📈 Statistics: avg=%.3f, std=%.3f, min=%.3f, max=%.3f",
                stats[1], stats[3], stats[4], stats[5]));
        Log.e(TAG, String.format("📊 Value Counts: veryHigh=%d, high=%d, moderate=%d, low=%d, negative=%d",
                counts[0], counts[1], counts[2], counts[3], counts[4]));

        // Adaptive threshold calculation
        float[] thresholds = calculateAdaptiveThresholds(metrics, staticAnalysis, livenessAnalysis);
        float fakeThreshold = thresholds[0], realThreshold = thresholds[1];

        Log.e(TAG, String.format("🎯 Adaptive Thresholds: fake>%.1f%%, real<%.1f%%", fakeThreshold, realThreshold));

        // Enhanced scoring system
        float realScore = calculateRealScore(counts, stats, metrics);
        float fakeScore = calculateFakeScore(counts, stats, metrics, staticAnalysis);

        Log.e(TAG, String.format("⚖️ Scores: real=%.1f, fake=%.1f", realScore, fakeScore));

        float totalScore = realScore + fakeScore;
        float fakePct = totalScore > 0 ? (fakeScore / totalScore) * 100 : 0;

        Log.e(TAG, String.format("🔢 Fake Percentage: %.1f%% (total score: %.1f)", fakePct, totalScore));

        // Classification logic
        boolean isReal;
        float confidence;
        String method;

        if (fakePct > fakeThreshold) {
            isReal = false;
            confidence = Math.min(95f, 50f + fakePct);
            method = "Statistical-Fake";
            Log.e(TAG, String.format("❌ FAKE: %.1f%% > %.1f%% threshold", fakePct, fakeThreshold));
        } else if (fakePct < realThreshold) {
            isReal = true;
            confidence = Math.min(95f, 80f + (realThreshold - fakePct));
            method = "Statistical-Real";
            Log.e(TAG, String.format("✅ REAL: %.1f%% < %.1f%% threshold", fakePct, realThreshold));
        } else {
            // Uncertain zone - use additional heuristics
            Log.e(TAG, String.format("❓ UNCERTAIN: %.1f%% is between thresholds", fakePct));
            isReal = resolveUncertainCase(rawOutputs, metrics, staticAnalysis, livenessAnalysis);
            confidence = 60f + Math.abs(fakePct - 50f) * 0.8f;
            method = "Statistical-Uncertain";
            Log.e(TAG, String.format("🔍 Uncertain resolved to: %s", isReal ? "REAL" : "FAKE"));
        }

        DetectionResult result = new DetectionResult(isReal, confidence, method);
        result.rawOutputs = rawOutputs;
        result.faceMetrics = metrics;
        result.staticAnalysis = staticAnalysis;
        result.livenessAnalysis = livenessAnalysis;

        return result;
    }

    private boolean resolveUncertainCase(float[] rawOutputs, FaceMetrics metrics,
                                         StaticAnalysis staticAnalysis, LivenessAnalysis livenessAnalysis) {
        int realIndicators = 0;
        int fakeIndicators = 0;

        // Check for weak static patterns
        if (staticAnalysis.suspiciousPatterns > 2) fakeIndicators += 2;

        // Check for any liveness signs
        if (livenessAnalysis.variabilityScore > 0.3f) realIndicators += 1;
        if (livenessAnalysis.movementScore > 0.2f) realIndicators += 1;

        // Distance-based heuristics
        if (metrics.distanceCategory.equals("FAR") || metrics.distanceCategory.equals("VERY_FAR")) {
            realIndicators += 1; // Real people often have varying distances
        }

        // Value distribution patterns
        float[] stats = calculateEnhancedStatistics(rawOutputs);
        float std = stats[3]; // standard deviation
        float max = stats[5]; // max value
        float min = stats[4]; // min value

        if (std > 0.4f) realIndicators += 1; // High variance suggests real
        if (max > 0.99f && min < 0.01f) fakeIndicators += 1; // Extreme values suggest fake

        return realIndicators > fakeIndicators;
    }

    private float[] calculateAdaptiveThresholds(FaceMetrics metrics, StaticAnalysis staticAnalysis, LivenessAnalysis livenessAnalysis) {
        float baseFakeThreshold = 50f; // Balanced starting point
        float baseRealThreshold = 30f;

        // Adjust based on static analysis - prioritize static detection
        if (staticAnalysis.suspiciousPatterns >= 2) {
            baseFakeThreshold -= staticAnalysis.suspiciousPatterns * 8f; // Strong adjustment for photos
        } else if (staticAnalysis.suspiciousPatterns > 0) {
            baseFakeThreshold -= staticAnalysis.suspiciousPatterns * 5f;
        }

        // Be more generous based on liveness - but not if static patterns exist
        if (staticAnalysis.suspiciousPatterns == 0) {
            if (livenessAnalysis.livenessScore > 0.4f) {
                baseRealThreshold += 15f; // More generous for true liveness
                baseFakeThreshold += 5f; // Harder to call fake
            } else if (livenessAnalysis.livenessScore > 0.2f) {
                baseRealThreshold += 10f; // Still generous
            }
        }

        // Photo-like penalty should be strong
        if (livenessAnalysis.livenessIndicators != null &&
                livenessAnalysis.livenessIndicators.contains("PhotoLikePenalty")) {
            baseFakeThreshold -= 12f; // Strong penalty for photo patterns
        }

        // Distance adjustments
        if (metrics.distanceCategory.equals("VERY_FAR")) {
            baseFakeThreshold -= 5f;
            baseRealThreshold += 5f;
        } else if (metrics.distanceCategory.equals("VERY_CLOSE") || metrics.distanceCategory.equals("CLOSE")) {
            baseFakeThreshold += 3f;
            baseRealThreshold += 8f; // More lenient for close faces
        }

        // Bounds checking
        baseFakeThreshold = Math.max(20f, Math.min(75f, baseFakeThreshold));
        baseRealThreshold = Math.max(10f, Math.min(60f, baseRealThreshold));

        return new float[]{baseFakeThreshold, baseRealThreshold};
    }

    private float calculateRealScore(int[] counts, float[] stats, FaceMetrics metrics) {
        float score = 0f;

        // Favor moderate variance (real faces have natural variation)
        if (stats[3] > 0.3f && stats[3] < 0.6f) score += 3f;

        // Favor balanced distributions
        if (counts[3] > 0 && counts[0] > 0) score += 2f; // Mix of low and high values

        // Distance-based scoring
        if (metrics.distanceCategory.equals("FAR") || metrics.distanceCategory.equals("MEDIUM")) {
            score += 2f; // Real people move and have varying distances
        }

        // Natural patterns
        if (counts[2] > 0) score += 1f; // Some moderate values
        if (stats[6] > 0.8f && stats[6] < 1.1f) score += 1f; // Good range

        return score;
    }

    private float calculateFakeScore(int[] counts, float[] stats, FaceMetrics metrics, StaticAnalysis staticAnalysis) {
        float score = 0f;

        // Static image indicators (most important)
        score += staticAnalysis.suspiciousPatterns * 3f;

        // Photo-specific patterns - CONSISTENT DETECTION
        if (counts[0] >= 1 && stats[5] > 0.98f && counts[4] >= 2 && stats[1] > 0.15f) {
            score += 5f; // Strong photo pattern - lowered threshold
        }

        // Very extreme patterns
        if (counts[0] >= 2 && stats[5] > 0.97f && counts[4] >= 2 && stats[3] > 0.3f) {
            score += 4f; // High contrast photo pattern
        }

        if (stats[5] > 0.995f && stats[3] < 0.1f) {
            score += 3f; // Extremely high max with very low variance
        }

        // Strong bi-modal distribution
        if ((counts[0] + counts[4]) >= 4 && (counts[1] + counts[2]) <= 2) {
            score += 2f; // Photo-like distribution
        }

        return score;
    }

    // Helper classes for specialized analysis

    private static class StaticImageAnalyzer {
        public StaticAnalysis analyzeForStaticPatterns(float[] rawOutputs, FaceMetrics metrics) {
            StaticAnalysis analysis = new StaticAnalysis();
            analysis.statisticalFeatures = calculateEnhancedStatistics(rawOutputs);
            analysis.suspiciousPatterns = 0;

            float avg = analysis.statisticalFeatures[1];
            float std = analysis.statisticalFeatures[3];
            float max = analysis.statisticalFeatures[5];
            float min = analysis.statisticalFeatures[4];

            int[] counts = countValueDistribution(rawOutputs);
            int veryHighCount = counts[0];
            int negativeCount = counts[4];

            Log.e(TAG, String.format("🔍 Static Check: vHigh=%d, neg=%d, max=%.3f, std=%.3f",
                    veryHighCount, negativeCount, max, std));

            // Pattern 1: Classic static image (very high values, low variance)
            if (veryHighCount >= 5 && std < 0.05f && max > 0.998f) {
                analysis.isStaticImage = true;
                analysis.pattern = "ClassicStatic";
                analysis.staticConfidence = 0.95f;
                analysis.suspiciousPatterns += 3;
                Log.e(TAG, "🚨 ClassicStatic pattern detected!");
                return analysis;
            }

            // Pattern 2: Photo/Screenshot pattern - CONSISTENT DETECTION
            if (max > 0.98f && veryHighCount >= 1 && negativeCount >= 2 && std > 0.3f && avg > 0.15f) {
                analysis.isStaticImage = true;
                analysis.pattern = "PhotoStatic";
                analysis.staticConfidence = 0.90f;
                analysis.suspiciousPatterns += 3;
                Log.e(TAG, "🚨 PhotoStatic pattern detected!");
                return analysis;
            }

            // Pattern 3: Strong photo indicators - LOWER THRESHOLD
            if (max > 0.975f && veryHighCount >= 1 && negativeCount >= 2 && avg > 0.1f) {
                analysis.isStaticImage = true;
                analysis.pattern = "StrongPhotoStatic";
                analysis.staticConfidence = 0.88f;
                analysis.suspiciousPatterns += 3;
                Log.e(TAG, "🚨 StrongPhotoStatic pattern detected!");
                return analysis;
            }

            // Pattern 4: High contrast photo pattern
            if (max > 0.95f && std > 0.35f && negativeCount >= 3 && veryHighCount >= 1) {
                analysis.isStaticImage = true;
                analysis.pattern = "HighContrastPhoto";
                analysis.staticConfidence = 0.85f;
                analysis.suspiciousPatterns += 3;
                Log.e(TAG, "🚨 HighContrastPhoto pattern detected!");
                return analysis;
            }

            // Only add suspicious patterns for moderate cases
            // Pattern 5: Very high values with moderate variance
            if (veryHighCount >= 2 && std > 0.25f && max > 0.94f) {
                analysis.suspiciousPatterns += 1;
                Log.e(TAG, "⚠️ High contrast pattern");
            }

            // Pattern 6: Extreme max with many negatives
            if (max > 0.97f && negativeCount >= 3) {
                analysis.suspiciousPatterns += 1;
                Log.e(TAG, "⚠️ Extreme range pattern");
            }

            // Pattern 7: Photo-like distribution
            if (max > 0.92f && std > 0.4f && avg > 0.2f) {
                analysis.suspiciousPatterns += 1;
                Log.e(TAG, "⚠️ Photo-like distribution");
            }

            // Require multiple suspicious patterns for static detection
            analysis.isStaticImage = analysis.suspiciousPatterns >= 2;
            analysis.staticConfidence = Math.min(0.95f, 0.65f + (analysis.suspiciousPatterns * 0.1f));

            if (analysis.suspiciousPatterns > 0) {
                Log.e(TAG, String.format("⚠️ Suspicious patterns: %d, isStatic: %s",
                        analysis.suspiciousPatterns, analysis.isStaticImage));
            }

            return analysis;
        }
    }

    private static class LivenessAnalyzer {
        private final List<float[]> recentOutputs = new ArrayList<>();

        public LivenessAnalysis analyzeLiveness(float[] rawOutputs, FaceMetrics metrics) {
            LivenessAnalysis analysis = new LivenessAnalysis();

            // Store recent outputs for temporal analysis
            recentOutputs.add(rawOutputs.clone());
            if (recentOutputs.size() > 10) {
                recentOutputs.remove(0);
            }

            // Calculate variability across recent frames
            analysis.variabilityScore = calculateVariability();
            analysis.movementScore = calculateMovement(metrics);

            // Detect liveness patterns - BE MORE GENEROUS TO REAL FACES
            StringBuilder indicators = new StringBuilder();
            float livenessScore = 0f;

            // More generous variability scoring
            if (analysis.variabilityScore > 0.5f) {
                livenessScore += 0.35f; // Increased bonus
                indicators.append("HighVariability ");
            } else if (analysis.variabilityScore > 0.3f) {
                livenessScore += 0.2f; // Increased bonus
                indicators.append("ModerateVariability ");
            } else if (analysis.variabilityScore > 0.1f) {
                livenessScore += 0.1f; // Bonus for any variability
                indicators.append("SomeVariability ");
            }

            // Natural value distributions - more generous
            int[] counts = countValueDistribution(rawOutputs);
            if (counts[3] > 0 && counts[0] >= 0) { // Even without high values
                livenessScore += 0.25f; // Increased bonus
                indicators.append("NaturalDistribution ");
            }

            // Movement detection - more generous
            if (analysis.movementScore > 0.4f) {
                livenessScore += 0.3f; // Increased bonus
                indicators.append("Movement ");
            } else if (analysis.movementScore > 0.2f) {
                livenessScore += 0.15f; // Generous for any movement
                indicators.append("SomeMovement ");
            }

            // Moderate statistics - more generous range
            float[] stats = calculateEnhancedStatistics(rawOutputs);
            if (stats[3] > 0.4f && stats[3] < 0.8f) { // Wider range
                livenessScore += 0.2f; // Increased bonus
                indicators.append("NaturalVariance ");
            } else if (stats[3] > 0.2f) {
                livenessScore += 0.1f; // Bonus for reasonable variance
                indicators.append("ModerateVariance ");
            }

            // Natural imperfections - more generous
            if (stats[5] < 0.98f || counts[4] > 0) { // More lenient threshold
                livenessScore += 0.1f; // Increased bonus
                indicators.append("NaturalImperfections ");
            }

            // Reduced penalties for borderline cases
            if (stats[5] > 0.98f && counts[4] >= 3 && stats[1] > 0.2f && counts[0] >= 1) {
                livenessScore -= 0.15f; // Penalty for clear photo patterns
                indicators.append("PhotoLikePenalty ");
            }

            analysis.livenessScore = Math.max(0f, livenessScore);
            analysis.showsLiveness = analysis.livenessScore > LIVENESS_CONFIDENCE_THRESHOLD;
            analysis.livenessIndicators = indicators.toString().trim();

            return analysis;
        }

        /**
         * Clears the recent outputs cache
         */
        public void clearCache() {
            recentOutputs.clear();
            Log.e(TAG, "🧹 LivenessAnalyzer cache cleared");
        }

        /**
         * Gets the current cache size
         */
        public int getCacheSize() {
            return recentOutputs.size();
        }

        private float calculateVariability() {
            if (recentOutputs.size() < 3) return 0f;

            float totalVariance = 0f;
            int validComparisons = 0;

            for (int i = 1; i < recentOutputs.size(); i++) {
                float[] prev = recentOutputs.get(i - 1);
                float[] curr = recentOutputs.get(i);

                float frameVariance = 0f;
                for (int j = 0; j < Math.min(prev.length, curr.length); j++) {
                    frameVariance += Math.abs(prev[j] - curr[j]);
                }
                frameVariance /= prev.length;

                totalVariance += frameVariance;
                validComparisons++;
            }

            return validComparisons > 0 ? totalVariance / validComparisons : 0f;
        }

        private float calculateMovement(FaceMetrics currentMetrics) {
            // This would be enhanced with actual face position tracking
            // For now, use face size variations as proxy for movement
            return Math.min(1f, currentMetrics.ratio * 2f); // Simplified
        }
    }

    private static class TemporalAnalyzer {
        public DetectionResult applyTemporalAnalysis(DetectionResult currentResult, List<DetectionResult> history) {
            if (history.size() < MIN_FRAMES_FOR_TEMPORAL) {
                return currentResult; // Not enough history
            }

            // Analyze recent decisions
            List<DetectionResult> recent = history.subList(Math.max(0, history.size() - 10), history.size());

            int realVotes = 0, fakeVotes = 0;
            int staticDetections = 0;
            float avgConfidence = 0f;

            for (DetectionResult result : recent) {
                if (result.isReal) realVotes++;
                else fakeVotes++;

                if (result.staticAnalysis != null && result.staticAnalysis.isStaticImage) {
                    staticDetections++;
                }

                avgConfidence += result.confidence;
            }

            avgConfidence /= recent.size();

            // Strong static override
            if (staticDetections >= 3) {
                return new DetectionResult(false, Math.min(95f, 80f + staticDetections * 2f),
                        "TemporalStaticOverride-" + currentResult.method);
            }

            // Temporal consensus
            if (realVotes > fakeVotes * 2) {
                float confidence = Math.min(95f, avgConfidence + 10f);
                return new DetectionResult(true, confidence, "TemporalReal-" + currentResult.method);
            } else if (fakeVotes > realVotes * 2) {
                float confidence = Math.min(95f, avgConfidence + 10f);
                return new DetectionResult(false, confidence, "TemporalFake-" + currentResult.method);
            }

            // No strong consensus, return current with temporal context
            String method = "TemporalUncertain-" + currentResult.method;
            return new DetectionResult(currentResult.isReal, currentResult.confidence, method);
        }
    }

    // Data classes

    public static class DetectionResult {
        public boolean isReal;
        public float confidence;
        public String method;
        public float[] rawOutputs;
        public FaceMetrics faceMetrics;
        public long timestamp;
        public StaticAnalysis staticAnalysis;
        public LivenessAnalysis livenessAnalysis;

        public DetectionResult(boolean isReal, float confidence, String method) {
            this.isReal = isReal;
            this.confidence = confidence;
            this.method = method;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class FaceMetrics {
        public int faceArea;
        public int imageArea;
        public float ratio;
        public int width, height;
        public String distanceCategory;
        public float distanceMultiplier;
    }

    public static class StaticAnalysis {
        public boolean isStaticImage;
        public float staticConfidence;
        public String pattern;
        public float[] statisticalFeatures;
        public int suspiciousPatterns;
    }

    public static class LivenessAnalysis {
        public boolean showsLiveness;
        public float livenessScore;
        public float variabilityScore;
        public float movementScore;
        public String livenessIndicators;
    }

    // Utility methods

    private static float[] calculateEnhancedStatistics(float[] values) {
        if (values.length == 0) return new float[8];

        float sum = 0f, min = values[0], max = values[0];
        for (float value : values) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        float avg = sum / values.length;
        float variance = 0f;
        float skewness = 0f;

        for (float value : values) {
            float diff = value - avg;
            variance += diff * diff;
            skewness += diff * diff * diff;
        }

        variance /= values.length;
        float std = (float) Math.sqrt(variance);
        skewness = variance > 0 ? skewness / (values.length * variance * std) : 0f;

        return new float[]{sum, avg, variance, std, min, max, max - min, skewness};
    }

    private static int[] countValueDistribution(float[] values) {
        int veryHigh = 0, high = 0, moderate = 0, low = 0, negative = 0;

        for (float value : values) {
            if (value < 0) negative++;
            else if (value < 0.3f) low++;
            else if (value < 0.7f) moderate++;
            else if (value < 0.95f) high++;
            else veryHigh++;
        }

        return new int[]{veryHigh, high, moderate, low, negative};
    }

    // [Rest of methods from original implementation - preprocessing, inference, etc.]

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
            Log.e(TAG, "❌ Advanced inference failed", e);
            throw new RuntimeException("Advanced inference failed", e);
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

    // Logging methods

    private void logFaceMetrics(FaceMetrics metrics) {
        if (metrics == null) {
            Log.e(TAG, "❌ Face metrics is null!");
            return;
        }
        Log.e(TAG, String.format("📏 Face metrics: area=%d, imageArea=%d, ratio=%.3f, size=%dx%d, distance=%s (×%.1f)",
                metrics.faceArea, metrics.imageArea, metrics.ratio, metrics.width, metrics.height,
                metrics.distanceCategory != null ? metrics.distanceCategory : "UNKNOWN", metrics.distanceMultiplier));
    }

    private void logStaticAnalysis(StaticAnalysis analysis) {
        if (analysis == null) {
            Log.e(TAG, "❌ Static analysis is null!");
            return;
        }
        Log.e(TAG, String.format("🖼️ Static Analysis: isStatic=%s, confidence=%.1f%%, pattern=%s, suspicious=%d",
                analysis.isStaticImage, analysis.staticConfidence * 100,
                analysis.pattern != null ? analysis.pattern : "None", analysis.suspiciousPatterns));
    }

    private void logLivenessAnalysis(LivenessAnalysis analysis) {
        if (analysis == null) {
            Log.e(TAG, "❌ Liveness analysis is null!");
            return;
        }
        Log.e(TAG, String.format("🎭 Liveness Analysis: showsLiveness=%s, score=%.1f%%, variability=%.3f, movement=%.3f, indicators='%s'",
                analysis.showsLiveness, analysis.livenessScore * 100, analysis.variabilityScore,
                analysis.movementScore, analysis.livenessIndicators != null ? analysis.livenessIndicators : "None"));
    }

    private void logFinalResult(DetectionResult result) {
        if (result == null) {
            Log.e(TAG, "❌ Final result is null!");
            return;
        }
        Log.e(TAG, "═══════════════════════════════════════════════════");
        Log.e(TAG, String.format("🎯 FINAL RESULT - IsReal: %s, Confidence: %.1f%% (Method: %s)",
                result.isReal, result.confidence, result.method != null ? result.method : "Unknown"));
        Log.e(TAG, "═══════════════════════════════════════════════════");
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
        forceClearCache(); // Clear cache on close
        Log.e(TAG, "🔒 Advanced AntiSpoofingDetector closed");
    }
}