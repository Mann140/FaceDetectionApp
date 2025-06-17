package com.example.facedetectionapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
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

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final long ATTENDANCE_COOLDOWN = 30000; // 30 seconds between attendance marks
    private static final long UI_UPDATE_INTERVAL = 100; // Update UI every 100ms

    // UI Components
    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private TextView statusTextView;
    private TextView attendanceCountTextView;
    private Button registerFaceButton;
    private Button viewAttendanceButton;

    // Camera and ML Kit
    private ProcessCameraProvider cameraProvider;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;

    // Face Recognition Components
    private AntiSpoofingDetector antiSpoofingDetector;
    private FaceRecognitionProcessor faceRecognitionProcessor;
    private AttendanceDatabase database;

    // State Management
    private long lastAttendanceTime = 0;
    private long lastUIUpdateTime = 0;
    private boolean isProcessingFace = false;
    private List<FaceData> currentFaceDataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🚀 MainActivity started");

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
        faceOverlayView = findViewById(R.id.faceOverlayView);
        statusTextView = findViewById(R.id.statusTextView);
        attendanceCountTextView = findViewById(R.id.attendanceCountTextView);
        registerFaceButton = findViewById(R.id.registerFaceButton);
        viewAttendanceButton = findViewById(R.id.viewAttendanceButton);

        // Configure face overlay
        faceOverlayView.setPreviewSize(640, 480);
        faceOverlayView.setFrontCamera(true);

        // Set initial status
        updateStatus("Initializing camera...");
    }

    private void initializeComponents() {
        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize database
        database = new AttendanceDatabase(this);
        updateAttendanceCount();

        // Initialize face detector with high performance settings
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f) // Minimum face size relative to image
                .enableTracking() // Enable face tracking for better performance
                .build();

        faceDetector = FaceDetection.getClient(options);

        // Initialize anti-spoofing detector
        antiSpoofingDetector = new AntiSpoofingDetector(this);

        // Initialize face recognition processor
        faceRecognitionProcessor = new FaceRecognitionProcessor(this);

        Log.d(TAG, "✅ All components initialized");
    }

    private void setupClickListeners() {
        registerFaceButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterFaceActivity.class);
            startActivity(intent);
        });

        viewAttendanceButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AttendanceActivity.class);
            startActivity(intent);
        });
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
                updateStatus("Camera ready - Looking for faces...");
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

            // Skip processing if already processing a face
            if (isProcessingFace) {
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
            currentFaceDataList.clear();

            if (faces.isEmpty()) {
                runOnUiThread(() -> {
                    updateStatus("No faces detected");
                    faceOverlayView.clearFaces();
                });
                imageProxy.close();
                return;
            }

            Log.d(TAG, "🔍 Processing " + faces.size() + " detected face(s)");

            // Process each detected face
            for (Face face : faces) {
                processAttendanceFace(imageProxy, face, currentFaceDataList);
            }

            // Update UI with current face data
            runOnUiThread(() -> {
                faceOverlayView.updateFaceData(new ArrayList<>(currentFaceDataList));
                if (!currentFaceDataList.isEmpty()) {
                    FaceData firstFace = currentFaceDataList.get(0);
                    updateStatus(firstFace.label + " (" + String.format("%.1f", firstFace.confidence) + "%)");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing faces", e);
            runOnUiThread(() -> updateStatus("Face processing error"));
        } finally {
            imageProxy.close();
        }
    }

    private void processAttendanceFace(ImageProxy imageProxy, Face face, List<FaceData> faceDataList) {
        // Check if processors are available
        if (antiSpoofingDetector == null || faceRecognitionProcessor == null) {
            FaceData errorData = new FaceData(
                    face.getBoundingBox(),
                    false,
                    0.0f,
                    "Models not loaded",
                    false
            );
            faceDataList.add(errorData);
            return;
        }

        try {
            // First, run anti-spoofing detection
            FaceData spoofData = antiSpoofingDetector.analyzeFace(imageProxy, face.getBoundingBox());

            Log.d(TAG, "🔍 Anti-spoof result: isReal=" + spoofData.isReal +
                    ", confidence=" + spoofData.confidence + "%");

            if (spoofData.isReal && spoofData.confidence > 70.0f) {
                // ✅ FIXED: Extract face bitmap immediately while ImageProxy is still valid
                Bitmap faceBitmap = extractFaceBitmapFromImageProxy(imageProxy, face.getBoundingBox());

                if (faceBitmap != null) {
                    // Add temporary data while processing
                    FaceData tempData = new FaceData(
                            face.getBoundingBox(),
                            true,
                            spoofData.confidence,
                            "Processing...",
                            false
                    );
                    faceDataList.add(tempData);

                    // Run face recognition on the extracted bitmap (in background thread)
                    runFaceRecognitionAsync(faceBitmap, face.getBoundingBox(), spoofData, faceDataList, tempData);
                } else {
                    Log.e(TAG, "❌ Failed to extract face bitmap");
                    FaceData faceData = new FaceData(
                            face.getBoundingBox(),
                            true,
                            spoofData.confidence,
                            "Real but extraction failed",
                            false
                    );
                    faceDataList.add(faceData);
                }
            } else {
                // Face is detected as fake or low confidence
                Log.d(TAG, "🚫 Face rejected: confidence=" + spoofData.confidence + "%");
                faceDataList.add(spoofData);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing face", e);
            FaceData errorData = new FaceData(
                    face.getBoundingBox(),
                    false,
                    0.0f,
                    "Processing error",
                    false
            );
            faceDataList.add(errorData);
        }
    }

    // ✅ FIXED: Extract face bitmap immediately while ImageProxy is still valid
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap extractFaceBitmapFromImageProxy(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Check if image is still valid
            android.media.Image image = imageProxy.getImage();
            if (image == null) {
                Log.e(TAG, "❌ ImageProxy.getImage() returned null");
                return null;
            }

            // Get dimensions safely
            int width = image.getWidth();
            int height = image.getHeight();

            Log.d(TAG, "🔍 Image dimensions: " + width + "x" + height);
            Log.d(TAG, "🔍 Face rect: " + faceRect.toString());

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.e(TAG, "❌ Insufficient image planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.e(TAG, "❌ Empty image buffers: Y=" + ySize + ", U=" + uSize + ", V=" + vSize);
                return null;
            }

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            Bitmap fullBitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Mirror the image for front camera
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            Bitmap mirroredBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width, height, matrix, false);

            // Extract face region with padding
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(mirroredBitmap.getWidth(), faceRect.right + padding);
            int bottom = Math.min(mirroredBitmap.getHeight(), faceRect.bottom + padding);

            int faceWidth = right - left;
            int faceHeight = bottom - top;

            if (faceWidth <= 0 || faceHeight <= 0) {
                Log.e(TAG, "❌ Invalid face dimensions: " + faceWidth + "x" + faceHeight);
                return null;
            }

            Bitmap faceBitmap = Bitmap.createBitmap(mirroredBitmap, left, top, faceWidth, faceHeight);

            // Make it square
            int size = Math.min(faceWidth, faceHeight);
            if (faceWidth != faceHeight) {
                int xOffset = (faceWidth - size) / 2;
                int yOffset = (faceHeight - size) / 2;
                faceBitmap = Bitmap.createBitmap(faceBitmap, xOffset, yOffset, size, size);
            }

            Log.d(TAG, "✅ Face extraction successful: " + size + "x" + size);
            return faceBitmap;

        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ Image already closed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face", e);
            return null;
        }
    }

    // ✅ NEW: Async face recognition method
    private void runFaceRecognitionAsync(Bitmap faceBitmap, Rect faceRect, FaceData spoofData,
                                         List<FaceData> faceDataList, FaceData tempData) {
        // Set processing flag
        isProcessingFace = true;

        // Run face recognition in background thread
        new Thread(() -> {
            try {
                Log.d(TAG, "🔍 Running face recognition...");

                // Generate face embedding from bitmap
                float[] faceEmbedding = faceRecognitionProcessor.generateEmbeddingFromBitmap(faceBitmap);

                if (faceEmbedding != null) {
                    // Compare with stored faces
                    FaceRecognitionProcessor.RecognitionResult recognitionResult =
                            faceRecognitionProcessor.recognizeFromEmbedding(faceEmbedding);

                    runOnUiThread(() -> {
                        // Remove temporary data
                        faceDataList.remove(tempData);

                        if (recognitionResult.user != null) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastAttendanceTime > ATTENDANCE_COOLDOWN) {
                                markAttendance(recognitionResult.user, recognitionResult.confidence);
                                lastAttendanceTime = currentTime;

                                Log.d(TAG, "✅ Attendance marked for: " + recognitionResult.user.name);
                            } else {
                                Log.d(TAG, "⏰ Attendance cooldown active");
                            }

                            FaceData faceData = new FaceData(
                                    faceRect,
                                    true,
                                    recognitionResult.confidence * 100,
                                    "✅ " + recognitionResult.user.name,
                                    false
                            );
                            faceDataList.add(faceData);
                        } else {
                            Log.d(TAG, "❓ Real face but unknown person");
                            FaceData faceData = new FaceData(
                                    faceRect,
                                    true,
                                    spoofData.confidence,
                                    "Real but Unknown",
                                    false
                            );
                            faceDataList.add(faceData);
                        }

                        // Update overlay
                        faceOverlayView.updateFaceData(new ArrayList<>(faceDataList));
                    });
                } else {
                    Log.e(TAG, "❌ Failed to generate face embedding");
                    runOnUiThread(() -> {
                        // Remove temporary data
                        faceDataList.remove(tempData);

                        FaceData faceData = new FaceData(
                                faceRect,
                                true,
                                spoofData.confidence,
                                "Real but recognition failed",
                                false
                        );
                        faceDataList.add(faceData);
                        faceOverlayView.updateFaceData(new ArrayList<>(faceDataList));
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Face recognition error", e);
                runOnUiThread(() -> {
                    // Remove temporary data
                    faceDataList.remove(tempData);

                    FaceData faceData = new FaceData(
                            faceRect,
                            true,
                            spoofData.confidence,
                            "Recognition error",
                            false
                    );
                    faceDataList.add(faceData);
                    faceOverlayView.updateFaceData(new ArrayList<>(faceDataList));
                });
            } finally {
                // Clear processing flag
                isProcessingFace = false;
            }
        }).start();
    }

    // ✅ YUV to RGB conversion helper
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

    private void markAttendance(AttendanceDatabase.User user, float confidence) {
        try {
            // Mark attendance in database
            long attendanceId = database.markAttendance(user.id);

            if (attendanceId != -1) {
                // Show success toast
                String message = "✅ Attendance marked for " + user.name +
                        " (" + String.format("%.1f", confidence * 100) + "% confidence)";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                // Update attendance count
                updateAttendanceCount();

                // Log success
                Log.d(TAG, "✅ Attendance marked: " + user.name + " at " +
                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

            } else {
                Toast.makeText(this, "❌ Failed to mark attendance", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "❌ Failed to mark attendance for user: " + user.name);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance", e);
            Toast.makeText(this, "❌ Error marking attendance", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String status) {
        if (statusTextView != null) {
            statusTextView.setText(status);
            Log.d(TAG, "📱 Status: " + status);
        }
    }

    private void updateAttendanceCount() {
        try {
            List<AttendanceDatabase.AttendanceRecord> todayRecords = database.getTodayAttendance();
            String countText = "Today's Attendance: " + todayRecords.size();

            if (attendanceCountTextView != null) {
                attendanceCountTextView.setText(countText);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating attendance count", e);
            if (attendanceCountTextView != null) {
                attendanceCountTextView.setText("Attendance: Error");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAttendanceCount(); // Refresh count when returning to activity
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

        Log.d(TAG, "🛑 MainActivity destroyed");
    }

    // ===== FACE DATA CLASS =====

    public static class FaceData {
        public final Rect boundingBox;
        public final boolean isReal;
        public final float confidence;
        public final String label;
        public final boolean isRecognized;

        public FaceData(Rect boundingBox, boolean isReal, float confidence, String label, boolean isRecognized) {
            this.boundingBox = boundingBox;
            this.isReal = isReal;
            this.confidence = confidence;
            this.label = label;
            this.isRecognized = isRecognized;
        }

        @Override
        public String toString() {
            return "FaceData{" +
                    "isReal=" + isReal +
                    ", confidence=" + confidence +
                    ", label='" + label + '\'' +
                    ", isRecognized=" + isRecognized +
                    '}';
        }
    }
}