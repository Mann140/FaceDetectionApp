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

public class FaceRecognitionDetector {
    private static final String TAG = "FaceRecognitionDetector";
    private static final String MODEL_FILE = "facenet.tflite";
    private static final int INPUT_SIZE = 160; // FaceNet input size
    private static final int EMBEDDING_SIZE = 512; // FaceNet embedding size

    private static final float[] MEAN = {127.5f, 127.5f, 127.5f};
    private static final float[] STD = {128.0f, 128.0f, 128.0f};

    private Interpreter interpreter;
    private boolean isModelLoaded = false;
    private DatabaseHelper databaseHelper;

    // Recognition threshold - faces with similarity above this are considered matches
    private static final float RECOGNITION_THRESHOLD = 0.6f;

    public FaceRecognitionDetector(Context context) {
        Log.d(TAG, "🚀 Initializing FaceRecognitionDetector with FaceNet model");

        databaseHelper = new DatabaseHelper(context);

        try {
            loadModel(context);
            isModelLoaded = true;
            Log.d(TAG, "✅ FaceNet model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load FaceNet model", e);
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

            Log.d(TAG, "🔍 FaceNet Input shape: " + java.util.Arrays.toString(inputShape));
            Log.d(TAG, "🔍 FaceNet Output shape: " + java.util.Arrays.toString(outputShape));

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in loadModel", e);
            throw new IOException("Failed to load FaceNet model", e);
        }
    }

    /**
     * Extract face embedding from the given face region
     */
    public float[] extractFaceEmbedding(ImageProxy imageProxy, Rect faceRect) {
        if (!isModelLoaded || interpreter == null) {
            Log.e(TAG, "❌ Model not loaded, cannot extract embedding");
            return null;
        }

        try {
            Bitmap faceBitmap = extractFaceFromImage(imageProxy, faceRect);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap");
                return null;
            }

            // Resize to FaceNet input size
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

            // Preprocess for FaceNet
            ByteBuffer inputBuffer = preprocessForFaceNet(resizedBitmap);

            // Run inference
            float[] embedding = runInference(inputBuffer);

            // Normalize embedding (L2 normalization)
            return normalizeEmbedding(embedding);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face embedding", e);
            return null;
        }
    }

    /**
     * Register a new person with their face embedding
     */
    public boolean registerPerson(String name, String employeeId, ImageProxy imageProxy, Rect faceRect) {
        float[] embedding = extractFaceEmbedding(imageProxy, faceRect);
        if (embedding == null) {
            Log.e(TAG, "❌ Failed to extract embedding for registration");
            return false;
        }

        try {
            Person person = new Person();
            person.name = name;
            person.employeeId = employeeId;
            person.faceEmbedding = embedding;
            person.registrationTime = System.currentTimeMillis();

            long personId = databaseHelper.insertPerson(person);

            Log.d(TAG, "✅ Successfully registered person: " + name + " with ID: " + personId);
            return personId > 0;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error registering person", e);
            return false;
        }
    }

    /**
     * Recognize a face and return the matched person or null if no match
     */
    public RecognitionResult recognizeFace(ImageProxy imageProxy, Rect faceRect) {
        float[] queryEmbedding = extractFaceEmbedding(imageProxy, faceRect);
        if (queryEmbedding == null) {
            Log.e(TAG, "❌ Failed to extract embedding for recognition");
            return null;
        }

        List<Person> registeredPersons = databaseHelper.getAllPersons();
        if (registeredPersons.isEmpty()) {
            Log.d(TAG, "📝 No registered persons found");
            return null;
        }

        Person bestMatch = null;
        float bestSimilarity = 0f;

        // Compare with all registered persons
        for (Person person : registeredPersons) {
            float similarity = calculateCosineSimilarity(queryEmbedding, person.faceEmbedding);

            Log.d(TAG, String.format("🔍 Comparing with %s: similarity = %.3f",
                    person.name, similarity));

            if (similarity > bestSimilarity && similarity > RECOGNITION_THRESHOLD) {
                bestSimilarity = similarity;
                bestMatch = person;
            }
        }

        if (bestMatch != null) {
            Log.d(TAG, String.format("✅ Recognition successful: %s (similarity: %.3f)",
                    bestMatch.name, bestSimilarity));

            return new RecognitionResult(bestMatch, bestSimilarity, true);
        } else {
            Log.d(TAG, "❌ No matching person found above threshold");
            return new RecognitionResult(null, bestSimilarity, false);
        }
    }

    /**
     * Mark attendance for a recognized person
     */
    public boolean markAttendance(Person person, AttendanceRecord.AttendanceType type) {
        try {
            AttendanceRecord record = new AttendanceRecord();
            record.personId = person.id;
            record.timestamp = System.currentTimeMillis();
            record.type = type;
            record.location = "Main Entrance"; // You can make this configurable

            long recordId = databaseHelper.insertAttendanceRecord(record);

            Log.d(TAG, String.format("✅ Attendance marked for %s: %s at %s",
                    person.name, type.toString(), new java.util.Date(record.timestamp).toString()));

            return recordId > 0;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance", e);
            return false;
        }
    }

    /**
     * Get today's attendance records
     */
    public List<AttendanceRecord> getTodayAttendance() {
        return databaseHelper.getTodayAttendanceRecords();
    }

    /**
     * Get attendance records for a specific person
     */
    public List<AttendanceRecord> getPersonAttendance(long personId, long startDate, long endDate) {
        return databaseHelper.getAttendanceRecords(personId, startDate, endDate);
    }

    /**
     * Get all registered persons
     */
    public List<Person> getAllRegisteredPersons() {
        return databaseHelper.getAllPersons();
    }

    private float[] runInference(ByteBuffer inputBuffer) {
        try {
            // Prepare output buffer
            ByteBuffer outputBuffer = ByteBuffer.allocateDirect(EMBEDDING_SIZE * 4);
            outputBuffer.order(ByteOrder.nativeOrder());

            // Run inference
            interpreter.run(inputBuffer, outputBuffer);

            // Extract results
            outputBuffer.rewind();
            float[] embedding = new float[EMBEDDING_SIZE];
            for (int i = 0; i < EMBEDDING_SIZE; i++) {
                embedding[i] = outputBuffer.getFloat();
            }

            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ FaceNet inference failed", e);
            throw new RuntimeException("FaceNet inference failed", e);
        }
    }

    private ByteBuffer preprocessForFaceNet(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // FaceNet preprocessing: (pixel - mean) / std
            float rNorm = (r - MEAN[0]) / STD[0];
            float gNorm = (g - MEAN[1]) / STD[1];
            float bNorm = (b - MEAN[2]) / STD[2];

            buffer.putFloat(rNorm);
            buffer.putFloat(gNorm);
            buffer.putFloat(bNorm);
        }

        buffer.rewind();
        return buffer;
    }

    private float[] normalizeEmbedding(float[] embedding) {
        // L2 normalization
        float norm = 0f;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    private float calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same length");
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0f || norm2 == 0f) {
            return 0f;
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private Bitmap extractFaceFromImage(ImageProxy imageProxy, Rect faceRect) {
        try {
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) return null;

            // Add padding around face
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;

            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Make square by cropping to center
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

            // Mirror for front camera
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

    // Result class for face recognition
    public static class RecognitionResult {
        public final Person person;
        public final float confidence;
        public final boolean isRecognized;

        public RecognitionResult(Person person, float confidence, boolean isRecognized) {
            this.person = person;
            this.confidence = confidence;
            this.isRecognized = isRecognized;
        }
    }
}