package com.example.facedetectionapp;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * Helper class for converting ImageProxy to Bitmap
 * This is needed for the FaceNet model to process camera frames
 */
public class ImageConversionHelper {
    private static final String TAG = "ImageConversionHelper";

    /**
     * Convert ImageProxy to Bitmap
     * This method handles YUV420 to RGB conversion for camera frames
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.w(TAG, "Image is null");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.w(TAG, "Not enough image planes: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            if (ySize == 0 || uSize == 0 || vSize == 0) {
                Log.w(TAG, "Empty buffer detected");
                return null;
            }

            // Create NV21 byte array
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            // Convert YUV to RGB
            int[] rgbArray = new int[width * height];
            convertYUV420ToRGB(nv21, rgbArray, width, height);

            // Create bitmap from RGB array
            Bitmap bitmap = Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);

            // Apply rotation and mirroring for front camera
            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            matrix.preScale(-1.0f, 1.0f); // Mirror for front camera

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);

            // Clean up original bitmap if we created a new one
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }

            return rotatedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert YUV420 to RGB
     * This is the core conversion algorithm
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

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }
}