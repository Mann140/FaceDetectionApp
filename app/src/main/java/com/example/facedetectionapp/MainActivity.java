package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private FaceOverlayView overlayView;
    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);

        // Initialize anti-spoofing detector
        antiSpoofingDetector = new AntiSpoofingDetector(this);

        // Configure face detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // We don't need ML Kit classifications anymore
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);

        // Replace placeholder view with custom overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, 100);
    }

    private void startCamera() {
        faceCountText.setText("Starting camera...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder().build();

                // Front camera selector
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Image analysis for face detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(),
                        new ImageAnalysis.Analyzer() {
                            @Override
                            public void analyze(@NonNull ImageProxy image) {
                                processImage(image);
                            }
                        });

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        // Create list of face data with TensorFlow Lite spoofing detection
                        List<FaceData> faceDataList = new ArrayList<>();

                        for (Face face : faces) {
                            // Use TensorFlow Lite model for anti-spoofing detection
                            FaceData faceData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());
                            faceDataList.add(faceData);
                        }

                        // Count real vs fake faces
                        int realFaces = 0;
                        int fakeFaces = 0;
                        for (FaceData data : faceDataList) {
                            if (data.isReal) realFaces++;
                            else fakeFaces++;
                        }

                        final int finalRealFaces = realFaces;
                        final int finalFakeFaces = fakeFaces;

                        runOnUiThread(() -> {
                            faceCountText.setText("Real faces: " + finalRealFaces +
                                    " | Fake: " + finalFakeFaces);

                            // Show warning if fake faces detected
                            if (finalFakeFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("⚠️ Spoofing detected by AI!");
                            } else if (finalRealFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("✓ Real faces detected by AI");
                            } else {
                                spoofWarningText.setVisibility(View.GONE);
                            }

                            // Update overlay with face data
                            overlayView.setFacesWithML(faceDataList,
                                    imageProxy.getWidth(),
                                    imageProxy.getHeight());
                        });
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the anti-spoofing detector
        if (antiSpoofingDetector != null) {
            antiSpoofingDetector.close();
        }
    }

    // Data class to hold face info with authenticity
    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public float confidence;
        public String detectionMethod;
        public boolean has3DStructure;

        public FaceData(Rect boundingBox, boolean isReal) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = 75.0f; // Default confidence
            this.detectionMethod = "ML";
            this.has3DStructure = false;
        }

        public FaceData(Rect boundingBox, boolean isReal, float confidence, String detectionMethod, boolean has3DStructure) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
            this.has3DStructure = has3DStructure;
        }
    }
}