package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
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
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI Components
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private TextView recognitionInfoText;
    private Button addPersonButton;
    private Button manageDatabaseButton;
    private FaceOverlayView overlayView;

    // Detection components
    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceNetModel faceNetModel;
    private FaceRecognitionManager recognitionManager;

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

    // Face recognition configuration
    private static final boolean USE_GPU = false; // Disabled for compatibility
    private static final boolean USE_XNNPACK = true;

    // Performance tracking
    private final AtomicLong totalFramesProcessed = new AtomicLong(0);
    private final AtomicLong totalFacesDetected = new AtomicLong(0);
    private final AtomicLong totalRecognitionsAttempted = new AtomicLong(0);
    private volatile long appStartTime = 0;

    // Object pools for memory optimization
    private final List<FaceData> reusableFaceDataList = new ArrayList<>();
    private final Object faceDataLock = new Object();

    // Face capture for adding to database
    private volatile boolean isCapturingForDatabase = false;
    private volatile String personNameToAdd = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appStartTime = System.currentTimeMillis();
        Log.i(TAG, "🚀 ========== APP STARTUP ==========");
        Log.i(TAG, "📱 Starting FaceDetectionApp with comprehensive logging");
        Log.i(TAG, "🔧 Configuration: GPU=" + USE_GPU + ", XNNPACK=" + USE_XNNPACK);

        setContentView(R.layout.activity_main);

        // Initialize handlers for threading
        Log.d(TAG, "🔧 Initializing threading system...");
        initializeThreading();

        // Initialize views
        Log.d(TAG, "🎨 Initializing UI components...");
        initializeViews();

        // Initialize detectors on background thread
        Log.d(TAG, "🤖 Starting AI model initialization...");
        initializeDetectors();

        // Start performance monitoring
        Log.d(TAG, "📊 Starting performance monitoring...");
        startPeriodicCacheCheck();

        // Check camera permission
        Log.d(TAG, "📸 Checking camera permissions...");
        if (checkCameraPermission()) {
            Log.i(TAG, "✅ Camera permission granted, starting camera");
            startCamera();
        } else {
            Log.w(TAG, "⚠️ Camera permission not granted, requesting...");
            requestCameraPermission();
        }

        Log.i(TAG, "✅ MainActivity onCreate completed");
    }

    private void initializeThreading() {
        long startTime = System.currentTimeMillis();

        mainHandler = new Handler(Looper.getMainLooper());
        backgroundThread = new HandlerThread("FaceProcessing", Thread.NORM_PRIORITY);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        cacheHandler = new Handler();

        long initTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, String.format("✅ Threading initialized in %dms", initTime));
    }

    private void initializeViews() {
        long startTime = System.currentTimeMillis();

        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);
        recognitionInfoText = findViewById(R.id.recognitionInfoText);
        addPersonButton = findViewById(R.id.addPersonButton);
        manageDatabaseButton = findViewById(R.id.manageDatabaseButton);

        Log.d(TAG, "🎨 UI components found, setting up overlay...");

        // Replace placeholder view with custom overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);

        Log.d(TAG, "✅ Custom overlay created and added");

        // Set up button listeners
        addPersonButton.setOnClickListener(v -> {
            Log.d(TAG, "👤 Add Person button clicked");
            showAddPersonDialog();
        });

        manageDatabaseButton.setOnClickListener(v -> {
            Log.d(TAG, "🗄️ Manage Database button clicked");
            showDatabaseManagementDialog();
        });

        // Initially disable buttons until models are loaded
        addPersonButton.setEnabled(false);
        manageDatabaseButton.setEnabled(false);

        long initTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, String.format("✅ UI initialization completed in %dms", initTime));
    }

    private void initializeDetectors() {
        backgroundHandler.post(() -> {
            long totalStartTime = System.currentTimeMillis();
            Log.i(TAG, "🤖 ========== AI MODEL INITIALIZATION ==========");

            try {
                // Initialize anti-spoofing detector
                Log.d(TAG, "🛡️ Initializing anti-spoofing detector...");
                long antispoofStart = System.currentTimeMillis();
                antiSpoofingDetector = new AntiSpoofingDetector(this);
                long antispoofTime = System.currentTimeMillis() - antispoofStart;
                Log.i(TAG, String.format("✅ Anti-spoofing detector loaded in %dms", antispoofTime));

                // Initialize FaceNet model
                Log.d(TAG, "🧠 Initializing FaceNet model...");
                long facenetStart = System.currentTimeMillis();
                faceNetModel = new FaceNetModel(this, USE_GPU, USE_XNNPACK);
                long facenetTime = System.currentTimeMillis() - facenetStart;

                if (faceNetModel.isReady()) {
                    Log.i(TAG, String.format("✅ FaceNet model loaded in %dms", facenetTime));

                    // Initialize face recognition manager
                    Log.d(TAG, "👥 Initializing face recognition manager...");
                    long recognitionStart = System.currentTimeMillis();
                    recognitionManager = new FaceRecognitionManager(this, faceNetModel);
                    long recognitionTime = System.currentTimeMillis() - recognitionStart;
                    Log.i(TAG, String.format("✅ Face recognition manager loaded in %dms", recognitionTime));
                } else {
                    Log.e(TAG, "❌ FaceNet model failed to load properly");
                }

                long totalTime = System.currentTimeMillis() - totalStartTime;
                Log.i(TAG, String.format("🎉 ALL AI MODELS LOADED in %dms total", totalTime));

                mainHandler.post(() -> {
                    updateUIAfterModelInit();
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ CRITICAL: AI model initialization failed: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    faceCountText.setText("❌ Failed to load AI models: " + e.getMessage());
                    Toast.makeText(this, "Model initialization failed", Toast.LENGTH_LONG).show();
                });
            }
        });

        // Configure face detector with optimized settings
        Log.d(TAG, "👁️ Configuring ML Kit face detector...");
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .setMinFaceSize(0.1f)
                .build();

        detector = FaceDetection.getClient(options);
        Log.d(TAG, "✅ ML Kit face detector configured");
    }

    private void updateUIAfterModelInit() {
        Log.d(TAG, "🎨 Updating UI after model initialization...");

        faceCountText.setText("✅ AI models loaded - Ready for detection and recognition");
        addPersonButton.setEnabled(true);
        manageDatabaseButton.setEnabled(true);

        // Update recognition info
        if (recognitionManager != null) {
            int totalPeople = recognitionManager.getAllPersonNames().size();
            int totalEmbeddings = recognitionManager.getTotalEmbeddings();
            recognitionInfoText.setText(String.format("Database: %d people, %d embeddings",
                    totalPeople, totalEmbeddings));
            Log.i(TAG, String.format("📊 Database status: %d people, %d embeddings",
                    totalPeople, totalEmbeddings));
        }

        Log.d(TAG, "✅ UI update completed");
    }

    // ========== FACE RECOGNITION UI METHODS WITH LOGGING ==========

    private void showAddPersonDialog() {
        Log.d(TAG, "👤 Showing add person dialog...");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Person to Database");

        // Create input field
        EditText input = new EditText(this);
        input.setHint("Enter person's name");
        builder.setView(input);

        builder.setPositiveButton("Capture Face", (dialog, which) -> {
            String name = input.getText().toString().trim();
            Log.d(TAG, "👤 User entered name: '" + name + "'");

            if (name.isEmpty()) {
                Log.w(TAG, "⚠️ Empty name entered");
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                return;
            }

            startFaceCapture(name);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.d(TAG, "👤 Add person dialog cancelled");
        });

        builder.show();
    }

    private void startFaceCapture(String personName) {
        Log.i(TAG, "📸 Starting face capture for: " + personName);

        personNameToAdd = personName;
        isCapturingForDatabase = true;

        // Show capture instructions
        Toast.makeText(this, "Look at the camera to capture your face for: " + personName,
                Toast.LENGTH_LONG).show();

        faceCountText.setText("📸 Capturing face for: " + personName + " - Look at camera...");

        // Auto-cancel capture after 10 seconds
        mainHandler.postDelayed(() -> {
            if (isCapturingForDatabase) {
                Log.w(TAG, "⏰ Face capture timed out for: " + personName);
                cancelFaceCapture();
                Toast.makeText(this, "Face capture timed out", Toast.LENGTH_SHORT).show();
            }
        }, 10000);

        Log.d(TAG, "✅ Face capture session started, 10s timeout set");
    }

    private void cancelFaceCapture() {
        Log.d(TAG, "❌ Cancelling face capture for: " + personNameToAdd);
        isCapturingForDatabase = false;
        personNameToAdd = null;
        faceCountText.setText("Face capture cancelled");
    }

    private void showDatabaseManagementDialog() {
        Log.d(TAG, "🗄️ Opening database management dialog...");

        if (recognitionManager == null) {
            Log.e(TAG, "❌ Recognition manager not ready");
            Toast.makeText(this, "Recognition manager not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> people = recognitionManager.getAllPersonNames();
        Log.d(TAG, String.format("📊 Database contains %d people: %s", people.size(), people.toString()));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Database Management (" + people.size() + " people)");

        if (people.isEmpty()) {
            Log.d(TAG, "📝 Empty database, showing empty state");
            builder.setMessage("No people in database");
            builder.setPositiveButton("OK", null);
        } else {
            // Create list of people with embedding counts
            String[] items = new String[people.size() + 1];
            for (int i = 0; i < people.size(); i++) {
                int embeddingCount = recognitionManager.getPersonEmbeddingCount(people.get(i));
                items[i] = String.format("%s (%d embeddings)", people.get(i), embeddingCount);
            }
            items[people.size()] = "Clear entire database";

            builder.setItems(items, (dialog, which) -> {
                if (which == people.size()) {
                    Log.d(TAG, "🗑️ User selected clear entire database");
                    showClearDatabaseConfirmation();
                } else {
                    String personName = people.get(which);
                    Log.d(TAG, "🗑️ User selected remove person: " + personName);
                    showRemovePersonConfirmation(personName);
                }
            });
        }

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.d(TAG, "🗄️ Database management cancelled");
        });

        builder.show();
    }

    private void showRemovePersonConfirmation(String personName) {
        Log.d(TAG, "🗑️ Showing remove confirmation for: " + personName);

        new AlertDialog.Builder(this)
                .setTitle("Remove Person")
                .setMessage("Remove " + personName + " from database?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    Log.i(TAG, "🗑️ Removing person from database: " + personName);

                    if (recognitionManager.removePerson(personName)) {
                        Log.i(TAG, "✅ Successfully removed: " + personName);
                        Toast.makeText(this, personName + " removed from database", Toast.LENGTH_SHORT).show();
                        updateRecognitionInfo();
                    } else {
                        Log.e(TAG, "❌ Failed to remove: " + personName);
                        Toast.makeText(this, "Failed to remove " + personName, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Log.d(TAG, "🗑️ Remove person cancelled for: " + personName);
                })
                .show();
    }

    private void showClearDatabaseConfirmation() {
        Log.d(TAG, "🗑️ Showing clear database confirmation");

        new AlertDialog.Builder(this)
                .setTitle("Clear Database")
                .setMessage("This will remove ALL people from the database. Continue?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    Log.i(TAG, "🗑️ Clearing entire database...");

                    recognitionManager.clearDatabase();
                    Log.i(TAG, "✅ Database cleared successfully");
                    Toast.makeText(this, "Database cleared", Toast.LENGTH_SHORT).show();
                    updateRecognitionInfo();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Log.d(TAG, "🗑️ Clear database cancelled");
                })
                .show();
    }

    private void updateRecognitionInfo() {
        Log.d(TAG, "📊 Updating recognition info display...");

        if (recognitionManager != null) {
            int totalPeople = recognitionManager.getAllPersonNames().size();
            int totalEmbeddings = recognitionManager.getTotalEmbeddings();
            recognitionInfoText.setText(String.format("Database: %d people, %d embeddings",
                    totalPeople, totalEmbeddings));
            Log.d(TAG, String.format("📊 Updated: %d people, %d embeddings", totalPeople, totalEmbeddings));
        }
    }

    // ========== ENHANCED IMAGE PROCESSING WITH DETAILED LOGGING ==========

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageOptimized(ImageProxy imageProxy) {
        long frameStartTime = System.currentTimeMillis();
        long frameNumber = totalFramesProcessed.incrementAndGet();

        // Skip processing if app is not active or already processing
        if (!shouldProcess.get() || isProcessing.get()) {
            imageProxy.close();
            if (frameNumber % 30 == 0) { // Log every 30th skipped frame
                Log.d(TAG, String.format("⏩ Skipped frame #%d (shouldProcess=%s, isProcessing=%s)",
                        frameNumber, shouldProcess.get(), isProcessing.get()));
            }
            return;
        }

        // Throttle frame processing for performance
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < FRAME_PROCESSING_INTERVAL) {
            imageProxy.close();
            return;
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }

        lastProcessTime = currentTime;

        if (frameNumber % 50 == 0) { // Log every 50th processed frame
            Log.d(TAG, String.format("🖼️ Processing frame #%d (%dx%d)",
                    frameNumber, imageProxy.getWidth(), imageProxy.getHeight()));
        }

        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        try {
                            long detectionTime = System.currentTimeMillis() - frameStartTime;
                            if (faces.size() > 0 || frameNumber % 100 == 0) { // Log when faces found or every 100th frame
                                Log.d(TAG, String.format("👁️ ML Kit detected %d faces in %dms (frame #%d)",
                                        faces.size(), detectionTime, frameNumber));
                            }

                            processFacesWithRecognition(faces, imageProxy, currentTime, frameNumber);
                        } finally {
                            imageProxy.close();
                            isProcessing.set(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        try {
                            Log.e(TAG, String.format("❌ ML Kit face detection failed on frame #%d: %s",
                                    frameNumber, e.getMessage()));
                            handleProcessingFailure();
                        } finally {
                            imageProxy.close();
                            isProcessing.set(false);
                        }
                    });
        } else {
            Log.w(TAG, String.format("⚠️ Frame #%d has null image", frameNumber));
            imageProxy.close();
            isProcessing.set(false);
        }
    }

    private void processFacesWithRecognition(List<Face> faces, ImageProxy imageProxy, long currentTime, long frameNumber) {
        if (!shouldProcess.get()) return;

        if (faces.isEmpty()) {
            // No faces detected - clear cache and update UI efficiently
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.clearCache();
            }
            updateUIOptimized("No faces detected - cache cleared", false, 0, 0, 0, currentTime);
            clearOverlayOptimized(imageProxy);
        } else {
            totalFacesDetected.addAndGet(faces.size());

            if (faces.size() > 0) {
                Log.d(TAG, String.format("👥 Processing %d faces from frame #%d", faces.size(), frameNumber));
            }

            // Faces detected - process them with both anti-spoofing and recognition
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.updateLastDetectionTime();

                // Check if we're capturing a face for database
                if (isCapturingForDatabase && faces.size() == 1) {
                    Log.i(TAG, String.format("📸 Capturing face for database: %s", personNameToAdd));
                    captureFaceForDatabase(faces.get(0), imageProxy);
                }

                processFacesWithBothSystems(faces, imageProxy, currentTime, frameNumber);
            }
        }
    }

    private void captureFaceForDatabase(Face face, ImageProxy imageProxy) {
        if (recognitionManager == null || personNameToAdd == null) return;

        Log.i(TAG, String.format("📸 Starting face capture process for: %s", personNameToAdd));

        backgroundHandler.post(() -> {
            long captureStartTime = System.currentTimeMillis();

            try {
                // Extract face bitmap
                Log.d(TAG, "🖼️ Extracting face bitmap from camera frame...");
                android.graphics.Bitmap faceBitmap = BitmapUtils.extractFaceFromImageProxy(
                        imageProxy, face.getBoundingBox());

                if (faceBitmap != null) {
                    Log.d(TAG, String.format("✅ Face bitmap extracted: %dx%d",
                            faceBitmap.getWidth(), faceBitmap.getHeight()));

                    // Add to database
                    Log.d(TAG, "💾 Adding face to recognition database...");
                    recognitionManager.addPersonAsync(personNameToAdd, faceBitmap)
                            .thenAccept(result -> {
                                long totalCaptureTime = System.currentTimeMillis() - captureStartTime;

                                mainHandler.post(() -> {
                                    if (result.success) {
                                        Log.i(TAG, String.format("✅ Face capture SUCCESS for %s in %dms: %s",
                                                personNameToAdd, totalCaptureTime, result.message));
                                        Toast.makeText(this, "Face captured: " + result.message,
                                                Toast.LENGTH_SHORT).show();
                                        updateRecognitionInfo();
                                    } else {
                                        Log.e(TAG, String.format("❌ Face capture FAILED for %s: %s",
                                                personNameToAdd, result.message));
                                        Toast.makeText(this, "Capture failed: " + result.message,
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    // Reset capture state
                                    isCapturingForDatabase = false;
                                    personNameToAdd = null;
                                });
                            });
                } else {
                    Log.e(TAG, "❌ Failed to extract face bitmap");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Failed to extract face from camera", Toast.LENGTH_SHORT).show();
                        isCapturingForDatabase = false;
                        personNameToAdd = null;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, String.format("❌ Face capture error for %s: %s", personNameToAdd, e.getMessage()), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error capturing face: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    isCapturingForDatabase = false;
                    personNameToAdd = null;
                });
            }
        });
    }

    private void processFacesWithBothSystems(List<Face> faces, ImageProxy imageProxy, long currentTime, long frameNumber) {
        long processingStartTime = System.currentTimeMillis();
        List<FaceData> faceDataList = getReusableFaceDataList(faces.size());

        Log.d(TAG, String.format("🔄 Starting dual analysis for %d faces (frame #%d)", faces.size(), frameNumber));

        // Process each face with both anti-spoofing and recognition
        for (int i = 0; i < faces.size(); i++) {
            final int index = i;
            final Face face = faces.get(i);

            if (!shouldProcess.get()) return;

            try {
                long faceStartTime = System.currentTimeMillis();

                // Anti-spoofing analysis
                Log.d(TAG, String.format("🛡️ Analyzing face %d/%d for spoofing...", i+1, faces.size()));
                FaceData antispoofData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());
                long antispoofTime = System.currentTimeMillis() - faceStartTime;

                // Face recognition (only if real face detected)
                String recognizedName = "Unknown";
                float recognitionConfidence = 0f;
                long recognitionTime = 0;

                if (antispoofData.isReal && recognitionManager != null && recognitionManager.isDatabaseReady()) {
                    long recogStartTime = System.currentTimeMillis();
                    totalRecognitionsAttempted.incrementAndGet();

                    Log.d(TAG, String.format("👤 Attempting recognition for face %d/%d...", i+1, faces.size()));
                    FaceRecognitionManager.RecognitionResult recognitionResult =
                            recognitionManager.recognizeFace(imageProxy, face.getBoundingBox());

                    recognitionTime = System.currentTimeMillis() - recogStartTime;

                    if (recognitionResult.isRecognized()) {
                        recognizedName = recognitionResult.personName;
                        recognitionConfidence = recognitionResult.confidence;
                        Log.i(TAG, String.format("✅ RECOGNIZED: %s (%.1f%% confidence) in %dms",
                                recognizedName, recognitionConfidence, recognitionTime));
                    } else {
                        Log.d(TAG, String.format("❓ Unknown person detected in %dms", recognitionTime));
                    }
                }

                // Create enhanced FaceData with recognition info
                FaceData enhancedData = new FaceData(
                        face.getBoundingBox(),
                        antispoofData.isReal,
                        antispoofData.confidence,
                        antispoofData.detectionMethod,
                        antispoofData.has3DStructure
                );
                enhancedData.recognizedName = recognizedName;
                enhancedData.recognitionConfidence = recognitionConfidence;

                long totalFaceTime = System.currentTimeMillis() - faceStartTime;
                Log.d(TAG, String.format("⏱️ Face %d analysis: antispoof=%dms, recognition=%dms, total=%dms",
                        i+1, antispoofTime, recognitionTime, totalFaceTime));

                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        faceDataList.set(index, enhancedData);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, String.format("❌ Error processing face %d/%d: %s", i+1, faces.size(), e.getMessage()), e);
                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        FaceData errorData = new FaceData(face.getBoundingBox(), false, 50.0f, "ProcessingError", false);
                        errorData.recognizedName = "Error";
                        faceDataList.set(index, errorData);
                    }
                }
            }
        }

        long totalProcessingTime = System.currentTimeMillis() - processingStartTime;
        Log.d(TAG, String.format("🏁 Completed processing %d faces in %dms (frame #%d)",
                faces.size(), totalProcessingTime, frameNumber));

        // Update UI with both anti-spoofing and recognition results
        updateUIWithBothSystems(faceDataList, imageProxy, currentTime);
    }

    private void updateUIWithBothSystems(List<FaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
        int realFaces = 0, fakeFaces = 0, recognizedFaces = 0;

        synchronized (faceDataLock) {
            for (FaceData data : faceDataList) {
                if (data != null) {
                    if (data.isReal) {
                        realFaces++;
                        if (data.recognizedName != null && !data.recognizedName.equals("Unknown")) {
                            recognizedFaces++;
                        }
                    } else {
                        fakeFaces++;
                    }
                }
            }
        }

        if (realFaces > 0 || fakeFaces > 0 || recognizedFaces > 0) {
            Log.d(TAG, String.format("📊 Frame results: real=%d, fake=%d, recognized=%d",
                    realFaces, fakeFaces, recognizedFaces));
        }

        updateUIOptimized(null, true, realFaces, fakeFaces, recognizedFaces, currentTime);
        updateOverlayOptimized(faceDataList, imageProxy, currentTime);
    }

    // ========== UPDATED UI METHODS WITH LOGGING ==========

    private void updateUIOptimized(String message, boolean hasFaces, int realFaces, int fakeFaces,
                                   int recognizedFaces, long currentTime) {
        if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) {
            return;
        }

        lastUIUpdateTime = currentTime;

        mainHandler.post(() -> {
            if (!shouldProcess.get()) return;

            if (message != null) {
                Log.d(TAG, "🎨 UI Update: " + message);
                faceCountText.setText(message);
                spoofWarningText.setVisibility(View.GONE);
            } else if (hasFaces) {
                String cacheStatus = antiSpoofingDetector != null ?
                        antiSpoofingDetector.getCacheStatus() : "";

                String statusText = String.format("Real: %d | Fake: %d | Recognized: %d | %s",
                        realFaces, fakeFaces, recognizedFaces, cacheStatus);
                faceCountText.setText(statusText);

                if (fakeFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("⚠️ Spoofing detected by AI!");
                    if (fakeFaces > 0) {
                        Log.w(TAG, String.format("🚨 SPOOF ALERT: %d fake faces detected", fakeFaces));
                    }
                } else if (realFaces > 0) {
                    spoofWarningText.setVisibility(View.VISIBLE);
                    spoofWarningText.setText("✓ Real faces detected by AI");
                } else {
                    spoofWarningText.setVisibility(View.GONE);
                }
            }
        });
    }

    // ========== PERFORMANCE TRACKING METHODS ==========

    private void logPerformanceStats() {
        long uptime = System.currentTimeMillis() - appStartTime;
        long totalFrames = totalFramesProcessed.get();
        long totalFaces = totalFacesDetected.get();
        long totalRecognitions = totalRecognitionsAttempted.get();

        double framesPerSecond = totalFrames > 0 ? (totalFrames * 1000.0) / uptime : 0;
        double facesPerSecond = totalFaces > 0 ? (totalFaces * 1000.0) / uptime : 0;

        Log.i(TAG, "📊 ========== PERFORMANCE STATS ==========");
        Log.i(TAG, String.format("⏱️ Uptime: %d seconds", uptime / 1000));
        Log.i(TAG, String.format("🖼️ Frames processed: %d (%.1f FPS)", totalFrames, framesPerSecond));
        Log.i(TAG, String.format("👥 Faces detected: %d (%.1f faces/sec)", totalFaces, facesPerSecond));
        Log.i(TAG, String.format("🔍 Recognition attempts: %d", totalRecognitions));

        if (recognitionManager != null) {
            Log.i(TAG, String.format("👤 Database: %d people, %d embeddings",
                    recognitionManager.getAllPersonNames().size(),
                    recognitionManager.getTotalEmbeddings()));
        }
        Log.i(TAG, "========================================");
    }

    // ========== CACHE MANAGEMENT WITH LOGGING ==========

    private void startPeriodicCacheCheck() {
        Log.d(TAG, "📊 Starting periodic cache monitoring...");

        cacheCheckRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if cache should be cleared due to inactivity
                if (antiSpoofingDetector != null) {
                    antiSpoofingDetector.clearCacheIfStale();
                }

                // Log performance stats every 30 seconds
                if (System.currentTimeMillis() % 30000 < CACHE_CHECK_INTERVAL) {
                    logPerformanceStats();
                }

                // Schedule next check only if activity is active
                if (!isFinishing() && !isDestroyed()) {
                    cacheHandler.postDelayed(this, CACHE_CHECK_INTERVAL);
                }
            }
        };

        cacheHandler.post(cacheCheckRunnable);
        Log.d(TAG, "✅ Periodic monitoring started");
    }

    private void stopPeriodicCacheCheck() {
        Log.d(TAG, "🛑 Stopping periodic cache monitoring...");
        if (cacheCheckRunnable != null) {
            cacheHandler.removeCallbacks(cacheCheckRunnable);
            cacheCheckRunnable = null;
        }
    }

    // ========== REST OF THE METHODS WITH ENHANCED LOGGING ==========
    // (Including camera setup, lifecycle management, etc.)

    private List<FaceData> getReusableFaceDataList(int size) {
        synchronized (faceDataLock) {
            reusableFaceDataList.clear();
            for (int i = 0; i < size; i++) {
                reusableFaceDataList.add(null);
            }
            return reusableFaceDataList;
        }
    }

    private void updateOverlayOptimized(List<FaceData> faceDataList, ImageProxy imageProxy, long currentTime) {
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
        Log.e(TAG, "❌ Processing failure detected, clearing caches...");

        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.clearCache());
        }
        mainHandler.post(() -> {
            faceCountText.setText("Detection failed - cache cleared");
            spoofWarningText.setVisibility(View.GONE);
        });
    }

    // Camera and permission methods with logging...
    private boolean checkCameraPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "📸 Camera permission check: " + hasPermission);
        return hasPermission;
    }

    private void requestCameraPermission() {
        Log.d(TAG, "📸 Requesting camera permission...");
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, 100);
    }

    private void startCamera() {
        Log.i(TAG, "📸 Starting camera initialization...");
        mainHandler.post(() -> faceCountText.setText("Starting camera..."));

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                Log.d(TAG, "📸 Camera provider ready, setting up camera...");
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                imageAnalysis.setAnalyzer(backgroundHandler::post, this::processImageOptimized);

                mainHandler.post(() -> {
                    try {
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());
                        cameraProvider.unbindAll();
                        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                        Log.i(TAG, "✅ Camera started successfully");
                        faceCountText.setText("Camera ready - AI processing active");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Camera binding failed: " + e.getMessage(), e);
                        Toast.makeText(this, "Camera binding failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        faceCountText.setText("Camera binding failed");
                    }
                });

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Camera initialization failed: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Camera failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    faceCountText.setText("Camera initialization failed");
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, String.format("📸 Permission result: requestCode=%d, results=%s",
                requestCode, java.util.Arrays.toString(grantResults)));

        if (requestCode == 100) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✅ Camera permission granted");
                startCamera();
            } else {
                Log.w(TAG, "❌ Camera permission denied");
                Toast.makeText(this, "Camera permission denied",
                        Toast.LENGTH_SHORT).show();
                faceCountText.setText("Camera permission required");
            }
        }
    }

    // Enhanced lifecycle methods with logging...
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "⏸️ App paused - stopping processing");
        shouldProcess.set(false);

        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.forceClearCache());
        }
        mainHandler.post(() -> faceCountText.setText("App paused - processing stopped"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "▶️ App resumed - restarting processing");
        shouldProcess.set(true);

        if (cacheCheckRunnable == null) {
            startPeriodicCacheCheck();
        }
        mainHandler.post(() -> faceCountText.setText("App resumed - processing active"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🛑 ========== APP SHUTDOWN ==========");
        shouldProcess.set(false);
        stopPeriodicCacheCheck();

        // Log final performance stats
        logPerformanceStats();

        if (backgroundThread != null) {
            Log.d(TAG, "🧵 Shutting down background thread...");
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                Log.d(TAG, "✅ Background thread stopped");
            } catch (InterruptedException e) {
                Log.w(TAG, "⚠️ Background thread interrupt: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (antiSpoofingDetector != null && backgroundHandler != null) {
            backgroundHandler.post(() -> {
                antiSpoofingDetector.forceClearCache();
                antiSpoofingDetector.close();
                Log.d(TAG, "✅ Anti-spoofing detector closed");
            });
        }

        if (faceNetModel != null) {
            backgroundHandler.post(() -> {
                faceNetModel.close();
                Log.d(TAG, "✅ FaceNet model closed");
            });
        }

        if (recognitionManager != null) {
            backgroundHandler.post(() -> {
                recognitionManager.close();
                Log.d(TAG, "✅ Recognition manager closed");
            });
        }

        Log.i(TAG, "🏁 App shutdown complete");
    }

    // ========== ENHANCED FACE DATA CLASS ==========

    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public float confidence;
        public String detectionMethod;
        public boolean has3DStructure;

        // Face recognition fields
        public String recognizedName = "Unknown";
        public float recognitionConfidence = 0f;

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