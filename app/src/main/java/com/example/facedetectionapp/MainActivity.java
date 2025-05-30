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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private FaceOverlayView overlayView;
    private FaceDetector detector;

    // Anti-spoofing tracking
    private Map<Integer, FaceTracker> faceTrackers = new HashMap<>();
    private long lastFrameTime = 0;

    // Inner class to track face movements
    private static class FaceTracker {
        Rect lastPosition;
        float lastSmileProb = -1;
        float lastLeftEyeProb = -1;
        float lastRightEyeProb = -1;
        float lastHeadRotationY = 0;
        float lastHeadRotationZ = 0;
        int movementCount = 0;
        int blinkCount = 0;
        long firstSeen = System.currentTimeMillis();
        boolean isLikelyReal = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);

        // Configure face detector with all classifications enabled
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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

            long currentTime = System.currentTimeMillis();
            float frameInterval = (currentTime - lastFrameTime) / 1000f; // seconds
            lastFrameTime = currentTime;

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        // Create list of face data with spoofing detection
                        List<FaceData> faceDataList = new ArrayList<>();
                        List<Integer> currentFaceIds = new ArrayList<>();

                        for (Face face : faces) {
                            int faceId = face.getTrackingId() != null ? face.getTrackingId() : -1;
                            currentFaceIds.add(faceId);

                            // Get or create tracker for this face
                            FaceTracker tracker = faceTrackers.computeIfAbsent(faceId,
                                    k -> new FaceTracker());

                            // Analyze face for anti-spoofing
                            boolean isLikelyReal = analyzeFaceAuthenticity(face, tracker, frameInterval);
                            tracker.isLikelyReal = isLikelyReal;

                            FaceData faceData = new FaceData(face.getBoundingBox(), isLikelyReal);
                            faceDataList.add(faceData);
                        }

                        // Clean up old face trackers
                        faceTrackers.keySet().retainAll(currentFaceIds);

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
                                spoofWarningText.setText("⚠️ Possible spoofing detected!");
                            } else if (finalRealFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("✓ Real faces detected");
                            } else {
                                spoofWarningText.setVisibility(View.GONE);
                            }

                            // Update overlay with face data
                            overlayView.setFacesWithAuth(faceDataList,
                                    imageProxy.getWidth(),
                                    imageProxy.getHeight());
                        });
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private boolean analyzeFaceAuthenticity(Face face, FaceTracker tracker, float frameInterval) {
        // Get face characteristics
        Float smileProb = face.getSmilingProbability();
        Float leftEyeProb = face.getLeftEyeOpenProbability();
        Float rightEyeProb = face.getRightEyeOpenProbability();
        float headRotationY = face.getHeadEulerAngleY();
        float headRotationZ = face.getHeadEulerAngleZ();
        Rect currentPosition = face.getBoundingBox();

        // Initialize authenticity score
        float authenticityScore = 20f;

        // 1. Check for eye blinking (strong indicator of real face)
        if (leftEyeProb != null && rightEyeProb != null &&
                tracker.lastLeftEyeProb > 0 && tracker.lastRightEyeProb > 0) {

            float leftEyeChange = Math.abs(leftEyeProb - tracker.lastLeftEyeProb);
            float rightEyeChange = Math.abs(rightEyeProb - tracker.lastRightEyeProb);

            // Detect blink (eye probability drops significantly)
            if ((leftEyeChange > 0.4f || rightEyeChange > 0.4f) &&
                    (leftEyeProb < 0.3f || rightEyeProb < 0.3f)) {
                tracker.blinkCount++;
                authenticityScore += 30f;
            }

            // Natural eye movement
            if (leftEyeChange > 0.1f || rightEyeChange > 0.1f) {
                authenticityScore += 5f;
            }
        }

        // 2. Check for facial expression changes
        if (smileProb != null && tracker.lastSmileProb > 0) {
            float smileChange = Math.abs(smileProb - tracker.lastSmileProb);
            if (smileChange > 0.2f) {
                authenticityScore += 15f;
            }
        }

        // 3. Check for head movement
        float headRotationYChange = Math.abs(headRotationY - tracker.lastHeadRotationY);
        float headRotationZChange = Math.abs(headRotationZ - tracker.lastHeadRotationZ);

        if (headRotationYChange > 5f || headRotationZChange > 5f) {
            authenticityScore += 20f;
            tracker.movementCount++;
        }

        // 4. Check for position movement
        if (tracker.lastPosition != null) {
            float dx = Math.abs(currentPosition.centerX() - tracker.lastPosition.centerX());
            float dy = Math.abs(currentPosition.centerY() - tracker.lastPosition.centerY());
            float movement = (float) Math.sqrt(dx * dx + dy * dy);

            if (movement > 10f) {
                authenticityScore += 10f;
                tracker.movementCount++;
            }
        }

        // 5. Time-based scoring (real faces show variation over time)
        long timeVisible = System.currentTimeMillis() - tracker.firstSeen;
        if (timeVisible > 2000) { // Face visible for more than 2 seconds
            if (tracker.blinkCount > 0) authenticityScore += 20f;
            if (tracker.movementCount > 5) authenticityScore += 10f;
        }

        // 6. Check for unnatural characteristics (possible photo)
        if (leftEyeProb != null && rightEyeProb != null) {
            // Both eyes constantly wide open (common in photos)
            if (leftEyeProb > 0.95f && rightEyeProb > 0.95f &&
                    tracker.lastLeftEyeProb > 0.95f && tracker.lastRightEyeProb > 0.95f) {
                authenticityScore -= 20f;
            }
        }

        // Update tracker
        tracker.lastSmileProb = smileProb != null ? smileProb : -1;
        tracker.lastLeftEyeProb = leftEyeProb != null ? leftEyeProb : -1;
        tracker.lastRightEyeProb = rightEyeProb != null ? rightEyeProb : -1;
        tracker.lastHeadRotationY = headRotationY;
        tracker.lastHeadRotationZ = headRotationZ;
        tracker.lastPosition = currentPosition;

        // Determine if face is likely real (threshold-based)
        return authenticityScore > 20f;
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

    // Data class to hold face info with authenticity
    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;

        public FaceData(Rect boundingBox, boolean isReal) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
        }
    }
}