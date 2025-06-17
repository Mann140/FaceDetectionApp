package com.example.facedetectionapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceRecognitionProcessor {

    private static final String TAG = "FaceRecognitionProcessor";
    private static final String FACENET_MODEL = "facenet_mobile.tflite";
    private static final float RECOGNITION_THRESHOLD = 0.5f; // Lowered for better recognition
    private static final long ATTENDANCE_COOLDOWN = 30000; // 30 seconds

    // Model specifications (will be auto-detected)
    private int inputSize = 160; // Default, will be updated
    private int embeddingSize = 512; // Default, will be updated

    // Components
    private Interpreter faceNetInterpreter;
    private AttendanceDatabase database;
    private boolean isModelLoaded = false;

    public FaceRecognitionProcessor(Context context) {
        this.database = new AttendanceDatabase(context);

        try {
            loadFaceNetModel(context);
            detectModelSizes();
            isModelLoaded = true;
            Log.d(TAG, "✅ FaceRecognitionProcessor initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize FaceRecognitionProcessor", e);
            isModelLoaded = false;
        }
    }

    private void loadFaceNetModel(Context context) throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(FACENET_MODEL);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Use 4 threads for better performance

            faceNetInterpreter = new Interpreter(modelBuffer, options);
            Log.d(TAG, "✅ FaceNet model loaded successfully");

        } catch (IOException e) {
            Log.e(TAG, "❌ Error loading FaceNet model", e);
            throw e;
        }
    }

    private void detectModelSizes() {
        try {
            if (faceNetInterpreter != null) {
                // Get input tensor info
                Tensor inputTensor = faceNetInterpreter.getInputTensor(0);
                int[] inputShape = inputTensor.shape();
                if (inputShape.length >= 3) {
                    inputSize = inputShape[1]; // Assuming NHWC format
                    Log.d(TAG, "📐 Detected input size: " + inputSize + "x" + inputSize);
                }

                // Get output tensor info
                Tensor outputTensor = faceNetInterpreter.getOutputTensor(0);
                int[] outputShape = outputTensor.shape();
                if (outputShape.length >= 2) {
                    embeddingSize = outputShape[1];
                    Log.d(TAG, "📐 Detected embedding size: " + embeddingSize);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "⚠️ Could not detect model sizes, using defaults", e);
        }
    }

    // ===== IMAGE PROCESSING METHODS =====

    /**
     * Extract face bitmap from ImageProxy (used by RegisterFaceActivity)
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    public Bitmap extractFaceBitmapFromImageProxy(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Check if image is still valid
            android.media.Image image = imageProxy.getImage();
            if (image == null) {
                Log.e(TAG, "❌ ImageProxy.getImage() returned null");
                return null;
            }

            // Get dimensions safely
            int width = image.getWidth();
            int height = image.getHeight();

            Log.d(TAG, "🔍 Image dimensions: " + width + "x" + height);
            Log.d(TAG, "🔍 Face rect: " + faceRect.toString());

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.e(TAG, "❌ Insufficient image planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.e(TAG, "❌ Empty image buffers: Y=" + ySize + ", U=" + uSize + ", V=" + vSize);
                return null;
            }

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap fullBitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Mirror the image for front camera
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            Bitmap mirroredBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width, height, matrix, false);

            // Extract face region with padding
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(mirroredBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(mirroredBitmap.getHeight(), faceRect.bottom + padding);

            int faceWidth = right - left;
            int faceHeight = bottom - top;

            if (faceWidth <= 0 || faceHeight <= 0) {
                Log.e(TAG, "❌ Invalid face dimensions: " + faceWidth + "x" + faceHeight);
                return null;
            }

            Bitmap faceBitmap = Bitmap.createBitmap(mirroredBitmap, left, top, faceWidth, faceHeight);

            // Make it square
            int size = Math.min(faceWidth, faceHeight);
            if (faceWidth != faceHeight) {
                int xOffset = (faceWidth - size) / 2;
                int yOffset = (faceHeight - size) / 2;
                faceBitmap = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);
            }

            Log.d(TAG, "✅ Face extraction successful: " + size + "x" + size);
            return faceBitmap;

        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ Image already closed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face", e);
            return null;
        }
    }

    /**
     * YUV420 to RGB conversion
     */
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

    /**
     * Preprocess bitmap for FaceNet model
     */
    private ByteBuffer preprocessForFaceNet(Bitmap bitmap) {
        // Resize bitmap to model input size
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputSize * inputSize];
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        // Normalize pixel values to [-1, 1] range for FaceNet
        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            inputBuffer.putFloat((r - 127.5f) / 127.5f);
            inputBuffer.putFloat((g - 127.5f) / 127.5f);
            inputBuffer.putFloat((b - 127.5f) / 127.5f);
        }

        return inputBuffer;
    }

    /**
     * Generate face embedding from bitmap
     */
    private float[] generateFaceEmbeddingFromBitmap(Bitmap faceBitmap) {
        if (!isModelLoaded || faceNetInterpreter == null) {
            Log.e(TAG, "❌ Model not loaded");
            return null;
        }

        try {
            // Preprocess the face bitmap
            ByteBuffer inputBuffer = preprocessForFaceNet(faceBitmap);

            // Prepare output array
            float[][] output = new float[1][embeddingSize];

            // Run inference
            faceNetInterpreter.run(inputBuffer, output);

            // Normalize the embedding
            float[] embedding = normalizeEmbedding(output[0]);

            Log.d(TAG, "✅ Face embedding generated: " + embedding.length + " dimensions");
            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error generating face embedding", e);
            return null;
        }
    }

    /**
     * Normalize embedding vector
     */
    private float[] normalizeEmbedding(float[] embedding) {
        float norm = 0.0f;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            float[] normalizedEmbedding = new float[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                normalizedEmbedding[i] = embedding[i] / norm;
            }
            return normalizedEmbedding;
        }

        return embedding;
    }

    // ===== PUBLIC API METHODS =====

    /**
     * Generate face embedding from bitmap (public method for MainActivity)
     */
    public float[] generateEmbeddingFromBitmap(Bitmap faceBitmap) {
        if (!isModelLoaded) {
            Log.e(TAG, "❌ Model not loaded");
            return null;
        }

        if (faceBitmap == null) {
            Log.e(TAG, "❌ Face bitmap is null");
            return null;
        }

        try {
            return generateFaceEmbeddingFromBitmap(faceBitmap);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error generating embedding from bitmap", e);
            return null;
        }
    }

    /**
     * Register face from bitmap (used by RegisterFaceActivity)
     * Updated signature to match RegisterFaceActivity usage
     */
    public boolean registerFaceFromBitmap(Bitmap faceBitmap, Rect faceRect, long userId) {
        // For backward compatibility, ignore faceRect and use the bitmap directly
        return registerFaceFromBitmap(faceBitmap, userId);
    }

    /**
     * Register face from bitmap (original method)
     */
    public boolean registerFaceFromBitmap(Bitmap faceBitmap, long userId) {
        if (!isModelLoaded) {
            Log.e(TAG, "❌ Model not loaded, cannot register face");
            return false;
        }

        if (faceBitmap == null) {
            Log.e(TAG, "❌ Face bitmap is null");
            return false;
        }

        try {
            // Generate face embedding
            float[] faceEmbedding = generateFaceEmbeddingFromBitmap(faceBitmap);

            if (faceEmbedding == null) {
                Log.e(TAG, "❌ Failed to generate face embedding");
                return false;
            }

            // Convert embedding to string for storage
            String embeddingString = arrayToString(faceEmbedding);

            // Store in database
            long encodingId = database.addFaceEncoding(userId, embeddingString);

            if (encodingId != -1) {
                Log.d(TAG, "✅ Face registered successfully for user " + userId);
                return true;
            } else {
                Log.e(TAG, "❌ Failed to store face encoding in database");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error registering face", e);
            return false;
        }
    }

    /**
     * Recognize face from pre-generated embedding
     */
    public RecognitionResult recognizeFromEmbedding(float[] queryEmbedding) {
        if (!isModelLoaded || queryEmbedding == null) {
            return new RecognitionResult(null, 0.0f);
        }

        try {
            // Get all stored face encodings from database
            List<AttendanceDatabase.FaceEncoding> storedEncodings = database.getAllFaceEncodings();

            if (storedEncodings.isEmpty()) {
                Log.d(TAG, "📭 No stored face encodings found");
                return new RecognitionResult(null, 0.0f);
            }

            Log.d(TAG, "🔍 Comparing against " + storedEncodings.size() + " stored faces");

            float bestSimilarity = 0.0f;
            AttendanceDatabase.User bestMatch = null;

            for (AttendanceDatabase.FaceEncoding encoding : storedEncodings) {
                try {
                    float[] storedEmbedding = stringToArray(encoding.encoding);
                    if (storedEmbedding == null || storedEmbedding.length != queryEmbedding.length) {
                        Log.w(TAG, "⚠️ Invalid stored embedding for user " + encoding.userId);
                        continue;
                    }

                    // Calculate cosine similarity
                    float similarity = cosineSimilarity(queryEmbedding, storedEmbedding);

                    Log.d(TAG, "📊 User " + encoding.userId + " similarity: " + similarity);

                    if (similarity > bestSimilarity && similarity > RECOGNITION_THRESHOLD) {
                        bestSimilarity = similarity;
                        bestMatch = database.getUserById(encoding.userId);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ Error comparing with stored encoding", e);
                }
            }

            if (bestMatch != null) {
                Log.d(TAG, "✅ Face recognized: " + bestMatch.name + " (similarity: " + bestSimilarity + ")");
                return new RecognitionResult(bestMatch, bestSimilarity);
            } else {
                Log.d(TAG, "❓ No match found (best similarity: " + bestSimilarity + ", threshold: " + RECOGNITION_THRESHOLD + ")");
                return new RecognitionResult(null, bestSimilarity);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in face recognition", e);
            return new RecognitionResult(null, 0.0f);
        }
    }

    /**
     * Recognize face from ImageProxy (used by MainActivity)
     */
    public RecognitionResult recognizeFace(ImageProxy imageProxy, Rect faceRect) {
        if (!isModelLoaded) {
            Log.e(TAG, "❌ Model not loaded");
            return new RecognitionResult(null, 0.0f);
        }

        try {
            // Extract face bitmap immediately while ImageProxy is still valid
            Bitmap faceBitmap = extractFaceBitmapFromImageProxy(imageProxy, faceRect);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap");
                return new RecognitionResult(null, 0.0f);
            }

            // Generate embedding from bitmap
            float[] faceEmbedding = generateFaceEmbeddingFromBitmap(faceBitmap);
            if (faceEmbedding == null) {
                Log.e(TAG, "❌ Failed to generate face embedding");
                return new RecognitionResult(null, 0.0f);
            }

            // Recognize from embedding
            return recognizeFromEmbedding(faceEmbedding);

        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ ImageProxy already closed: " + e.getMessage());
            return new RecognitionResult(null, 0.0f);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error recognizing face", e);
            return new RecognitionResult(null, 0.0f);
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Calculate cosine similarity between two embeddings
     */
    private float cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }

        return dotProduct / (float)(Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Convert float array to string for database storage
     */
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * Convert string to float array from database
     */
    private float[] stringToArray(String str) {
        try {
            String[] parts = str.split(",");
            float[] array = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                array[i] = Float.parseFloat(parts[i].trim());
            }
            return array;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting string to array", e);
            return null;
        }
    }

    /**
     * Check if model is loaded
     */
    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    /**
     * Get recognition threshold
     */
    public float getRecognitionThreshold() {
        return RECOGNITION_THRESHOLD;
    }

    /**
     * Close/cleanup resources (used by RegisterFaceActivity)
     */
    public void close() {
        try {
            if (faceNetInterpreter != null) {
                faceNetInterpreter.close();
                faceNetInterpreter = null;
            }
            isModelLoaded = false;
            Log.d(TAG, "🗑️ FaceRecognitionProcessor resources closed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error closing FaceRecognitionProcessor", e);
        }
    }

    // ===== INNER CLASSES =====

    /**
     * Recognition result class
     */
    public static class RecognitionResult {
        public final AttendanceDatabase.User user;
        public final float confidence;

        public RecognitionResult(AttendanceDatabase.User user, float confidence) {
            this.user = user;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            String userName = (user != null) ? user.name : "Unknown";
            return "RecognitionResult{user=" + userName + ", confidence=" + confidence + "}";
        }
    }
}