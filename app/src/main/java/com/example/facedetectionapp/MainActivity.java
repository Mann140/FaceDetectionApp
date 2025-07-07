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
import android.widget.Button;
import android.widget.LinearLayout;
import android.util.Log;

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
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI Components
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private TextView embeddingInfoText;
    private FaceOverlayView overlayView;
    private LinearLayout controlsLayout;

    // Detection Components
    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;
    private EmbeddingIntegration embeddingIntegration;

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
    private final List<EmbeddingIntegration.EnhancedFaceData> reusableEnhancedFaceDataList = new ArrayList<>();
    private final Object faceDataLock = new Object();

    // Embedding test mode
    private boolean embeddingTestMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🚀 MainActivity onCreate - Enhanced with Embedding Comparison");

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

        // Initialize embedding comparison
        initializeEmbeddingComparison();

        // Add control buttons
        addControlButtons();

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

        // Create embedding info text view
        embeddingInfoText = new TextView(this);
        embeddingInfoText.setTextColor(getResources().getColor(android.R.color.white));
        embeddingInfoText.setTextSize(14f);
        embeddingInfoText.setBackground(ContextCompat.getDrawable(this, android.R.color.transparent));
        embeddingInfoText.setPadding(10, 10, 10, 10);
        embeddingInfoText.setText("Embedding Analysis Ready");

        // Replace placeholder view with custom overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);

        // Add embedding info to the main layout
        LinearLayout mainLayout = (LinearLayout) parent;
        mainLayout.addView(embeddingInfoText);
    }

    private void initializeDetectors() {
        // Initialize anti-spoofing detector on background thread
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                antiSpoofingDetector = new AntiSpoofingDetector(MainActivity.this);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        faceCountText.setText("AI models loaded - Ready");
                    }
                });
            }
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

    private void initializeEmbeddingComparison() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "🔧 Initializing Embedding Comparison System...");

                try {
                    // FIX: Pass the Context parameter to EmbeddingIntegration constructor
                    embeddingIntegration = new EmbeddingIntegration(MainActivity.this);

                    // Run comprehensive test
                    EmbeddingTestDemo.runComprehensiveTest();

                    // Test sample data comparison
                    testSampleEmbeddings();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embeddingInfoText.setText("✅ Embedding System Ready - 4 Known Faces");
                            Log.d(TAG, "✅ Embedding comparison system initialized successfully");
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to initialize embedding system", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embeddingInfoText.setText("❌ Embedding System Error");
                        }
                    });
                }
            }
        });
    }

    private void addControlButtons() {
        // Create control buttons layout
        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlsLayout.setPadding(16, 8, 16, 8);

        // Test Embeddings Button
        Button testEmbeddingButton = new Button(this);
        testEmbeddingButton.setText("Test Embeddings");
        testEmbeddingButton.setTextSize(12f);
        testEmbeddingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testEmbeddingComparison();
            }
        });

        // Compare Sample Button
        Button compareSampleButton = new Button(this);
        compareSampleButton.setText("Compare Samples");
        compareSampleButton.setTextSize(12f);
        compareSampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compareSamplePeople("Mrunal Patil", "Shivam Bind");
            }
        });

        // Toggle Embedding Mode Button
        Button toggleModeButton = new Button(this);
        toggleModeButton.setText("Toggle Mode");
        toggleModeButton.setTextSize(12f);
        toggleModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEmbeddingTestMode();
            }
        });

        // Add buttons to layout
        controlsLayout.addView(testEmbeddingButton);
        controlsLayout.addView(compareSampleButton);
        controlsLayout.addView(toggleModeButton);

        // Add controls to main layout
        ViewGroup mainLayout = findViewById(android.R.id.content);
        if (mainLayout instanceof ViewGroup) {
            ((ViewGroup) mainLayout).addView(controlsLayout);
        }
    }

    // ========== EMBEDDING TEST METHODS ==========

    private void testSampleEmbeddings() {
        Log.d(TAG, "🧪 Testing Sample Embeddings");

        Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();

        // Test all pairwise comparisons
        String[] names = samples.keySet().toArray(new String[0]);
        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                float[] emb1 = samples.get(names[i]);
                float[] emb2 = samples.get(names[j]);

                float similarity = EmbeddingComparator.cosineSimilarity(emb1, emb2);
                boolean samePersonDetected = EmbeddingComparator.areSamePerson(emb1, emb2);

                Log.d(TAG, String.format("📊 %s vs %s: %.4f similarity (%s)",
                        names[i], names[j], similarity, samePersonDetected ? "MATCH" : "NO MATCH"));
            }
        }
    }

    private void testEmbeddingComparison() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "🚀 Running Embedding Comparison Test");

                // Run quick test
                EmbeddingTestDemo.runQuickTest();

                // Test integration
                if (embeddingIntegration != null) {
                    embeddingIntegration.testEmbeddingComparison();
                }

                // Update UI with test results
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        embeddingInfoText.setText("✅ Embedding Test Complete - Check Logs");
                        faceCountText.setText("Embedding test completed - Check logcat");
                    }
                });
            }
        });
    }

    private void compareSamplePeople(String person1, String person2) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
                float[] embedding1 = samples.get(person1);
                float[] embedding2 = samples.get(person2);

                if (embedding1 != null && embedding2 != null) {
                    float similarity = EmbeddingComparator.cosineSimilarity(embedding1, embedding2);
                    float distance = EmbeddingComparator.l2Distance(embedding1, embedding2);
                    boolean samePersonDetected = EmbeddingComparator.areSamePerson(embedding1, embedding2);

                    EmbeddingComparator.ComparisonResult result =
                            embeddingIntegration.compareFaces(embedding1, embedding2);

                    Log.d(TAG, String.format(
                            "🔍 Comparison: %s vs %s\n" +
                                    "  Cosine Similarity: %.4f\n" +
                                    "  L2 Distance: %.4f\n" +
                                    "  Same Person: %s",
                            person1, person2, similarity, distance,
                            samePersonDetected ? "YES" : "NO"
                    ));

                    // Update UI with results
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String message = String.format("%s vs %s: %.1f%% similar (%s)",
                                    person1, person2, similarity * 100,
                                    samePersonDetected ? "MATCH" : "NO MATCH");
                            faceCountText.setText(message);
                            embeddingInfoText.setText(String.format("Cosine: %.4f | L2: %.4f", similarity, distance));
                        }
                    });
                } else {
                    Log.w(TAG, "One or both persons not found in sample data");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            faceCountText.setText("Sample data not found");
                        }
                    });
                }
            }
        });
    }

    private void toggleEmbeddingTestMode() {
        embeddingTestMode = !embeddingTestMode;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (embeddingTestMode) {
                    embeddingInfoText.setText("🧪 Embedding Test Mode ON");
                    faceCountText.setText("Test mode: Will show embedding comparisons");
                } else {
                    embeddingInfoText.setText("📹 Live Detection Mode ON");
                    faceCountText.setText("Live mode: Normal face detection");
                }
            }
        });

        Log.d(TAG, "Embedding test mode: " + (embeddingTestMode ? "ON" : "OFF"));
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

    // ========== ENHANCED LIFECYCLE METHODS ==========

    @Override
    protected void onPause() {
        super.onPause();
        shouldProcess.set(false);

        // Clear cache when app is paused
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    antiSpoofingDetector.forceClearCache();
                }
            });
        }

        // Update UI to show paused state
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                faceCountText.setText("App paused - processing stopped");
                embeddingInfoText.setText("⏸️ Paused");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        shouldProcess.set(true);

        // Restart periodic checking when app resumes
        if (cacheCheckRunnable == null) {
            startPeriodicCacheCheck();
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                faceCountText.setText("App resumed - processing active");
                embeddingInfoText.setText("▶️ Active - " + (embeddingTestMode ? "Test Mode" : "Live Mode"));
            }
        });
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
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    antiSpoofingDetector.forceClearCache();
                    antiSpoofingDetector.close();
                }
            });
        }

        Log.d(TAG, "🔚 MainActivity destroyed - cleaned up resources");
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
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                faceCountText.setText("Starting camera...");
            }
        });

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
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
                    imageAnalysis.setAnalyzer(backgroundHandler::post, MainActivity.this::processImageOptimized);

                    // Camera operations must be on main thread
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                                // Unbind all use cases before rebinding
                                cameraProvider.unbindAll();

                                // Bind use cases to camera
                                cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, imageAnalysis);

                                faceCountText.setText("Camera ready - AI + Embedding processing active");
                                embeddingInfoText.setText("🎥 Camera Active - " + (embeddingTestMode ? "Test Mode" : "Live Mode"));
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Camera binding failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                faceCountText.setText("Camera binding failed");
                            }
                        }
                    });

                } catch (ExecutionException | InterruptedException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Camera failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            faceCountText.setText("Camera initialization failed");
                        }
                    });
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ========== ENHANCED IMAGE PROCESSING ==========

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
                            // Use enhanced processing with embedding comparison
                            processFacesWithEmbedding(faces, imageProxy, currentTime);
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

    private void processFacesWithEmbedding(List<Face> faces, ImageProxy imageProxy, long currentTime) {
        if (!shouldProcess.get()) return;

        if (faces.isEmpty()) {
            // No faces detected - clear cache and update UI efficiently
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.clearCache();
            }

            updateUIOptimized("No faces detected - cache cleared", false, 0, 0, 0, currentTime);
            clearOverlayOptimized(imageProxy);
        } else {
            // Faces detected - process them with embedding comparison
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.updateLastDetectionTime();

                // Process faces immediately on background thread
                processFacesWithEmbeddingImmediately(faces, imageProxy, currentTime);
            }
        }
    }

    private void processFacesWithEmbeddingImmediately(List<Face> faces, ImageProxy imageProxy, long currentTime) {
        // Get or create reusable enhanced face data list
        List<EmbeddingIntegration.EnhancedFaceData> enhancedFaceDataList = getReusableEnhancedFaceDataList(faces.size());

        // Process all faces immediately to avoid ImageProxy being closed
        for (int i = 0; i < faces.size(); i++) {
            final int index = i;
            final Face face = faces.get(i);

            if (!shouldProcess.get()) return;

            try {
                // Perform enhanced analysis (anti-spoofing + embedding comparison)
                EmbeddingIntegration.EnhancedFaceData enhancedFaceData = null;

                if (embeddingIntegration != null) {
                    enhancedFaceData = embeddingIntegration.analyzeAndCompareFace(
                            imageProxy,
                            face.getBoundingBox(),
                            antiSpoofingDetector
                    );
                } else {
                    // Fallback to basic anti-spoofing if embedding integration not ready
                    FaceData basicFaceData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());
                    enhancedFaceData = new EmbeddingIntegration.EnhancedFaceData(
                            basicFaceData.boundingBox,
                            basicFaceData.isReal,
                            basicFaceData.confidence,
                            basicFaceData.detectionMethod,
                            basicFaceData.has3DStructure,
                            null
                    );
                }

                synchronized (faceDataLock) {
                    if (index < enhancedFaceDataList.size()) {
                        enhancedFaceDataList.set(index, enhancedFaceData);
                    }
                }
            } catch (Exception e) {
                // Handle individual face processing errors gracefully
                Log.e(TAG, "Error processing face " + index, e);
                synchronized (faceDataLock) {
                    if (index < enhancedFaceDataList.size()) {
                        enhancedFaceDataList.set(index, new EmbeddingIntegration.EnhancedFaceData(
                                face.getBoundingBox(),
                                false,
                                50.0f,
                                "ProcessingError",
                                false,
                                null
                        ));
                    }
                }
            }
        }

        // Update UI after processing all faces
        updateUIWithEnhancedFaceData(enhancedFaceDataList, imageProxy, currentTime);
    }

    private List<EmbeddingIntegration.EnhancedFaceData> getReusableEnhancedFaceDataList(int size) {
        synchronized (faceDataLock) {
            reusableEnhancedFaceDataList.clear();
            for (int i = 0; i < size; i++) {
                reusableEnhancedFaceDataList.add(null);
            }
            return reusableEnhancedFaceDataList;
        }
    }

    private void updateUIWithEnhancedFaceData(List<EmbeddingIntegration.EnhancedFaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
        // Count real vs fake faces and recognized faces
        int realFaces = 0, fakeFaces = 0, recognizedFaces = 0;
        Set<String> recognizedNames = new HashSet<>();

        synchronized (faceDataLock) {
            for (EmbeddingIntegration.EnhancedFaceData data : faceDataList) {
                if (data != null) {
                    if (data.isReal) {
                        realFaces++;
                        if (data.isRecognized()) {
                            recognizedFaces++;
                            String name = data.getRecognizedName();
                            if (name != null) {
                                recognizedNames.add(name);
                            }
                        }
                    } else {
                        fakeFaces++;
                    }
                }
            }
        }

        // Update UI on main thread with enhanced information
        updateEnhancedUI(realFaces, fakeFaces, recognizedFaces, recognizedNames, currentTime);

        // Update overlay efficiently
        updateEnhancedOverlay(faceDataList, imageProxy, currentTime);
    }

    private void updateUIOptimized(String message, boolean hasFaces, int realFaces, int fakeFaces, int recognizedFaces, long currentTime) {
        // Throttle UI updates for better performance
        if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) {
            return;
        }

        lastUIUpdateTime = currentTime;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!shouldProcess.get()) return;

                if (message != null) {
                    faceCountText.setText(message);
                    spoofWarningText.setVisibility(View.GONE);
                    embeddingInfoText.setText("No faces - " + (embeddingTestMode ? "Test Mode" : "Live Mode"));
                }
            }
        });
    }

    private void updateEnhancedUI(int realFaces, int fakeFaces, int recognizedFaces,
                                  Set<String> recognizedNames, long currentTime) {
        // Throttle UI updates for better performance
        if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) {
            return;
        }

        lastUIUpdateTime = currentTime;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!shouldProcess.get()) return;

                // Show enhanced face count and recognition info
                String cacheStatus = antiSpoofingDetector != null ?
                        antiSpoofingDetector.getCacheStatus() : "";

                String recognitionInfo = "";
                if (!recognizedNames.isEmpty()) {
                    StringBuilder sb = new StringBuilder(" | Recognized: ");
                    boolean first = true;
                    for (String name : recognizedNames) {
                        if (!first) sb.append(", ");
                        sb.append(name);
                        first = false;
                    }
                    recognitionInfo = sb.toString();
                }

                faceCountText.setText(String.format("Real: %d | Fake: %d | Known: %d%s | %s",
                        realFaces, fakeFaces, recognizedFaces, recognitionInfo, cacheStatus));

                // Update embedding info
                String modeText = embeddingTestMode ? "🧪 Test" : "📹 Live";
                if (recognizedFaces > 0) {
                    embeddingInfoText.setText(String.format("%s | ✅ %d Recognized", modeText, recognizedFaces));
                } else if (realFaces > 0) {
                    embeddingInfoText.setText(String.format("%s | ❓ %d Unknown Real", modeText, realFaces));
                } else {
                    embeddingInfoText.setText(String.format("%s | Ready", modeText));
                }

                // Update warning text with recognition info
                if (fakeFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("⚠️ Spoofing detected by AI!");
                } else if (recognizedFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    StringBuilder sb = new StringBuilder("✅ Recognized: ");
                    boolean first = true;
                    for (String name : recognizedNames) {
                        if (!first) sb.append(", ");
                        sb.append(name);
                        first = false;
                    }
                    spoofWarningText.setText(sb.toString());
                } else if (realFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("✓ Real faces detected (unknown persons)");
                } else {
                    spoofWarningText.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateEnhancedOverlay(List<EmbeddingIntegration.EnhancedFaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!shouldProcess.get() || overlayView == null) return;

                // Convert enhanced face data to regular face data for overlay compatibility
                List<FaceData> regularFaceDataList = new ArrayList<>();
                synchronized (faceDataLock) {
                    for (EmbeddingIntegration.EnhancedFaceData enhancedData : faceDataList) {
                        if (enhancedData != null) {
                            String displayMethod = enhancedData.detectionMethod;
                            if (enhancedData.isRecognized()) {
                                displayMethod = enhancedData.detectionMethod + "-" + enhancedData.getRecognizedName();
                            }

                            regularFaceDataList.add(new FaceData(
                                    enhancedData.boundingBox,
                                    enhancedData.isReal,
                                    enhancedData.confidence,
                                    displayMethod,
                                    enhancedData.has3DStructure
                            ));
                        }
                    }
                }

                overlayView.setFacesWithMLOptimized(regularFaceDataList,
                        imageProxy.getWidth(),
                        imageProxy.getHeight());
            }
        });
    }

    private void clearOverlayOptimized(ImageProxy imageProxy) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (overlayView != null) {
                    overlayView.setFacesWithMLOptimized(new ArrayList<>(),
                            imageProxy.getWidth(),
                            imageProxy.getHeight());
                }
            }
        });
    }

    private void handleProcessingFailure() {
        // Clear cache and update UI on failure
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    antiSpoofingDetector.clearCache();
                }
            });
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                faceCountText.setText("Detection failed - cache cleared");
                spoofWarningText.setVisibility(View.GONE);
                embeddingInfoText.setText("❌ Processing Error");
            }
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
                embeddingInfoText.setText("❌ Permission Denied");
            }
        }
    }

    // ========== PUBLIC UTILITY METHODS ==========

    /**
     * Add a new known face to the embedding database
     */
    public void addKnownFaceFromCamera(String personName) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Would add new known face: " + personName);
                // In a real implementation, you would:
                // 1. Get the current face detection
                // 2. Extract the face bitmap
                // 3. Generate embedding using FaceNet
                // 4. Add to known faces database

                if (embeddingIntegration != null) {
                    // Example usage (would need actual embedding):
                    // embeddingIntegration.addKnownFace(personName, currentEmbedding);
                    Log.d(TAG, "Embedding integration ready for adding: " + personName);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embeddingInfoText.setText("Would add: " + personName);
                        }
                    });
                }
            }
        });
    }

    /**
     * Get detailed statistics about a person's embedding
     */
    public void getEmbeddingStats(String personName) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Map<String, float[]> samples = EmbeddingComparator.getSampleEmbeddings();
                float[] embedding = samples.get(personName);

                if (embedding != null) {
                    EmbeddingComparator.EmbeddingStats stats =
                            EmbeddingComparator.calculateEmbeddingStats(embedding);

                    Log.d(TAG, String.format("Stats for %s: %s", personName, stats.toString()));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            embeddingInfoText.setText(String.format("%s: mag=%.2f, std=%.2f",
                                    personName, stats.magnitude, stats.standardDeviation));
                        }
                    });
                } else {
                    Log.w(TAG, "Person not found in sample data: " + personName);
                }
            }
        });
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