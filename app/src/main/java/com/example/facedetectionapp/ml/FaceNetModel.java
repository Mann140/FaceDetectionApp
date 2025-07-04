package com.example.facedetectionapp.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

// Utility class for FaceNet model (Simplified without JNI)
public class FaceNetModel {
    private static final String TAG = "FaceNetModel";

    // Input image size for FaceNet model.
    private final int imgSize;

    // Output embedding size
    private final int embeddingDim;
    private final float[][] faceNetModelOutputs;

    private Interpreter interpreter;
    private final ImageProcessor imageTensorProcessor;

    public FaceNetModel(Context context, ModelInfo model, boolean useGpu, boolean useXNNPack) {
        this.imgSize = model.inputDims;
        this.embeddingDim = model.outputDims;
        this.faceNetModelOutputs = new float[1][embeddingDim];

        this.imageTensorProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(new StandardizeOp())
                .build();

        try {
            initializeInterpreter(context, model, useGpu, useXNNPack);
            Log.d(TAG, "FaceNet model initialized successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize FaceNet model", e);
            throw new RuntimeException("Failed to initialize FaceNet model", e);
        }
    }

    private void initializeInterpreter(Context context, ModelInfo model, boolean useGpu, boolean useXNNPack) throws IOException {
        Interpreter.Options interpreterOptions = new Interpreter.Options();

        // Add the GPU Delegate if supported.
        if (useGpu) {
            CompatibilityList compatibilityList = new CompatibilityList();
            if (compatibilityList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
                GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
                interpreterOptions.addDelegate(gpuDelegate);
                Log.d(TAG, "GPU delegate added");
            } else {
                Log.d(TAG, "GPU delegate not supported, using CPU");
            }
        } else {
            // Number of threads for computation
            interpreterOptions.setNumThreads(4);
        }

        interpreterOptions.setUseXNNPACK(useXNNPack);
        interpreterOptions.setUseNNAPI(true);

        ByteBuffer model_buffer = FileUtil.loadMappedFile(context, model.assetsFilename);
        interpreter = new Interpreter(model_buffer, interpreterOptions);

        Log.d(TAG, String.format("Model loaded: %s, Input size: %d, Output dims: %d",
                model.name, imgSize, embeddingDim));
    }

    // Gets a face embedding using FaceNet.
    public float[] getFaceEmbedding(Bitmap image) {
        try {
            return runFaceNet(convertBitmapToBuffer(image))[0];
        } catch (Exception e) {
            Log.e(TAG, "Error generating face embedding", e);
            return new float[embeddingDim]; // Return zero vector on error
        }
    }

    // Run the FaceNet model.
    private float[][] runFaceNet(ByteBuffer inputs) {
        long startTime = System.currentTimeMillis();
        interpreter.run(inputs, faceNetModelOutputs);
        long endTime = System.currentTimeMillis();
        Log.d(TAG, String.format("FaceNet inference took %d ms", (endTime - startTime)));
        return faceNetModelOutputs;
    }

    // Resize the given bitmap and convert it to a ByteBuffer
    private ByteBuffer convertBitmapToBuffer(Bitmap image) {
        return imageTensorProcessor.process(TensorImage.fromBitmap(image)).getBuffer();
    }

    // Op to perform standardization: x' = (x - mean) / std_dev (Pure Java Implementation)
    public static class StandardizeOp implements TensorOperator {

        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] values = input.getFloatArray();
            float[] standardized = standardize(values);

            TensorBuffer output = TensorBuffer.createFrom(input, DataType.FLOAT32);
            output.loadArray(standardized);
            return output;
        }

        // Pure Java standardization implementation
        private float[] standardize(float[] values) {
            if (values.length == 0) return values;

            // Calculate mean
            float mean = 0.0f;
            for (float value : values) {
                mean += value;
            }
            mean /= values.length;

            // Calculate standard deviation
            float variance = 0.0f;
            for (float value : values) {
                float diff = value - mean;
                variance += diff * diff;
            }
            variance /= values.length;
            float stdDev = (float) Math.sqrt(variance);

            // Avoid division by zero
            if (stdDev < 1e-8f) {
                stdDev = 1.0f;
            }

            // Standardize values
            float[] result = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = (values[i] - mean) / stdDev;
            }

            return result;
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}