package com.example.facedetectionapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages face recognition database and matching operations with comprehensive logging
 * Stores face embeddings and performs real-time recognition
 */
public class FaceRecognitionManager {
    private static final String TAG = "FaceRecognitionManager";

    // Storage configuration
    private static final String EMBEDDINGS_FILE = "face_embeddings.dat";
    private static final String PREF_KEY_DB_VERSION = "face_db_version";
    private static final int CURRENT_DB_VERSION = 1;

    // Recognition parameters
    private static final float RECOGNITION_CONFIDENCE_THRESHOLD = 0.6f;
    private static final boolean USE_COSINE_SIMILARITY = true; // true for cosine, false for L2
    private static final int MAX_FACES_PER_PERSON = 5; // Limit embeddings per person

    private final Context context;
    private final FaceNetModel faceNetModel;
    private final FaceDetector faceDetector;
    private final SharedPreferences preferences;

    // Thread-safe storage for face embeddings
    private final ConcurrentHashMap<String, List<PersonEmbedding>> faceDatabase = new ConcurrentHashMap<>();
    private volatile boolean isDatabaseLoaded = false;

    // Performance tracking
    private final AtomicLong totalRecognitionAttempts = new AtomicLong(0);
    private final AtomicLong successfulRecognitions = new AtomicLong(0);
    private final AtomicLong totalEmbeddingsGenerated = new AtomicLong(0);
    private volatile long managerStartTime = 0;

    public FaceRecognitionManager(Context context, FaceNetModel faceNetModel) {
        managerStartTime = System.currentTimeMillis();
        Log.i(TAG, "🚀 ========== FACE RECOGNITION MANAGER INIT ==========");
        Log.d(TAG, "🔧 Initializing face recognition manager...");

        this.context = context;
        this.faceNetModel = faceNetModel;
        this.preferences = context.getSharedPreferences("FaceRecognition", Context.MODE_PRIVATE);

        // Initialize face detector for cropping faces from images
        Log.d(TAG, "👁️ Setting up ML Kit face detector for image processing...");
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build();

        this.faceDetector = FaceDetection.getClient(options);
        Log.d(TAG, "✅ Face detector configured");

        // Load existing database
        Log.d(TAG, "💾 Loading existing face database...");
        loadFaceDatabase();

        long initTime = System.currentTimeMillis() - managerStartTime;
        Log.i(TAG, String.format("✅ FaceRecognitionManager initialized in %dms with %d embeddings",
                initTime, getTotalEmbeddings()));
        Log.i(TAG, "==============================================");
    }

    /**
     * Add a person to the face database using a bitmap image
     * @param personName Name of the person
     * @param faceBitmap Image containing the person's face
     * @return CompletableFuture that resolves when processing is complete
     */
    public CompletableFuture<AddResult> addPersonAsync(String personName, Bitmap faceBitmap) {
        Log.i(TAG, String.format("👤 ========== ADDING PERSON: %s ==========", personName));

        return CompletableFuture.supplyAsync(() -> {
            long addStartTime = System.currentTimeMillis();

            try {
                Log.d(TAG, String.format("🔍 Processing face image for: %s", personName));

                if (!faceNetModel.isReady()) {
                    Log.e(TAG, "❌ FaceNet model not ready");
                    return new AddResult(false, "FaceNet model not ready");
                }

                // Log bitmap info
                Log.d(TAG, String.format("🖼️ Input bitmap: %dx%d, config: %s",
                        faceBitmap.getWidth(), faceBitmap.getHeight(),
                        faceBitmap.getConfig()));

                // Detect faces in the image
                Log.d(TAG, "👁️ Detecting faces in input image...");
                InputImage inputImage = InputImage.fromBitmap(faceBitmap, 0);

                CompletableFuture<List<Face>> detectionFuture = new CompletableFuture<>();
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            Log.d(TAG, String.format("✅ Face detection complete: %d faces found", faces.size()));
                            detectionFuture.complete(faces);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Face detection failed: " + e.getMessage(), e);
                            detectionFuture.completeExceptionally(e);
                        });

                List<Face> faces = detectionFuture.get();

                if (faces.isEmpty()) {
                    Log.e(TAG, "❌ No faces detected in image");
                    return new AddResult(false, "No faces detected in image");
                }

                if (faces.size() > 1) {
                    Log.w(TAG, String.format("⚠️ Multiple faces detected (%d), using the largest one", faces.size()));
                }

                // Use the largest face
                Face targetFace = faces.get(0);
                for (Face face : faces) {
                    int faceArea = face.getBoundingBox().width() * face.getBoundingBox().height();
                    int targetArea = targetFace.getBoundingBox().width() * targetFace.getBoundingBox().height();
                    if (faceArea > targetArea) {
                        targetFace = face;
                    }
                }

                Rect faceRect = targetFace.getBoundingBox();
                Log.d(TAG, String.format("🎯 Selected face: %dx%d at (%d,%d)",
                        faceRect.width(), faceRect.height(), faceRect.left, faceRect.top));

                // Crop and process the face
                Log.d(TAG, "✂️ Cropping face from image...");
                Bitmap croppedFace = cropFaceFromBitmap(faceBitmap, targetFace.getBoundingBox());
                if (croppedFace == null) {
                    Log.e(TAG, "❌ Failed to crop face from image");
                    return new AddResult(false, "Failed to crop face from image");
                }

                Log.d(TAG, String.format("✅ Face cropped: %dx%d",
                        croppedFace.getWidth(), croppedFace.getHeight()));

                // Generate embedding
                Log.d(TAG, "🧠 Generating face embedding with FaceNet...");
                long embeddingStartTime = System.currentTimeMillis();
                float[] embedding = faceNetModel.getFaceEmbedding(croppedFace);
                long embeddingTime = System.currentTimeMillis() - embeddingStartTime;

                if (embedding == null) {
                    Log.e(TAG, "❌ Failed to generate face embedding");
                    return new AddResult(false, "Failed to generate face embedding");
                }

                totalEmbeddingsGenerated.incrementAndGet();
                Log.d(TAG, String.format("✅ Embedding generated in %dms (dim: %d)",
                        embeddingTime, embedding.length));

                // Log embedding statistics
                logEmbeddingStats(embedding, personName);

                // Add to database
                Log.d(TAG, "💾 Adding embedding to database...");
                addEmbeddingToDatabase(personName, embedding);

                // Save database
                Log.d(TAG, "💾 Saving database to persistent storage...");
                saveFaceDatabase();

                int totalEmbeddings = getPersonEmbeddingCount(personName);
                long totalTime = System.currentTimeMillis() - addStartTime;
                String message = String.format("Added %s (total embeddings: %d)", personName, totalEmbeddings);

                Log.i(TAG, String.format("✅ SUCCESS: %s in %dms", message, totalTime));
                Log.i(TAG, "=============================================");

                return new AddResult(true, message);

            } catch (Exception e) {
                long totalTime = System.currentTimeMillis() - addStartTime;
                Log.e(TAG, String.format("❌ FAILED to add person %s after %dms: %s",
                        personName, totalTime, e.getMessage()), e);
                return new AddResult(false, "Error: " + e.getMessage());
            }
        });
    }

    /**
     * Recognize a person from a face region in an ImageProxy
     * @param imageProxy Camera frame
     * @param faceRect Bounding box of the detected face
     * @return Recognition result with person name and confidence
     */
    public RecognitionResult recognizeFace(ImageProxy imageProxy, Rect faceRect) {
        long recognitionStartTime = System.currentTimeMillis();
        long recognitionId = totalRecognitionAttempts.incrementAndGet();

        Log.d(TAG, String.format("🔍 Recognition #%d: Starting face recognition...", recognitionId));

        if (!isDatabaseLoaded || faceDatabase.isEmpty()) {
            Log.d(TAG, String.format("❓ Recognition #%d: No faces in database", recognitionId));
            return new RecognitionResult("Unknown", 0f, "No faces in database");
        }

        if (!faceNetModel.isReady()) {
            Log.e(TAG, String.format("❌ Recognition #%d: FaceNet model not ready", recognitionId));
            return new RecognitionResult("Unknown", 0f, "FaceNet model not ready");
        }

        try {
            // Extract face from ImageProxy
            Log.d(TAG, String.format("🖼️ Recognition #%d: Extracting face from camera frame...", recognitionId));
            Bitmap faceBitmap = BitmapUtils.extractFaceFromImageProxy(imageProxy, faceRect);
            if (faceBitmap == null) {
                Log.e(TAG, String.format("❌ Recognition #%d: Failed to extract face", recognitionId));
                return new RecognitionResult("Unknown", 0f, "Failed to extract face");
            }

            Log.d(TAG, String.format("✅ Recognition #%d: Face extracted (%dx%d)",
                    recognitionId, faceBitmap.getWidth(), faceBitmap.getHeight()));

            // Generate embedding
            Log.d(TAG, String.format("🧠 Recognition #%d: Generating embedding...", recognitionId));
            long embeddingStartTime = System.currentTimeMillis();
            float[] queryEmbedding = faceNetModel.getFaceEmbedding(faceBitmap);
            long embeddingTime = System.currentTimeMillis() - embeddingStartTime;

            if (queryEmbedding == null) {
                Log.e(TAG, String.format("❌ Recognition #%d: Failed to generate embedding", recognitionId));
                return new RecognitionResult("Unknown", 0f, "Failed to generate embedding");
            }

            Log.d(TAG, String.format("✅ Recognition #%d: Embedding generated in %dms",
                    recognitionId, embeddingTime));

            // Find best match
            Log.d(TAG, String.format("🎯 Recognition #%d: Finding best match in database...", recognitionId));
            RecognitionResult result = findBestMatch(queryEmbedding, recognitionId);

            long totalTime = System.currentTimeMillis() - recognitionStartTime;

            if (result.isRecognized()) {
                successfulRecognitions.incrementAndGet();
                Log.i(TAG, String.format("✅ Recognition #%d: MATCH FOUND - %s (%.1f%%) in %dms",
                        recognitionId, result.personName, result.confidence, totalTime));
            } else {
                Log.d(TAG, String.format("❓ Recognition #%d: No match found in %dms",
                        recognitionId, totalTime));
            }

            return result;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - recognitionStartTime;
            Log.e(TAG, String.format("❌ Recognition #%d: Error after %dms - %s",
                    recognitionId, totalTime, e.getMessage()), e);
            return new RecognitionResult("Unknown", 0f, "Recognition error");
        }
    }

    /**
     * Find the best matching person for a given embedding
     */
    private RecognitionResult findBestMatch(float[] queryEmbedding, long recognitionId) {
        Log.d(TAG, String.format("🔍 Recognition #%d: Comparing against %d people in database",
                recognitionId, faceDatabase.size()));

        String bestPersonName = "Unknown";
        float bestScore = USE_COSINE_SIMILARITY ? 0f : Float.MAX_VALUE;
        String bestDetails = "No match found";

        int totalComparisons = 0;

        // Compare with all stored embeddings
        for (Map.Entry<String, List<PersonEmbedding>> entry : faceDatabase.entrySet()) {
            String personName = entry.getKey();
            List<PersonEmbedding> embeddings = entry.getValue();

            Log.d(TAG, String.format("👤 Recognition #%d: Comparing against %s (%d embeddings)",
                    recognitionId, personName, embeddings.size()));

            // Calculate average score for this person
            float totalScore = 0f;
            int validComparisons = 0;

            for (PersonEmbedding personEmbedding : embeddings) {
                FaceNetModel.MatchResult matchResult = FaceNetModel.compareEmbeddings(
                        queryEmbedding, personEmbedding.embedding, USE_COSINE_SIMILARITY);

                if (matchResult != null) {
                    totalScore += matchResult.score;
                    validComparisons++;
                    totalComparisons++;
                }
            }

            if (validComparisons > 0) {
                float averageScore = totalScore / validComparisons;

                Log.d(TAG, String.format("📊 Recognition #%d: %s average score: %.3f",
                        recognitionId, personName, averageScore));

                // Check if this is the best match
                boolean isBetter = USE_COSINE_SIMILARITY ?
                        (averageScore > bestScore) : (averageScore < bestScore);

                if (isBetter) {
                    bestScore = averageScore;
                    bestPersonName = personName;
                    bestDetails = String.format("%s (avg of %d embeddings)",
                            USE_COSINE_SIMILARITY ? "Cosine" : "L2", validComparisons);
                }
            }
        }

        Log.d(TAG, String.format("🎯 Recognition #%d: Best candidate: %s (score: %.3f, %d total comparisons)",
                recognitionId, bestPersonName, bestScore, totalComparisons));

        // Check if the best match meets the confidence threshold
        boolean isConfidentMatch = USE_COSINE_SIMILARITY ?
                (bestScore > RECOGNITION_CONFIDENCE_THRESHOLD) :
                (bestScore < RECOGNITION_CONFIDENCE_THRESHOLD);

        if (!isConfidentMatch) {
            Log.d(TAG, String.format("⚠️ Recognition #%d: Best match below confidence threshold (%.3f vs %.3f)",
                    recognitionId, bestScore, RECOGNITION_CONFIDENCE_THRESHOLD));
            bestPersonName = "Unknown";
            bestDetails = String.format("Low confidence (%.3f)", bestScore);
        }

        float confidencePercentage = USE_COSINE_SIMILARITY ?
                (bestScore * 100f) :
                (Math.max(0f, (RECOGNITION_CONFIDENCE_THRESHOLD - bestScore) / RECOGNITION_CONFIDENCE_THRESHOLD) * 100f);

        return new RecognitionResult(bestPersonName, confidencePercentage, bestDetails);
    }

    /**
     * Add an embedding to the database for a specific person
     */
    private void addEmbeddingToDatabase(String personName, float[] embedding) {
        Log.d(TAG, String.format("💾 Adding embedding for: %s", personName));

        List<PersonEmbedding> personEmbeddings = faceDatabase.computeIfAbsent(
                personName, k -> new ArrayList<>());

        int previousCount = personEmbeddings.size();

        // Add new embedding
        personEmbeddings.add(new PersonEmbedding(embedding, System.currentTimeMillis()));

        // Limit number of embeddings per person
        if (personEmbeddings.size() > MAX_FACES_PER_PERSON) {
            // Remove oldest embedding
            personEmbeddings.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
            PersonEmbedding removed = personEmbeddings.remove(0);
            Log.d(TAG, String.format("🗑️ Removed oldest embedding for %s (limit: %d)",
                    personName, MAX_FACES_PER_PERSON));
        }

        int newCount = personEmbeddings.size();
        Log.d(TAG, String.format("✅ Embedding added for %s: %d -> %d embeddings",
                personName, previousCount, newCount));
    }

    /**
     * Crop face from bitmap using bounding box
     */
    private Bitmap cropFaceFromBitmap(Bitmap source, Rect faceRect) {
        Log.d(TAG, String.format("✂️ Cropping face: %dx%d from %dx%d image",
                faceRect.width(), faceRect.height(), source.getWidth(), source.getHeight()));

        try {
            // Add padding around face
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(source.getWidth(), faceRect.right + padding);
            int bottom = Math.min(source.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            Log.d(TAG, String.format("📏 Crop dimensions: %dx%d with %dpx padding", width, height, padding));

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "❌ Invalid crop dimensions");
                return null;
            }

            Bitmap croppedBitmap = Bitmap.createBitmap(source, left, top, width, height);

            // Make square if needed
            int size = Math.min(width, height);
            if (width != height) {
                int xOffset = (width - size) / 2;
                int yOffset = (height - size) / 2;
                croppedBitmap = Bitmap.createBitmap(croppedBitmap, xOffset, yOffset, size, size);
                Log.d(TAG, String.format("📐 Made square: %dx%d", size, size));
            }

            Log.d(TAG, "✅ Face cropping successful");
            return croppedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error cropping face: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Save face database to persistent storage
     */
    private synchronized void saveFaceDatabase() {
        long saveStartTime = System.currentTimeMillis();
        Log.d(TAG, "💾 Saving face database to storage...");

        try {
            File file = new File(context.getFilesDir(), EMBEDDINGS_FILE);

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                // Convert to serializable format
                HashMap<String, List<PersonEmbedding>> serializableMap = new HashMap<>(faceDatabase);
                oos.writeObject(serializableMap);
                oos.writeInt(CURRENT_DB_VERSION);
            }

            preferences.edit().putInt(PREF_KEY_DB_VERSION, CURRENT_DB_VERSION).apply();

            long saveTime = System.currentTimeMillis() - saveStartTime;
            long fileSize = file.length();

            Log.i(TAG, String.format("✅ Database saved in %dms (%d bytes, %d people, %d embeddings)",
                    saveTime, fileSize, faceDatabase.size(), getTotalEmbeddings()));

        } catch (IOException e) {
            Log.e(TAG, "❌ Error saving face database: " + e.getMessage(), e);
        }
    }

    /**
     * Load face database from persistent storage
     */
    @SuppressWarnings("unchecked")
    private synchronized void loadFaceDatabase() {
        long loadStartTime = System.currentTimeMillis();
        Log.d(TAG, "💾 Loading face database from storage...");

        try {
            File file = new File(context.getFilesDir(), EMBEDDINGS_FILE);

            if (!file.exists()) {
                Log.d(TAG, "📝 No existing face database found - starting fresh");
                isDatabaseLoaded = true;
                return;
            }

            Log.d(TAG, String.format("📄 Database file found: %d bytes", file.length()));

            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                HashMap<String, List<PersonEmbedding>> loadedMap =
                        (HashMap<String, List<PersonEmbedding>>) ois.readObject();

                // Check version compatibility
                int savedVersion = ois.readInt();
                Log.d(TAG, String.format("🔢 Database version: saved=%d, current=%d",
                        savedVersion, CURRENT_DB_VERSION));

                if (savedVersion != CURRENT_DB_VERSION) {
                    Log.w(TAG, "⚠️ Database version mismatch - clearing database");
                    faceDatabase.clear();
                } else {
                    faceDatabase.clear();
                    faceDatabase.putAll(loadedMap);

                    // Validate loaded data
                    int totalEmbeddings = 0;
                    for (Map.Entry<String, List<PersonEmbedding>> entry : faceDatabase.entrySet()) {
                        String person = entry.getKey();
                        int embeddingCount = entry.getValue().size();
                        totalEmbeddings += embeddingCount;
                        Log.d(TAG, String.format("👤 Loaded: %s (%d embeddings)", person, embeddingCount));
                    }
                }
            }

            long loadTime = System.currentTimeMillis() - loadStartTime;
            Log.i(TAG, String.format("✅ Database loaded in %dms: %d people, %d total embeddings",
                    loadTime, faceDatabase.size(), getTotalEmbeddings()));

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading face database: " + e.getMessage(), e);
            faceDatabase.clear();
        } finally {
            isDatabaseLoaded = true;
        }
    }

    private void logEmbeddingStats(float[] embedding, String personName) {
        if (embedding == null || embedding.length == 0) return;

        float min = Float.MAX_VALUE, max = Float.MIN_VALUE, sum = 0f;
        for (float value : embedding) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        float mean = sum / embedding.length;

        // Calculate standard deviation
        float varianceSum = 0f;
        for (float value : embedding) {
            float diff = value - mean;
            varianceSum += diff * diff;
        }
        float std = (float) Math.sqrt(varianceSum / embedding.length);

        Log.d(TAG, String.format("📊 Embedding stats for %s: min=%.3f, max=%.3f, mean=%.3f, std=%.3f",
                personName, min, max, mean, std));
    }

    // ========== PUBLIC UTILITY METHODS WITH LOGGING ==========

    /**
     * Remove a person from the database
     */
    public boolean removePerson(String personName) {
        Log.i(TAG, String.format("🗑️ Removing person: %s", personName));

        List<PersonEmbedding> removed = faceDatabase.remove(personName);
        if (removed != null) {
            Log.i(TAG, String.format("✅ Removed %s (%d embeddings)", personName, removed.size()));
            saveFaceDatabase();
            return true;
        } else {
            Log.w(TAG, String.format("⚠️ Person not found: %s", personName));
            return false;
        }
    }

    /**
     * Get all person names in the database
     */
    public List<String> getAllPersonNames() {
        List<String> names = new ArrayList<>(faceDatabase.keySet());
        Log.d(TAG, String.format("📝 Retrieved %d person names: %s", names.size(), names));
        return names;
    }

    /**
     * Get number of embeddings for a specific person
     */
    public int getPersonEmbeddingCount(String personName) {
        List<PersonEmbedding> embeddings = faceDatabase.get(personName);
        int count = embeddings != null ? embeddings.size() : 0;
        Log.d(TAG, String.format("📊 %s has %d embeddings", personName, count));
        return count;
    }

    /**
     * Get total number of embeddings in database
     */
    public int getTotalEmbeddings() {
        int total = faceDatabase.values().stream()
                .mapToInt(List::size)
                .sum();
        return total;
    }

    /**
     * Check if database is ready
     */
    public boolean isDatabaseReady() {
        boolean ready = isDatabaseLoaded && faceNetModel.isReady();
        if (!ready) {
            Log.d(TAG, String.format("❌ Database not ready: loaded=%s, model=%s",
                    isDatabaseLoaded, faceNetModel.isReady()));
        }
        return ready;
    }

    /**
     * Clear entire database
     */
    public void clearDatabase() {
        Log.i(TAG, "🗑️ Clearing entire face database...");

        int previousSize = faceDatabase.size();
        int previousEmbeddings = getTotalEmbeddings();

        faceDatabase.clear();
        saveFaceDatabase();

        Log.i(TAG, String.format("✅ Database cleared: %d people, %d embeddings removed",
                previousSize, previousEmbeddings));
    }

    /**
     * Get performance statistics
     */
    public void logPerformanceStats() {
        long uptime = System.currentTimeMillis() - managerStartTime;
        long totalAttempts = totalRecognitionAttempts.get();
        long successfulRecogs = successfulRecognitions.get();
        long totalEmbeddings = totalEmbeddingsGenerated.get();

        float successRate = totalAttempts > 0 ? (successfulRecogs * 100.0f) / totalAttempts : 0f;

        Log.i(TAG, "📊 ========== RECOGNITION PERFORMANCE ==========");
        Log.i(TAG, String.format("⏱️ Manager uptime: %d seconds", uptime / 1000));
        Log.i(TAG, String.format("🎯 Recognition attempts: %d", totalAttempts));
        Log.i(TAG, String.format("✅ Successful recognitions: %d (%.1f%%)", successfulRecogs, successRate));
        Log.i(TAG, String.format("🧠 Embeddings generated: %d", totalEmbeddings));
        Log.i(TAG, String.format("👥 People in database: %d", faceDatabase.size()));
        Log.i(TAG, String.format("💾 Total stored embeddings: %d", getTotalEmbeddings()));
        Log.i(TAG, "==============================================");
    }

    /**
     * Clean up resources
     */
    public void close() {
        Log.i(TAG, "🔒 Closing FaceRecognitionManager...");

        // Log final stats
        logPerformanceStats();

        // Face detector doesn't need explicit closing
        Log.d(TAG, "✅ FaceRecognitionManager closed");
    }

    // ========== DATA CLASSES ==========

    /**
     * Represents a stored face embedding for a person
     */
    private static class PersonEmbedding implements Serializable {
        private static final long serialVersionUID = 1L;

        final float[] embedding;
        final long timestamp;

        PersonEmbedding(float[] embedding, long timestamp) {
            this.embedding = embedding.clone(); // Defensive copy
            this.timestamp = timestamp;
        }
    }

    /**
     * Result of adding a person to the database
     */
    public static class AddResult {
        public final boolean success;
        public final String message;

        public AddResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Result of face recognition
     */
    public static class RecognitionResult {
        public final String personName;
        public final float confidence;
        public final String details;

        public RecognitionResult(String personName, float confidence, String details) {
            this.personName = personName;
            this.confidence = confidence;
            this.details = details;
        }

        public boolean isRecognized() {
            return !"Unknown".equals(personName);
        }
    }
}