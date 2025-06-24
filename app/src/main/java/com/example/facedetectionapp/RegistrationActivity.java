package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class RegistrationActivity extends AppCompatActivity {
    private static final String TAG = "RegistrationActivity";

    private PreviewView previewView;
    private EditText nameEditText;
    private EditText employeeIdEditText;
    private Button captureButton;
    private Button registerButton;
    private TextView instructionText;
    private TextView faceStatusText;

    private FaceDetector detector;
    private FaceRecognitionDetector faceRecognitionDetector;
    private ProcessCameraProvider cameraProvider;

    // Current state
    private ImageProxy currentImageProxy;
    private Face currentFace;
    private boolean hasCapturedFace = false;
    private boolean isProcessingFrame = false;
    private boolean isRegistering = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        Log.d(TAG, "🚀 RegistrationActivity onCreate started");

        setupToolbar();
        initializeViews();
        initializeComponents();
        setupUI();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Register New Person");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        nameEditText = findViewById(R.id.nameEditText);
        employeeIdEditText = findViewById(R.id.employeeIdEditText);
        captureButton = findViewById(R.id.captureButton);
        registerButton = findViewById(R.id.registerButton);
        instructionText = findViewById(R.id.instructionText);
        faceStatusText = findViewById(R.id.faceStatusText);

        Log.d(TAG, "✅ Views initialized successfully");
    }

    private void initializeComponents() {
        try {
            Log.d(TAG, "🔧 Initializing ML components...");

            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .enableTracking()
                    .build();

            detector = FaceDetection.getClient(options);
            faceRecognitionDetector = new FaceRecognitionDetector(this);

            Log.d(TAG, "✅ Face detector and recognition detector initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing components", e);
            Toast.makeText(this, "Error initializing face detection", Toast.LENGTH_LONG).show();
        }
    }

    private void setupUI() {
        captureButton.setOnClickListener(v -> {
            Log.d(TAG, "📸 Capture button clicked");
            handleCaptureButton();
        });

        registerButton.setOnClickListener(v -> {
            Log.d(TAG, "📝 Register button clicked");
            handleRegisterButton();
        });

        // Initial UI state
        registerButton.setEnabled(false);
        captureButton.setEnabled(true);
        instructionText.setText("📝 Enter your details and position your face in camera");
        faceStatusText.setText("👤 Position your face in camera view");

        Log.d(TAG, "✅ UI setup completed");
    }

    private void handleCaptureButton() {
        if (!validateInput()) {
            return;
        }

        if (currentFace == null || currentImageProxy == null) {
            Toast.makeText(this, "❌ No face detected. Please position your face in camera.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isProcessingFrame) {
            Toast.makeText(this, "⏳ Please wait, processing...", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "📸 Capturing face for registration...");

        // Capture the current face
        hasCapturedFace = true;
        captureButton.setText("RECAPTURE");
        registerButton.setEnabled(true);

        instructionText.setText("✅ Face captured successfully! Click REGISTER to complete.");
        faceStatusText.setText("✅ Face captured - Ready to register");
        faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

        Toast.makeText(this, "✅ Face captured successfully!", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "✅ Face captured successfully");
    }

    private void handleRegisterButton() {
        if (!hasCapturedFace) {
            Toast.makeText(this, "❌ Please capture a face first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!validateInput()) {
            return;
        }

        if (isRegistering) {
            Toast.makeText(this, "⏳ Registration in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        performFaceRegistration();
    }

    private void performFaceRegistration() {
        if (currentImageProxy == null || currentFace == null) {
            Toast.makeText(this, "❌ No face data available. Please recapture.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();

        Log.d(TAG, "🚀 Starting REAL face registration for: " + name + " (" + employeeId + ")");

        isRegistering = true;
        registerButton.setEnabled(false);
        registerButton.setText("Registering...");
        captureButton.setEnabled(false);

        instructionText.setText("⏳ Registering face... Please wait");
        faceStatusText.setText("🔄 Processing face data...");

        // Perform registration in background thread
        new Thread(() -> {
            try {
                boolean success = faceRecognitionDetector.registerFace(
                        currentImageProxy, currentFace, name, employeeId
                );

                runOnUiThread(() -> {
                    isRegistering = false;

                    if (success) {
                        Log.d(TAG, "✅ REAL face registration successful for: " + name);

                        Toast.makeText(this, "✅ " + name + " registered successfully!", Toast.LENGTH_LONG).show();

                        // Show success state
                        instructionText.setText("✅ Registration complete! " + name + " can now use face recognition.");
                        faceStatusText.setText("✅ Registration successful");
                        faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                        // Reset for next registration
                        resetRegistrationForm();

                    } else {
                        Log.e(TAG, "❌ REAL face registration failed for: " + name);

                        Toast.makeText(this, "❌ Registration failed. Please try again.", Toast.LENGTH_LONG).show();

                        instructionText.setText("❌ Registration failed. Please try again.");
                        faceStatusText.setText("❌ Registration failed");
                        faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                        // Re-enable buttons for retry
                        registerButton.setText("REGISTER");
                        registerButton.setEnabled(true);
                        captureButton.setEnabled(true);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error during face registration", e);

                runOnUiThread(() -> {
                    isRegistering = false;

                    Toast.makeText(this, "❌ Registration error: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    instructionText.setText("❌ Registration error. Please try again.");
                    faceStatusText.setText("❌ Error occurred");
                    faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                    registerButton.setText("REGISTER");
                    registerButton.setEnabled(true);
                    captureButton.setEnabled(true);
                });
            }
        }).start();
    }

    private boolean validateInput() {
        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();

        if (name.isEmpty()) {
            nameEditText.setError("Name is required");
            nameEditText.requestFocus();
            return false;
        }

        if (employeeId.isEmpty()) {
            employeeIdEditText.setError("Employee ID is required");
            employeeIdEditText.requestFocus();
            return false;
        }

        if (name.length() < 2) {
            nameEditText.setError("Name must be at least 2 characters");
            nameEditText.requestFocus();
            return false;
        }

        if (employeeId.length() < 3) {
            employeeIdEditText.setError("Employee ID must be at least 3 characters");
            employeeIdEditText.requestFocus();
            return false;
        }

        return true;
    }

    private void resetRegistrationForm() {
        // Clear form
        nameEditText.setText("");
        employeeIdEditText.setText("");

        // Reset state
        hasCapturedFace = false;
        currentImageProxy = null;
        currentFace = null;

        // Reset UI
        registerButton.setText("REGISTER");
        registerButton.setEnabled(false);
        captureButton.setText("CAPTURE");
        captureButton.setEnabled(true);

        instructionText.setText("📝 Ready for next registration - Enter details and position face");
        faceStatusText.setText("👤 Position your face in camera view");
        faceStatusText.setTextColor(getResources().getColor(android.R.color.darker_gray));
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
        Log.d(TAG, "📹 Starting camera setup...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(720, 720))
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(720, 720))
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::processImageSafely);

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                Log.d(TAG, "✅ Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Camera startup failed", e);
                Toast.makeText(this, "Camera failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageSafely(ImageProxy imageProxy) {
        try {
            if (isFinishing() || isDestroyed()) {
                imageProxy.close();
                return;
            }

            if (isProcessingFrame) {
                imageProxy.close();
                return;
            }

            isProcessingFrame = true;

            Log.d(TAG, String.format("🔄 Processing frame: %dx%d format=%d rotation=%d",
                    imageProxy.getWidth(), imageProxy.getHeight(),
                    imageProxy.getFormat(), imageProxy.getImageInfo().getRotationDegrees()));

            InputImage inputImage;
            try {
                inputImage = InputImage.fromMediaImage(
                        imageProxy.getImage(),
                        imageProxy.getImageInfo().getRotationDegrees()
                );
                Log.d(TAG, "✅ InputImage created successfully");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to create InputImage", e);
                imageProxy.close();
                isProcessingFrame = false;
                return;
            }

            detector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        try {
                            Log.d(TAG, String.format("✅ ML Kit success: %d faces detected", faces.size()));
                            processFaces(faces, imageProxy);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error in face processing", e);
                            imageProxy.close();
                            isProcessingFrame = false;
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Face detection failed", e);
                        imageProxy.close();
                        isProcessingFrame = false;
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing image safely", e);
            imageProxy.close();
            isProcessingFrame = false;
        }
    }

    private void processFaces(List<Face> faces, ImageProxy imageProxy) {
        try {
            if (faces.size() == 1) {
                Face face = faces.get(0);
                Log.d(TAG, String.format("👤 Single face detected: %s", face.getBoundingBox().toString()));

                // Store current face data for capture
                currentFace = face;

                // Only update currentImageProxy if we haven't captured yet
                if (!hasCapturedFace) {
                    if (currentImageProxy != null) {
                        currentImageProxy.close();
                    }
                    currentImageProxy = imageProxy;
                    // Don't close imageProxy here since we're storing it
                } else {
                    imageProxy.close();
                }

                runOnUiThread(() -> {
                    if (!hasCapturedFace) {
                        faceStatusText.setText("✅ Face detected - Click CAPTURE when ready");
                        faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        captureButton.setEnabled(true);
                        instructionText.setText("✅ Face ready - Click CAPTURE when ready");
                    }
                });

            } else if (faces.size() > 1) {
                Log.d(TAG, String.format("👥 Multiple faces: %d", faces.size()));

                imageProxy.close();
                currentFace = null;

                runOnUiThread(() -> {
                    if (!hasCapturedFace) {
                        faceStatusText.setText("⚠️ Multiple faces detected - Only one person allowed");
                        faceStatusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        captureButton.setEnabled(false);
                    }
                });

            } else {
                Log.d(TAG, "👻 No faces detected");

                imageProxy.close();
                currentFace = null;

                runOnUiThread(() -> {
                    if (!hasCapturedFace) {
                        faceStatusText.setText("👤 No face detected - Look at camera");
                        faceStatusText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                        captureButton.setEnabled(false);
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing faces", e);
            imageProxy.close();
        } finally {
            isProcessingFrame = false;
            Log.d(TAG, "🔒 ImageProxy closed successfully");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "📴 Activity paused");
        isProcessingFrame = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "📱 Activity resumed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💥 Activity destroyed");

        isProcessingFrame = false;

        // Cleanup stored image
        if (currentImageProxy != null) {
            currentImageProxy.close();
            currentImageProxy = null;
        }

        // Cleanup detectors
        if (detector != null) {
            detector.close();
        }

        if (faceRecognitionDetector != null) {
            faceRecognitionDetector.close();
        }
    }
}