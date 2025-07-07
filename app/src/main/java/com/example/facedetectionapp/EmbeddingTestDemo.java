package com.example.facedetectionapp;

import android.content.Context;
import android.util.Log;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Demo class to test the embedding comparison functionality
 * Call this from your MainActivity to see the embedding comparison in action
 */
public class EmbeddingTestDemo {

    private static final String TAG = "EmbeddingTestDemo";

    /**
     * Run a comprehensive test of the embedding comparison functionality
     * Call this method from your MainActivity.onCreate() or any button click
     */
    public static void runComprehensiveTest() {
        Log.d(TAG, "🚀 Starting Comprehensive Embedding Test");
        Log.d(TAG, "==========================================");

        // Test 1: Basic similarity calculations
        testBasicSimilarity();

        // Test 2: Same person recognition
        testSamePersonRecognition();

        // Test 3: Different person comparison
        testDifferentPersonComparison();

        // Test 4: Find best matches
        testBestMatches();

        // Test 5: Embedding statistics
        testEmbeddingStatistics();

        Log.d(TAG, "==========================================");
        Log.d(TAG, "✅ Comprehensive Embedding Test Complete");
    }

    /**
     * Run comprehensive test with EmbeddingIntegration (requires Context)
     */
    public static void runComprehensiveTestWithIntegration(Context context) {
        Log.d(TAG, "🚀 Starting Comprehensive Test with Integration");
        Log.d(TAG, "===============================================");

        // Run basic tests first
        runComprehensiveTest();

        // Test 6: Integration test with context
        testIntegrationClass(context);

        Log.d(TAG, "===============================================");
        Log.d(TAG, "✅ Comprehensive Test with Integration Complete");
    }

    private static void testBasicSimilarity() {
        Log.d(TAG, "\n🔍 Test 1: Basic Similarity Calculations");
        Log.d(TAG, "----------------------------------------");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
        float[] mrunal = samples.get("Mrunal Patil");
        float[] shivam = samples.get("Shivam Bind");
        float[] payal = samples.get("Payal Mistry");
        float[] akash = samples.get("Akash");

        if (mrunal != null && shivam != null && payal != null && akash != null) {
            // Test cosine similarity
            float cosineMrunalShivam = EmbeddingComparator.cosineSimilarity(mrunal, shivam);
            float cosineMrunalPayal = EmbeddingComparator.cosineSimilarity(mrunal, payal);
            float cosineShivamAkash = EmbeddingComparator.cosineSimilarity(shivam, akash);

            Log.d(TAG, String.format(Locale.getDefault(), "Cosine Similarity - Mrunal vs Shivam: %.4f", cosineMrunalShivam));
            Log.d(TAG, String.format(Locale.getDefault(), "Cosine Similarity - Mrunal vs Payal:  %.4f", cosineMrunalPayal));
            Log.d(TAG, String.format(Locale.getDefault(), "Cosine Similarity - Shivam vs Akash:  %.4f", cosineShivamAkash));

            // Test L2 distance
            float l2MrunalShivam = EmbeddingComparator.l2Distance(mrunal, shivam);
            float l2MrunalPayal = EmbeddingComparator.l2Distance(mrunal, payal);
            float l2ShivamAkash = EmbeddingComparator.l2Distance(shivam, akash);

            Log.d(TAG, String.format(Locale.getDefault(), "L2 Distance - Mrunal vs Shivam: %.4f", l2MrunalShivam));
            Log.d(TAG, String.format(Locale.getDefault(), "L2 Distance - Mrunal vs Payal:  %.4f", l2MrunalPayal));
            Log.d(TAG, String.format(Locale.getDefault(), "L2 Distance - Shivam vs Akash:  %.4f", l2ShivamAkash));
        } else {
            Log.w(TAG, "❌ Some sample embeddings are null");
        }
    }

    private static void testSamePersonRecognition() {
        Log.d(TAG, "\n👤 Test 2: Same Person Recognition");
        Log.d(TAG, "-----------------------------------");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();

        for (String name : samples.keySet()) {
            float[] embedding = samples.get(name);

            if (embedding != null) {
                // Test recognition against themselves (should always match)
                boolean isSamePerson = EmbeddingComparator.areSamePerson(embedding, embedding);
                float cosine = EmbeddingComparator.cosineSimilarity(embedding, embedding);
                float l2 = EmbeddingComparator.l2Distance(embedding, embedding);

                Log.d(TAG, String.format(Locale.getDefault(), "%s vs %s: %s (cosine=%.4f, l2=%.4f)",
                        name, name, isSamePerson ? "✅ MATCH" : "❌ NO MATCH", cosine, l2));
            }
        }
    }

    private static void testDifferentPersonComparison() {
        Log.d(TAG, "\n👥 Test 3: Different Person Comparison");
        Log.d(TAG, "--------------------------------------");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
        String[] names = samples.keySet().toArray(new String[0]);

        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                String name1 = names[i];
                String name2 = names[j];
                float[] emb1 = samples.get(name1);
                float[] emb2 = samples.get(name2);

                if (emb1 != null && emb2 != null) {
                    boolean isSamePerson = EmbeddingComparator.areSamePerson(emb1, emb2);
                    float cosine = EmbeddingComparator.cosineSimilarity(emb1, emb2);
                    float l2 = EmbeddingComparator.l2Distance(emb1, emb2);

                    Log.d(TAG, String.format(Locale.getDefault(), "%s vs %s: %s (cosine=%.4f, l2=%.4f)",
                            name1, name2, isSamePerson ? "✅ MATCH" : "❌ NO MATCH", cosine, l2));
                }
            }
        }
    }

    private static void testBestMatches() {
        Log.d(TAG, "\n🎯 Test 4: Best Match Finding");
        Log.d(TAG, "------------------------------");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();

        for (String queryName : samples.keySet()) {
            float[] queryEmbedding = samples.get(queryName);

            if (queryEmbedding != null) {
                // Find best match
                EmbeddingComparator.ComparisonResult bestMatch =
                        EmbeddingComparator.findBestMatch(queryEmbedding, samples);

                if (bestMatch != null) {
                    Log.d(TAG, String.format(Locale.getDefault(), "Query: %s → Best Match: %s (cosine=%.4f, match=%s)",
                            queryName, bestMatch.name, bestMatch.cosineSimilarity,
                            bestMatch.isMatch ? "✅" : "❌"));
                } else {
                    Log.d(TAG, String.format(Locale.getDefault(), "Query: %s → No match found", queryName));
                }

                // Get all comparisons
                List<EmbeddingComparator.ComparisonResult> allComparisons =
                        EmbeddingComparator.compareWithSamples(queryEmbedding);

                Log.d(TAG, String.format(Locale.getDefault(), "  All comparisons for %s:", queryName));
                for (EmbeddingComparator.ComparisonResult result : allComparisons) {
                    Log.d(TAG, String.format(Locale.getDefault(), "    %s: cosine=%.4f, l2=%.4f, match=%s",
                            result.name, result.cosineSimilarity, result.l2Distance,
                            result.isMatch ? "✅" : "❌"));
                }
            }
        }
    }

    private static void testEmbeddingStatistics() {
        Log.d(TAG, "\n📊 Test 5: Embedding Statistics");
        Log.d(TAG, "--------------------------------");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();

        for (String name : samples.keySet()) {
            float[] embedding = samples.get(name);

            if (embedding != null) {
                EmbeddingComparator.EmbeddingStats stats =
                        EmbeddingComparator.calculateEmbeddingStats(embedding);

                Log.d(TAG, String.format(Locale.getDefault(), "%s stats:", name));
                Log.d(TAG, String.format(Locale.getDefault(), "  Dimension: %d", stats.dimension));
                Log.d(TAG, String.format(Locale.getDefault(), "  Mean: %.4f", stats.mean));
                Log.d(TAG, String.format(Locale.getDefault(), "  Std Dev: %.4f", stats.standardDeviation));
                Log.d(TAG, String.format(Locale.getDefault(), "  Min: %.4f", stats.min));
                Log.d(TAG, String.format(Locale.getDefault(), "  Max: %.4f", stats.max));
                Log.d(TAG, String.format(Locale.getDefault(), "  Magnitude: %.4f", stats.magnitude));
            }
        }
    }

    private static void testIntegrationClass(Context context) {
        Log.d(TAG, "\n🔧 Test 6: Integration Class Test");
        Log.d(TAG, "----------------------------------");

        try {
            EmbeddingIntegration integration = new EmbeddingIntegration(context);

            // Test the integration
            integration.testEmbeddingComparison();

            // Test FaceNet status
            Log.d(TAG, "FaceNet Status: " + integration.getFaceNetInfo());
            Log.d(TAG, "FaceNet Ready: " + integration.isFaceNetReady());

            // Test adding and removing faces (with dummy embedding)
            float[] testEmbedding = EmbeddingComparator.getSampleEmbeddings().get("Mrunal Patil");
            if (testEmbedding != null) {
                integration.addKnownFace("Test Person", testEmbedding);

                Map<String, float[]> knownFaces = integration.getKnownFaces();
                Log.d(TAG, String.format(Locale.getDefault(), "Known faces count after adding: %d", knownFaces.size()));

                integration.removeKnownFace("Test Person");
                knownFaces = integration.getKnownFaces();
                Log.d(TAG, String.format(Locale.getDefault(), "Known faces count after removing: %d", knownFaces.size()));
            }

            // Clean up
            integration.close();

            Log.d(TAG, "✅ Integration class test completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Integration class test failed", e);
        }
    }

    /**
     * Quick test method that you can call from a button click or menu item
     */
    public static void runQuickTest() {
        Log.d(TAG, "🚀 Quick Embedding Test");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
        float[] mrunal = samples.get("Mrunal Patil");
        float[] shivam = samples.get("Shivam Bind");

        if (mrunal != null && shivam != null) {
            // Compare same person
            float sameCosine = EmbeddingComparator.cosineSimilarity(mrunal, mrunal);
            Log.d(TAG, String.format(Locale.getDefault(), "Mrunal vs Mrunal (same): %.4f", sameCosine));

            // Compare different persons
            float diffCosine = EmbeddingComparator.cosineSimilarity(mrunal, shivam);
            Log.d(TAG, String.format(Locale.getDefault(), "Mrunal vs Shivam (diff): %.4f", diffCosine));

            // Find best match for Mrunal
            EmbeddingComparator.ComparisonResult bestMatch =
                    EmbeddingComparator.findBestMatch(mrunal, samples);

            if (bestMatch != null) {
                Log.d(TAG, String.format(Locale.getDefault(), "Best match for Mrunal: %s (%.4f)",
                        bestMatch.name, bestMatch.cosineSimilarity));
            }
        } else {
            Log.w(TAG, "❌ Sample embeddings not found");
        }

        Log.d(TAG, "✅ Quick test complete");
    }

    /**
     * Quick test with integration (requires Context)
     */
    public static void runQuickTestWithIntegration(Context context) {
        Log.d(TAG, "🚀 Quick Test with Integration");

        // Run basic quick test first
        runQuickTest();

        // Test integration if context provided
        if (context != null) {
            try {
                EmbeddingIntegration integration = new EmbeddingIntegration(context);
                Log.d(TAG, "Integration created: " + integration.getFaceNetInfo());
                integration.close();
            } catch (Exception e) {
                Log.e(TAG, "❌ Integration test failed", e);
            }
        }

        Log.d(TAG, "✅ Quick test with integration complete");
    }

    /**
     * Test specific to your sample data - compares all provided embeddings
     */
    public static void testYourSampleData() {
        Log.d(TAG, "🎯 Testing Your Sample Data");
        Log.d(TAG, "===========================");

        // This will run the built-in sample test
        String testResults = EmbeddingComparator.runSampleTest();
        Log.d(TAG, testResults);
    }

    /**
     * Test with context - use this version in MainActivity
     */
    public static void testYourSampleDataWithContext(Context context) {
        Log.d(TAG, "🎯 Testing Your Sample Data with Context");
        Log.d(TAG, "=========================================");

        // Run basic sample test
        testYourSampleData();

        // Test with integration if context provided
        if (context != null) {
            try {
                EmbeddingIntegration integration = new EmbeddingIntegration(context);
                Log.d(TAG, "✅ Sample data test with integration successful");
                Log.d(TAG, "FaceNet Info: " + integration.getFaceNetInfo());
                integration.close();
            } catch (Exception e) {
                Log.e(TAG, "❌ Sample data test with integration failed", e);
            }
        }
    }
}