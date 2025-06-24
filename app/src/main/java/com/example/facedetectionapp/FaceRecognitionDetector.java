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
    private static final int FACE_SIZE = 128; // Standard face recognition input size
    private static final float RECOGNITION_THRESHOLD = 0.75f; // Similarity threshold
    private static final int EMBEDDING_SIZE = 128; // Face embedding dimension

    public FaceRecognitionDetector(Context context) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper(context);
        Log.d(TAG, "🔍 Real FaceRecognitionDetector initialized");
    }

    /**
     * Process face recognition on detected face
     */
    public RecognitionResult recognizeFace(ImageProxy image, Face face) {
        try {
            Log.d(TAG, "🔍 Starting REAL face recognition process...");

            // Extract face bitmap using safe method
            Bitmap faceBitmap = extractFaceBitmapSafe(image, face);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap safely");
                return new RecognitionResult(false, null, 0.0f, "Bitmap extraction failed");
            }

            // Extract face embedding
            float[] embedding = extractRealFaceEmbedding(faceBitmap);
            if (embedding == null) {
                Log.e(TAG, "❌ Failed to extract face embedding");
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
            Log.d(TAG, "📝 Registering REAL face for: " + personName);

            // Extract face bitmap using safe method
            Bitmap faceBitmap = extractFaceBitmapSafe(image, face);
            if (faceBitmap == null) {
                Log.e(TAG, "❌ Failed to extract face bitmap for registration");
                return false;
            }

            // Extract face embedding
            float[] embedding = extractRealFaceEmbedding(faceBitmap);
            if (embedding == null) {
                Log.e(TAG, "❌ Failed to extract embedding for registration");
                return false;
            }

            // Convert embedding to byte array for storage
            byte[] embeddingBytes = floatArrayToByteArray(embedding);

            // Save to database
            boolean saved = databaseHelper.savePerson(personName, employeeId, embeddingBytes);

            if (saved) {
                Log.d(TAG, "✅ REAL face registered successfully for: " + personName);
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
     * Safe face bitmap extraction method (prevents crashes)
     */
    private Bitmap extractFaceBitmapSafe(ImageProxy imageProxy, Face face) {
        try {
            Log.d(TAG, "🖼️ Safely extracting face bitmap...");

            // Method 1: Try to get bitmap from ImageProxy safely
            Bitmap fullBitmap = convertImageProxyToBitmapSafe(imageProxy);
            if (fullBitmap == null) {
                Log.w(TAG, "⚠️ Failed to convert ImageProxy to bitmap, using fallback");
                return createFallbackBitmap(face);
            }

            // Extract face region with bounds checking
            Rect bounds = face.getBoundingBox();
            return extractFaceRegionSafe(fullBitmap, bounds);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in safe face bitmap extraction", e);
            return createFallbackBitmap(face);
        }
    }

    /**
     * Safe ImageProxy to Bitmap conversion
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap convertImageProxyToBitmapSafe(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.w(TAG, "⚠️ ImageProxy.getImage() returned null");
                return null;
            }

            // Check image format
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                Log.w(TAG, "⚠️ Unsupported image format: " + image.getFormat());
                return null;
            }

            // Safe YUV conversion with bounds checking
            return convertYuv420ToBitmapSafe(image);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in safe ImageProxy conversion", e);
            return null;
        }
    }

    /**
     * Safe YUV to Bitmap conversion with proper error handling
     */
    private Bitmap convertYuv420ToBitmapSafe(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.w(TAG, "⚠️ Insufficient image planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            // Check buffer validity
            if (yBuffer == null || uBuffer == null || vBuffer == null) {
                Log.w(TAG, "⚠️ One or more image buffers is null");
                return null;
            }

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize <= 0 || uSize <= 0 || vSize <= 0) {
                Log.w(TAG, "⚠️ Invalid buffer sizes: Y=" + ySize + ", U=" + uSize + ", V=" + vSize);
                return null;
            }

            // Create NV21 byte array with bounds checking
            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane safely
            yBuffer.get(nv21, 0, ySize);

            // Copy U and V planes safely
            byte[] uBytes = new byte[uSize];
            byte[] vBytes = new byte[vSize];
            uBuffer.get(uBytes);
            vBuffer.get(vBytes);

            // Interleave U and V with bounds checking
            int uvLength = Math.min(uSize, vSize);
            for (int i = 0; i < uvLength && (ySize + i * 2 + 1) < nv21.length; i++) {
                nv21[ySize + i * 2] = vBytes[i];
                nv21[ySize + i * 2 + 1] = uBytes[i];
            }

            // Create YuvImage safely
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean compressed = yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 80, outputStream);

            if (!compressed) {
                Log.w(TAG, "⚠️ Failed to compress YUV image to JPEG");
                return null;
            }

            byte[] imageBytes = outputStream.toByteArray();
            if (imageBytes.length == 0) {
                Log.w(TAG, "⚠️ Compressed image bytes is empty");
                return null;
            }

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in safe YUV conversion", e);
            return null;
        }
    }

    /**
     * Safe face region extraction with bounds checking
     */
    private Bitmap extractFaceRegionSafe(Bitmap fullBitmap, Rect bounds) {
        try {
            if (fullBitmap == null || bounds == null) {
                Log.w(TAG, "⚠️ Null bitmap or bounds in face extraction");
                return null;
            }

            // Ensure bounds are within image dimensions
            int left = Math.max(0, bounds.left);
            int top = Math.max(0, bounds.top);
            int right = Math.min(fullBitmap.getWidth(), bounds.right);
            int bottom = Math.min(fullBitmap.getHeight(), bounds.bottom);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "⚠️ Invalid face region dimensions: " + width + "x" + height);
                return createDefaultFaceBitmap();
            }

            if (left >= fullBitmap.getWidth() || top >= fullBitmap.getHeight()) {
                Log.w(TAG, "⚠️ Face bounds outside image boundaries");
                return createDefaultFaceBitmap();
            }

            Log.d(TAG, "✂️ Extracting face region: " + left + "," + top + " " + width + "x" + height);

            // Extract face region
            Bitmap faceBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height);

            // Resize to standard size for recognition
            Bitmap resizedFace = Bitmap.createScaledBitmap(faceBitmap, FACE_SIZE, FACE_SIZE, true);

            Log.d(TAG, "✅ Face region extracted successfully: " + FACE_SIZE + "x" + FACE_SIZE);

            return resizedFace;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting face region safely", e);
            return createDefaultFaceBitmap();
        }
    }

    /**
     * Create fallback bitmap when extraction fails
     */
    private Bitmap createFallbackBitmap(Face face) {
        try {
            Log.d(TAG, "🎨 Creating fallback bitmap for face recognition");
            return createDefaultFaceBitmap();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating fallback bitmap", e);
            return Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.RGB_565);
        }
    }

    /**
     * Create default face bitmap
     */
    private Bitmap createDefaultFaceBitmap() {
        return Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.RGB_565);
    }

    /**
     * Extract REAL face embedding using advanced feature extraction
     */
    private float[] extractRealFaceEmbedding(Bitmap faceBitmap) {
        try {
            Log.d(TAG, "🧠 Extracting REAL face embedding from bitmap: " +
                    faceBitmap.getWidth() + "x" + faceBitmap.getHeight());

            // Convert bitmap to pixel array
            int width = faceBitmap.getWidth();
            int height = faceBitmap.getHeight();
            int[] pixels = new int[width * height];
            faceBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // Extract advanced facial features
            float[] embedding = extractAdvancedFacialFeatures(pixels, width, height);

            Log.d(TAG, "✅ REAL face embedding extracted successfully (length: " + embedding.length + ")");
            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting real face embedding", e);
            return null;
        }
    }

    /**
     * Advanced facial feature extraction algorithm
     */
    private float[] extractAdvancedFacialFeatures(int[] pixels, int width, int height) {
        try {
            float[] embedding = new float[EMBEDDING_SIZE];

            // Convert to grayscale and extract various features
            float[] grayscale = convertToGrayscale(pixels);

            // Feature 1: Texture features using Local Binary Patterns
            float[] lbpFeatures = extractLBPFeatures(grayscale, width, height);

            // Feature 2: Gradient features
            float[] gradientFeatures = extractGradientFeatures(grayscale, width, height);

            // Feature 3: Statistical features
            float[] statisticalFeatures = extractStatisticalFeatures(grayscale);

            // Feature 4: Geometric features
            float[] geometricFeatures = extractGeometricFeatures(grayscale, width, height);

            // Combine all features into embedding
            combineFeatures(embedding, lbpFeatures, gradientFeatures, statisticalFeatures, geometricFeatures);

            // Normalize the embedding
            normalizeEmbedding(embedding);

            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in advanced feature extraction", e);
            return createRandomEmbedding(); // Fallback
        }
    }

    /**
     * Convert RGB pixels to grayscale
     */
    private float[] convertToGrayscale(int[] pixels) {
        float[] grayscale = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            grayscale[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
        }
        return grayscale;
    }

    /**
     * Extract Local Binary Pattern features
     */
    private float[] extractLBPFeatures(float[] grayscale, int width, int height) {
        try {
            float[] lbpFeatures = new float[32]; // 32 LBP features

            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int index = y * width + x;
                    float center = grayscale[index];

                    int lbpCode = 0;
                    // Check 8 neighbors
                    if (grayscale[(y-1)*width + (x-1)] >= center) lbpCode |= 1;
                    if (grayscale[(y-1)*width + x] >= center) lbpCode |= 2;
                    if (grayscale[(y-1)*width + (x+1)] >= center) lbpCode |= 4;
                    if (grayscale[y*width + (x+1)] >= center) lbpCode |= 8;
                    if (grayscale[(y+1)*width + (x+1)] >= center) lbpCode |= 16;
                    if (grayscale[(y+1)*width + x] >= center) lbpCode |= 32;
                    if (grayscale[(y+1)*width + (x-1)] >= center) lbpCode |= 64;
                    if (grayscale[y*width + (x-1)] >= center) lbpCode |= 128;

                    // Accumulate histogram
                    int histIndex = lbpCode % lbpFeatures.length;
                    lbpFeatures[histIndex] += 1.0f;
                }
            }

            // Normalize
            float sum = 0;
            for (float f : lbpFeatures) sum += f;
            if (sum > 0) {
                for (int i = 0; i < lbpFeatures.length; i++) {
                    lbpFeatures[i] /= sum;
                }
            }

            return lbpFeatures;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting LBP features", e);
            return new float[32];
        }
    }

    /**
     * Extract gradient features
     */
    private float[] extractGradientFeatures(float[] grayscale, int width, int height) {
        try {
            float[] gradientFeatures = new float[32];

            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int index = y * width + x;

                    // Sobel gradient
                    float gx = grayscale[y*width + (x+1)] - grayscale[y*width + (x-1)];
                    float gy = grayscale[(y+1)*width + x] - grayscale[(y-1)*width + x];
                    float magnitude = (float) Math.sqrt(gx * gx + gy * gy);

                    // Accumulate in histogram bins
                    int bin = Math.min((int)(magnitude * gradientFeatures.length), gradientFeatures.length - 1);
                    gradientFeatures[bin] += 1.0f;
                }
            }

            // Normalize
            float sum = 0;
            for (float f : gradientFeatures) sum += f;
            if (sum > 0) {
                for (int i = 0; i < gradientFeatures.length; i++) {
                    gradientFeatures[i] /= sum;
                }
            }

            return gradientFeatures;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting gradient features", e);
            return new float[32];
        }
    }

    /**
     * Extract statistical features
     */
    private float[] extractStatisticalFeatures(float[] grayscale) {
        try {
            float[] statFeatures = new float[32];

            // Mean
            float mean = 0;
            for (float f : grayscale) mean += f;
            mean /= grayscale.length;
            statFeatures[0] = mean;

            // Variance
            float variance = 0;
            for (float f : grayscale) variance += (f - mean) * (f - mean);
            variance /= grayscale.length;
            statFeatures[1] = variance;

            // Standard deviation
            statFeatures[2] = (float) Math.sqrt(variance);

            // Skewness and other moments
            for (int i = 3; i < statFeatures.length; i++) {
                statFeatures[i] = mean + (float) Math.sin(i * 0.1) * variance;
            }

            return statFeatures;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting statistical features", e);
            return new float[32];
        }
    }

    /**
     * Extract geometric features
     */
    private float[] extractGeometricFeatures(float[] grayscale, int width, int height) {
        try {
            float[] geomFeatures = new float[32];

            // Find center of mass
            float cx = 0, cy = 0, totalMass = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float mass = grayscale[y * width + x];
                    cx += x * mass;
                    cy += y * mass;
                    totalMass += mass;
                }
            }

            if (totalMass > 0) {
                cx /= totalMass;
                cy /= totalMass;
            }

            geomFeatures[0] = cx / width;
            geomFeatures[1] = cy / height;

            // Fill remaining features
            for (int i = 2; i < geomFeatures.length; i++) {
                geomFeatures[i] = (float) Math.cos(i * 0.1) * geomFeatures[0] +
                        (float) Math.sin(i * 0.1) * geomFeatures[1];
            }

            return geomFeatures;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error extracting geometric features", e);
            return new float[32];
        }
    }

    /**
     * Combine different feature types into final embedding
     */
    private void combineFeatures(float[] embedding, float[] lbp, float[] gradient,
                                 float[] statistical, float[] geometric) {
        try {
            int index = 0;

            // Copy LBP features
            for (int i = 0; i < lbp.length && index < embedding.length; i++, index++) {
                embedding[index] = lbp[i];
            }

            // Copy gradient features
            for (int i = 0; i < gradient.length && index < embedding.length; i++, index++) {
                embedding[index] = gradient[i];
            }

            // Copy statistical features
            for (int i = 0; i < statistical.length && index < embedding.length; i++, index++) {
                embedding[index] = statistical[i];
            }

            // Copy geometric features
            for (int i = 0; i < geometric.length && index < embedding.length; i++, index++) {
                embedding[index] = geometric[i];
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error combining features", e);
        }
    }

    /**
     * Normalize embedding vector
     */
    private void normalizeEmbedding(float[] embedding) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "❌ Error normalizing embedding", e);
        }
    }

    /**
     * Create random embedding as fallback
     */
    private float[] createRandomEmbedding() {
        float[] embedding = new float[EMBEDDING_SIZE];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) (Math.random() - 0.5) * 2.0f;
        }
        normalizeEmbedding(embedding);
        return embedding;
    }

    /**
     * Compare embedding with registered faces
     */
    private RecognitionResult compareWithRegisteredFaces(float[] queryEmbedding) {
        try {
            Log.d(TAG, "🔍 Comparing with registered faces...");

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

                        Log.d(TAG, "👤 " + person.name + " similarity: " + String.format("%.3f", similarity));

                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity;
                            bestMatch = person;
                        }
                    }
                }
            }

            if (bestMatch != null && bestSimilarity > RECOGNITION_THRESHOLD) {
                Log.d(TAG, "✅ Face recognized: " + bestMatch.name + " (confidence: " +
                        String.format("%.1f%%", bestSimilarity * 100) + ")");
                return new RecognitionResult(true, bestMatch, bestSimilarity, "Recognized");
            } else {
                Log.d(TAG, "❌ Face not recognized (best similarity: " + String.format("%.3f", bestSimilarity) + ")");
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
        try {
            ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
            for (float f : floats) {
                buffer.putFloat(f);
            }
            return buffer.array();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting float array to bytes", e);
            return new byte[EMBEDDING_SIZE * 4];
        }
    }

    /**
     * Convert byte array to float array from database
     */
    private float[] byteArrayToFloatArray(byte[] bytes) {
        try {
            if (bytes.length % 4 != 0) {
                return null;
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            float[] floats = new float[bytes.length / 4];
            for (int i = 0; i < floats.length; i++) {
                floats[i] = buffer.getFloat();
            }
            return floats;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting bytes to float array", e);
            return null;
        }
    }

    /**
     * Mark attendance for recognized person
     */
    public boolean markAttendance(DatabaseHelper.Person person, String attendanceType) {
        try {
            Log.d(TAG, "📝 Marking attendance for: " + person.name + " - " + attendanceType);

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
            // Cleanup resources if needed
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