package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
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

    // Cache management fields
    private Handler cacheHandler = new Handler();
    private Runnable cacheCheckRunnable;
    private static final long CACHE_CHECK_INTERVAL = 1000; // Check every second

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

        // Start periodic cache checking
        startPeriodicCacheCheck();

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    // ========== CACHE MANAGEMENT METHODS ==========

    private void startPeriodicCacheCheck() {
        cacheCheckRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if cache should be cleared due to inactivity
                if (antiSpoofingDetector != null) {
                    antiSpoofingDetector.clearCacheIfStale();
                }

                // Schedule next check
                cacheHandler.postDelayed(this, CACHE_CHECK_INTERVAL);
            }
        };

        // Start the periodic check
        cacheHandler.post(cacheCheckRunnable);
    }

    private void stopPeriodicCacheCheck() {
        if (cacheCheckRunnable != null) {
            cacheHandler.removeCallbacks(cacheCheckRunnable);
            cacheCheckRunnable = null;
        }
    }

    // ========== LIFECYCLE METHODS ==========

    @Override
    protected void onPause() {
        super.onPause();
        // Clear cache when app is paused
        if (antiSpoofingDetector != null) {
            antiSpoofingDetector.forceClearCache();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart periodic checking when app resumes
        if (cacheCheckRunnable == null) {
            startPeriodicCacheCheck();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop periodic cache checking
        stopPeriodicCacheCheck();

        // Clean up the anti-spoofing detector
        if (antiSpoofingDetector != null) {
            antiSpoofingDetector.forceClearCache(); // Force clear on destroy
            antiSpoofingDetector.close();
        }
    }

    // ========== CAMERA METHODS ==========

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

    // ========== IMAGE PROCESSING WITH CACHE MANAGEMENT ==========

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        // Check if any faces are detected
                        if (faces.isEmpty()) {
                            // No faces detected - clear the anti-spoofing cache
                            if (antiSpoofingDetector != null) {
                                antiSpoofingDetector.clearCache();
                            }

                            runOnUiThread(() -> {
                                faceCountText.setText("No faces detected - cache cleared");
                                spoofWarningText.setVisibility(View.GONE);

                                // Clear the overlay
                                overlayView.setFacesWithML(new ArrayList<>(),
                                        imageProxy.getWidth(),
                                        imageProxy.getHeight());
                            });
                        } else {
                            // Faces detected - update detection time and process normally
                            if (antiSpoofingDetector != null) {
                                antiSpoofingDetector.updateLastDetectionTime();
                            }

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
                                // Show face count and cache status
                                String cacheStatus = antiSpoofingDetector != null ?
                                        antiSpoofingDetector.getCacheStatus() : "";

                                faceCountText.setText("Real: " + finalRealFaces +
                                        " | Fake: " + finalFakeFaces +
                                        " | " + cacheStatus);

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
                        }
                    })
                    .addOnFailureListener(e -> {
                        // On failure, also clear cache to prevent stale data
                        if (antiSpoofingDetector != null) {
                            antiSpoofingDetector.clearCache();
                        }

                        runOnUiThread(() -> {
                            faceCountText.setText("Detection failed - cache cleared");
                            spoofWarningText.setVisibility(View.GONE);
                        });
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    // ========== PERMISSION HANDLING ==========

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

    // ========== DATA CLASS ==========

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