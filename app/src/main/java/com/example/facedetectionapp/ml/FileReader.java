package com.example.facedetectionapp.ml;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.example.facedetectionapp.BitmapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Utility class to read images from storage and generate embeddings
 */
public class FileReader {
    private static final String TAG = "FileReader";

    private final FaceNetModel faceNetModel;
    private final FaceDetector detector;

    public FileReader(FaceNetModel faceNetModel) {
        this.faceNetModel = faceNetModel;

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        this.detector = FaceDetection.getClient(options);
    }

    public static class FileReaderResult {
        public final List<FaceEmbeddingAnnotator.Pair<String, float[]>> embeddedFaces;
        public final int numImagesWithNoFaces;

        public FileReaderResult(List<FaceEmbeddingAnnotator.Pair<String, float[]>> embeddedFaces, int numImagesWithNoFaces) {
            this.embeddedFaces = embeddedFaces;
            this.numImagesWithNoFaces = numImagesWithNoFaces;
        }
    }

    public void run(ArrayList<FaceEmbeddingAnnotator.Pair<String, Bitmap>> data, FileReaderCallback callback) {
        List<FaceEmbeddingAnnotator.Pair<String, float[]>> imageData = new ArrayList<>();
        int numImagesWithNoFaces = 0;

        Log.d(TAG, String.format("Processing %d images for face embeddings", data.size()));

        for (FaceEmbeddingAnnotator.Pair<String, Bitmap> item : data) {
            try {
                FileReaderResult result = scanImage(item.first, item.second);
                if (result.embeddedFaces.size() > 0) {
                    imageData.addAll(result.embeddedFaces);
                } else {
                    numImagesWithNoFaces++;
                }
            } catch (Exception e) {
                Log.e(TAG, String.format("Error processing image for %s", item.first), e);
                numImagesWithNoFaces++;
            }
        }

        Log.d(TAG, String.format("Processed %d faces, %d images had no faces",
                imageData.size(), numImagesWithNoFaces));

        FileReaderResult finalResult = new FileReaderResult(imageData, numImagesWithNoFaces);
        callback.onResult(finalResult);
    }

    // Crop faces and produce embeddings (using FaceNet) from given image.
    private FileReaderResult scanImage(String name, Bitmap image) {
        List<FaceEmbeddingAnnotator.Pair<String, float[]>> results = new ArrayList<>();

        try {
            InputImage inputImage = InputImage.fromBitmap(image, 0);
            List<Face> faces = Tasks.await(detector.process(inputImage));

            if (!faces.isEmpty() && validateRect(image, faces.get(0).getBoundingBox())) {
                Bitmap croppedFace = cropRectFromBitmap(image, faces.get(0).getBoundingBox());
                float[] embedding = faceNetModel.getFaceEmbedding(croppedFace);
                results.add(new FaceEmbeddingAnnotator.Pair<>(name, embedding));

                Log.d(TAG, String.format("Generated embedding for %s", name));
            } else {
                Log.w(TAG, String.format("No valid faces found in image for %s", name));
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, String.format("Face detection failed for %s", name), e);
        }

        return new FileReaderResult(results, results.isEmpty() ? 1 : 0);
    }

    private boolean validateRect(Bitmap bitmap, Rect boundingBox) {
        return boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                (boundingBox.left + boundingBox.width()) < bitmap.getWidth() &&
                (boundingBox.top + boundingBox.height()) < bitmap.getHeight();
    }

    private Bitmap cropRectFromBitmap(Bitmap source, Rect rect) {
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height());
    }

    public interface FileReaderCallback {
        void onResult(FileReaderResult result);
    }
}