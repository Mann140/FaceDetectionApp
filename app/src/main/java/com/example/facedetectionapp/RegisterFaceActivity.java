package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterFaceActivity extends AppCompatActivity {

    private static final String TAG = "RegisterFaceActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final long UI_UPDATE_INTERVAL = 500; // Update UI every 500ms

    // UI Components
    private PreviewView previewView;
    private ImageView overlayImageView;
    private EditText nameEditText;
    private EditText employeeIdEditText;
    private EditText departmentEditText;
    private TextView statusTextView;
    private Button captureButton;
    private Button saveButton;

    // Camera and ML Kit
    private ProcessCameraProvider cameraProvider;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;

    // Face Recognition Components
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionProcessor faceRecognitionProcessor;
    private AttendanceDatabase database;

    // State Management
    private boolean isCapturing = false;
    private boolean hasCapturedFace = false;
    private long lastUIUpdateTime = 0;
    private Bitmap capturedFaceBitmap = null;
    private Rect capturedFaceRect = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_face);

        Log.d(TAG, "🚀 RegisterFaceActivity started");

        initializeViews();
        initializeComponents();
        setupClickListeners();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        overlayImageView = findViewById(R.id.overlayImageView);
        nameEditText = findViewById(R.id.nameEditText);
        employeeIdEditText = findViewById(R.id.employeeIdEditText);
        departmentEditText = findViewById(R.id.departmentEditText);
        statusTextView = findViewById(R.id.statusTextView);
        captureButton = findViewById(R.id.captureButton);
        saveButton = findViewById(R.id.saveButton);

        // Initial state
        updateStatus("Position your face in the camera");
        saveButton.setEnabled(false);
    }

    private void initializeComponents() {
        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize database
        database = new AttendanceDatabase(this);

        // Initialize face detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.4f) // Larger minimum face size for registration
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);

        // Initialize anti-spoofing detector
        antiSpoofingDetector = new AntiSpoofingDetector(this);

        // Initialize face recognition processor
        faceRecognitionProcessor = new FaceRecognitionProcessor(this);

        Log.d(TAG, "✅ All components initialized");
    }

    private void setupClickListeners() {
        captureButton.setOnClickListener(v -> {
            if (!isCapturing && !hasCapturedFace) {
                startCapturing();
            } else if (hasCapturedFace) {
                resetCapture();
            }
        });

        saveButton.setOnClickListener(v -> saveFaceRegistration());
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                updateStatus("Camera ready - Position your face in the frame");
                Log.d(TAG, "✅ Camera started successfully");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Error starting camera", e);
                updateStatus("Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.e(TAG, "❌ Camera provider is null");
            return;
        }

        // Preview use case
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(640, 480))
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image analysis use case for face detection
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // Select front camera
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

        } catch (Exception e) {
            Log.e(TAG, "❌ Use case binding failed", e);
            updateStatus("Camera binding failed: " + e.getMessage());
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        try {
            // Check if we should process this frame (throttling)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) {
                imageProxy.close();
                return;
            }

            // Skip processing if already captured
            if (hasCapturedFace) {
                imageProxy.close();
                return;
            }

            // Convert ImageProxy to InputImage
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // Detect faces
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        processFaces(imageProxy, faces);
                        lastUIUpdateTime = currentTime;
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Face detection failed", e);
                        runOnUiThread(() -> updateStatus("Face detection error"));
                        imageProxy.close();
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error analyzing image", e);
            imageProxy.close();
        }
    }

    private void processFaces(ImageProxy imageProxy, List<Face> faces) {
        try {
            if (faces.isEmpty()) {
                runOnUiThread(() -> {
                    updateStatus("No face detected - Move closer to camera");
                    updateOverlay(null);
                });
                imageProxy.close();
                return;
            }

            if (faces.size() > 1) {
                runOnUiThread(() -> {
                    updateStatus("Multiple faces detected - Only one face allowed");
                    updateOverlay(null);
                });
                imageProxy.close();
                return;
            }

            Face face = faces.get(0);

            // Check if this is capturing mode
            if (isCapturing) {
                captureFace(imageProxy, face);
            } else {
                // Just show face detection
                runOnUiThread(() -> {
                    updateStatus("Face detected - Click 'Capture Face' when ready");
                    updateOverlay(face);
                });
                imageProxy.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing faces", e);
            runOnUiThread(() -> updateStatus("Face processing error"));
            imageProxy.close();
        }
    }

    private void captureFace(ImageProxy imageProxy, Face face) {
        try {
            // Run anti-spoofing detection first
            MainActivity.FaceData spoofData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());

            Log.d(TAG, "🔍 Anti-spoof result: isReal=" + spoofData.isReal +
                    ", confidence=" + spoofData.confidence + "%");

            if (spoofData.isReal && spoofData.confidence > 70.0f) {
                // Extract face bitmap immediately while ImageProxy is still valid
                Bitmap faceBitmap = faceRecognitionProcessor.extractFaceBitmapFromImageProxy(imageProxy, face.getBoundingBox());

                if (faceBitmap != null) {
                    // Successfully captured face
                    capturedFaceBitmap = faceBitmap;
                    capturedFaceRect = face.getBoundingBox();
                    isCapturing = false;
                    hasCapturedFace = true;

                    runOnUiThread(() -> {
                        updateStatus("✅ Face captured successfully! Fill in details and save.");
                        captureButton.setText("Retake");
                        saveButton.setEnabled(true);
                        updateOverlay(face);
                    });

                    Log.d(TAG, "✅ Face captured successfully");
                } else {
                    Log.e(TAG, "❌ Failed to extract face bitmap");
                    runOnUiThread(() -> {
                        updateStatus("❌ Failed to capture face - Please try again");
                        isCapturing = false;
                    });
                }
            } else {
                // Face is detected as fake or low confidence
                Log.d(TAG, "🚫 Face rejected: confidence=" + spoofData.confidence + "%");
                runOnUiThread(() -> {
                    updateStatus("❌ Please use a real face, not a photo or video");
                    isCapturing = false;
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error capturing face", e);
            runOnUiThread(() -> {
                updateStatus("❌ Error capturing face - Please try again");
                isCapturing = false;
            });
        } finally {
            imageProxy.close();
        }
    }

    private void startCapturing() {
        isCapturing = true;
        updateStatus("📸 Capturing face... Hold still");
        captureButton.setText("Capturing...");
        captureButton.setEnabled(false);

        // Re-enable button after a delay
        captureButton.postDelayed(() -> {
            if (isCapturing) {
                captureButton.setEnabled(true);
                captureButton.setText("Capture Face");
                isCapturing = false;
                updateStatus("❌ Capture timeout - Please try again");
            }
        }, 5000); // 5 second timeout
    }

    private void resetCapture() {
        isCapturing = false;
        hasCapturedFace = false;
        capturedFaceBitmap = null;
        capturedFaceRect = null;

        captureButton.setText("Capture Face");
        saveButton.setEnabled(false);
        updateStatus("Position your face in the camera");
        updateOverlay(null);

        Log.d(TAG, "🔄 Capture reset");
    }

    private void saveFaceRegistration() {
        // Validate input fields
        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();
        String department = departmentEditText.getText().toString().trim();

        if (name.isEmpty()) {
            nameEditText.setError("Name is required");
            nameEditText.requestFocus();
            return;
        }

        if (employeeId.isEmpty()) {
            employeeIdEditText.setError("Employee ID is required");
            employeeIdEditText.requestFocus();
            return;
        }

        if (capturedFaceBitmap == null) {
            Toast.makeText(this, "Please capture a face first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable save button to prevent double submission
        saveButton.setEnabled(false);
        updateStatus("💾 Saving registration...");

        // Save in background thread
        new Thread(() -> {
            try {
                // Check if employee ID already exists
                AttendanceDatabase.User existingUser = database.getUserByEmployeeId(employeeId);
                if (existingUser != null) {
                    runOnUiThread(() -> {
                        employeeIdEditText.setError("Employee ID already exists");
                        employeeIdEditText.requestFocus();
                        saveButton.setEnabled(true);
                        updateStatus("❌ Employee ID already exists");
                    });
                    return;
                }

                // Add user to database
                long userId = database.addUser(name, employeeId, department);

                if (userId != -1) {
                    // ✅ FIXED: registerFaceFromBitmap returns boolean, handle it properly
                    boolean registrationSuccess = faceRecognitionProcessor.registerFaceFromBitmap(
                            capturedFaceBitmap, capturedFaceRect, userId);

                    if (registrationSuccess) {
                        // Success
                        runOnUiThread(() -> {
                            updateStatus("✅ Registration successful!");
                            Toast.makeText(RegisterFaceActivity.this,
                                    "✅ " + name + " registered successfully!", Toast.LENGTH_LONG).show();

                            // Clear form and reset
                            clearForm();
                            resetCapture();
                            saveButton.setEnabled(false);
                        });

                        Log.d(TAG, "✅ Registration successful for: " + name);
                    } else {
                        // Face encoding failed
                        runOnUiThread(() -> {
                            updateStatus("❌ Failed to process face data");
                            Toast.makeText(RegisterFaceActivity.this,
                                    "❌ Failed to process face data. Please try again.", Toast.LENGTH_LONG).show();
                            saveButton.setEnabled(true);
                        });

                        Log.e(TAG, "❌ Face encoding failed for user: " + name);
                    }
                } else {
                    // Database insertion failed
                    runOnUiThread(() -> {
                        updateStatus("❌ Failed to save user data");
                        Toast.makeText(RegisterFaceActivity.this,
                                "❌ Failed to save user. Please try again.", Toast.LENGTH_LONG).show();
                        saveButton.setEnabled(true);
                    });

                    Log.e(TAG, "❌ Database insertion failed for user: " + name);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error during registration", e);
                runOnUiThread(() -> {
                    updateStatus("❌ Registration error");
                    Toast.makeText(RegisterFaceActivity.this,
                            "❌ Registration error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void clearForm() {
        nameEditText.setText("");
        employeeIdEditText.setText("");
        departmentEditText.setText("");
        nameEditText.clearFocus();
        employeeIdEditText.clearFocus();
        departmentEditText.clearFocus();
    }

    private void updateOverlay(Face face) {
        if (previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            return;
        }

        int previewWidth = previewView.getWidth();
        int previewHeight = previewView.getHeight();

        Bitmap overlayBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlayBitmap);

        if (face != null) {
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setAntiAlias(true);

            // Set color based on capture state
            if (hasCapturedFace) {
                paint.setColor(Color.GREEN); // Successfully captured
            } else if (isCapturing) {
                paint.setColor(Color.YELLOW); // Currently capturing
            } else {
                paint.setColor(Color.CYAN); // Face detected, ready to capture
            }

            // Scale and mirror face rect for front camera
            RectF scaledRect = new RectF();
            scaledRect.left = previewWidth - (face.getBoundingBox().right * previewWidth / 640f);
            scaledRect.top = face.getBoundingBox().top * previewHeight / 480f;
            scaledRect.right = previewWidth - (face.getBoundingBox().left * previewWidth / 640f);
            scaledRect.bottom = face.getBoundingBox().bottom * previewHeight / 480f;

            // Draw bounding box
            canvas.drawRect(scaledRect, paint);

            // Draw center crosshair
            paint.setStrokeWidth(4f);
            float centerX = scaledRect.centerX();
            float centerY = scaledRect.centerY();
            canvas.drawLine(centerX - 20, centerY, centerX + 20, centerY, paint);
            canvas.drawLine(centerX, centerY - 20, centerX, centerY + 20, paint);
        }

        overlayImageView.setImageBitmap(overlayBitmap);
    }

    private void updateStatus(String status) {
        if (statusTextView != null) {
            statusTextView.setText(status);
            Log.d(TAG, "📱 Status: " + status);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Shutdown camera executor
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        // Close face detector
        if (faceDetector != null) {
            faceDetector.close();
        }

        // Close face recognition processor
        if (faceRecognitionProcessor != null) {
            faceRecognitionProcessor.close();
        }

        Log.d(TAG, "🛑 RegisterFaceActivity destroyed");
    }

    // ===== FACE DATA CLASS =====

    public static class FaceData {
        public final Rect boundingBox;
        public final boolean isReal;
        public final float confidence;
        public final String label;

        public FaceData(Rect boundingBox, boolean isReal, float confidence, String label) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.label = label;
        }

        @Override
        public String toString() {
            return "FaceData{" +
                    "isReal=" + isReal +
                    ", confidence=" + confidence +
                    ", label='" + label + '\'' +
                    '}';
        }
    }
}