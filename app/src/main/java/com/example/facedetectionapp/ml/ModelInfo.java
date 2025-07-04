package com.example.facedetectionapp.ml;

public class ModelInfo {
    public final String name;
    public final String assetsFilename;
    public final float cosineThreshold;
    public final float l2Threshold;
    public final int outputDims;
    public final int inputDims;
    public final String description;

    public ModelInfo(String name, String assetsFilename, float cosineThreshold,
                     float l2Threshold, int outputDims, int inputDims, String description) {
        this.name = name;
        this.assetsFilename = assetsFilename;
        this.cosineThreshold = cosineThreshold;
        this.l2Threshold = l2Threshold;
        this.outputDims = outputDims;
        this.inputDims = inputDims;
        this.description = description;
    }

    public ModelInfo(String name, String assetsFilename, float cosineThreshold,
                     float l2Threshold, int outputDims, int inputDims) {
        this(name, assetsFilename, cosineThreshold, l2Threshold, outputDims, inputDims, "");
    }
}