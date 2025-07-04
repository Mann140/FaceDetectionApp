package com.example.facedetectionapp.ml;

import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplified face embedding annotator that works without JNI
 * Uses pure Java implementation for face comparison
 */
public class FaceEmbeddingAnnotator {
    private static final String TAG = "FaceEmbeddingAnnotator";
    private static final float COSINE_THRESHOLD = 0.4f;

    private Map<String, float[]> faceDatabase = new HashMap<>();
    private Map<String, Integer> nameCount = new HashMap<>();
    private boolean ready = false;

    public void initialize(List<Pair<String, float[]>> scannedEmbeddings) {
        try {
            faceDatabase.clear();
            nameCount.clear();

            // Store embeddings with names
            for (Pair<String, float[]> pair : scannedEmbeddings) {
                String name = pair.first;
                float[] embedding = pair.second;

                // Store the embedding (for multiple images per person, we'll use the first one)
                if (!faceDatabase.containsKey(name)) {
                    faceDatabase.put(name, embedding);
                    nameCount.put(name, 1);
                } else {
                    // For multiple images, we could average embeddings, but for simplicity, keep the first
                    nameCount.put(name, nameCount.get(name) + 1);
                }
            }

            this.ready = true;
            Log.d(TAG, String.format("Annotator initialized with %d unique people", faceDatabase.size()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize annotator", e);
            this.ready = false;
        }
    }

    public String run(float[] embedding) {
        if (!ready || faceDatabase.isEmpty()) {
            Log.w(TAG, "Annotator not ready");
            return "UNKNOWN";
        }

        try {
            String bestMatch = "UNKNOWN";
            float bestScore = -1f;

            // Compare with all stored embeddings
            for (Map.Entry<String, float[]> entry : faceDatabase.entrySet()) {
                String name = entry.getKey();
                float[] storedEmbedding = entry.getValue();

                float similarity = calculateCosineSimilarity(embedding, storedEmbedding);

                if (similarity > bestScore && similarity > COSINE_THRESHOLD) {
                    bestScore = similarity;
                    bestMatch = name;
                }
            }

            Log.d(TAG, String.format("Best match: %s (score: %.3f)", bestMatch, bestScore));
            return bestMatch;

        } catch (Exception e) {
            Log.e(TAG, "Error during face identification", e);
            return "UNKNOWN";
        }
    }

    public boolean isReady() {
        return ready;
    }

    public void release() {
        faceDatabase.clear();
        nameCount.clear();
        ready = false;
        Log.d(TAG, "Annotator released");
    }

    // Calculate cosine similarity between two embeddings
    private float calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
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

        norm1 = (float) Math.sqrt(norm1);
        norm2 = (float) Math.sqrt(norm2);

        if (norm1 == 0f || norm2 == 0f) {
            return 0f;
        }

        return dotProduct / (norm1 * norm2);
    }

    // Simple Pair class for storing name-embedding pairs
    public static class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
}