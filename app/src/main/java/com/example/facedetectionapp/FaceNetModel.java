package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * FaceNet model wrapper for generating face embeddings using TensorFlow Lite
 * This class loads and runs the facenet.tflite model to generate 128-dimensional embeddings
 */
public class FaceNetModel {
    private static final String TAG = "FaceNetModel";
    private static final String MODEL_FILE = "facenet.tflite";

    // FaceNet model configurations
    private static final int INPUT_SIZE = 160; // FaceNet input size (160x160)
    private static final int EMBEDDING_DIM = 128; // FaceNet output embedding dimension
    private static final int CHANNELS = 3; // RGB channels

    // Normalization values for FaceNet preprocessing
    private static final float[] MEAN = {127.5f, 127.5f, 127.5f};
    private static final float[] STD = {128.0f, 128.0f, 128.0f};

    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    // Pre-allocated buffers for performance
    private ByteBuffer inputBuffer;
    private float[][] outputBuffer;

    public FaceNetModel(Context context) {
        try {
            loadModel(context);
            prepareBuffers();
            isModelLoaded = true;
            Log.d(TAG, "✅ FaceNet model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load FaceNet model", e);
            isModelLoaded = false;
        }
    }

    private void loadModel(Context context) throws IOException {
        try {
            // Load the TensorFlow Lite model
            ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);

            // Configure interpreter options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Use 4 threads for better performance
            options.setUseXNNPACK(true); // Enable XNNPACK for acceleration

            // Create interpreter
            interpreter = new Interpreter(modelBuffer, options);

            // Log model info
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            Log.d(TAG, "FaceNet Model Info:");
            Log.d(TAG, "  Input shape: " + java.util.Arrays.toString(inputShape));
            Log.d(TAG, "  Output shape: " + java.util.Arrays.toString(outputShape));
            Log.d(TAG, "  Expected input: [1, " + INPUT_SIZE + ", " + INPUT_SIZE + ", " + CHANNELS + "]");
            Log.d(TAG, "  Expected output: [1, " + EMBEDDING_DIM + "]");

        } catch (IOException e) {
            Log.e(TAG, "Error loading FaceNet model: " + e.getMessage());
            throw e;
        }
    }

    private void prepareBuffers() {
        // Pre-allocate input buffer
        int inputSize = INPUT_SIZE * INPUT_SIZE * CHANNELS * 4; // 4 bytes per float
        inputBuffer = ByteBuffer.allocateDirect(inputSize);
        inputBuffer.order(ByteOrder.nativeOrder());

        // Pre-allocate output buffer
        outputBuffer = new float[1][EMBEDDING_DIM];
    }

    /**
     * Generate face embedding from a face bitmap
     * @param faceBitmap Cropped face bitmap (will be resized to 160x160)
     * @return 128-dimensional embedding vector, or null if processing fails
     */
    public float[] getFaceEmbedding(Bitmap faceBitmap) {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "FaceNet model not loaded");
            return null;
        }

        if (faceBitmap == null || faceBitmap.isRecycled()) {
            Log.w(TAG, "Invalid input bitmap");
            return null;
        }

        try {
            // Preprocess the bitmap
            preprocessBitmap(faceBitmap);

            // Run inference
            interpreter.run(inputBuffer, outputBuffer);

            // Extract and normalize the embedding
            float[] embedding = outputBuffer[0].clone();
            return normalizeEmbedding(embedding);

        } catch (Exception e) {
            Log.e(TAG, "Error generating face embedding", e);
            return null;
        }
    }

    /**
     * Preprocess bitmap for FaceNet input
     * Resizes to 160x160 and normalizes pixel values
     */
    private void preprocessBitmap(Bitmap bitmap) {
        // Resize bitmap to 160x160 if needed
        Bitmap resizedBitmap;
        if (bitmap.getWidth() != INPUT_SIZE || bitmap.getHeight() != INPUT_SIZE) {
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        } else {
            resizedBitmap = bitmap;
        }

        // Clear and rewind the input buffer
        inputBuffer.clear();
        inputBuffer.rewind();

        // Convert bitmap to normalized float values
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            // Extract RGB values
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Normalize using FaceNet preprocessing: (pixel - mean) / std
            float rNorm = (r - MEAN[0]) / STD[0];
            float gNorm = (g - MEAN[1]) / STD[1];
            float bNorm = (b - MEAN[2]) / STD[2];

            // Add to input buffer
            inputBuffer.putFloat(rNorm);
            inputBuffer.putFloat(gNorm);
            inputBuffer.putFloat(bNorm);
        }

        // Rewind buffer for inference
        inputBuffer.rewind();

        // Clean up resized bitmap if we created a new one
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle();
        }
    }

    /**
     * Normalize embedding to unit vector (L2 normalization)
     * This is important for cosine similarity calculations
     */
    private float[] normalizeEmbedding(float[] embedding) {
        float magnitude = 0.0f;

        // Calculate magnitude
        for (float value : embedding) {
            magnitude += value * value;
        }
        magnitude = (float) Math.sqrt(magnitude);

        // Normalize if magnitude is not zero
        if (magnitude > 0.0f) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = embedding[i] / magnitude;
            }
        }

        return embedding;
    }

    /**
     * Check if the model is loaded and ready
     */
    public boolean isModelReady() {
        return isModelLoaded && interpreter != null;
    }

    /**
     * Get embedding dimension (should be 128 for FaceNet)
     */
    public int getEmbeddingDimension() {
        return EMBEDDING_DIM;
    }

    /**
     * Get expected input size (should be 160 for FaceNet)
     */
    public int getInputSize() {
        return INPUT_SIZE;
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isModelLoaded = false;
        Log.d(TAG, "FaceNet model closed");
    }

    /**
     * Test the model with a dummy input to verify it's working
     */
    public boolean testModel() {
        if (!isModelReady()) {
            return false;
        }

        try {
            // Create a dummy 160x160 RGB bitmap
            Bitmap testBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
            testBitmap.eraseColor(0xFF808080); // Fill with gray

            // Generate embedding
            float[] embedding = getFaceEmbedding(testBitmap);

            // Clean up
            testBitmap.recycle();

            // Check if embedding is valid
            boolean isValid = embedding != null && embedding.length == EMBEDDING_DIM;

            if (isValid) {
                Log.d(TAG, "✅ FaceNet model test passed");
                Log.d(TAG, "Generated embedding with " + embedding.length + " dimensions");

                // Log first few values for verification
                StringBuilder sb = new StringBuilder("First 5 values: ");
                for (int i = 0; i < Math.min(5, embedding.length); i++) {
                    sb.append(String.format("%.4f ", embedding[i]));
                }
                Log.d(TAG, sb.toString());
            } else {
                Log.e(TAG, "❌ FaceNet model test failed");
            }

            return isValid;

        } catch (Exception e) {
            Log.e(TAG, "❌ FaceNet model test failed with exception", e);
            return false;
        }
    }
}