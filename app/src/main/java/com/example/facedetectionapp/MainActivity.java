package com.example.facedetectionapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import androidx.documentfile.provider.DocumentFile;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.example.facedetectionapp.ml.FaceEmbeddingAnnotator;
import com.example.facedetectionapp.ml.FaceNetModel;
import com.example.facedetectionapp.ml.FileReader;
import com.example.facedetectionapp.ml.Models;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final String SERIALIZED_DATA_FILENAME = "face_embeddings_data";

    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private TextView recognitionText;
    private Button setupRecognitionButton;
    private FaceOverlayView overlayView;
    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;

    // Face Recognition Components
    private FaceNetModel faceNetModel;
    private FaceEmbeddingAnnotator faceAnnotator;
    private FileReader fileReader;
    private boolean isRecognitionEnabled = false;

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

        // Initialize face recognition UI elements
        recognitionText = findViewById(R.id.recognitionStatusText);
        setupRecognitionButton = findViewById(R.id.setupRecognitionButton);

        // Set up button click listener
        setupRecognitionButton.setOnClickListener(v -> setupFaceRecognition());

        // Initialize camera switch button if it exists
        Button cameraSwitchButton = findViewById(R.id.cameraSwitchButton);
        if (cameraSwitchButton != null) {
            cameraSwitchButton.setOnClickListener(v -> switchCamera());
        }

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
                .setMinFaceSize(0.1f)
                .build();

        detector = FaceDetection.getClient(options);

        // Check if face recognition data exists
        checkForExistingRecognitionData();
    }

    private void checkForExistingRecognitionData() {
        backgroundHandler.post(() -> {
            File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);
            if (serializedDataFile.exists()) {
                mainHandler.post(() -> {
                    setupRecognitionButton.setText("Load Existing Data");
                    if (recognitionText != null) {
                        recognitionText.setText("Face Recognition: Data Found");
                    }
                });
            } else {
                mainHandler.post(() -> {
                    setupRecognitionButton.setText("Setup Face Recognition");
                    if (recognitionText != null) {
                        recognitionText.setText("Face Recognition: Disabled");
                    }
                });
            }
        });
    }

    private void setupFaceRecognition() {
        File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);

        if (serializedDataFile.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Face Recognition Setup")
                    .setMessage("Existing face data found. Load it or scan new images?")
                    .setPositiveButton("Load Existing", (dialog, which) -> loadExistingFaceData())
                    .setNegativeButton("Scan New Images", (dialog, which) -> selectImagesDirectory())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Setup Face Recognition")
                    .setMessage("Select a directory containing face images organized in subfolders by person name.")
                    .setPositiveButton("Select Directory", (dialog, which) -> selectImagesDirectory())
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void loadExistingFaceData() {
        showLoadingOverlay(true, "Loading Face Data...", "Please wait while we load your saved face recognition data");

        backgroundHandler.post(() -> {
            try {
                mainHandler.post(() -> showLoadingOverlay(true, "Initializing FaceNet Model...", "Loading AI model for face recognition"));

                // Initialize FaceNet model
                faceNetModel = new FaceNetModel(this, Models.FACENET, true, true);
                fileReader = new FileReader(faceNetModel);

                mainHandler.post(() -> showLoadingOverlay(true, "Loading Face Database...", "Reading saved face embeddings"));

                // Load serialized data
                File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(serializedDataFile));
                @SuppressWarnings("unchecked")
                ArrayList<FaceEmbeddingAnnotator.Pair<String, float[]>> faceList =
                        (ArrayList<FaceEmbeddingAnnotator.Pair<String, float[]>>) objectInputStream.readObject();
                objectInputStream.close();

                mainHandler.post(() -> showLoadingOverlay(true, "Initializing Face Recognition...", "Setting up face comparison system"));

                // Initialize annotator
                faceAnnotator = new FaceEmbeddingAnnotator();
                faceAnnotator.initialize(faceList);

                isRecognitionEnabled = true;

                mainHandler.post(() -> {
                    recognitionText.setText(String.format("Face Recognition: Enabled (%d faces)", faceList.size()));
                    setupRecognitionButton.setText("Reconfigure Recognition");
                    faceCountText.setText("Face recognition loaded successfully");
                    showStatisticsPanel(true);
                    showLoadingOverlay(false, null, null);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to load face data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    faceCountText.setText("Failed to load face data");
                    showLoadingOverlay(false, null, null);
                });
            }
        });
    }

    private void selectImagesDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        directoryPickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri dirUri = result.getData().getData();
                    processFaceImagesDirectory(dirUri);
                }
            }
    );

    private void processFaceImagesDirectory(Uri dirUri) {
        showLoadingOverlay(true, "Initializing Face Recognition...", "Starting face recognition setup process");

        backgroundHandler.post(() -> {
            try {
                mainHandler.post(() -> showLoadingOverlay(true, "Loading FaceNet Model...", "Initializing AI model for face recognition"));

                // Initialize FaceNet model
                faceNetModel = new FaceNetModel(this, Models.FACENET, true, true);
                fileReader = new FileReader(faceNetModel);

                mainHandler.post(() -> showLoadingOverlay(true, "Scanning Directory...", "Looking for face images in selected folder"));

                // Scan directory for images
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        dirUri, DocumentsContract.getTreeDocumentId(dirUri));
                DocumentFile tree = DocumentFile.fromTreeUri(this, childrenUri);

                ArrayList<FaceEmbeddingAnnotator.Pair<String, android.graphics.Bitmap>> images = new ArrayList<>();

                if (tree != null && tree.listFiles().length > 0) {
                    DocumentFile[] folders = tree.listFiles();
                    final int totalFolders = folders.length;
                    final int[] folderCount = {0}; // Use array to make it effectively final

                    for (DocumentFile doc : folders) {
                        if (doc.isDirectory()) {
                            final String personName = doc.getName();
                            folderCount[0]++;
                            final int currentFolder = folderCount[0];

                            mainHandler.post(() -> showLoadingOverlay(true,
                                    "Processing Images...",
                                    String.format("Loading images for %s (%d/%d folders)", personName, currentFolder, totalFolders)));

                            for (DocumentFile imageDoc : doc.listFiles()) {
                                try {
                                    android.graphics.Bitmap bitmap = BitmapUtils.getFixedBitmap(this, imageDoc.getUri());
                                    if (bitmap != null) {
                                        images.add(new FaceEmbeddingAnnotator.Pair<>(personName, bitmap));
                                    }
                                } catch (Exception e) {
                                    // Skip problematic images
                                }
                            }
                        }
                    }
                }

                if (images.isEmpty()) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "No valid face images found in selected directory", Toast.LENGTH_LONG).show();
                        faceCountText.setText("No face images found");
                        showLoadingOverlay(false, null, null);
                    });
                    return;
                }

                mainHandler.post(() -> showLoadingOverlay(true,
                        "Generating Face Embeddings...",
                        String.format("Processing %d images to create face database", images.size())));

                // Process images to generate embeddings
                fileReader.run(images, result -> {
                    try {
                        mainHandler.post(() -> showLoadingOverlay(true, "Saving Face Database...", "Storing face data for future use"));

                        // Save embeddings to file
                        File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(serializedDataFile));
                        objectOutputStream.writeObject(new ArrayList<>(result.embeddedFaces));
                        objectOutputStream.flush();
                        objectOutputStream.close();

                        mainHandler.post(() -> showLoadingOverlay(true, "Finalizing Setup...", "Preparing face recognition system"));

                        // Initialize annotator
                        faceAnnotator = new FaceEmbeddingAnnotator();
                        faceAnnotator.initialize(result.embeddedFaces);

                        isRecognitionEnabled = true;

                        mainHandler.post(() -> {
                            recognitionText.setText(String.format("Face Recognition: Enabled (%d faces)", result.embeddedFaces.size()));
                            setupRecognitionButton.setText("Reconfigure Recognition");
                            faceCountText.setText(String.format("Face recognition setup complete: %d faces, %d images skipped",
                                    result.embeddedFaces.size(), result.numImagesWithNoFaces));
                            showStatisticsPanel(true);
                            showLoadingOverlay(false, null, null);
                        });

                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Failed to save face data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            faceCountText.setText("Failed to save face data");
                            showLoadingOverlay(false, null, null);
                        });
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error processing directory: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    faceCountText.setText("Error processing directory");
                    showLoadingOverlay(false, null, null);
                });
            }
        });
    }

    // ========== CACHE MANAGEMENT ==========

    private void startPeriodicCacheCheck() {
        cacheCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (antiSpoofingDetector != null) {
                    antiSpoofingDetector.clearCacheIfStale();
                }
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

    // ========== LIFECYCLE METHODS ==========

    @Override
    protected void onPause() {
        super.onPause();
        shouldProcess.set(false);
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.forceClearCache());
        }
        mainHandler.post(() -> faceCountText.setText("App paused - processing stopped"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        shouldProcess.set(true);
        if (cacheCheckRunnable == null) {
            startPeriodicCacheCheck();
        }
        mainHandler.post(() -> faceCountText.setText("App resumed - processing active"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shouldProcess.set(false);
        stopPeriodicCacheCheck();

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (antiSpoofingDetector != null && backgroundHandler != null) {
            backgroundHandler.post(() -> {
                antiSpoofingDetector.forceClearCache();
                antiSpoofingDetector.close();
            });
        }

        // Clean up face recognition resources
        if (faceNetModel != null) {
            faceNetModel.close();
        }
        if (faceAnnotator != null) {
            faceAnnotator.release();
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
        mainHandler.post(() -> faceCountText.setText("Starting camera..."));

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
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

    // ========== ENHANCED IMAGE PROCESSING ==========

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageOptimized(ImageProxy imageProxy) {
        if (!shouldProcess.get() || isProcessing.get()) {
            imageProxy.close();
            return;
        }

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

        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        try {
                            processFacesOptimized(faces, imageProxy, currentTime);
                        } finally {
                            imageProxy.close();
                            isProcessing.set(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        try {
                            handleProcessingFailure();
                        } finally {
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
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.clearCache();
            }
            updateUIOptimized("No faces detected - cache cleared", false, 0, 0, 0, currentTime);
            clearOverlayOptimized(imageProxy);
        } else {
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.updateLastDetectionTime();
                processFacesImmediately(faces, imageProxy, currentTime);
            }
        }
    }

    private void processFacesImmediately(List<Face> faces, ImageProxy imageProxy, long currentTime) {
        List<FaceData> faceDataList = getReusableFaceDataList(faces.size());

        for (int i = 0; i < faces.size(); i++) {
            final int index = i;
            final Face face = faces.get(i);

            if (!shouldProcess.get()) return;

            try {
                // Anti-spoofing detection
                FaceData faceData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());

                // Face recognition (if enabled)
                if (isRecognitionEnabled && faceNetModel != null && faceAnnotator != null && faceData.isReal) {
                    try {
                        android.graphics.Bitmap faceBitmap = extractFaceFromImageProxy(imageProxy, face.getBoundingBox());
                        if (faceBitmap != null) {
                            float[] embedding = faceNetModel.getFaceEmbedding(faceBitmap);
                            String identity = faceAnnotator.run(embedding);
                            faceData.identity = identity;
                        }
                    } catch (Exception e) {
                        // Face recognition failed, keep anti-spoofing result
                        faceData.identity = "Recognition Error";
                    }
                }

                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        faceDataList.set(index, faceData);
                    }
                }
            } catch (Exception e) {
                synchronized (faceDataLock) {
                    if (index < faceDataList.size()) {
                        faceDataList.set(index, new FaceData(face.getBoundingBox(), false, 50.0f, "ProcessingError", false));
                    }
                }
            }
        }

        updateUIWithFaceData(faceDataList, imageProxy, currentTime);
    }

    private android.graphics.Bitmap extractFaceFromImageProxy(ImageProxy imageProxy, Rect faceRect) {
        // This is a simplified implementation - you may need to enhance this
        // based on your specific requirements
        try {
            android.graphics.Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) return null;

            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;

            return android.graphics.Bitmap.createBitmap(fullBitmap, left, top, width, height);
        } catch (Exception e) {
            return null;
        }
    }

    private android.graphics.Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        // This is a simplified conversion - you may need the full implementation
        // from your AntiSpoofingDetector class

        // For now, return null - the face recognition will skip if bitmap conversion fails
        // You can implement this method based on your existing imageProxyToBitmap in AntiSpoofingDetector
        return null;
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
        int realFaces = 0, fakeFaces = 0, recognizedFaces = 0;

        synchronized (faceDataLock) {
            for (FaceData data : faceDataList) {
                if (data != null) {
                    if (data.isReal) {
                        realFaces++;
                        if (data.identity != null && !data.identity.equals("UNKNOWN")) {
                            recognizedFaces++;
                        }
                    } else {
                        fakeFaces++;
                    }
                }
            }
        }

        updateUIOptimized(null, true, realFaces, fakeFaces, recognizedFaces, currentTime);
        updateOverlayOptimized(faceDataList, imageProxy, currentTime);
    }

    private void updateUIOptimized(String message, boolean hasFaces, int realFaces, int fakeFaces, int recognizedFaces, long currentTime) {
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
                String cacheStatus = antiSpoofingDetector != null ?
                        antiSpoofingDetector.getCacheStatus() : "";

                String statusText = String.format("Real: %d | Fake: %d", realFaces, fakeFaces);
                if (isRecognitionEnabled) {
                    statusText += String.format(" | Recognized: %d", recognizedFaces);
                }
                statusText += " | " + cacheStatus;

                faceCountText.setText(statusText);

                // Update statistics panel
                if (hasFaces) {
                    updateStatisticsCounts(realFaces, fakeFaces, recognizedFaces);
                    showStatisticsPanel(true);
                }

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
        if (antiSpoofingDetector != null) {
            backgroundHandler.post(() -> antiSpoofingDetector.clearCache());
        }

        mainHandler.post(() -> {
            faceCountText.setText("Detection failed - cache cleared");
            spoofWarningText.setVisibility(View.GONE);
        });
    }

    // ========== UI HELPER METHODS ==========

    private void switchCamera() {
        // Implementation for camera switching
        Toast.makeText(this, "Camera switching not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void showStatisticsPanel(boolean show) {
        View statisticsPanel = findViewById(R.id.statisticsPanel);
        if (statisticsPanel != null) {
            statisticsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showLoadingOverlay(boolean show, String message, String subtext) {
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        TextView loadingText = findViewById(R.id.loadingText);
        TextView loadingSubtext = findViewById(R.id.loadingSubtext);

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (loadingText != null && message != null) {
            loadingText.setText(message);
        }
        if (loadingSubtext != null && subtext != null) {
            loadingSubtext.setText(subtext);
        }
    }

    private void updateStatisticsCounts(int realFaces, int fakeFaces, int recognizedFaces) {
        TextView realCount = findViewById(R.id.realFacesCount);
        TextView fakeCount = findViewById(R.id.fakeFacesCount);
        TextView recognizedCount = findViewById(R.id.recognizedFacesCount);

        if (realCount != null) realCount.setText(String.valueOf(realFaces));
        if (fakeCount != null) fakeCount.setText(String.valueOf(fakeFaces));
        if (recognizedCount != null) recognizedCount.setText(String.valueOf(recognizedFaces));
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

    // ========== ENHANCED DATA CLASS ==========

    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public float confidence;
        public String detectionMethod;
        public boolean has3DStructure;
        public String identity; // Added for face recognition

        public FaceData(Rect boundingBox, boolean isReal) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = 75.0f;
            this.detectionMethod = "ML";
            this.has3DStructure = false;
            this.identity = null;
        }

        public FaceData(Rect boundingBox, boolean isReal, float confidence, String detectionMethod, boolean has3DStructure) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
            this.has3DStructure = has3DStructure;
            this.identity = null;
        }
    }
}