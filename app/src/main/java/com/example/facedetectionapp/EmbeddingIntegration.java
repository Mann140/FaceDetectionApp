package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import java.util.*;

/**
 * Integration class that uses real FaceNet model for embedding generation
 * This extends the current face detection functionality with live embedding comparison
 */
public class EmbeddingIntegration {

    private static final String TAG = "EmbeddingIntegration";

    // Known face database - in a real app, this would be loaded from storage
    private final Map<String, float[]> knownFaces = new HashMap<>();

    // FaceNet model for generating embeddings
    private FaceNetModel faceNetModel;
    private boolean isFaceNetReady = false;

    public EmbeddingIntegration(Context context) {
        // Load sample embeddings
        knownFaces.putAll(EmbeddingComparator.getSampleEmbeddings());

        // Initialize FaceNet model
        initializeFaceNetModel(context);

        // Run sample test to verify functionality
        Log.d(TAG, EmbeddingComparator.runSampleTest());
    }

    private void initializeFaceNetModel(Context context) {
        try {
            Log.d(TAG, "🔧 Initializing FaceNet model...");
            faceNetModel = new FaceNetModel(context);

            if (faceNetModel.isModelReady()) {
                // Test the model
                boolean testPassed = faceNetModel.testModel();
                if (testPassed) {
                    isFaceNetReady = true;
                    Log.d(TAG, "✅ FaceNet model ready for live embedding generation");
                } else {
                    Log.w(TAG, "⚠️ FaceNet model loaded but test failed");
                }
            } else {
                Log.w(TAG, "⚠️ FaceNet model not ready - using sample embeddings only");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize FaceNet model", e);
            isFaceNetReady = false;
        }
    }

    /**
     * Enhanced face analysis that combines anti-spoofing with embedding comparison
     */
    public EnhancedFaceData analyzeAndCompareFace(ImageProxy imageProxy, Rect faceRect,
                                                  AntiSpoofingDetector antiSpoofingDetector) {

        // First, perform anti-spoofing detection (existing functionality)
        MainActivity.FaceData basicFaceData = antiSpoofingDetector.analyzeFace(imageProxy, faceRect);

        // If the face is determined to be real, proceed with embedding comparison
        EmbeddingAnalysisResult embeddingResults = null;
        if (basicFaceData.isReal && isFaceNetReady) {
            embeddingResults = performLiveEmbeddingComparison(imageProxy, faceRect);
        }

        return new EnhancedFaceData(
                basicFaceData.boundingBox,
                basicFaceData.isReal,
                basicFaceData.confidence,
                basicFaceData.detectionMethod,
                basicFaceData.has3DStructure,
                embeddingResults
        );
    }

    /**
     * Perform live embedding comparison using FaceNet model
     */
    private EmbeddingAnalysisResult performLiveEmbeddingComparison(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Extract face bitmap from the image
            Bitmap faceBitmap = extractFaceFromImageProxy(imageProxy, faceRect);

            if (faceBitmap != null && faceNetModel != null) {
                // Generate embedding using FaceNet model
                float[] embedding = faceNetModel.getFaceEmbedding(faceBitmap);

                if (embedding != null) {
                    Log.d(TAG, String.format("✅ Generated live embedding: %d dimensions", embedding.length));

                    // Find best match from known faces
                    EmbeddingComparator.ComparisonResult bestMatch =
                            EmbeddingComparator.findBestMatch(embedding, knownFaces);

                    // Get all comparisons for analysis
                    List<EmbeddingComparator.ComparisonResult> allComparisons =
                            EmbeddingComparator.compareWithSamples(embedding);

                    // Calculate embedding statistics
                    EmbeddingComparator.EmbeddingStats stats =
                            EmbeddingComparator.calculateEmbeddingStats(embedding);

                    // Log recognition results
                    if (bestMatch != null && bestMatch.isMatch) {
                        Log.d(TAG, String.format("🎯 RECOGNIZED: %s (%.3f similarity)",
                                bestMatch.name, bestMatch.cosineSimilarity));
                    } else {
                        Log.d(TAG, "❓ Unknown person - no match found");
                    }

                    return new EmbeddingAnalysisResult(
                            embedding,
                            bestMatch,
                            allComparisons,
                            stats
                    );
                } else {
                    Log.w(TAG, "⚠️ Failed to generate embedding from face bitmap");
                }

                // Clean up bitmap
                faceBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in live embedding comparison", e);
        }

        return null;
    }

    /**
     * Extract face bitmap from ImageProxy using the face rectangle
     * This uses the same extraction logic as AntiSpoofingDetector
     */
    private Bitmap extractFaceFromImageProxy(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Convert ImageProxy to Bitmap (this would be similar to your existing code)
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) {
                Log.w(TAG, "Failed to convert ImageProxy to Bitmap");
                return null;
            }

            // Add padding around the face
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Invalid face rectangle after padding");
                return null;
            }

            // Extract face region
            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Make it square for better FaceNet input
            int size = Math.min(width, height);
            if (width != height) {
                int xOffset = (width - size) / 2;
                int yOffset = (height - size) / 2;
                Bitmap squareFace = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);
                faceBitmap.recycle(); // Clean up original
                faceBitmap = squareFace;
            }

            Log.d(TAG, String.format("✅ Extracted face bitmap: %dx%d", faceBitmap.getWidth(), faceBitmap.getHeight()));
            return faceBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error extracting face from ImageProxy", e);
            return null;
        }
    }

    /**
     * Convert ImageProxy to Bitmap using the helper class
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        return ImageConversionHelper.imageProxyToBitmap(imageProxy);
    }

    /**
     * Add a new face to the known faces database
     * This can be called to register new people
     */
    public void addKnownFace(String name, float[] embedding) {
        knownFaces.put(name, embedding);
        Log.d(TAG, "Added new known face: " + name);
    }

    /**
     * Add a new face by capturing from current camera frame
     */
    public boolean addKnownFaceFromCamera(String name, ImageProxy imageProxy, Rect faceRect) {
        if (!isFaceNetReady) {
            Log.w(TAG, "FaceNet model not ready for capturing new face");
            return false;
        }

        try {
            Bitmap faceBitmap = extractFaceFromImageProxy(imageProxy, faceRect);
            if (faceBitmap != null) {
                float[] embedding = faceNetModel.getFaceEmbedding(faceBitmap);
                if (embedding != null) {
                    addKnownFace(name, embedding);
                    faceBitmap.recycle();
                    Log.d(TAG, "✅ Successfully added face from camera: " + name);
                    return true;
                } else {
                    Log.w(TAG, "Failed to generate embedding for new face");
                }
                faceBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding face from camera", e);
        }

        return false;
    }

    /**
     * Remove a face from the known faces database
     */
    public void removeKnownFace(String name) {
        knownFaces.remove(name);
        Log.d(TAG, "Removed known face: " + name);
    }

    /**
     * Get all known faces
     */
    public Map<String, float[]> getKnownFaces() {
        return new HashMap<>(knownFaces);
    }

    /**
     * Compare two faces directly using their embeddings
     */
    public EmbeddingComparator.ComparisonResult compareFaces(float[] embedding1, float[] embedding2) {
        float cosine = EmbeddingComparator.cosineSimilarity(embedding1, embedding2);
        float l2 = EmbeddingComparator.l2Distance(embedding1, embedding2);
        boolean isMatch = EmbeddingComparator.areSamePerson(embedding1, embedding2);

        return new EmbeddingComparator.ComparisonResult(
                "Direct Comparison",
                cosine,
                l2,
                isMatch
        );
    }

    /**
     * Batch compare an embedding against all known faces
     */
    public List<EmbeddingComparator.ComparisonResult> batchCompare(float[] queryEmbedding) {
        return EmbeddingComparator.compareWithSamples(queryEmbedding);
    }

    /**
     * Check if FaceNet model is ready for live embedding generation
     */
    public boolean isFaceNetReady() {
        return isFaceNetReady && faceNetModel != null && faceNetModel.isModelReady();
    }

    /**
     * Get FaceNet model info
     */
    public String getFaceNetInfo() {
        if (isFaceNetReady && faceNetModel != null) {
            return String.format("FaceNet Ready: %dx%d → %d dims",
                    faceNetModel.getInputSize(),
                    faceNetModel.getInputSize(),
                    faceNetModel.getEmbeddingDimension());
        } else {
            return "FaceNet Not Available";
        }
    }

    /**
     * Test the embedding comparison functionality
     */
    public void testEmbeddingComparison() {
        Log.d(TAG, "Testing embedding comparison functionality...");

        // Test with sample embeddings
        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
        float[] mrunalEmbedding = samples.get("Mrunal Patil");
        float[] shivamEmbedding = samples.get("Shivam Bind");

        if (mrunalEmbedding != null && shivamEmbedding != null) {
            // Compare same person (should be high similarity)
            EmbeddingComparator.ComparisonResult samePersonResult = compareFaces(mrunalEmbedding, mrunalEmbedding);
            Log.d(TAG, "Same person comparison: " + samePersonResult.toString());

            // Compare different persons (should be low similarity)
            EmbeddingComparator.ComparisonResult differentPersonResult = compareFaces(mrunalEmbedding, shivamEmbedding);
            Log.d(TAG, "Different person comparison: " + differentPersonResult.toString());

            // Find best match
            EmbeddingComparator.ComparisonResult bestMatch =
                    EmbeddingComparator.findBestMatch(mrunalEmbedding, knownFaces);
            Log.d(TAG, "Best match for Mrunal: " + (bestMatch != null ? bestMatch.toString() : "null"));

            // Get all comparisons
            List<EmbeddingComparator.ComparisonResult> allComparisons = batchCompare(mrunalEmbedding);
            Log.d(TAG, "All comparisons for Mrunal:");
            for (EmbeddingComparator.ComparisonResult result : allComparisons) {
                Log.d(TAG, "  " + result.toString());
            }
        }

        // Test FaceNet model status
        Log.d(TAG, "FaceNet Status: " + getFaceNetInfo());
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (faceNetModel != null) {
            faceNetModel.close();
            faceNetModel = null;
        }
        isFaceNetReady = false;
        Log.d(TAG, "EmbeddingIntegration closed");
    }

    // ... (EnhancedFaceData and EmbeddingAnalysisResult classes remain the same)

    /**
     * Enhanced face data that includes embedding comparison results
     */
    public static class EnhancedFaceData {
        public final Rect boundingBox;
        public final boolean isReal;
        public final float confidence;
        public final String detectionMethod;
        public final boolean has3DStructure;
        public final EmbeddingAnalysisResult embeddingResults;

        public EnhancedFaceData(Rect boundingBox, boolean isReal, float confidence,
                                String detectionMethod, boolean has3DStructure,
                                EmbeddingAnalysisResult embeddingResults) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
            this.has3DStructure = has3DStructure;
            this.embeddingResults = embeddingResults;
        }

        /**
         * Get the recognized person's name if available
         */
        public String getRecognizedName() {
            if (embeddingResults != null && embeddingResults.bestMatch != null) {
                return embeddingResults.bestMatch.name;
            }
            return null;
        }

        /**
         * Check if this face was successfully recognized
         */
        public boolean isRecognized() {
            return embeddingResults != null &&
                    embeddingResults.bestMatch != null &&
                    embeddingResults.bestMatch.isMatch;
        }

        /**
         * Get recognition confidence score
         */
        public float getRecognitionConfidence() {
            if (embeddingResults != null && embeddingResults.bestMatch != null) {
                return embeddingResults.bestMatch.cosineSimilarity;
            }
            return 0.0f;
        }
    }

    /**
     * Results of embedding analysis
     */
    public static class EmbeddingAnalysisResult {
        public final float[] queryEmbedding;
        public final EmbeddingComparator.ComparisonResult bestMatch;
        public final List<EmbeddingComparator.ComparisonResult> allComparisons;
        public final EmbeddingComparator.EmbeddingStats embeddingStats;

        public EmbeddingAnalysisResult(float[] queryEmbedding,
                                       EmbeddingComparator.ComparisonResult bestMatch,
                                       List<EmbeddingComparator.ComparisonResult> allComparisons,
                                       EmbeddingComparator.EmbeddingStats embeddingStats) {
            this.queryEmbedding = queryEmbedding;
            this.bestMatch = bestMatch;
            this.allComparisons = allComparisons;
            this.embeddingStats = embeddingStats;
        }

        @Override
        public String toString() {
            return String.format("EmbeddingAnalysisResult(bestMatch=%s, comparisons=%d, stats=%s)",
                    bestMatch != null ? bestMatch.toString() : "null",
                    allComparisons != null ? allComparisons.size() : 0,
                    embeddingStats != null ? embeddingStats.toString() : "null");
        }
    }
}