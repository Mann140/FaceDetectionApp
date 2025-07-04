package com.example.facedetectionapp.ml;

public class Models {
    public static final ModelInfo FACENET = new ModelInfo(
            "FaceNet",
            "facenet.tflite",
            0.4f,
            10f,
            128,
            160,
            "Standard FaceNet model with 128-dimensional embeddings"
    );

    public static final ModelInfo FACENET_512 = new ModelInfo(
            "FaceNet-512",
            "facenet_512.tflite",
            0.3f,
            23.56f,
            512,
            160,
            "Extended FaceNet model with 512-dimensional embeddings"
    );

    public static final ModelInfo[] models = {
            FACENET,
            FACENET_512
    };
}