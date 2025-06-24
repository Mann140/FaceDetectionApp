package com.example.facedetectionapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    // UI Components
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView attendanceStatsText;
    private TextView recognitionStatusText;
    private MaterialButton checkInButton;
    private MaterialButton checkOutButton;
    private FaceOverlayView overlayView;

    // Camera and Detection Components
    private ProcessCameraProvider cameraProvider;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;

    // Detection Components
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionDetector faceRecognitionDetector;
    private DatabaseHelper databaseHelper;

    // Current Detection State
    private FaceData currentFaceData;
    private boolean isProcessingFrame = false;
    private long lastProcessTime = 0;
    private static final long PROCESS_INTERVAL = 1000; // Process every 1 second

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🚀 MainActivity created");

        setupToolbar();
        initializeComponents();
        initializeUI();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Face Attendance System");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_register) {
            openRegistrationActivity();
            return true;
        } else if (id == R.id.menu_attendance_records) {
            openAttendanceRecords();
            return true;
        } else if (id == R.id.menu_manage_persons) {
            openManagePersons();
            return true;
        } else if (id == R.id.menu_settings) {
            Toast.makeText(this, "Settings - Coming Soon", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeComponents() {
        try {
            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor();

            // Initialize face detector with optimal settings
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .setMinFaceSize(0.1f)
                    .enableTracking()
                    .build();
            faceDetector = FaceDetection.getClient(options);

            // Initialize detection components
            antiSpoofingDetector = new AntiSpoofingDetector(this);
            faceRecognitionDetector = new FaceRecognitionDetector(this);
            databaseHelper = new DatabaseHelper(this);

            // Initialize current face data
            currentFaceData = new FaceData();

            Log.d(TAG, "✅ All components initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing components", e);
            Toast.makeText(this, "Error initializing camera components", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUI() {
        try {
            // Find UI components
            previewView = findViewById(R.id.previewView);
            faceCountText = findViewById(R.id.faceCountText);
            attendanceStatsText = findViewById(R.id.attendanceStatsText);
            recognitionStatusText = findViewById(R.id.recognitionStatusText);
            checkInButton = findViewById(R.id.checkInButton);
            checkOutButton = findViewById(R.id.checkOutButton);

            // Initialize overlay
            View overlayViewRaw = findViewById(R.id.overlay);
            if (overlayViewRaw instanceof FaceOverlayView) {
                overlayView = (FaceOverlayView) overlayViewRaw;
            }

            // Set initial text
            if (faceCountText != null) {
                faceCountText.setText("🔍 Looking for faces...");
            }

            // Update attendance stats
            updateAttendanceStats();

            // Set button click listeners
            if (checkInButton != null) {
                checkInButton.setOnClickListener(v -> handleManualCheckIn());
            }
            if (checkOutButton != null) {
                checkOutButton.setOnClickListener(v -> handleManualCheckOut());
            }

            Log.d(TAG, "✅ UI initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing UI", e);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                Log.d(TAG, "📹 Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Error starting camera", e);
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        try {
            // Unbind all use cases
            cameraProvider.unbindAll();

            // Preview use case
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Image analysis use case for face detection
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(720, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFace);

            // Camera selector (front camera for face detection)
            CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            Log.d(TAG, "✅ Camera use cases bound successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error binding camera use cases", e);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFace(ImageProxy imageProxy) {
        try {
            // Throttle processing to avoid overwhelming the system
            long currentTime = System.currentTimeMillis();
            if (isProcessingFrame || (currentTime - lastProcessTime) < PROCESS_INTERVAL) {
                imageProxy.close();
                return;
            }

            isProcessingFrame = true;
            lastProcessTime = currentTime;

            Log.d(TAG, "🔄 Processing frame: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() +
                    " format=" + imageProxy.getFormat() + " rotation=" + imageProxy.getImageInfo().getRotationDegrees());

            // Create InputImage from ImageProxy
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            Log.d(TAG, "✅ InputImage created successfully");

            // Detect faces
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        processFaceDetectionResults(imageProxy, faces);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Face detection failed", e);
                        runOnUiThread(() -> updateUI("❌ Face detection failed"));
                        isProcessingFrame = false;
                        imageProxy.close();
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in face analysis", e);
            isProcessingFrame = false;
            imageProxy.close();
        }
    }

    private void processFaceDetectionResults(ImageProxy imageProxy, List<Face> faces) {
        try {
            if (faces.isEmpty()) {
                Log.d(TAG, "👻 No faces detected");
                runOnUiThread(() -> updateUI("🔍 Looking for faces..."));
                clearFaceOverlay();
                isProcessingFrame = false;
                imageProxy.close();
                return;
            }

            if (faces.size() > 1) {
                Log.d(TAG, "👥 Multiple faces detected: " + faces.size());
                runOnUiThread(() -> updateUI("👥 Multiple faces detected - Only one person allowed"));
                isProcessingFrame = false;
                imageProxy.close();
                return;
            }

            // Single face detected - process it
            Face face = faces.get(0);
            Log.d(TAG, "✅ ML Kit success: 1 face detected");
            Log.d(TAG, "👤 Single face detected: " + face.getBoundingBox().toString());

            // Update UI for face detection
            runOnUiThread(() -> updateUI("✅ Face detected - Analyzing..."));

            // Perform anti-spoofing detection
            performAntiSpoofingDetection(imageProxy, face);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing face detection results", e);
            isProcessingFrame = false;
            imageProxy.close();
        }
    }

    private void performAntiSpoofingDetection(ImageProxy imageProxy, Face face) {
        try {
            Log.d(TAG, "🔒 Starting anti-spoofing detection...");

            // Use the existing AntiSpoofingDetector method
            boolean isReal = antiSpoofingDetector.isRealFace(imageProxy, face);
            String detectionMethod = isReal ? "Anti-Spoofing-Passed" : "Anti-Spoofing-Failed";

            // Update the overlay
            updateFaceOverlay(face, isReal, detectionMethod);

            if (isReal) {
                Log.d(TAG, "🎯 Anti-spoofing PASSED: Real face detected");

                // Update UI for anti-spoofing success
                runOnUiThread(() -> updateUI("🎯 Real face detected - Recognizing..."));

                // Proceed to face recognition
                performFaceRecognition(imageProxy, face);

            } else {
                Log.d(TAG, "🚫 Anti-spoofing FAILED: Fake face detected");

                runOnUiThread(() -> updateUI("🚫 Fake face detected - Spoofing attempt"));
                isProcessingFrame = false;
                imageProxy.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in anti-spoofing detection", e);
            runOnUiThread(() -> updateUI("❌ Anti-spoofing error - Please try again"));
            isProcessingFrame = false;
            imageProxy.close();
        }
    }

    private void performFaceRecognition(ImageProxy imageProxy, Face face) {
        try {
            Log.d(TAG, "🔍 Starting face recognition...");

            // Perform face recognition
            FaceRecognitionDetector.RecognitionResult recognitionResult =
                    faceRecognitionDetector.recognizeFace(imageProxy, face);

            if (recognitionResult.isRecognized) {
                Log.d(TAG, "✅ Face recognized: " + recognitionResult.person.name +
                        " (confidence: " + (recognitionResult.confidence * 100) + "%)");

                // Update current face data
                currentFaceData.recognizedPerson = recognitionResult.person;
                currentFaceData.confidence = recognitionResult.confidence * 100;
                currentFaceData.isRecognized = true;
                currentFaceData.isReal = true;

                // Update UI with recognized person
                runOnUiThread(() -> {
                    updateUI("✅ Welcome " + recognitionResult.person.name + "!");
                    updateRecognitionStatus(recognitionResult.person.name, recognitionResult.confidence);
                });

            } else {
                Log.d(TAG, "❌ Face not recognized");
                currentFaceData.isRecognized = false;

                runOnUiThread(() -> {
                    updateUI("❌ Face not recognized - Please register first");
                    updateRecognitionStatus("Unknown Person", 0.0f);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in face recognition", e);
            runOnUiThread(() -> updateUI("❌ Recognition error - Please try again"));
        } finally {
            isProcessingFrame = false;
            imageProxy.close();
        }
    }

    private void updateFaceOverlay(Face face, boolean isReal, String detectionMethod) {
        try {
            // Create FaceData with all required fields
            currentFaceData = new FaceData(face.getBoundingBox(), isReal, detectionMethod);
            currentFaceData.confidence = isReal ? 85.0f : 15.0f;
            currentFaceData.has3DStructure = isReal;

            // Create list for overlay
            List<FaceData> faceDataList = new ArrayList<>();
            faceDataList.add(currentFaceData);

            // Update overlay if it exists
            if (overlayView != null) {
                runOnUiThread(() -> {
                    // Use actual camera resolution
                    int imageWidth = 720;
                    int imageHeight = 720;

                    overlayView.setFacesWithML(faceDataList, imageWidth, imageHeight);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating face overlay", e);
        }
    }

    private void clearFaceOverlay() {
        if (overlayView != null) {
            runOnUiThread(() -> {
                overlayView.setFacesWithML(new ArrayList<>(), 720, 720);
            });
        }
    }

    private void updateUI(String message) {
        if (faceCountText != null) {
            faceCountText.setText(message);
        }
        Log.d(TAG, "📱 UI Updated: " + message);
    }

    private void updateRecognitionStatus(String personName, float confidence) {
        if (recognitionStatusText != null) {
            if (confidence > 0) {
                recognitionStatusText.setText("👤 " + personName + " (" + String.format("%.1f%%", confidence * 100) + ")");
                recognitionStatusText.setVisibility(View.VISIBLE);
            } else {
                recognitionStatusText.setText("👤 " + personName);
                recognitionStatusText.setVisibility(View.VISIBLE);
            }
        }
    }

    private void handleManualCheckIn() {
        if (currentFaceData != null && currentFaceData.isRecognized && currentFaceData.recognizedPerson != null) {
            markAttendanceForPerson(currentFaceData.recognizedPerson, "check_in", currentFaceData.confidence / 100f);
        } else {
            Toast.makeText(this, "Please position your face for recognition first", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleManualCheckOut() {
        if (currentFaceData != null && currentFaceData.isRecognized && currentFaceData.recognizedPerson != null) {
            markAttendanceForPerson(currentFaceData.recognizedPerson, "check_out", currentFaceData.confidence / 100f);
        } else {
            Toast.makeText(this, "Please position your face for recognition first", Toast.LENGTH_SHORT).show();
        }
    }

    private void markAttendanceForPerson(DatabaseHelper.Person person, String attendanceType, float confidence) {
        try {
            Log.d(TAG, "📝 Marking attendance: " + person.name + " - " + attendanceType);

            boolean success = faceRecognitionDetector.markAttendance(person, attendanceType, confidence);

            if (success) {
                Log.d(TAG, "✅ Attendance marked for: " + person.name);
                runOnUiThread(() -> {
                    showAttendanceSuccess(person.name, attendanceType);
                    updateAttendanceStats();
                });
            } else {
                Log.e(TAG, "❌ Failed to mark attendance for: " + person.name);
                runOnUiThread(() -> showAttendanceError());
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance", e);
            runOnUiThread(() -> showAttendanceError());
        }
    }

    private void showAttendanceSuccess(String personName, String attendanceType) {
        String message = "✅ " + personName + " - " + attendanceType.replace("_", " ").toUpperCase();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showAttendanceError() {
        Toast.makeText(this, "❌ Failed to mark attendance", Toast.LENGTH_SHORT).show();
    }

    private void updateAttendanceStats() {
        try {
            DatabaseHelper.Stats stats = databaseHelper.getTodaysStats();

            String statsText = "📊 Today: " + stats.total + " records | " +
                    stats.present + " present | " +
                    stats.checkedIn + " in | " +
                    stats.checkedOut + " out";

            if (attendanceStatsText != null) {
                attendanceStatsText.setText(statsText);
                attendanceStatsText.setVisibility(View.VISIBLE);
            }

            Log.d(TAG, "📊 Attendance stats updated: " + stats.toString());

        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating attendance stats", e);
            if (attendanceStatsText != null) {
                attendanceStatsText.setText("📊 Stats unavailable");
            }
        }
    }

    // ==================== NAVIGATION METHODS ====================

    private void openRegistrationActivity() {
        try {
            Intent intent = new Intent(this, RegistrationActivity.class);
            startActivity(intent);
            Log.d(TAG, "🚀 Opening Registration Activity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening Registration Activity", e);
            Toast.makeText(this, "Error opening registration", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAttendanceRecords() {
        try {
            Intent intent = new Intent(this, AttendanceRecordsActivity.class);
            startActivity(intent);
            Log.d(TAG, "🚀 Opening Attendance Records Activity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening Attendance Records Activity", e);
            Toast.makeText(this, "Error opening attendance records", Toast.LENGTH_SHORT).show();
        }
    }

    private void openManagePersons() {
        try {
            Intent intent = new Intent(this, ManagePersonsActivity.class);
            startActivity(intent);
            Log.d(TAG, "🚀 Opening Manage Persons Activity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening Manage Persons Activity", e);
            Toast.makeText(this, "Error opening manage persons", Toast.LENGTH_SHORT).show();
        }
    }



    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                Log.d(TAG, "✅ Camera permissions granted");
            } else {
                Toast.makeText(this, "Camera permission is required for face detection",
                        Toast.LENGTH_LONG).show();
                Log.w(TAG, "⚠️ Camera permissions not granted");
                finish();
            }
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "📱 Activity resumed");

        // Update attendance stats when returning to main screen
        updateAttendanceStats();

        // Reset processing state
        isProcessingFrame = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "📴 Activity paused");

        // Reset processing state
        isProcessingFrame = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💥 Activity destroyed");

        try {
            // Cleanup resources
            if (cameraExecutor != null) {
                cameraExecutor.shutdown();
            }

            if (faceDetector != null) {
                faceDetector.close();
            }

            if (faceRecognitionDetector != null) {
                faceRecognitionDetector.close();
            }

            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.close();
            }

            if (databaseHelper != null) {
                databaseHelper.close();
            }

            Log.d(TAG, "✅ Resources cleaned up successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error during cleanup", e);
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Face data holder class with all required fields
     */
    public static class FaceData {
        public DatabaseHelper.Person recognizedPerson;
        public float confidence;
        public boolean isRecognized;
        public long timestamp;

        // Required fields for FaceOverlayView
        public Rect boundingBox;
        public boolean isReal;
        public String detectionMethod;
        public boolean has3DStructure;

        public FaceData() {
            this.isRecognized = false;
            this.confidence = 0.0f;
            this.timestamp = System.currentTimeMillis();
            this.isReal = false;
            this.detectionMethod = "Unknown";
            this.has3DStructure = false;
            this.boundingBox = new Rect();
        }

        public FaceData(Rect boundingBox, boolean isReal, String detectionMethod) {
            this();
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.detectionMethod = detectionMethod;
        }

        @Override
        public String toString() {
            return "FaceData{recognizedPerson=" +
                    (recognizedPerson != null ? recognizedPerson.name : "null") +
                    ", confidence=" + confidence +
                    ", isRecognized=" + isRecognized +
                    ", isReal=" + isReal +
                    ", detectionMethod='" + detectionMethod + "'" +
                    ", boundingBox=" + boundingBox + "}";
        }
    }
}