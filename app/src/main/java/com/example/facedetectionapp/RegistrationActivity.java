package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
    private Bitmap capturedFaceBitmap;
    private boolean hasCapturedFace = false;
    private boolean isProcessingFrame = false;

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
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
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

            Log.d(TAG, "✅ Face detector initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing components", e);
            Toast.makeText(this, "Error initializing face detection", Toast.LENGTH_LONG).show();
        }
    }

    private void setupUI() {
        captureButton.setOnClickListener(v -> {
            Log.d(TAG, "📸 Capture button clicked");

            if (validateInput()) {
                Toast.makeText(this, "Face capture functionality - implement your logic here", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "✅ Capture logic would go here");

                // For now, just enable register button
                hasCapturedFace = true;
                registerButton.setEnabled(true);
                captureButton.setText("RECAPTURE");
                faceStatusText.setText("✅ Face captured (demo)");
                instructionText.setText("📸 Face captured! Click REGISTER to complete");
            }
        });

        registerButton.setOnClickListener(v -> {
            Log.d(TAG, "📝 Register button clicked");

            if (hasCapturedFace && validateInput()) {
                registerPerson();
            } else if (!hasCapturedFace) {
                Toast.makeText(this, "Please capture a face first", Toast.LENGTH_SHORT).show();
            }
        });

        registerButton.setEnabled(false);
        captureButton.setEnabled(true);
        instructionText.setText("📝 Enter your details and position your face in camera");

        Log.d(TAG, "✅ UI setup completed");
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
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(640, 480))
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
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
                        processImageSafely(imageProxy);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing image", e);
                        imageProxy.close();
                        isProcessingFrame = false;
                    }
                });

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
        Log.d(TAG, String.format("🔄 Processing frame: %dx%d format=%d rotation=%d",
                imageProxy.getWidth(), imageProxy.getHeight(),
                imageProxy.getFormat(), imageProxy.getImageInfo().getRotationDegrees()));

        Image image = imageProxy.getImage();
        if (image == null) {
            Log.e(TAG, "❌ ImageProxy.getImage() returned null");
            imageProxy.close();
            isProcessingFrame = false;
            return;
        }

        InputImage inputImage;
        try {
            inputImage = InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees());
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
                        processFaces(faces);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error in face processing", e);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Face detection failed", e);
                })
                .addOnCompleteListener(task -> {
                    try {
                        imageProxy.close();
                        Log.d(TAG, "🔒 ImageProxy closed successfully");
                    } catch (Exception e) {
                        Log.w(TAG, "⚠️ Error closing ImageProxy: " + e.getMessage());
                    } finally {
                        isProcessingFrame = false;
                    }
                });
    }

    private void processFaces(List<Face> faces) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (faces.size() == 1) {
                Log.d(TAG, String.format("👤 Single face detected: %s", faces.get(0).getBoundingBox().toString()));

                faceStatusText.setText("✅ Face detected - Click CAPTURE when ready");
                faceStatusText.setTextColor(0xFF00AA00);

                if (!hasCapturedFace) {
                    captureButton.setEnabled(true);
                    instructionText.setText("✅ Face ready - Click CAPTURE when ready");
                }

            } else if (faces.size() > 1) {
                Log.d(TAG, String.format("👥 Multiple faces: %d", faces.size()));

                faceStatusText.setText("⚠️ Multiple faces detected - Only one person allowed");
                faceStatusText.setTextColor(0xFFFF9900);
                captureButton.setEnabled(false);

            } else {
                Log.d(TAG, "👻 No faces detected");

                faceStatusText.setText("👤 No face detected - Look at camera");
                faceStatusText.setTextColor(0xFF666666);

                if (!hasCapturedFace) {
                    captureButton.setEnabled(false);
                }
            }
        });
    }

    private void registerPerson() {
        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();

        Log.d(TAG, "🚀 Starting registration for: " + name + " (" + employeeId + ")");

        registerButton.setEnabled(false);
        registerButton.setText("Registering...");

        // Simulate registration process
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simulate processing time

                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ " + name + " registered successfully!", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "✅ Registration successful for: " + name);

                    resetRegistrationForm();
                    instructionText.setText("✅ Registration complete! You can register another person.");
                    faceStatusText.setText("Ready for next registration");
                    faceStatusText.setTextColor(0xFF666666);
                });

            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Registration interrupted", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Registration failed", Toast.LENGTH_SHORT).show();
                    registerButton.setText("REGISTER");
                    registerButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void resetRegistrationForm() {
        nameEditText.setText("");
        employeeIdEditText.setText("");
        hasCapturedFace = false;

        if (capturedFaceBitmap != null && !capturedFaceBitmap.isRecycled()) {
            capturedFaceBitmap.recycle();
            capturedFaceBitmap = null;
        }

        registerButton.setText("REGISTER");
        registerButton.setEnabled(false);
        captureButton.setText("CAPTURE");
        captureButton.setEnabled(true);
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

        if (capturedFaceBitmap != null && !capturedFaceBitmap.isRecycled()) {
            capturedFaceBitmap.recycle();
            capturedFaceBitmap = null;
        }

        if (detector != null) {
            detector.close();
        }
    }
}