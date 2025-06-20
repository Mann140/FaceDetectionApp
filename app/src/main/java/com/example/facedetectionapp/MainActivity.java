package com.example.facedetectionapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView faceCountText;
    private TextView spoofWarningText;
    private TextView attendanceStatsText;
    private TextView recognitionStatusText;
    private FaceOverlayView overlayView;
    private Button checkInButton, checkOutButton;

    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionDetector faceRecognitionDetector;
    private DatabaseHelper databaseHelper;

    private AttendanceRecord.AttendanceType pendingAttendanceType = AttendanceRecord.AttendanceType.CHECK_IN;
    private boolean isProcessingAttendance = false;
    private Handler uiHandler;

    // Cooldown to prevent repeated attendance marking
    private static final long ATTENDANCE_COOLDOWN_MS = 5000; // 5 seconds
    private long lastAttendanceTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        uiHandler = new Handler();
        initializeViews();
        initializeComponents();
        setupToolbar();
        setupUI();

        // Check camera permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        updateAttendanceStats();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        faceCountText = findViewById(R.id.faceCountText);
        spoofWarningText = findViewById(R.id.spoofWarningText);
        attendanceStatsText = findViewById(R.id.attendanceStatsText);
        recognitionStatusText = findViewById(R.id.recognitionStatusText);
        checkInButton = findViewById(R.id.checkInButton);
        checkOutButton = findViewById(R.id.checkOutButton);

        // Replace placeholder view with custom overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);
    }

    private void initializeComponents() {
        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Initialize detectors
        antiSpoofingDetector = new AntiSpoofingDetector(this);
        faceRecognitionDetector = new FaceRecognitionDetector(this);

        // Configure face detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
    }

    private void setupToolbar() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Face Attendance System");
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void setupUI() {
        checkInButton.setOnClickListener(v -> {
            pendingAttendanceType = AttendanceRecord.AttendanceType.CHECK_IN;
            checkInButton.setEnabled(false);
            checkOutButton.setEnabled(true);
            recognitionStatusText.setText("🔍 Ready to mark CHECK IN - Look at camera");
            recognitionStatusText.setVisibility(View.VISIBLE);
        });

        checkOutButton.setOnClickListener(v -> {
            pendingAttendanceType = AttendanceRecord.AttendanceType.CHECK_OUT;
            checkInButton.setEnabled(true);
            checkOutButton.setEnabled(false);
            recognitionStatusText.setText("🔍 Ready to mark CHECK OUT - Look at camera");
            recognitionStatusText.setVisibility(View.VISIBLE);
        });

        // Initially enable check-in
        checkInButton.setEnabled(true);
        checkOutButton.setEnabled(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_register) {
            startActivity(new Intent(this, RegistrationActivity.class));
            return true;
        } else if (id == R.id.menu_attendance_records) {
            startActivity(new Intent(this, AttendanceRecordsActivity.class));
            return true;
        } else if (id == R.id.menu_manage_persons) {
            startActivity(new Intent(this, ManagePersonsActivity.class));
            return true;
        } else if (id == R.id.menu_settings) {
            // Future: Settings activity
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
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

                // Image analysis for face detection and recognition
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
                        List<FaceData> faceDataList = new ArrayList<>();

                        for (Face face : faces) {
                            // First check if face is real using anti-spoofing
                            FaceData spoofingResult = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());

                            if (spoofingResult.isReal) {
                                // If face is real, try to recognize it
                                FaceRecognitionDetector.RecognitionResult recognitionResult =
                                        faceRecognitionDetector.recognizeFace(imageProxy, face.getBoundingBox());

                                if (recognitionResult != null && recognitionResult.isRecognized) {
                                    // Known person detected
                                    FaceData faceData = new FaceData(
                                            face.getBoundingBox(),
                                            true,
                                            recognitionResult.confidence * 100,
                                            "Real-" + recognitionResult.person.name,
                                            false
                                    );
                                    faceData.recognizedPerson = recognitionResult.person;
                                    faceDataList.add(faceData);

                                    // Auto-mark attendance if button was pressed and not in cooldown
                                    long currentTime = System.currentTimeMillis();
                                    if (!isProcessingAttendance &&
                                            (currentTime - lastAttendanceTime) > ATTENDANCE_COOLDOWN_MS &&
                                            (pendingAttendanceType != null)) {

                                        isProcessingAttendance = true;
                                        lastAttendanceTime = currentTime;

                                        // Mark attendance in background thread
                                        new Thread(() -> {
                                            boolean success = faceRecognitionDetector.markAttendance(
                                                    recognitionResult.person, pendingAttendanceType);

                                            uiHandler.post(() -> {
                                                isProcessingAttendance = false;

                                                if (success) {
                                                    String message = String.format("✅ %s marked for %s",
                                                            pendingAttendanceType.getDisplayName(),
                                                            recognitionResult.person.name);

                                                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                                                    recognitionStatusText.setText(message);

                                                    // Reset buttons
                                                    checkInButton.setEnabled(true);
                                                    checkOutButton.setEnabled(true);
                                                    pendingAttendanceType = null;

                                                    // Update stats
                                                    updateAttendanceStats();
                                                } else {
                                                    Toast.makeText(MainActivity.this,
                                                            "❌ Failed to mark attendance", Toast.LENGTH_SHORT).show();
                                                    recognitionStatusText.setText("❌ Attendance marking failed");
                                                }
                                            });
                                        }).start();
                                    }
                                } else {
                                    // Real face but unknown person
                                    FaceData faceData = new FaceData(
                                            face.getBoundingBox(),
                                            true,
                                            spoofingResult.confidence,
                                            "Real-Unknown",
                                            false
                                    );
                                    faceDataList.add(faceData);
                                }
                            } else {
                                // Fake face detected
                                faceDataList.add(spoofingResult);
                            }
                        }

                        // Count real vs fake faces
                        int realFaces = 0;
                        int fakeFaces = 0;
                        int recognizedFaces = 0;

                        for (FaceData data : faceDataList) {
                            if (data.isReal) {
                                realFaces++;
                                if (data.recognizedPerson != null) {
                                    recognizedFaces++;
                                }
                            } else {
                                fakeFaces++;
                            }
                        }

                        final int finalRealFaces = realFaces;
                        final int finalFakeFaces = fakeFaces;
                        final int finalRecognizedFaces = recognizedFaces;

                        runOnUiThread(() -> {
                            String faceCountMsg = String.format("Faces: %d real (%d known), %d fake",
                                    finalRealFaces, finalRecognizedFaces, finalFakeFaces);
                            faceCountText.setText(faceCountMsg);

                            // Show warnings/status
                            if (finalFakeFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("⚠️ Spoofing attempt detected!");
                                spoofWarningText.setBackgroundColor(0x80FF0000); // Red background
                            } else if (finalRecognizedFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("✓ Known person detected");
                                spoofWarningText.setBackgroundColor(0x8000FF00); // Green background
                            } else if (finalRealFaces > 0) {
                                spoofWarningText.setVisibility(View.VISIBLE);
                                spoofWarningText.setText("? Real face - Unknown person");
                                spoofWarningText.setBackgroundColor(0x80FFA500); // Orange background
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

    private void updateAttendanceStats() {
        new Thread(() -> {
            DatabaseHelper.AttendanceStats stats = databaseHelper.getTodayStats();
            List<Person> allPersons = faceRecognitionDetector.getAllRegisteredPersons();

            uiHandler.post(() -> {
                String statsText = String.format(Locale.getDefault(),
                        "📊 Today: %d/%d present • In: %d • Out: %d",
                        stats.currentlyPresent,
                        stats.totalRegistered,
                        stats.checkedInToday,
                        stats.checkedOutToday
                );
                attendanceStatsText.setText(statsText);
                attendanceStatsText.setVisibility(View.VISIBLE);
            });
        }).start();
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
                Toast.makeText(this, "Camera permission required for attendance system",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAttendanceStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up detectors
        if (antiSpoofingDetector != null) {
            antiSpoofingDetector.close();
        }
        if (faceRecognitionDetector != null) {
            faceRecognitionDetector.close();
        }
    }

    // Enhanced FaceData class with recognition info
    public static class FaceData {
        public Rect boundingBox;
        public boolean isReal;
        public float confidence;
        public String detectionMethod;
        public boolean has3DStructure;
        public Person recognizedPerson; // Added for recognition

        public FaceData(Rect boundingBox, boolean isReal) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = 75.0f;
            this.detectionMethod = "ML";
            this.has3DStructure = false;
            this.recognizedPerson = null;
        }

        public FaceData(Rect boundingBox, boolean isReal, float confidence, String detectionMethod, boolean has3DStructure) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
            this.has3DStructure = has3DStructure;
            this.recognizedPerson = null;
        }
    }
}