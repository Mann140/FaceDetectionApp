package com.example.facedetectionapp;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * Utility class for bitmap and image processing operations
 * Handles conversion between different image formats and cropping operations
 */
public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

    /**
     * Extract face bitmap from ImageProxy using face bounding box
     * @param imageProxy Camera frame from CameraX
     * @param faceRect Bounding box of the detected face
     * @return Cropped face bitmap, or null if extraction fails
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    public static Bitmap extractFaceFromImageProxy(ImageProxy imageProxy, Rect faceRect) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap fullBitmap = imageProxyToBitmap(imageProxy);
            if (fullBitmap == null) {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap");
                return null;
            }

            // Crop face from full bitmap
            return cropFaceFromBitmap(fullBitmap, faceRect);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting face from ImageProxy: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     * Handles YUV420 to RGB conversion and rotation
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.e(TAG, "ImageProxy.getImage() returned null");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.e(TAG, "Invalid number of image planes: " + planes.length);
                return null;
            }

            // Extract YUV data
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.e(TAG, "Empty buffer detected");
                return null;
            }

            // Create NV21 byte array
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            // Convert YUV420 to RGB
            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            // Create bitmap
            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Apply transformations (mirror for front camera)
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f); // Mirror horizontally

            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Crop face from bitmap using bounding box with padding
     * @param source Source bitmap
     * @param faceRect Face bounding box
     * @return Cropped face bitmap
     */
    public static Bitmap cropFaceFromBitmap(Bitmap source, Rect faceRect) {
        if (source == null || faceRect == null) {
            Log.e(TAG, "Invalid input parameters for face cropping");
            return null;
        }

        try {
            // Calculate padding (10% of face size, minimum 20 pixels)
            int padding = Math.max(20, Math.min(faceRect.width(), faceRect.height()) / 10);

            // Apply padding to bounding box
            int left = Math.max(0, faceRect.left - padding);
            int top = Math.max(0, faceRect.top - padding);
            int right = Math.min(source.getWidth(), faceRect.right + padding);
            int bottom = Math.min(source.getHeight(), faceRect.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            // Validate dimensions
            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid crop dimensions: " + width + "x" + height);
                return null;
            }

            // Crop the face region
            Bitmap croppedBitmap = Bitmap.createBitmap(source, left, top, width, height);

            // Make it square by cropping to the smaller dimension
            int size = Math.min(width, height);
            if (width != height) {
                int xOffset = (width - size) / 2;
                int yOffset = (height - size) / 2;
                croppedBitmap = Bitmap.createBitmap(croppedBitmap, xOffset, yOffset, size, size);
            }

            Log.d(TAG, String.format("Face cropped successfully: %dx%d -> %dx%d",
                    source.getWidth(), source.getHeight(), size, size));

            return croppedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error cropping face from bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Rotate bitmap by specified degrees
     * @param source Source bitmap
     * @param degrees Rotation angle in degrees
     * @return Rotated bitmap
     */
    public static Bitmap rotateBitmap(Bitmap source, float degrees) {
        if (source == null) return null;

        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);
        } catch (Exception e) {
            Log.e(TAG, "Error rotating bitmap: " + e.getMessage(), e);
            return source; // Return original on failure
        }
    }

    /**
     * Flip bitmap horizontally
     * @param source Source bitmap
     * @return Horizontally flipped bitmap
     */
    public static Bitmap flipBitmapHorizontally(Bitmap source) {
        if (source == null) return null;

        try {
            Matrix matrix = new Matrix();
            matrix.postScale(-1f, 1f, source.getWidth() / 2f, source.getHeight() / 2f);
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Error flipping bitmap: " + e.getMessage(), e);
            return source; // Return original on failure
        }
    }

    /**
     * Resize bitmap to specified dimensions
     * @param source Source bitmap
     * @param width Target width
     * @param height Target height
     * @return Resized bitmap
     */
    public static Bitmap resizeBitmap(Bitmap source, int width, int height) {
        if (source == null) return null;

        try {
            return Bitmap.createScaledBitmap(source, width, height, true);
        } catch (Exception e) {
            Log.e(TAG, "Error resizing bitmap: " + e.getMessage(), e);
            return source; // Return original on failure
        }
    }

    /**
     * Convert YUV420 format to RGB array
     * This handles the color space conversion from camera YUV to RGB
     */
    private static void convertYUV420ToRGB(byte[] yuv420sp, int[] rgb, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width;
            int u = 0, v = 0;

            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;

                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                // YUV to RGB conversion formula
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                // Clamp values to 0-255 range
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                // Combine RGB values into ARGB pixel
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    /**
     * Calculate bitmap memory usage in bytes
     * @param bitmap Bitmap to analyze
     * @return Memory usage in bytes
     */
    public static long getBitmapMemorySize(Bitmap bitmap) {
        if (bitmap == null) return 0;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            return bitmap.getAllocationByteCount();
        } else {
            return bitmap.getByteCount();
        }
    }

    /**
     * Check if bitmap is valid and not recycled
     * @param bitmap Bitmap to check
     * @return true if bitmap is valid
     */
    public static boolean isBitmapValid(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0;
    }

    /**
     * Safe bitmap recycling
     * @param bitmap Bitmap to recycle
     */
    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                bitmap.recycle();
            } catch (Exception e) {
                Log.w(TAG, "Error recycling bitmap: " + e.getMessage());
            }
        }
    }

    /**
     * Create a copy of a bitmap
     * @param source Source bitmap
     * @return Copy of the bitmap
     */
    public static Bitmap copyBitmap(Bitmap source) {
        if (!isBitmapValid(source)) return null;

        try {
            return source.copy(source.getConfig(), false);
        } catch (Exception e) {
            Log.e(TAG, "Error copying bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Log bitmap information for debugging
     * @param bitmap Bitmap to analyze
     * @param tag Log tag
     */
    public static void logBitmapInfo(Bitmap bitmap, String tag) {
        if (bitmap == null) {
            Log.d(tag, "Bitmap is null");
            return;
        }

        Log.d(tag, String.format("Bitmap info: %dx%d, Config: %s, Size: %d bytes, Recycled: %s",
                bitmap.getWidth(), bitmap.getHeight(),
                bitmap.getConfig() != null ? bitmap.getConfig().toString() : "null",
                getBitmapMemorySize(bitmap),
                bitmap.isRecycled()));
    }
}