#include <jni.h>
#include <android/log.h>
#include "face_annotator.cpp"
#include "embedding_utils.cpp"

// Fixed JNI function signatures

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_facedetectionapp_ml_FaceEmbeddingAnnotator_createAnnotator(
        JNIEnv* env,
        jobject thiz,
        jobjectArray subject_names,
        jobjectArray subject_embeddings,
        jint embedding_dim,
        jfloat threshold_cosine
) {

    // Read subject names
    std::vector<std::string> names;
    int num_names = env->GetArrayLength(subject_names);
    jboolean is_copy = JNI_FALSE;

    for (int i = 0; i < num_names; i++) {
        jstring j_name = (jstring)(env->GetObjectArrayElement(subject_names, i));
        const char* chars = env->GetStringUTFChars(j_name, &is_copy);
        std::string name = chars;
        env->ReleaseStringUTFChars(j_name, chars);
        env->DeleteLocalRef(j_name);
        names.push_back(name);
    }

    // Read subject_embeddings
    int num_embeddings = env->GetArrayLength(subject_embeddings);
    if (num_embeddings != num_names) {
        return -1L;
    }

    std::vector<float*> embeddings;
    for (int i = 0; i < num_embeddings; i++) {
        jfloatArray embedding = (jfloatArray)env->GetObjectArrayElement(subject_embeddings, i);
        jfloat* embedding_elements = env->GetFloatArrayElements(embedding, &is_copy);

        float* embedding_fp = new float[embedding_dim];
        for (int j = 0; j < embedding_dim; j++) {
            embedding_fp[j] = embedding_elements[j];
        }

        env->ReleaseFloatArrayElements(embedding, embedding_elements, 0);
        env->DeleteLocalRef(embedding);
        embeddings.push_back(embedding_fp);
    }

    FaceAnnotator* annotator = new FaceAnnotator(names, embeddings, embedding_dim, threshold_cosine);
    return reinterpret_cast<jlong>(annotator);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_facedetectionapp_ml_FaceEmbeddingAnnotator_identify(
        JNIEnv* env,
        jobject thiz,
        jlong annotator_ptr,
        jfloatArray subject_embedding
) {

    FaceAnnotator* annotator = reinterpret_cast<FaceAnnotator*>(annotator_ptr);
    if (annotator == nullptr) {
        return env->NewStringUTF("UNKNOWN");
    }

    jboolean is_copy = JNI_FALSE;
    jfloat* embedding_elements = env->GetFloatArrayElements(subject_embedding, &is_copy);

    float* embedding_fp = new float[annotator->embedding_dim];
    for (int j = 0; j < annotator->embedding_dim; j++) {
        embedding_fp[j] = embedding_elements[j];
    }

    env->ReleaseFloatArrayElements(subject_embedding, embedding_elements, 0);

    std::string label = annotator->identify(embedding_fp);
    jstring output = env->NewStringUTF(label.c_str());

    delete[] embedding_fp;
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_facedetectionapp_ml_FaceEmbeddingAnnotator_releaseAnnotator(
        JNIEnv* env,
        jobject thiz,
        jlong annotator_ptr) {

    FaceAnnotator* annotator = reinterpret_cast<FaceAnnotator*>(annotator_ptr);
    if (annotator != nullptr) {
        // Clean up embeddings
        for (float* embedding : annotator->subject_embeddings) {
            delete[] embedding;
        }
        delete annotator;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_facedetectionapp_ml_FaceNetModel_00024StandardizeOp_standardize(
        JNIEnv* env,
        jobject thiz,
        jfloatArray values) {

    jboolean is_copy = JNI_FALSE;
    jsize num_elements = env->GetArrayLength(values);
    jfloat* elements = env->GetFloatArrayElements(values, &is_copy);

    // Convert to vector for processing
    std::vector<float> embedding_vector;
    embedding_vector.reserve(num_elements);
    for (jsize i = 0; i < num_elements; i++) {
        embedding_vector.push_back(elements[i]);
    }

    // Standardize
    std::vector<float> standardized = EmbeddingUtils::standardize(embedding_vector);

    // Copy back to array
    for (jsize i = 0; i < num_elements; i++) {
        elements[i] = standardized[i];
    }

    env->ReleaseFloatArrayElements(values, elements, 0);
    return values;
}