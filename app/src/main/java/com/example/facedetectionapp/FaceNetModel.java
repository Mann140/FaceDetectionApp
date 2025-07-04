package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * FaceNet model for generating face embeddings
 * CRASH-FIXED VERSION - No GPU dependencies to avoid runtime errors
 */
public class FaceNetModel {
    private static final String TAG = "FaceNetModel";

    // Model configuration
    private static final String MODEL_FILE = "facenet.tflite";
    private static final int INPUT_SIZE = 160;
    private static final int EMBEDDING_DIM = 128;

    // Thresholds for face matching
    private static final float COSINE_THRESHOLD = 0.4f;
    private static final float L2_THRESHOLD = 10f;

    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private boolean isModelLoaded = false;

    public FaceNetModel(Context context, boolean useGpu, boolean useXNNPack) {
        Log.d(TAG, "🚀 Initializing FaceNet model (CRASH-FIXED VERSION)...");

        // Force CPU-only to avoid crashes
        if (useGpu) {
            Log.w(TAG, "⚠️ GPU requested but disabled to prevent crashes. Using optimized CPU instead.");
        }

        try {
            initializeModel(context, useXNNPack);
            initializeImageProcessor();
            isModelLoaded = true;
            Log.d(TAG, "✅ FaceNet model loaded successfully with CPU optimization");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load FaceNet model: " + e.getMessage(), e);
            isModelLoaded = false;
        }
    }

    private void initializeModel(Context context, boolean useXNNPack) throws IOException {
        Log.d(TAG, "🔧 Loading FaceNet model from assets...");

        ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);

        Interpreter.Options options = new Interpreter.Options();

        // CPU-only configuration with optimization
        int numThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
        options.setNumThreads(numThreads);

        // Enable XNNPACK for CPU optimization (safe)
        if (useXNNPack) {
            try {
                options.setUseXNNPACK(true);
                Log.d(TAG, "✅ XNNPACK enabled for CPU optimization");
            } catch (Exception e) {
                Log.w(TAG, "⚠️ XNNPACK not available: " + e.getMessage());
                options.setUseXNNPACK(false);
            }
        } else {
            options.setUseXNNPACK(false);
        }

        // Disable NNAPI to avoid compatibility issues
        try {
            options.setUseNNAPI(false);
            Log.d(TAG, "🔧 NNAPI disabled for compatibility");
        } catch (Exception e) {
            // Ignore if not available
            Log.d(TAG, "NNAPI setting not available");
        }

        Log.d(TAG, String.format("🔧 CPU Configuration: %d threads, XNNPACK: %s",
                numThreads, useXNNPack));

        // Create interpreter
        interpreter = new Interpreter(modelBuffer, options);

        // Log model information safely
        try {
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "📊 Model input shape: " + java.util.Arrays.toString(inputShape));
            Log.d(TAG, "📊 Model output shape: " + java.util.Arrays.toString(outputShape));

            // Verify expected shapes
            if (inputShape.length >= 4 && inputShape[1] == INPUT_SIZE && inputShape[2] == INPUT_SIZE) {
                Log.d(TAG, "✅ Input shape verification passed");
            } else {
                Log.w(TAG, "⚠️ Unexpected input shape, but proceeding...");
            }

            if (outputShape.length >= 2 && outputShape[1] == EMBEDDING_DIM) {
                Log.d(TAG, "✅ Output shape verification passed");
            } else {
                Log.w(TAG, "⚠️ Unexpected output shape, but proceeding...");
            }

        } catch (Exception e) {
            Log.w(TAG, "Could not verify tensor shapes: " + e.getMessage());
        }

        Log.d(TAG, "🎉 FaceNet model initialization complete!");
    }

    private void initializeImageProcessor() {
        Log.d(TAG, "🖼️ Initializing image processor...");

        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new StandardizeOp())
                .build();

        Log.d(TAG, "✅ Image processor ready");
    }

    /**
     * Generate face embedding from a cropped face bitmap
     * @param faceBitmap Cropped face image
     * @return 128-dimensional embedding vector, or null if failed
     */
    public float[] getFaceEmbedding(Bitmap faceBitmap) {
        if (!isModelLoaded || interpreter == null) {
            Log.e(TAG, "❌ Model not loaded or interpreter is null");
            return null;
        }

        if (faceBitmap == null) {
            Log.e(TAG, "❌ Input face bitmap is null");
            return null;
        }

        if (faceBitmap.isRecycled()) {
            Log.e(TAG, "❌ Input face bitmap is recycled");
            return null;
        }

        try {
            long startTime = System.currentTimeMillis();

            Log.d(TAG, String.format("🔄 Processing face bitmap: %dx%d",
                    faceBitmap.getWidth(), faceBitmap.getHeight()));

            // Preprocess the image
            ByteBuffer inputBuffer = preprocessImage(faceBitmap);
            if (inputBuffer == null) {
                Log.e(TAG, "❌ Failed to preprocess image");
                return null;
            }

            // Run inference
            float[][] output = new float[1][EMBEDDING_DIM];
            interpreter.run(inputBuffer, output);

            // Verify output
            if (output[0] == null || output[0].length != EMBEDDING_DIM) {
                Log.e(TAG, "❌ Invalid model output");
                return null;
            }

            long inferenceTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, String.format("✅ FaceNet inference completed in %dms", inferenceTime));

            // Log embedding statistics for debugging
            float[] embedding = output[0];
            float min = Float.MAX_VALUE, max = Float.MIN_VALUE, sum = 0f;
            for (float value : embedding) {
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
            float mean = sum / embedding.length;

            Log.d(TAG, String.format("📊 Embedding stats: min=%.3f, max=%.3f, mean=%.3f", min, max, mean));

            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error generating face embedding: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Preprocess bitmap for FaceNet model
     */
    private ByteBuffer preprocessImage(Bitmap bitmap) {
        try {
            Log.d(TAG, "🔄 Preprocessing image for FaceNet...");

            TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
            TensorImage processedImage = imageProcessor.process(tensorImage);

            ByteBuffer buffer = processedImage.getBuffer();

            Log.d(TAG, String.format("✅ Image preprocessed: buffer size = %d bytes", buffer.capacity()));

            return buffer;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error preprocessing image: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity score (higher = more similar)
     */
    public static float calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null ||
                embedding1.length != embedding2.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        float magnitude = (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
        return magnitude > 0 ? dotProduct / magnitude : 0f;
    }

    /**
     * Calculate L2 distance between two embeddings
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return L2 distance (lower = more similar)
     */
    public static float calculateL2Distance(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null ||
                embedding1.length != embedding2.length) {
            return Float.MAX_VALUE;
        }

        float sum = 0f;
        for (int i = 0; i < embedding1.length; i++) {
            float diff = embedding1[i] - embedding2[i];
            sum += diff * diff;
        }

        return (float) Math.sqrt(sum);
    }

    /**
     * Check if two embeddings match based on similarity thresholds
     * @param embedding1 First embedding
     * @param embedding2 Second embedding
     * @param useCosineSimilarity Whether to use cosine similarity (true) or L2 distance (false)
     * @return Match result with similarity score
     */
    public static MatchResult compareEmbeddings(float[] embedding1, float[] embedding2,
                                                boolean useCosineSimilarity) {
        if (embedding1 == null || embedding2 == null) {
            return new MatchResult(false, 0f, "Invalid embeddings");
        }

        if (useCosineSimilarity) {
            float similarity = calculateCosineSimilarity(embedding1, embedding2);
            boolean isMatch = similarity > COSINE_THRESHOLD;
            return new MatchResult(isMatch, similarity,
                    String.format("Cosine: %.3f (threshold: %.3f)", similarity, COSINE_THRESHOLD));
        } else {
            float distance = calculateL2Distance(embedding1, embedding2);
            boolean isMatch = distance < L2_THRESHOLD;
            return new MatchResult(isMatch, distance,
                    String.format("L2: %.3f (threshold: %.3f)", distance, L2_THRESHOLD));
        }
    }

    /**
     * Get embedding dimension
     */
    public int getEmbeddingDim() {
        return EMBEDDING_DIM;
    }

    /**
     * Check if model is ready for inference
     */
    public boolean isReady() {
        boolean ready = isModelLoaded && interpreter != null;
        Log.d(TAG, "🔍 Model ready check: " + ready);
        return ready;
    }

    /**
     * Clean up resources
     */
    public void close() {
        Log.d(TAG, "🔒 Closing FaceNet model...");

        if (interpreter != null) {
            try {
                interpreter.close();
                Log.d(TAG, "✅ TensorFlow Lite interpreter closed");
            } catch (Exception e) {
                Log.w(TAG, "Warning during interpreter close: " + e.getMessage());
            }
            interpreter = null;
        }

        isModelLoaded = false;
        Log.d(TAG, "🔒 FaceNet model closed successfully");
    }

    /**
     * Custom TensorFlow Lite operation for standardization
     * Performs: (x - mean) / std_dev
     */
    private static class StandardizeOp implements org.tensorflow.lite.support.common.TensorOperator {
        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] pixels = input.getFloatArray();

            // Calculate mean
            float sum = 0f;
            for (float pixel : pixels) {
                sum += pixel;
            }
            float mean = sum / pixels.length;

            // Calculate standard deviation
            float varianceSum = 0f;
            for (float pixel : pixels) {
                float diff = pixel - mean;
                varianceSum += diff * diff;
            }
            float std = (float) Math.sqrt(varianceSum / pixels.length);

            // Avoid division by zero
            std = Math.max(std, 1f / (float) Math.sqrt(pixels.length));

            // Standardize pixels
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] - mean) / std;
            }

            // Create output buffer
            TensorBuffer output = TensorBufferFloat.createFixedSize(input.getShape(), DataType.FLOAT32);
            output.loadArray(pixels);
            return output;
        }
    }

    /**
     * Result of embedding comparison
     */
    public static class MatchResult {
        public final boolean isMatch;
        public final float score;
        public final String details;

        public MatchResult(boolean isMatch, float score, String details) {
            this.isMatch = isMatch;
            this.score = score;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("Match: %s, Score: %.3f, Details: %s",
                    isMatch, score, details);
        }
    }
}