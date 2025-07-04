#include <vector>
#include <math.h>

class EmbeddingUtils {
public:
    // Standardize embeddings: x' = (x - mean) / std_dev
    static std::vector<float> standardize(const std::vector<float>& values) {
        if (values.empty()) return values;

        // Calculate mean
        float mean = 0.0f;
        for (float value : values) {
            mean += value;
        }
        mean /= static_cast<float>(values.size());

        // Calculate standard deviation
        float variance = 0.0f;
        for (float value : values) {
            float diff = value - mean;
            variance += diff * diff;
        }
        variance /= static_cast<float>(values.size());
        float std_dev = sqrt(variance);

        // Avoid division by zero
        if (std_dev < 1e-8f) {
            std_dev = 1.0f;
        }

        // Standardize values
        std::vector<float> standardized;
        standardized.reserve(values.size());
        for (float value : values) {
            standardized.push_back((value - mean) / std_dev);
        }

        return standardized;
    }

    // Calculate L2 norm of embedding
    static float calculateNorm(const std::vector<float>& embedding) {
        float sum = 0.0f;
        for (float value : embedding) {
            sum += value * value;
        }
        return sqrt(sum);
    }

    // Normalize embedding to unit vector
    static std::vector<float> normalize(const std::vector<float>& embedding) {
        float norm = calculateNorm(embedding);
        if (norm < 1e-8f) {
            return embedding; // Return original if norm is too small
        }

        std::vector<float> normalized;
        normalized.reserve(embedding.size());
        for (float value : embedding) {
            normalized.push_back(value / norm);
        }
        return normalized;
    }
};