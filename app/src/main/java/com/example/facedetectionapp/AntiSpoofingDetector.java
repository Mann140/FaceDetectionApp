package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AntiSpoofingDetector {
    private static final String TAG = "AntiSpoofingDetector";
    private static final String MODEL_FILE = "FaceAntiSpoofing.tflite"; // Updated model name
    private static final int INPUT_SIZE = 224; // Standard input size for FaceAntiSpoofing model
    private static final int CHANNELS = 3; // RGB

    // Normalization parameters for the model (typical ImageNet normalization)
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private boolean isModelLoaded = false;

    public AntiSpoofingDetector(Context context) {
        try {
            loadModel(context);
            setupImageProcessor();
            isModelLoaded = true;
            Log.d(TAG, "FaceAntiSpoofing model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load FaceAntiSpoofing model", e);
            isModelLoaded = false;
        }
    }

    private void loadModel(Context context) throws IOException {
        ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Use multiple threads for better performance
        interpreter = new Interpreter(modelBuffer, options);

        // Log input/output tensor info for debugging
        Log.d(TAG, "Model input shape: " + java.util.Arrays.toString(interpreter.getInputTensor(0).shape()));
        Log.d(TAG, "Model output shape: " + java.util.Arrays.toString(interpreter.getOutputTensor(0).shape()));
    }

    private void setupImageProcessor() {
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build();
    }

    public MainActivity.FaceData analyzeFace(ImageProxy imageProxy, Rect faceRect) {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, falling back to heuristic detection");
            return new MainActivity.FaceData(faceRect, true, 50.0f, "Fallback", false);
        }

        try {
            // Extract face region from the image
            Bitmap faceBitmap = extractFaceFromImage(imageProxy, faceRect);
            if (faceBitmap == null) {
                return new MainActivity.FaceData(faceRect, true, 50.0f, "Error", false);
            }

            // Preprocess the face image
            TensorImage tensorImage = new TensorImage();
            tensorImage.load(faceBitmap);
            tensorImage = imageProcessor.process(tensorImage);

            // Get the preprocessed image as ByteBuffer and apply manual normalization
            ByteBuffer inputBuffer = normalizeImage(tensorImage.getBuffer());

            // Prepare output array - FaceAntiSpoofing model typically outputs single value
            float[][] output = new float[1][1]; // Single score output

            // Run inference
            interpreter.run(inputBuffer, output);

            // Get the spoofing score (0 = real, 1 = fake)
            float spoofScore = output[0][0];

            // Convert to probability and determine if real
            // For FaceAntiSpoofing model: lower scores indicate real faces
            boolean isReal = spoofScore < 0.5f; // Threshold can be adjusted
            float confidence = isReal ? (1.0f - spoofScore) * 100f : spoofScore * 100f;

            Log.d(TAG, String.format("FaceAntiSpoofing results - Score: %.3f, IsReal: %s, Confidence: %.1f%%",
                    spoofScore, isReal, confidence));

            return new MainActivity.FaceData(faceRect, isReal, confidence, "FaceAntiSpoofing", false);

        } catch (Exception e) {
            Log.e(TAG, "Error during anti-spoofing detection", e);
            return new MainActivity.FaceData(faceRect, true, 50.0f, "Error", false);
        }
    }

    @Deprecated
    public boolean isReal(ImageProxy imageProxy, Rect faceRect) {
        MainActivity.FaceData result = analyzeFace(imageProxy, faceRect);
        return result.isReal;
    }

    private Bitmap extractFaceFromImage(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) {
                return null;
            }

            // Calculate face region with some padding
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                return null;
            }

            // Extract face region
            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Make the face square by cropping to the smaller dimension
            int size = Math.min(width, height);
            int xOffset = (width - size) / 2;
            int yOffset = (height - size) / 2;

            Bitmap squareFace = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);

            // The ImageProcessor will handle resizing to INPUT_SIZE
            return squareFace;

        } catch (Exception e) {
            Log.e(TAG, "Error extracting face from image", e);
            return null;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                return null;
            }

            // Get image dimensions
            int width = image.getWidth();
            int height = image.getHeight();

            // Convert YUV420 to RGB
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            // Convert to RGB bitmap
            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Handle front camera mirroring
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f); // Mirror horizontally for front camera

            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
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

    private ByteBuffer normalizeImage(ByteBuffer inputBuffer) {
        // Create a new buffer for normalized data
        ByteBuffer normalizedBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * 4);
        normalizedBuffer.order(ByteOrder.nativeOrder());

        inputBuffer.rewind();

        // Convert from uint8 to float and normalize
        while (inputBuffer.hasRemaining()) {
            // Read RGB values (0-255)
            int r = inputBuffer.get() & 0xFF;
            int g = inputBuffer.get() & 0xFF;
            int b = inputBuffer.get() & 0xFF;

            // Normalize to [0,1] then apply ImageNet normalization
            float normalizedR = (r / 255.0f - MEAN[0]) / STD[0];
            float normalizedG = (g / 255.0f - MEAN[1]) / STD[1];
            float normalizedB = (b / 255.0f - MEAN[2]) / STD[2];

            normalizedBuffer.putFloat(normalizedR);
            normalizedBuffer.putFloat(normalizedG);
            normalizedBuffer.putFloat(normalizedB);
        }

        normalizedBuffer.rewind();
        return normalizedBuffer;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}