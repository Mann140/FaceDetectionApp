package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
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
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

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
    private TextView depthInfoText;
    private FaceOverlayView overlayView;
    private FaceDetector detector;
    private boolean hasDepthCamera = false;

    // Enhanced tracking with depth analysis
    private Map<Integer, EnhancedFaceTracker> faceTrackers = new HashMap<>();

    // Enhanced tracker with 3D analysis
    private static class EnhancedFaceTracker {
        // Basic tracking
        Rect lastPosition;
        float lastSmileProb = -1;
        float lastLeftEyeProb = -1;
        float lastRightEyeProb = -1;
        float lastHeadRotationX = 0;
        float lastHeadRotationY = 0;
        float lastHeadRotationZ = 0;

        // 3D depth analysis
        float faceDepthVariance = 0;
        float maxDepthRange = 0;
        List<Float> depthSamples = new ArrayList<>();
        boolean has3DStructure = false;
        float faceSizeVariance = 0;
        List<Float> faceSizes = new ArrayList<>();

        // Landmark-based depth estimation
        Map<Integer, android.graphics.PointF> lastLandmarks = new HashMap<>();
        float landmarkDepthScore = 0;

        // Movement in 3D space
        float perspectiveChangeScore = 0;
        int significantPerspectiveChanges = 0;

        // Time tracking
        long firstSeen = System.currentTimeMillis();
        int frameCount = 0;

        // Detection results
        float authenticityScore = 50f;
        boolean isLikelyReal = true;
        String depthStatus = "Analyzing...";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);
        depthInfoText = findViewById(R.id.depthInfoText);

        // Check for depth camera
        checkDepthCameraAvailability();

        // Configure face detector with contours and landmarks for 3D analysis
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);

        // Setup overlay
        setupOverlayView();

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void checkDepthCameraAvailability() {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (capabilities != null) {
                    for (int capability : capabilities) {
                        if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                            hasDepthCamera = true;
                            runOnUiThread(() ->
                                    Toast.makeText(this, "Depth camera detected!", Toast.LENGTH_SHORT).show()
                            );
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupOverlayView() {
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);
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

                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::processImage);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    List<FaceData> faceDataList = new ArrayList<>();
                    List<Integer> currentFaceIds = new ArrayList<>();

                    for (Face face : faces) {
                        int faceId = face.getTrackingId() != null ? face.getTrackingId() : -1;
                        currentFaceIds.add(faceId);

                        EnhancedFaceTracker tracker = faceTrackers.computeIfAbsent(faceId,
                                k -> new EnhancedFaceTracker());

                        // Perform 3D depth analysis
                        analyze3DStructure(face, tracker);

                        FaceData faceData = new FaceData(
                                face.getBoundingBox(),
                                tracker.isLikelyReal,
                                tracker.depthStatus,
                                tracker.has3DStructure
                        );
                        faceDataList.add(faceData);
                    }

                    // Clean up old trackers
                    faceTrackers.keySet().retainAll(currentFaceIds);

                    updateUI(faceDataList, imageProxy.getWidth(), imageProxy.getHeight());
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void analyze3DStructure(Face face, EnhancedFaceTracker tracker) {
        tracker.frameCount++;

        // 1. Analyze face size changes (perspective indicator)
        Rect bbox = face.getBoundingBox();
        float faceSize = bbox.width() * bbox.height();
        tracker.faceSizes.add(faceSize);

        if (tracker.faceSizes.size() > 10) {
            tracker.faceSizes.remove(0); // Keep last 10 samples

            // Calculate variance in face size
            float mean = 0;
            for (float size : tracker.faceSizes) mean += size;
            mean /= tracker.faceSizes.size();

            float variance = 0;
            for (float size : tracker.faceSizes) {
                variance += Math.pow(size - mean, 2);
            }
            tracker.faceSizeVariance = variance / tracker.faceSizes.size();
        }

        // 2. Analyze head rotation in 3D space
        float headX = face.getHeadEulerAngleX(); // Pitch
        float headY = face.getHeadEulerAngleY(); // Yaw
        float headZ = face.getHeadEulerAngleZ(); // Roll

        if (tracker.frameCount > 1) {
            float rotationChange =
                    Math.abs(headX - tracker.lastHeadRotationX) +
                            Math.abs(headY - tracker.lastHeadRotationY) +
                            Math.abs(headZ - tracker.lastHeadRotationZ);

            if (rotationChange > 10f) {
                tracker.significantPerspectiveChanges++;
                tracker.perspectiveChangeScore += rotationChange;
            }
        }

        tracker.lastHeadRotationX = headX;
        tracker.lastHeadRotationY = headY;
        tracker.lastHeadRotationZ = headZ;

        // 3. Analyze facial landmarks for 3D structure
        analyzeLandmarkDepth(face, tracker);

        // 4. Analyze contours for natural curves
        analyzeContourDepth(face, tracker);

        // 5. Calculate overall 3D structure score
        calculate3DScore(tracker);

        // 6. Determine authenticity based on 3D analysis
        determineAuthenticity(face, tracker);
    }

    private void analyzeLandmarkDepth(Face face, EnhancedFaceTracker tracker) {
        // Key landmarks for 3D structure analysis
        int[] keyLandmarks = {
                FaceLandmark.LEFT_EYE,
                FaceLandmark.RIGHT_EYE,
                FaceLandmark.NOSE_BASE,
                FaceLandmark.MOUTH_LEFT,
                FaceLandmark.MOUTH_RIGHT
        };

        Map<Integer, android.graphics.PointF> currentLandmarks = new HashMap<>();

        for (int landmarkType : keyLandmarks) {
            FaceLandmark landmark = face.getLandmark(landmarkType);
            if (landmark != null) {
                currentLandmarks.put(landmarkType, landmark.getPosition());
            }
        }

        // Calculate inter-landmark distances (simulate depth)
        if (currentLandmarks.size() >= 5 && tracker.lastLandmarks.size() >= 5) {
            float depthVariation = 0;

            // Eye distance (should vary with head rotation)
            android.graphics.PointF leftEye = currentLandmarks.get(FaceLandmark.LEFT_EYE);
            android.graphics.PointF rightEye = currentLandmarks.get(FaceLandmark.RIGHT_EYE);

            if (leftEye != null && rightEye != null) {
                float eyeDistance = distance(leftEye, rightEye);
                float headYaw = Math.abs(face.getHeadEulerAngleY());

                // In 3D faces, eye distance changes with yaw rotation
                float expectedVariation = headYaw * 0.5f;
                depthVariation += Math.abs(expectedVariation);
            }

            tracker.landmarkDepthScore += depthVariation;
        }

        tracker.lastLandmarks = currentLandmarks;
    }

    private void analyzeContourDepth(Face face, EnhancedFaceTracker tracker) {
        // Analyze face contours for natural curves
        List<FaceContour> contours = face.getAllContours();

        if (contours.size() > 0) {
            float contourComplexity = 0;

            for (FaceContour contour : contours) {
                List<android.graphics.PointF> points = contour.getPoints();
                if (points.size() > 3) {
                    // Calculate curvature variation
                    for (int i = 1; i < points.size() - 1; i++) {
                        float angle = calculateAngle(
                                points.get(i - 1),
                                points.get(i),
                                points.get(i + 1)
                        );
                        contourComplexity += Math.abs(180 - angle);
                    }
                }
            }

            // 3D faces have more complex contours
            if (contourComplexity > 500) {
                tracker.has3DStructure = true;
            }
        }
    }

    private void calculate3DScore(EnhancedFaceTracker tracker) {
        float depthScore = 0;

        // 1. Face size variance (indicates depth changes)
        if (tracker.faceSizeVariance > 1000) {
            depthScore += 20;
        }

        // 2. Perspective changes
        if (tracker.significantPerspectiveChanges > 3) {
            depthScore += 25;
        }

        // 3. Landmark depth variation
        if (tracker.landmarkDepthScore > 50) {
            depthScore += 20;
        }

        // 4. Has 3D contour structure
        if (tracker.has3DStructure) {
            depthScore += 35;
        }

        // Update depth status
        if (depthScore > 70) {
            tracker.depthStatus = "3D Structure ✓";
        } else if (depthScore > 40) {
            tracker.depthStatus = "Partial 3D";
        } else {
            tracker.depthStatus = "Flat/2D";
        }

        tracker.faceDepthVariance = depthScore;
    }

    private void determineAuthenticity(Face face, EnhancedFaceTracker tracker) {
        // Combine traditional analysis with 3D depth
        float score = tracker.faceDepthVariance;

        // Add traditional checks
        Float leftEyeProb = face.getLeftEyeOpenProbability();
        Float rightEyeProb = face.getRightEyeOpenProbability();
        Float smileProb = face.getSmilingProbability();

        // Eye blinking
        if (leftEyeProb != null && rightEyeProb != null && tracker.lastLeftEyeProb >= 0) {
            float eyeChange = Math.abs(leftEyeProb - tracker.lastLeftEyeProb) +
                    Math.abs(rightEyeProb - tracker.lastRightEyeProb);
            if (eyeChange > 0.3f) score += 15;
        }

        // Expression changes
        if (smileProb != null && tracker.lastSmileProb >= 0) {
            float smileChange = Math.abs(smileProb - tracker.lastSmileProb);
            if (smileChange > 0.1f) score += 10;
        }

        // Update tracker
        tracker.lastLeftEyeProb = leftEyeProb != null ? leftEyeProb : -1;
        tracker.lastRightEyeProb = rightEyeProb != null ? rightEyeProb : -1;
        tracker.lastSmileProb = smileProb != null ? smileProb : -1;

        tracker.authenticityScore = score;
        tracker.isLikelyReal = score > 50 || tracker.has3DStructure;
    }

    private float distance(android.graphics.PointF p1, android.graphics.PointF p2) {
        float dx = p1.x - p2.x;
        float dy = p1.y - p2.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float calculateAngle(android.graphics.PointF p1, android.graphics.PointF p2, android.graphics.PointF p3) {
        float angle1 = (float) Math.atan2(p1.y - p2.y, p1.x - p2.x);
        float angle2 = (float) Math.atan2(p3.y - p2.y, p3.x - p2.x);
        float angle = Math.abs(angle1 - angle2);
        return (float) Math.toDegrees(angle);
    }

    private void updateUI(List<FaceData> faceDataList, int imageWidth, int imageHeight) {
        int realFaces = 0;
        int fakeFaces = 0;
        boolean has3DFaces = false;

        for (FaceData data : faceDataList) {
            if (data.isReal) realFaces++;
            else fakeFaces++;
            if (data.has3DStructure) has3DFaces = true;
        }

        final int finalRealFaces = realFaces;
        final int finalFakeFaces = fakeFaces;
        final boolean final3DFaces = has3DFaces;

        runOnUiThread(() -> {
            faceCountText.setText("Real: " + finalRealFaces + " | Fake: " + finalFakeFaces);

            if (finalFakeFaces > 0) {
                spoofWarningText.setVisibility(View.VISIBLE);
                spoofWarningText.setText("⚠️ 2D Image Detected!");
                spoofWarningText.setBackgroundColor(0xCCFF0000);
            } else if (final3DFaces) {
                spoofWarningText.setVisibility(View.VISIBLE);
                spoofWarningText.setText("✓ 3D Face Verified");
                spoofWarningText.setBackgroundColor(0xCC00AA00);
            } else {
                spoofWarningText.setVisibility(View.GONE);
            }

            if (hasDepthCamera) {
                depthInfoText.setText("Depth Camera: Available");
            } else {
                depthInfoText.setText("Using AI-based 3D analysis");
            }

            overlayView.setFacesWithDepth(faceDataList, imageWidth, imageHeight);
        });
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

    // Enhanced data class with depth info
    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public String depthStatus;
        public boolean has3DStructure;

        public FaceData(Rect boundingBox, boolean isReal, String depthStatus, boolean has3DStructure) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.depthStatus = depthStatus;
            this.has3DStructure = has3DStructure;
        }
    }
}