package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private FaceOverlayView overlayView;
    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;

    // Performance optimization fields
    private Handler mainHandler;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler cacheHandler;
    private Runnable cacheCheckRunnable;

    // Threading and performance controls
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean shouldProcess = new AtomicBoolean(true);
    private volatile long lastProcessTime = 0;
    private volatile long lastUIUpdateTime = 0;

    // Performance configuration
    private static final long FRAME_PROCESSING_INTERVAL = 100; // Process every 100ms (10 FPS)
    private static final long UI_UPDATE_INTERVAL = 50; // Update UI every 50ms (20 FPS)
    private static final long CACHE_CHECK_INTERVAL = 1000; // Check cache every second

    // Object pools for memory optimization
    private final List<FaceData> reusableFaceDataList = new ArrayList<>();
    private final Object faceDataLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize handlers for threading
        mainHandler = new Handler(Looper.getMainLooper());
        backgroundThread = new HandlerThread("FaceProcessing", Thread.NORM_PRIORITY);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        cacheHandler = new Handler();

        // Initialize views
        initializeViews();

        // Initialize detectors
        initializeDetectors();

        // Start performance monitoring
        startPeriodicCacheCheck();

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);

        // Replace placeholder view with custom overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);
    }

    private void initializeDetectors() {
        // Initialize anti-spoofing detector on background thread
        backgroundHandler.post(() -> {
            antiSpoofingDetector = new AntiSpoofingDetector(this);

            mainHandler.post(() -> {
                faceCountText.setText("AI models loaded - Ready");
            });
        });

        // Configure face detector with optimized settings
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .setMinFaceSize(0.1f) // Slightly larger minimum face size for better performance
                .build();

        detector = FaceDetection.getClient(options);
    }

    // ========== OPTIMIZED CACHE MANAGEMENT ==========

    private void startPeriodicCacheCheck() {
        cacheCheckRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if cache should be cleared due to inactivity
                if (antiSpoofingDetector != null) {
                    antiSpoofingDetector.clearCacheIfStale();
                }

                // Schedule next check only if activity is active
                if (!isFinishing() && !isDestroyed()) {
                    cacheHandler.postDelayed(this, CACHE_CHECK_INTERVAL);
                }
            }
        };

        cacheHandler.post(cacheCheckRunnable);
    }

    private void stopPeriodicCacheCheck() {
        if (cacheCheckRunnable != null) {
            cacheHandler.removeCallbacks(cacheCheckRunnable);
            cacheCheckRunnable = null;
        }
    }

    // ========== OPTIMIZED LIFECYCLE METHODS ==========

    @Override
    protected void onPause() {
        super.onPause();
        shouldProcess.set(false);

        // Clear cache when app is paused
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.forceClearCache());
        }

        // Update UI to show paused state
        mainHandler.post(() -> faceCountText.setText("App paused - processing stopped"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        shouldProcess.set(true);

        // Restart periodic checking when app resumes
        if (cacheCheckRunnable == null) {
            startPeriodicCacheCheck();
        }

        mainHandler.post(() -> faceCountText.setText("App resumed - processing active"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop all processing
        shouldProcess.set(false);
        stopPeriodicCacheCheck();

        // Clean up background thread
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Clean up the anti-spoofing detector on background thread
        if (antiSpoofingDetector != null && backgroundHandler != null) {
            backgroundHandler.post(() -> {
                antiSpoofingDetector.forceClearCache();
                antiSpoofingDetector.close();
            });
        }
    }

    // ========== OPTIMIZED CAMERA METHODS ==========

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, 100);
    }

    private void startCamera() {
        mainHandler.post(() -> faceCountText.setText("Starting camera..."));

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Optimized preview configuration
                Preview preview = new Preview.Builder()
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                // Front camera selector
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Optimized image analysis configuration
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                // Set analyzer with optimized processing
                imageAnalysis.setAnalyzer(backgroundHandler::post, this::processImageOptimized);

                // Camera operations must be on main thread
                mainHandler.post(() -> {
                    try {
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        // Unbind all use cases before rebinding
                        cameraProvider.unbindAll();

                        // Bind use cases to camera
                        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                        faceCountText.setText("Camera ready - AI processing active");
                    } catch (Exception e) {
                        Toast.makeText(this, "Camera binding failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        faceCountText.setText("Camera binding failed");
                    }
                });

            } catch (ExecutionException | InterruptedException e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Camera failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    faceCountText.setText("Camera initialization failed");
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ========== OPTIMIZED IMAGE PROCESSING ==========

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageOptimized(ImageProxy imageProxy) {
        // Skip processing if app is not active or already processing
        if (!shouldProcess.get() || isProcessing.get()) {
            imageProxy.close(); // Close immediately if not processing
            return;
        }

        // Throttle frame processing for performance
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < FRAME_PROCESSING_INTERVAL) {
            imageProxy.close(); // Close immediately if throttling
            return;
        }

        // Set processing flag
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close(); // Close immediately if couldn't set processing flag
            return;
        }

        lastProcessTime = currentTime;

        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        try {
                            processFacesOptimized(faces, imageProxy, currentTime);
                        } finally {
                            // Close ImageProxy only after processing is complete
                            imageProxy.close();
                            isProcessing.set(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        try {
                            handleProcessingFailure();
                        } finally {
                            // Close ImageProxy on failure too
                            imageProxy.close();
                            isProcessing.set(false);
                        }
                    });
        } else {
            imageProxy.close();
            isProcessing.set(false);
        }
    }

    private void processFacesOptimized(List<Face> faces, ImageProxy imageProxy, long currentTime) {
        if (!shouldProcess.get()) return;

        if (faces.isEmpty()) {
            // No faces detected - clear cache and update UI efficiently
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.clearCache();
            }

            updateUIOptimized("No faces detected - cache cleared", false, 0, 0, currentTime);
            clearOverlayOptimized(imageProxy);
        } else {
            // Faces detected - process them efficiently
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.updateLastDetectionTime();

                // Process faces immediately on background thread
                processFacesImmediately(faces, imageProxy, currentTime);
            }
        }
    }

    private void processFacesImmediately(List<Face> faces, ImageProxy imageProxy, long currentTime) {
        // Get or create reusable face data list
        List<FaceData> faceDataList = getReusableFaceDataList(faces.size());

        // Process all faces immediately to avoid ImageProxy being closed
        for (int i = 0; i < faces.size(); i++) {
            final int index = i;
            final Face face = faces.get(i);

            if (!shouldProcess.get()) return;

            try {
                FaceData faceData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());

                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        faceDataList.set(index, faceData);
                    }
                }
            } catch (Exception e) {
                // Handle individual face processing errors gracefully
                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        faceDataList.set(index, new FaceData(face.getBoundingBox(), false, 50.0f, "ProcessingError", false));
                    }
                }
            }
        }

        // Update UI after processing all faces
        updateUIWithFaceData(faceDataList, imageProxy, currentTime);
    }

    private List<FaceData> getReusableFaceDataList(int size) {
        synchronized (faceDataLock) {
            reusableFaceDataList.clear();
            for (int i = 0; i < size; i++) {
                reusableFaceDataList.add(null);
            }
            return reusableFaceDataList;
        }
    }

    private void updateUIWithFaceData(List<FaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
        // Count real vs fake faces efficiently
        int realFaces = 0, fakeFaces = 0;

        synchronized (faceDataLock) {
            for (FaceData data : faceDataList) {
                if (data != null) {
                    if (data.isReal) realFaces++;
                    else fakeFaces++;
                }
            }
        }

        // Update UI on main thread with throttling
        updateUIOptimized(null, true, realFaces, fakeFaces, currentTime);

        // Update overlay efficiently
        updateOverlayOptimized(faceDataList, imageProxy, currentTime);
    }

    private void updateUIOptimized(String message, boolean hasFaces, int realFaces, int fakeFaces, long currentTime) {
        // Throttle UI updates for better performance
        if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) {
            return;
        }

        lastUIUpdateTime = currentTime;

        mainHandler.post(() -> {
            if (!shouldProcess.get()) return;

            if (message != null) {
                faceCountText.setText(message);
                spoofWarningText.setVisibility(View.GONE);
            } else if (hasFaces) {
                // Show face count and cache status efficiently
                String cacheStatus = antiSpoofingDetector != null ?
                        antiSpoofingDetector.getCacheStatus() : "";

                faceCountText.setText(String.format("Real: %d | Fake: %d | %s",
                        realFaces, fakeFaces, cacheStatus));

                // Update warning text efficiently
                if (fakeFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("⚠️ Spoofing detected by AI!");
                } else if (realFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("✓ Real faces detected by AI");
                } else {
                    spoofWarningText.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateOverlayOptimized(List<FaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
        // Update overlay on main thread with efficient copying
        mainHandler.post(() -> {
            if (!shouldProcess.get() || overlayView == null) return;

            List<FaceData> copyList = new ArrayList<>();
            synchronized (faceDataLock) {
                for (FaceData data : faceDataList) {
                    if (data != null) {
                        copyList.add(data);
                    }
                }
            }

            overlayView.setFacesWithMLOptimized(copyList,
                    imageProxy.getWidth(),
                    imageProxy.getHeight());
        });
    }

    private void clearOverlayOptimized(ImageProxy imageProxy) {
        mainHandler.post(() -> {
            if (overlayView != null) {
                overlayView.setFacesWithMLOptimized(new ArrayList<>(),
                        imageProxy.getWidth(),
                        imageProxy.getHeight());
            }
        });
    }

    private void handleProcessingFailure() {
        // Clear cache and update UI on failure
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.clearCache());
        }

        mainHandler.post(() -> {
            faceCountText.setText("Detection failed - cache cleared");
            spoofWarningText.setVisibility(View.GONE);
        });
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
                faceCountText.setText("Camera permission required");
            }
        }
    }

    // ========== DATA CLASS ==========

    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public float confidence;
        public String detectionMethod;
        public boolean has3DStructure;

        public FaceData(Rect boundingBox, boolean isReal) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = 75.0f;
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