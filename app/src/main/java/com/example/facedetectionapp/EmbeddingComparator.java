package com.example.facedetectionapp;

import android.util.Log;
import java.util.*;

/**
 * Utility class for comparing face embeddings using various similarity metrics
 * Supports cosine similarity, L2 distance, and face recognition functionality
 */
public class EmbeddingComparator {

    private static final String TAG = "EmbeddingComparator";

    // Default thresholds for face recognition
    public static final float DEFAULT_COSINE_THRESHOLD = 0.6f;
    public static final float DEFAULT_L2_THRESHOLD = 1.0f;
    public static final float DEFAULT_EUCLIDEAN_THRESHOLD = 0.8f;

    // Sample embeddings from the provided data
    private static final Map<String, float[]> SAMPLE_EMBEDDINGS = new HashMap<>();

    static {
        // Initialize sample embeddings
        SAMPLE_EMBEDDINGS.put("Mrunal Patil", new float[]{
                1.2507889f, -0.98879457f, -0.72605574f, 1.1618929f, -0.4800903f, 1.7459981f,
                -0.18791543f, -0.8644619f, 1.331312f, 0.14789945f, -1.7783893f, -0.4741546f,
                -1.0199805f, -0.47419125f, -1.4397385f, -1.1795986f, -0.07610887f, 0.97320175f,
                -0.18939656f, -0.5850892f, 1.0973954f, -0.06890939f, 1.3598377f, -2.5572748f,
                0.58873796f, -0.10465925f, -0.071751654f, -0.9957396f, -0.3604141f, 0.45989475f,
                0.43810993f, -0.993532f, -0.33581078f, -0.04995872f, 0.6278108f, -1.5909216f,
                -0.49578032f, 1.17654f, 0.2667407f, 0.06786358f, -0.19715327f, -0.5333488f,
                -1.0207341f, -0.6859002f, 1.428981f, -0.29990762f, -0.34220672f, 2.0513732f,
                0.106612794f, -0.43852308f, -0.4616757f, 0.5544293f, -0.15384772f, -0.69540644f,
                2.733055f, 0.2767703f, 0.614125f, -0.104976185f, 0.33754146f, -1.2721523f,
                0.8347949f, -0.30518454f, 0.11488426f, -0.559978f, -1.1474346f, 1.5517875f,
                -1.1990511f, 0.6759815f, -2.4241376f, -1.955549f, -0.78959376f, -0.16723564f,
                0.67607486f, -1.3076606f, -0.04564629f, 0.5747561f, -1.4731385f, 0.4249839f,
                0.4541785f, -0.9197077f, -1.2959939f, -0.17717913f, 0.21191745f, 0.90838516f,
                0.2049798f, -1.155141f, -1.0014263f, -0.012476012f, 1.6598676f, -0.5657608f,
                -0.7536352f, 0.4545086f, 0.35065657f, 0.9634288f, 0.7903724f, -0.35930124f,
                0.5163571f, 0.85567755f, -0.018289577f, -0.77550656f, 2.6387858f, -0.3322504f,
                -1.0822357f, 0.32182616f, 0.69234765f, 0.71744627f, 0.2909864f, 0.3731432f,
                -0.033502646f, -0.9006285f, 0.25965586f, 0.4012667f, -0.18589455f, -1.1606302f,
                -0.87353635f, 0.7879237f, -1.6569883f, 0.6417821f, -0.2032024f, -0.48554924f,
                0.29907674f, 1.1516094f, -0.7573599f, 0.16077872f, -0.18889861f, 0.36221144f,
                -0.66500103f, -1.1830188f
        });

        SAMPLE_EMBEDDINGS.put("Shivam Bind", new float[]{
                -0.22929472f, 1.4142711f, 0.39344305f, -1.4450269f, -2.1727285f, 0.5864796f,
                -0.9775673f, -1.5111139f, -0.5731728f, -1.0031483f, -0.054518532f, -0.37446404f,
                -1.1810418f, -0.8874692f, -0.12640339f, -0.0013078563f, 0.90752876f, 0.32171047f,
                1.0173788f, -1.2314863f, -0.030617863f, -1.0165163f, 0.7509405f, -0.94815564f,
                -0.29044086f, -0.4475525f, -1.3645098f, 0.6414247f, -1.023528f, 0.61176616f,
                -0.8259032f, -1.9042869f, -0.55191565f, 0.9743968f, 1.1653318f, 0.7074046f,
                0.47363853f, 0.4695127f, 0.6066334f, 0.8226874f, -0.23328227f, -0.7579054f,
                -0.97319925f, -0.035594877f, 0.827177f, -0.3688568f, 0.22672468f, 2.1803336f,
                -0.62147355f, -0.6922033f, -0.53508776f, 0.92885447f, 0.6302337f, -0.31535763f,
                0.37489814f, 1.5417618f, 0.52404994f, 1.0874816f, 0.059896674f, -0.708926f,
                0.73394966f, -0.88646805f, -0.8645119f, 1.3480698f, 0.14657354f, 1.3337238f,
                0.11531004f, 0.10061527f, -0.43518746f, -0.925054f, 1.2152551f, 0.08804907f,
                0.24311166f, -0.40799022f, -0.14049958f, 1.7741549f, -0.23740603f, 0.7870872f,
                -0.4551759f, -1.3053998f, -1.0304332f, 0.37937257f, -0.7444628f, 0.50607157f,
                2.157324f, -1.5914432f, -0.07565213f, -0.82600904f, -1.4709948f, 0.1270808f,
                -0.70694315f, -0.83902764f, 1.66803f, 0.8947653f, -0.27732518f, 0.030576045f,
                -0.4187659f, 1.2959868f, -1.186794f, 0.046280332f, 1.5391549f, 0.09916165f,
                0.73776823f, 0.6958472f, -2.0696602f, 0.95402193f, -1.144173f, 0.6176919f,
                -0.8418586f, -1.0386951f, -0.44368318f, 1.171377f, 0.23089126f, 0.13736652f,
                -0.81915253f, 1.2106593f, 0.76945245f, -0.61587363f, -0.95326567f, 1.9242841f,
                0.99430877f, 0.40849233f, 0.24135505f, -1.5797868f, -0.16571002f, 0.12854302f,
                0.9198183f, -0.4038666f
        });

        SAMPLE_EMBEDDINGS.put("Payal Mistry", new float[]{
                -0.32200125f, -1.7598358f, -0.1027742f, -0.9714464f, -1.6993257f, 2.3548076f,
                -0.12701055f, 0.37804052f, -0.39634147f, 0.534628f, -1.7338599f, 0.23432703f,
                0.0931786f, -0.57858396f, -0.10705824f, -2.0571752f, 0.9130721f, -0.6750927f,
                0.21313754f, -0.7829092f, 0.6602609f, -2.1847868f, 0.8342467f, -0.39760163f,
                0.9802983f, -0.107153736f, -0.2598714f, -0.4856252f, -0.028772347f, 1.2235559f,
                1.829916f, -1.2666861f, 0.5324772f, -0.2667557f, 0.12450555f, -1.696164f,
                -0.7472896f, 2.0084338f, 1.4146862f, 0.34004566f, -0.22434816f, 0.86647296f,
                0.14856315f, -0.44525614f, 1.8645225f, 0.030602342f, 0.46545625f, 1.0539355f,
                0.18555057f, -0.8442503f, -1.8113811f, 0.61150426f, 1.1875396f, -1.7172068f,
                -0.49293396f, 1.1973597f, 1.0306859f, -0.3216848f, 1.0738248f, 0.41911685f,
                1.5231639f, 0.73976046f, -0.92003846f, 0.33536747f, -0.9830604f, -0.6604965f,
                -0.8287586f, -0.45127094f, -0.7396852f, -1.0339694f, 0.30509934f, 0.38540742f,
                -0.13629065f, -1.5904824f, 1.6899983f, -0.23510182f, -0.41501626f, -0.08651842f,
                0.08740828f, 0.3652159f, -0.17263031f, -0.45440587f, -0.5455391f, -0.9475289f,
                0.87176484f, 0.491946f, 0.3543849f, -0.2834773f, -0.8965924f, 0.6869984f,
                -1.1153321f, -0.02870067f, -1.3510274f, 1.0850829f, 0.9726122f, 1.1910611f,
                -0.80825764f, 0.1872228f, -0.36510018f, 0.19432074f, 2.284755f, -0.36144248f,
                1.0222414f, 0.17870612f, -0.92817336f, 0.5262706f, -0.938005f, -1.0556699f,
                0.41532686f, -0.8184866f, -0.5074941f, -0.27598494f, -1.4294027f, -0.39558265f,
                -0.92875415f, 0.21034169f, -1.5712572f, 0.8918892f, -0.73193544f, 0.02888618f,
                2.220356f, -0.23616934f, -0.6923568f, 0.3783744f, -1.6487118f, -0.5365464f,
                0.4535886f, -1.0991858f
        });

        SAMPLE_EMBEDDINGS.put("Akash", new float[]{
                -0.6783024f, 0.2241247f, -0.1611563f, 0.6772548f, 0.6102152f, 2.0844316f,
                -1.4414961f, -0.080327675f, 1.083378f, 1.3833973f, -0.68211615f, -1.0749956f,
                -1.7276679f, -0.82709086f, -1.2701054f, 0.8097951f, 0.05034427f, 0.58404577f,
                -0.34318253f, -0.7702186f, 0.17335701f, 0.6062567f, 0.11231751f, -0.012843609f,
                -1.0580356f, 0.23450214f, 0.9286841f, 0.034673806f, -0.7325319f, 1.231789f,
                -0.4436253f, 0.3111809f, -1.081971f, -1.2612001f, 1.7324066f, 0.2053328f,
                -1.2261536f, 1.5187898f, 2.7385392f, 1.4603146f, 1.037483f, -1.7365729f,
                0.43229342f, -0.106072344f, 1.6710597f, 0.012877366f, 0.03762934f, 0.42639467f,
                -0.6394125f, 1.094188f, -1.1562663f, 1.3697487f, 1.7488917f, 1.289546f,
                -0.262707f, 0.9485333f, 0.55345786f, -2.632902f, -0.9227998f, -1.1268349f,
                -0.08032713f, -1.0938222f, 0.04750363f, -0.08957268f, -0.102351256f, 0.5628403f,
                0.5655581f, -0.10610189f, -1.0712332f, -1.2586021f, -1.2655805f, 0.6888077f,
                0.51324016f, 0.26876354f, 0.75043213f, -0.4456611f, -0.9492712f, 0.57910424f,
                -0.5318877f, 0.29287344f, 0.11104157f, -0.86251116f, 1.2571106f, 1.715812f,
                1.1344622f, 0.26770708f, 0.85181004f, 0.5427087f, -0.8170469f, 0.6575555f,
                -0.68597f, 0.52896637f, 0.5436275f, 0.6927217f, 0.74358445f, -0.112663776f,
                0.074642226f, -0.348134f, -1.4185891f, -0.81487745f, -0.512352f, 0.2838264f,
                -1.2451532f, 0.2720492f, -0.04787986f, -0.6934722f, -1.464844f, 1.9774767f,
                0.4591728f, 0.03700775f, 2.442f, -0.62810004f, -0.030993164f, 1.526002f,
                -1.7723899f, 0.17273538f, -0.05945453f, 1.2631475f, -0.7124799f, -1.3250072f,
                0.26263264f, -0.6214843f, 0.51925194f, -0.6423867f, -1.2040992f, -0.26068878f,
                -0.060354903f, -0.43249714f
        });
    }

    /**
     * Calculate cosine similarity between two embeddings
     * Returns a value between -1 and 1, where 1 means identical vectors
     */
    public static float cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }

        float dotProduct = 0.0f;
        float magnitude1 = 0.0f;
        float magnitude2 = 0.0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            magnitude1 += embedding1[i] * embedding1[i];
            magnitude2 += embedding2[i] * embedding2[i];
        }

        magnitude1 = (float) Math.sqrt(magnitude1);
        magnitude2 = (float) Math.sqrt(magnitude2);

        if (magnitude1 != 0.0f && magnitude2 != 0.0f) {
            return dotProduct / (magnitude1 * magnitude2);
        } else {
            return 0.0f;
        }
    }

    /**
     * Calculate L2 (Euclidean) distance between two embeddings
     * Returns a value >= 0, where 0 means identical vectors
     */
    public static float l2Distance(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }

        float sum = 0.0f;
        for (int i = 0; i < embedding1.length; i++) {
            float diff = embedding1[i] - embedding2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * Calculate normalized L2 distance (between 0 and 1)
     */
    public static float normalizedL2Distance(float[] embedding1, float[] embedding2) {
        float l2Dist = l2Distance(embedding1, embedding2);
        // Normalize by the maximum possible distance for unit vectors
        float maxDistance = (float) Math.sqrt(2.0f); // Maximum distance between two unit vectors
        return Math.min(l2Dist / maxDistance, 1.0f);
    }

    /**
     * Compare two embeddings and determine if they represent the same person
     */
    public static boolean areSamePerson(float[] embedding1, float[] embedding2) {
        return areSamePerson(embedding1, embedding2, DEFAULT_COSINE_THRESHOLD, DEFAULT_L2_THRESHOLD);
    }

    /**
     * Compare two embeddings with custom thresholds
     */
    public static boolean areSamePerson(float[] embedding1, float[] embedding2,
                                        float cosineThreshold, float l2Threshold) {
        float cosineScore = cosineSimilarity(embedding1, embedding2);
        float l2Score = l2Distance(embedding1, embedding2);

        return cosineScore >= cosineThreshold && l2Score <= l2Threshold;
    }

    /**
     * Find the best match for a given embedding from a map of known embeddings
     */
    public static ComparisonResult findBestMatch(float[] queryEmbedding, Map<String, float[]> knownEmbeddings) {
        return findBestMatch(queryEmbedding, knownEmbeddings, DEFAULT_COSINE_THRESHOLD, DEFAULT_L2_THRESHOLD);
    }

    /**
     * Find the best match with custom thresholds
     */
    public static ComparisonResult findBestMatch(float[] queryEmbedding, Map<String, float[]> knownEmbeddings,
                                                 float cosineThreshold, float l2Threshold) {
        ComparisonResult bestMatch = null;
        float bestScore = -1.0f;

        for (Map.Entry<String, float[]> entry : knownEmbeddings.entrySet()) {
            String name = entry.getKey();
            float[] embedding = entry.getValue();

            float cosineScore = cosineSimilarity(queryEmbedding, embedding);
            float l2Score = l2Distance(queryEmbedding, embedding);

            ComparisonResult result = new ComparisonResult(
                    name,
                    cosineScore,
                    l2Score,
                    cosineScore >= cosineThreshold && l2Score <= l2Threshold
            );

            if (result.isMatch && cosineScore > bestScore) {
                bestScore = cosineScore;
                bestMatch = result;
            }
        }

        return bestMatch;
    }

    /**
     * Compare embedding with all sample embeddings
     */
    public static List<ComparisonResult> compareWithSamples(float[] queryEmbedding) {
        List<ComparisonResult> results = new ArrayList<>();

        for (Map.Entry<String, float[]> entry : SAMPLE_EMBEDDINGS.entrySet()) {
            String name = entry.getKey();
            float[] embedding = entry.getValue();

            float cosineScore = cosineSimilarity(queryEmbedding, embedding);
            float l2Score = l2Distance(queryEmbedding, embedding);

            ComparisonResult result = new ComparisonResult(
                    name,
                    cosineScore,
                    l2Score,
                    areSamePerson(queryEmbedding, embedding)
            );

            results.add(result);
        }

        // Sort by cosine similarity (descending)
        Collections.sort(results, new Comparator<ComparisonResult>() {
            @Override
            public int compare(ComparisonResult a, ComparisonResult b) {
                return Float.compare(b.cosineSimilarity, a.cosineSimilarity);
            }
        });

        return results;
    }

    /**
     * Calculate embedding statistics for analysis
     */
    public static EmbeddingStats calculateEmbeddingStats(float[] embedding) {
        float sum = 0.0f;
        float min = embedding[0];
        float max = embedding[0];

        for (float value : embedding) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        float mean = sum / embedding.length;

        float varianceSum = 0.0f;
        float magnitudeSum = 0.0f;
        for (float value : embedding) {
            float diff = value - mean;
            varianceSum += diff * diff;
            magnitudeSum += value * value;
        }

        float variance = varianceSum / embedding.length;
        float stdDev = (float) Math.sqrt(variance);
        float magnitude = (float) Math.sqrt(magnitudeSum);

        return new EmbeddingStats(
                embedding.length,
                mean,
                stdDev,
                min,
                max,
                magnitude
        );
    }

    /**
     * Normalize embedding to unit vector
     */
    public static float[] normalizeEmbedding(float[] embedding) {
        float magnitudeSum = 0.0f;
        for (float value : embedding) {
            magnitudeSum += value * value;
        }
        float magnitude = (float) Math.sqrt(magnitudeSum);

        if (magnitude != 0f) {
            float[] normalized = new float[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                normalized[i] = embedding[i] / magnitude;
            }
            return normalized;
        } else {
            return embedding.clone();
        }
    }

    /**
     * Get sample embeddings map
     */
    public static Map<String, float[]> getSampleEmbeddings() {
        return new HashMap<>(SAMPLE_EMBEDDINGS);
    }

    /**
     * Test the embedding comparison with sample data
     */
    public static String runSampleTest() {
        StringBuilder results = new StringBuilder();
        results.append("=== Embedding Comparison Test ===\n\n");

        // Test same person comparison
        float[] mrunalEmbedding = SAMPLE_EMBEDDINGS.get("Mrunal Patil");
        EmbeddingStats mrunalStats = calculateEmbeddingStats(mrunalEmbedding);
        results.append("Mrunal Patil embedding stats:\n");
        results.append(String.format("  Dimension: %d\n", mrunalStats.dimension));
        results.append(String.format("  Mean: %.4f\n", mrunalStats.mean));
        results.append(String.format("  Std Dev: %.4f\n", mrunalStats.standardDeviation));
        results.append(String.format("  Magnitude: %.4f\n", mrunalStats.magnitude));
        results.append("\n");

        // Compare all pairs
        List<String> names = new ArrayList<>(SAMPLE_EMBEDDINGS.keySet());
        results.append("Pairwise Comparison Results:\n");
        results.append(String.format("%-15s %-15s %10s %10s %8s\n", "Person 1", "Person 2", "Cosine", "L2 Dist", "Match"));
        results.append("----------------------------------------------------------------------\n");

        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String name1 = names.get(i);
                String name2 = names.get(j);
                float[] emb1 = SAMPLE_EMBEDDINGS.get(name1);
                float[] emb2 = SAMPLE_EMBEDDINGS.get(name2);

                float cosine = cosineSimilarity(emb1, emb2);
                float l2 = l2Distance(emb1, emb2);
                boolean match = areSamePerson(emb1, emb2);

                results.append(String.format("%-15s %-15s %10.4f %10.4f %8s\n",
                        name1, name2, cosine, l2, match ? "YES" : "NO"));
            }
        }

        return results.toString();
    }

    /**
     * Data class to hold comparison results
     */
    public static class ComparisonResult {
        public final String name;
        public final float cosineSimilarity;
        public final float l2Distance;
        public final boolean isMatch;

        public ComparisonResult(String name, float cosineSimilarity, float l2Distance, boolean isMatch) {
            this.name = name;
            this.cosineSimilarity = cosineSimilarity;
            this.l2Distance = l2Distance;
            this.isMatch = isMatch;
        }

        @Override
        public String toString() {
            return String.format("ComparisonResult(name='%s', cosine=%.4f, l2=%.4f, match=%s)",
                    name, cosineSimilarity, l2Distance, isMatch);
        }
    }

    /**
     * Data class to hold embedding statistics
     */
    public static class EmbeddingStats {
        public final int dimension;
        public final float mean;
        public final float standardDeviation;
        public final float min;
        public final float max;
        public final float magnitude;

        public EmbeddingStats(int dimension, float mean, float standardDeviation,
                              float min, float max, float magnitude) {
            this.dimension = dimension;
            this.mean = mean;
            this.standardDeviation = standardDeviation;
            this.min = min;
            this.max = max;
            this.magnitude = magnitude;
        }

        @Override
        public String toString() {
            return String.format("EmbeddingStats(dim=%d, mean=%.4f, std=%.4f, magnitude=%.4f)",
                    dimension, mean, standardDeviation, magnitude);
        }
    }
}