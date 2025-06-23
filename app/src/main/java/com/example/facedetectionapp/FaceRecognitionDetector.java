package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.face.Face;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FaceRecognitionDetector {
    private static final String TAG = "FaceRecognitionDetector";
    private Context context;
    private DatabaseHelper databaseHelper;

    // Face recognition parameters
    private static final int FACE_SIZE = 112; // Standard face recognition input size
    private static final float RECOGNITION_THRESHOLD = 0.7f; // Similarity threshold

    public FaceRecognitionDetector(Context context) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper(context);
        Log.d(TAG, "🔍 FaceRecognitionDetector initialized");
    }

    /**
     * Process face recognition on detected face
     */
    public RecognitionResult recognizeFace(ImageProxy image, Face face) {
        try {
            Log.d(TAG, "🔍 Starting face recognition process...");

            // Extract face bitmap
            Bitmap faceBitmap = extractFaceBitmap(image, face);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap");
                return new RecognitionResult(false, null, 0.0f, "Bitmap extraction failed");
            }

            // Extract face embedding
            float[] embedding = extractFaceEmbedding(faceBitmap);
            if (embedding == null) {
                Log.e(TAG, "❌ Failed to extract embedding for recognition");
                return new RecognitionResult(false, null, 0.0f, "Embedding extraction failed");
            }

            Log.d(TAG, "✅ Face embedding extracted successfully (length: " + embedding.length + ")");

            // Compare with registered faces
            return compareWithRegisteredFaces(embedding);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in face recognition", e);
            return new RecognitionResult(false, null, 0.0f, "Recognition error: " + e.getMessage());
        }
    }

    /**
     * Register a new face for recognition
     */
    public boolean registerFace(ImageProxy image, Face face, String personName, String employeeId) {
        try {
            Log.d(TAG, "📝 Registering new face for: " + personName);

            // Extract face bitmap
            Bitmap faceBitmap = extractFaceBitmap(image, face);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap for registration");
                return false;
            }

            // Extract face embedding
            float[] embedding = extractFaceEmbedding(faceBitmap);
            if (embedding == null) {
                Log.e(TAG, "❌ Failed to extract embedding for registration");
                return false;
            }

            // Convert embedding to byte array for storage
            byte[] embeddingBytes = floatArrayToByteArray(embedding);

            // Save to database
            boolean saved = databaseHelper.savePerson(personName, employeeId, embeddingBytes);

            if (saved) {
                Log.d(TAG, "✅ Face registered successfully for: " + personName);
                return true;
            } else {
                Log.e(TAG, "❌ Failed to save person to database");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error registering face", e);
            return false;
        }
    }

    /**
     * Extract face bitmap from camera image
     */
    private Bitmap extractFaceBitmap(ImageProxy image, Face face) {
        try {
            Log.d(TAG, "🖼️ Extracting face bitmap from image: " +
                    image.getWidth() + "x" + image.getHeight());

            // Convert ImageProxy to Bitmap
            Bitmap fullBitmap = imageProxyToBitmap(image);
            if (fullBitmap == null) {
                Log.e(TAG, "❌ Failed to convert ImageProxy to Bitmap");
                return null;
            }

            // Get face bounds
            Rect bounds = face.getBoundingBox();
            Log.d(TAG, "👤 Face bounds: " + bounds.toString());

            // Ensure bounds are within image dimensions
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(fullBitmap.getWidth(), bounds.right);
            int bottom = Math.min(fullBitmap.getHeight(), bounds.bottom);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "❌ Invalid face dimensions: " + width + "x" + height);
                return null;
            }

            Log.d(TAG, "✂️ Cropping face: " + left + "," + top + " " + width + "x" + height);

            // Extract face region
            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Resize to standard size for recognition
            Bitmap resizedFace = Bitmap.createScaledBitmap(faceBitmap, FACE_SIZE, FACE_SIZE, true);

            Log.d(TAG, "✅ Face bitmap extracted successfully: " +
                    resizedFace.getWidth() + "x" + resizedFace.getHeight());

            return resizedFace;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face bitmap", e);
            return null;
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            // Method 1: Try using MediaImage (API 24+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Image mediaImage = image.getImage();
                if (mediaImage != null) {
                    Bitmap bitmap = mediaImageToBitmap(mediaImage);
                    if (bitmap != null) {
                        // Handle rotation
                        if (image.getImageInfo().getRotationDegrees() != 0) {
                            Matrix matrix = new Matrix();
                            matrix.postRotate(image.getImageInfo().getRotationDegrees());
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                    bitmap.getHeight(), matrix, true);
                        }
                        return bitmap;
                    }
                }
            }

            // Method 2: Fallback to YUV conversion
            return yuv420ToBitmap(image);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert MediaImage to Bitmap (API 24+)
     */
    private Bitmap mediaImageToBitmap(Image image) {
        try {
            if (image.getFormat() == ImageFormat.YUV_420_888) {
                return yuv420ImageToBitmap(image);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting MediaImage to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert YUV_420_888 Image to Bitmap
     */
    private Bitmap yuv420ImageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);

            // Copy UV planes (interleave U and V)
            byte[] uvBuffer = new byte[uSize];
            uBuffer.get(uvBuffer);
            byte[] vArray = new byte[vSize];
            vBuffer.get(vArray);

            for (int i = 0; i < uSize; i++) {
                nv21[ySize + i * 2] = vArray[i];
                nv21[ySize + i * 2 + 1] = uvBuffer[i];
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()),
                    100, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting YUV420 Image to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert ImageProxy YUV to Bitmap
     */
    private Bitmap yuv420ToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);

            // Copy and interleave U and V planes
            byte[] uvPixelStride = new byte[uSize];
            uBuffer.get(uvPixelStride);
            byte[] vPixelStride = new byte[vSize];
            vBuffer.get(vPixelStride);

            for (int i = 0; i < uSize; i++) {
                nv21[ySize + i * 2] = vPixelStride[i];
                nv21[ySize + i * 2 + 1] = uvPixelStride[i];
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()),
                    100, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            // Handle rotation
            if (image.getImageInfo().getRotationDegrees() != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(image.getImageInfo().getRotationDegrees());
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting YUV420 to Bitmap", e);
            return null;
        }
    }

    /**
     * Extract face embedding from bitmap
     * Note: This is a simplified version. In production, you'd use a proper
     * face recognition model like FaceNet, ArcFace, etc.
     */
    private float[] extractFaceEmbedding(Bitmap faceBitmap) {
        try {
            Log.d(TAG, "🧠 Extracting face embedding from bitmap: " +
                    faceBitmap.getWidth() + "x" + faceBitmap.getHeight());

            // For now, create a simple feature vector based on image properties
            // In production, replace this with actual deep learning model inference
            float[] embedding = createSimpleEmbedding(faceBitmap);

            Log.d(TAG, "✅ Face embedding extracted successfully (length: " + embedding.length + ")");
            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face embedding", e);
            return null;
        }
    }

    /**
     * Create a simple embedding (placeholder for real ML model)
     * Replace this with actual face recognition model like MobileFaceNet
     */
    private float[] createSimpleEmbedding(Bitmap bitmap) {
        try {
            // Simple feature extraction based on image characteristics
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // Create 128-dimensional feature vector (common size for face embeddings)
            float[] embedding = new float[128];

            // Extract simple statistical features
            long sumR = 0, sumG = 0, sumB = 0;
            for (int pixel : pixels) {
                sumR += (pixel >> 16) & 0xFF;
                sumG += (pixel >> 8) & 0xFF;
                sumB += pixel & 0xFF;
            }

            int totalPixels = pixels.length;
            float avgR = (float) sumR / totalPixels;
            float avgG = (float) sumG / totalPixels;
            float avgB = (float) sumB / totalPixels;

            // Fill embedding with normalized features
            for (int i = 0; i < embedding.length; i++) {
                if (i % 3 == 0) embedding[i] = avgR / 255.0f;
                else if (i % 3 == 1) embedding[i] = avgG / 255.0f;
                else embedding[i] = avgB / 255.0f;

                // Add some variance based on position
                embedding[i] += (float) Math.sin(i * 0.1) * 0.1f;
            }

            // Normalize the embedding
            float norm = 0;
            for (float f : embedding) {
                norm += f * f;
            }
            norm = (float) Math.sqrt(norm);

            if (norm > 0) {
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] /= norm;
                }
            }

            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating simple embedding", e);
            return null;
        }
    }

    /**
     * Compare embedding with registered faces
     */
    private RecognitionResult compareWithRegisteredFaces(float[] queryEmbedding) {
        try {
            Log.d(TAG, "🔍 Comparing with registered faces...");

            // Get all registered persons from database
            List<DatabaseHelper.Person> persons = databaseHelper.getAllPersons();

            if (persons.isEmpty()) {
                Log.d(TAG, "ℹ️ No registered faces found");
                return new RecognitionResult(false, null, 0.0f, "No registered faces");
            }

            float bestSimilarity = 0.0f;
            DatabaseHelper.Person bestMatch = null;

            for (DatabaseHelper.Person person : persons) {
                if (person.faceEncoding != null) {
                    float[] personEmbedding = byteArrayToFloatArray(person.faceEncoding);
                    if (personEmbedding != null) {
                        float similarity = calculateCosineSimilarity(queryEmbedding, personEmbedding);

                        Log.d(TAG, "👤 " + person.name + " similarity: " + similarity);

                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity;
                            bestMatch = person;
                        }
                    }
                }
            }

            if (bestMatch != null && bestSimilarity > RECOGNITION_THRESHOLD) {
                Log.d(TAG, "✅ Face recognized: " + bestMatch.name + " (confidence: " +
                        (bestSimilarity * 100) + "%)");
                return new RecognitionResult(true, bestMatch, bestSimilarity, "Recognized");
            } else {
                Log.d(TAG, "❌ Face not recognized (best similarity: " + bestSimilarity + ")");
                return new RecognitionResult(false, bestMatch, bestSimilarity, "Not recognized");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error comparing with registered faces", e);
            return new RecognitionResult(false, null, 0.0f, "Comparison error");
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    private float calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        norm1 = (float) Math.sqrt(norm1);
        norm2 = (float) Math.sqrt(norm2);

        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }

        return dotProduct / (norm1 * norm2);
    }

    /**
     * Convert float array to byte array for database storage
     */
    private byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    /**
     * Convert byte array to float array from database
     */
    private float[] byteArrayToFloatArray(byte[] bytes) {
        if (bytes.length % 4 != 0) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    /**
     * Mark attendance for recognized person
     */
    public boolean markAttendance(DatabaseHelper.Person person, String attendanceType) {
        try {
            Log.d(TAG, "📝 Marking attendance for: " + person.name + " - " + attendanceType);

            // Record attendance with default confidence
            boolean success = databaseHelper.recordAttendance(person.id, attendanceType, 1.0f);

            if (success) {
                Log.d(TAG, "✅ Attendance marked successfully for: " + person.name);
            } else {
                Log.e(TAG, "❌ Failed to mark attendance for: " + person.name);
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance", e);
            return false;
        }
    }

    /**
     * Mark attendance with confidence score
     */
    public boolean markAttendance(DatabaseHelper.Person person, String attendanceType, float confidence) {
        try {
            Log.d(TAG, "📝 Marking attendance for: " + person.name + " - " + attendanceType + " (confidence: " + confidence + ")");

            boolean success = databaseHelper.recordAttendance(person.id, attendanceType, confidence);

            if (success) {
                Log.d(TAG, "✅ Attendance marked successfully for: " + person.name);
            } else {
                Log.e(TAG, "❌ Failed to mark attendance for: " + person.name);
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance", e);
            return false;
        }
    }

    /**
     * Close and cleanup resources
     */
    public void close() {
        try {
            Log.d(TAG, "🔒 Closing FaceRecognitionDetector");
            // Add any cleanup logic here if needed
            // For now, just log the close operation
        } catch (Exception e) {
            Log.e(TAG, "❌ Error closing FaceRecognitionDetector", e);
        }
    }

    /**
     * Recognition result class
     */
    public static class RecognitionResult {
        public boolean isRecognized;
        public DatabaseHelper.Person person;
        public float confidence;
        public String message;

        public RecognitionResult(boolean isRecognized, DatabaseHelper.Person person,
                                 float confidence, String message) {
            this.isRecognized = isRecognized;
            this.person = person;
            this.confidence = confidence;
            this.message = message;
        }
    }
}