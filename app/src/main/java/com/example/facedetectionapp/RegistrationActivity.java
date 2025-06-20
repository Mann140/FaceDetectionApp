package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class RegistrationActivity extends AppCompatActivity {
    private PreviewView previewView;
    private EditText nameEditText;
    private EditText employeeIdEditText;
    private Button captureButton;
    private Button registerButton;
    private TextView instructionText;
    private TextView faceStatusText;
    private FaceOverlayView overlayView;

    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionDetector faceRecognitionDetector;

    private ImageProxy capturedImageProxy;
    private Rect capturedFaceRect;
    private boolean isCapturing = false;
    private boolean hasCapturedFace = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        initializeViews();
        initializeComponents();
        setupUI();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
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

        // Setup overlay
        View placeholder = findViewById(R.id.overlay);
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int index = parent.indexOfChild(placeholder);
        parent.removeView(placeholder);

        overlayView = new FaceOverlayView(this, null);
        overlayView.setLayoutParams(placeholder.getLayoutParams());
        parent.addView(overlayView, index);
    }

    private void initializeComponents() {
        antiSpoofingDetector = new AntiSpoofingDetector(this);
        faceRecognitionDetector = new FaceRecognitionDetector(this);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
    }

    private void setupUI() {
        getSupportActionBar().setTitle("Register New Person");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        captureButton.setOnClickListener(v -> {
            if (validateInput()) {
                isCapturing = true;
                captureButton.setText("Capturing...");
                captureButton.setEnabled(false);
                instructionText.setText("📸 Hold still - Capturing your face...");
            }
        });

        registerButton.setOnClickListener(v -> {
            if (hasCapturedFace && validateInput()) {
                registerPerson();
            }
        });

        registerButton.setEnabled(false);
        instructionText.setText("📝 Enter your details and look at the camera");
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

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(),
                        new ImageAnalysis.Analyzer() {
                            @Override
                            public void analyze(@NonNull ImageProxy image) {
                                processImage(image);
                            }
                        });

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
        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        processFaces(faces, imageProxy);
                    })
                    .addOnCompleteListener(task -> {
                        if (!isCapturing) {
                            imageProxy.close();
                        }
                    });
        } else {
            imageProxy.close();
        }
    }

    private void processFaces(List<Face> faces, ImageProxy imageProxy) {
        List<MainActivity.FaceData> faceDataList = new ArrayList<>();

        if (faces.size() == 1) {
            Face face = faces.get(0);

            // Check if face is real
            MainActivity.FaceData spoofResult = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());
            faceDataList.add(spoofResult);

            if (spoofResult.isReal && spoofResult.confidence > 70) {
                runOnUiThread(() -> {
                    faceStatusText.setText("✅ Real face detected - Good quality");
                    faceStatusText.setTextColor(0xFF00AA00);

                    if (!hasCapturedFace) {
                        captureButton.setEnabled(true);
                        instructionText.setText("✅ Face ready - Click CAPTURE when ready");
                    }
                });

                // If capturing, save the face
                if (isCapturing) {
                    captureButton.setText("CAPTURE");
                    capturedImageProxy = imageProxy;
                    capturedFaceRect = face.getBoundingBox();
                    hasCapturedFace = true;
                    isCapturing = false;

                    runOnUiThread(() -> {
                        instructionText.setText("📸 Face captured! Click REGISTER to complete");
                        registerButton.setEnabled(true);
                        captureButton.setText("RECAPTURE");
                        captureButton.setEnabled(true);
                        faceStatusText.setText("✅ Face captured successfully");
                    });

                    // Don't close imageProxy if we're capturing
                    return;
                }

            } else if (spoofResult.isReal) {
                runOnUiThread(() -> {
                    faceStatusText.setText("⚠️ Real face but low quality - Move closer");
                    faceStatusText.setTextColor(0xFFFF9900);
                    captureButton.setEnabled(false);
                });
            } else {
                runOnUiThread(() -> {
                    faceStatusText.setText("❌ Fake face detected - Use real face");
                    faceStatusText.setTextColor(0xFFFF0000);
                    captureButton.setEnabled(false);
                });
            }

        } else if (faces.size() > 1) {
            runOnUiThread(() -> {
                faceStatusText.setText("⚠️ Multiple faces detected - Only one person allowed");
                faceStatusText.setTextColor(0xFFFF9900);
                captureButton.setEnabled(false);
            });

            // Add all faces to overlay
            for (Face face : faces) {
                MainActivity.FaceData faceData = new MainActivity.FaceData(face.getBoundingBox(), false, 50f, "MultipleFaces", false);
                faceDataList.add(faceData);
            }

        } else {
            runOnUiThread(() -> {
                faceStatusText.setText("👤 No face detected - Look at camera");
                faceStatusText.setTextColor(0xFF666666);
                captureButton.setEnabled(false);
            });
        }

        // Update overlay
        runOnUiThread(() -> {
            overlayView.setFacesWithML(faceDataList,
                    imageProxy.getWidth(),
                    imageProxy.getHeight());
        });

        imageProxy.close();
    }

    private void registerPerson() {
        if (capturedImageProxy == null || capturedFaceRect == null) {
            Toast.makeText(this, "No face captured. Please capture a face first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();

        // Check if employee ID already exists
        new Thread(() -> {
            Person existingPerson = faceRecognitionDetector.getAllRegisteredPersons().stream()
                    .filter(p -> p.employeeId.equals(employeeId))
                    .findFirst()
                    .orElse(null);

            runOnUiThread(() -> {
                if (existingPerson != null) {
                    Toast.makeText(this, "Employee ID already exists. Please use a different ID.",
                            Toast.LENGTH_LONG).show();
                    employeeIdEditText.setError("Employee ID already exists");
                    employeeIdEditText.requestFocus();
                    return;
                }

                // Proceed with registration
                performRegistration(name, employeeId);
            });
        }).start();
    }

    private void performRegistration(String name, String employeeId) {
        registerButton.setEnabled(false);
        registerButton.setText("Registering...");

        new Thread(() -> {
            boolean success = faceRecognitionDetector.registerPerson(name, employeeId, capturedImageProxy, capturedFaceRect);

            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "✅ " + name + " registered successfully!", Toast.LENGTH_LONG).show();

                    // Clear form and reset
                    nameEditText.setText("");
                    employeeIdEditText.setText("");
                    hasCapturedFace = false;
                    capturedImageProxy = null;
                    capturedFaceRect = null;

                    registerButton.setText("REGISTER");
                    registerButton.setEnabled(false);
                    captureButton.setText("CAPTURE");

                    instructionText.setText("✅ Registration complete! You can register another person or go back.");
                    faceStatusText.setText("Ready for next registration");
                    faceStatusText.setTextColor(0xFF666666);

                } else {
                    Toast.makeText(this, "❌ Registration failed. Please try again.", Toast.LENGTH_LONG).show();
                    registerButton.setText("REGISTER");
                    registerButton.setEnabled(true);
                }
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required for registration", Toast.LENGTH_LONG).show();
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
    protected void onDestroy() {
        super.onDestroy();
        if (antiSpoofingDetector != null) {
            antiSpoofingDetector.close();
        }
        if (faceRecognitionDetector != null) {
            faceRecognitionDetector.close();
        }
    }
}