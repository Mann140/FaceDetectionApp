package com.example.facedetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    private FaceOverlayView overlayView;

    private FaceDetector detector;
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionDetector faceRecognitionDetector;

    private ImageProxy capturedImageProxy;
    private Rect capturedFaceRect;
    private Bitmap capturedFaceBitmap; // Store Bitmap instead of ImageProxy
    private Bitmap currentFaceBitmap; // Store current good face for immediate capture
    private boolean isCapturing = false;
    private boolean hasCapturedFace = false;
    private boolean hasGoodFace = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

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
        // Set up the toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);

            // Set up ActionBar with null check
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Register New Person");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            // Handle navigation click
            toolbar.setNavigationOnClickListener(v -> {
                onBackPressed();
            });
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
        if (placeholder != null) {
            ViewGroup parent = (ViewGroup) placeholder.getParent();
            int index = parent.indexOfChild(placeholder);
            parent.removeView(placeholder);

            overlayView = new FaceOverlayView(this, null);
            overlayView.setLayoutParams(placeholder.getLayoutParams());
            parent.addView(overlayView, index);
        }
    }

    private void initializeComponents() {
        try {
            antiSpoofingDetector = new AntiSpoofingDetector(this);
            faceRecognitionDetector = new FaceRecognitionDetector(this);

            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .enableTracking()
                    .build();

            detector = FaceDetection.getClient(options);

            Log.d(TAG, "✅ All components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing components", e);
            Toast.makeText(this, "Error initializing face detection components", Toast.LENGTH_LONG).show();
        }
    }

    private void setupUI() {
        captureButton.setOnClickListener(v -> {
            if (validateInput() && hasGoodFace && currentFaceBitmap != null) {
                // Immediately capture the current good face
                capturedFaceBitmap = currentFaceBitmap.copy(currentFaceBitmap.getConfig(), false);
                hasCapturedFace = true;

                instructionText.setText("📸 Face captured! Click REGISTER to complete");
                registerButton.setEnabled(true);
                captureButton.setText("RECAPTURE");
                faceStatusText.setText("✅ Face captured successfully");

                Log.d(TAG, "✅ Face captured successfully from current bitmap");
            } else if (!hasGoodFace) {
                Toast.makeText(this, "No good quality face detected. Please position your face properly.", Toast.LENGTH_SHORT).show();
            }
        });

        registerButton.setOnClickListener(v -> {
            if (hasCapturedFace && validateInput()) {
                registerPerson();
            } else if (!hasCapturedFace) {
                Toast.makeText(this, "Please capture a face first", Toast.LENGTH_SHORT).show();
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

                Log.d(TAG, "✅ Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "❌ Camera startup failed", e);
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
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Face detection failed", e);
                        imageProxy.close();
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

            // Check if face is real using anti-spoofing
            MainActivity.FaceData spoofResult = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());
            faceDataList.add(spoofResult);

            if (spoofResult.isReal && spoofResult.confidence > 70) {
                // Extract face bitmap immediately for good faces
                Bitmap currentFace = extractFaceAsBitmap(imageProxy, face.getBoundingBox());
                if (currentFace != null) {
                    // Update current face bitmap
                    if (currentFaceBitmap != null && !currentFaceBitmap.isRecycled()) {
                        currentFaceBitmap.recycle();
                    }
                    currentFaceBitmap = currentFace;
                    hasGoodFace = true;

                    runOnUiThread(() -> {
                        faceStatusText.setText("✅ Real face detected - Good quality");
                        faceStatusText.setTextColor(0xFF00AA00);

                        if (!hasCapturedFace) {
                            captureButton.setEnabled(true);
                            instructionText.setText("✅ Face ready - Click CAPTURE when ready");
                        }
                    });
                } else {
                    hasGoodFace = false;
                    runOnUiThread(() -> {
                        faceStatusText.setText("⚠️ Failed to process face - Try again");
                        faceStatusText.setTextColor(0xFFFF9900);
                        captureButton.setEnabled(false);
                    });
                }

            } else if (spoofResult.isReal) {
                hasGoodFace = false;
                runOnUiThread(() -> {
                    faceStatusText.setText("⚠️ Real face but low quality - Move closer");
                    faceStatusText.setTextColor(0xFFFF9900);
                    captureButton.setEnabled(false);
                });
            } else {
                hasGoodFace = false;
                runOnUiThread(() -> {
                    faceStatusText.setText("❌ Fake face detected - Use real face");
                    faceStatusText.setTextColor(0xFFFF0000);
                    captureButton.setEnabled(false);
                });
            }

        } else if (faces.size() > 1) {
            hasGoodFace = false;
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
            hasGoodFace = false;
            runOnUiThread(() -> {
                faceStatusText.setText("👤 No face detected - Look at camera");
                faceStatusText.setTextColor(0xFF666666);
                captureButton.setEnabled(false);
            });
        }

        // Update overlay
        runOnUiThread(() -> {
            if (overlayView != null) {
                overlayView.setFacesWithML(faceDataList,
                        imageProxy.getWidth(),
                        imageProxy.getHeight());
            }
        });

        imageProxy.close();
    }

    private void registerPerson() {
        if (capturedFaceBitmap == null) {
            Toast.makeText(this, "No face captured. Please capture a face first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEditText.getText().toString().trim();
        String employeeId = employeeIdEditText.getText().toString().trim();

        Log.d(TAG, "🚀 Starting registration for: " + name + " (" + employeeId + ")");

        // Check if employee ID already exists
        new Thread(() -> {
            try {
                List<Person> existingPersons = faceRecognitionDetector.getAllRegisteredPersons();
                Person existingPerson = null;

                for (Person p : existingPersons) {
                    if (p.employeeId.equals(employeeId)) {
                        existingPerson = p;
                        break;
                    }
                }

                final Person finalExistingPerson = existingPerson;
                runOnUiThread(() -> {
                    if (finalExistingPerson != null) {
                        Toast.makeText(this, "Employee ID already exists. Please use a different ID.",
                                Toast.LENGTH_LONG).show();
                        employeeIdEditText.setError("Employee ID already exists");
                        employeeIdEditText.requestFocus();
                        Log.w(TAG, "⚠️ Employee ID already exists: " + employeeId);
                        return;
                    }

                    // Proceed with registration
                    performRegistration(name, employeeId);
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Error checking existing persons", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error checking existing records. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void performRegistration(String name, String employeeId) {
        registerButton.setEnabled(false);
        registerButton.setText("Registering...");

        new Thread(() -> {
            try {
                Log.d(TAG, "🎯 Performing registration with Bitmap");
                boolean success = faceRecognitionDetector.registerPerson(name, employeeId, capturedFaceBitmap);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "✅ " + name + " registered successfully!", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "✅ Registration successful for: " + name);

                        // Clear form and reset
                        resetRegistrationForm();

                        instructionText.setText("✅ Registration complete! You can register another person or go back.");
                        faceStatusText.setText("Ready for next registration");
                        faceStatusText.setTextColor(0xFF666666);

                    } else {
                        Toast.makeText(this, "❌ Registration failed. Please try again.", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "❌ Registration failed for: " + name);
                        registerButton.setText("REGISTER");
                        registerButton.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception during registration", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Registration error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        hasGoodFace = false;
        capturedImageProxy = null;
        capturedFaceRect = null;

        // Clean up bitmaps
        if (capturedFaceBitmap != null && !capturedFaceBitmap.isRecycled()) {
            capturedFaceBitmap.recycle();
            capturedFaceBitmap = null;
        }
        if (currentFaceBitmap != null && !currentFaceBitmap.isRecycled()) {
            currentFaceBitmap.recycle();
            currentFaceBitmap = null;
        }

        registerButton.setText("REGISTER");
        registerButton.setEnabled(false);
        captureButton.setText("CAPTURE");
        captureButton.setEnabled(false);
    }

    /**
     * Extract face as Bitmap to avoid ImageProxy lifecycle issues
     */
    private Bitmap extractFaceAsBitmap(ImageProxy imageProxy, Rect faceRect) {
        try {
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) {
                Log.e(TAG, "❌ Failed to convert ImageProxy to Bitmap");
                return null;
            }

            // Add padding around face
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(fullBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(fullBitmap.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "❌ Invalid face dimensions: " + width + "x" + height);
                return null;
            }

            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Make square by cropping to center
            int size = Math.min(width, height);
            if (width != height) {
                int xOffset = (width - size) / 2;
                int yOffset = (height - size) / 2;
                faceBitmap = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);
            }

            Log.d(TAG, "✅ Face extracted as Bitmap: " + size + "x" + size);
            return faceBitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face as bitmap", e);
            return null;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.e(TAG, "❌ ImageProxy.getImage() returned null");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.e(TAG, "❌ Image has insufficient planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.e(TAG, "❌ Empty image buffers: Y=" + ySize + " U=" + uSize + " V=" + vSize);
                return null;
            }

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Mirror for front camera
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    private void convertYUV420ToRGB(byte[] yuv420sp, int[] rgb, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
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

        // Clean up resources
        try {
            if (antiSpoofingDetector != null) {
                antiSpoofingDetector.close();
            }
            if (faceRecognitionDetector != null) {
                faceRecognitionDetector.close();
            }

            // Clean up captured bitmaps
            if (capturedFaceBitmap != null && !capturedFaceBitmap.isRecycled()) {
                capturedFaceBitmap.recycle();
                capturedFaceBitmap = null;
            }
            if (currentFaceBitmap != null && !currentFaceBitmap.isRecycled()) {
                currentFaceBitmap.recycle();
                currentFaceBitmap = null;
            }

            Log.d(TAG, "✅ Resources cleaned up successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error during cleanup", e);
        }
    }
}