package com.example.facedetectionapp;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// Helper class for operations on Bitmaps
public class BitmapUtils {

    // Crop the given bitmap with the given rect.
    public static Bitmap cropRectFromBitmap(Bitmap source, Rect rect) {
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height());
    }

    public static boolean validateRect(Bitmap cameraFrameBitmap, Rect boundingBox) {
        return boundingBox.left >= 0 &&
                boundingBox.top >= 0 &&
                (boundingBox.left + boundingBox.width()) < cameraFrameBitmap.getWidth() &&
                (boundingBox.top + boundingBox.height()) < cameraFrameBitmap.getHeight();
    }

    // Get the image as a Bitmap from given Uri
    // Source -> https://developer.android.com/training/data-storage/shared/documents-files#bitmap
    public static Bitmap getBitmapFromUri(ContentResolver contentResolver, Uri uri) {
        try {
            return BitmapFactory.decodeStream(contentResolver.openInputStream(uri));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Rotate the given `source` by `degrees`.
    // See this SO answer -> https://stackoverflow.com/a/16219591/10878733
    public static Bitmap rotateBitmap(Bitmap source, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);
    }

    // Use this method to save a Bitmap to the internal storage (app-specific storage) of your device.
    public static void saveBitmap(Context context, Bitmap image, String name) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(
                    new File(context.getFilesDir().getAbsolutePath() + "/" + name + ".png"));
            image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the image as a Bitmap from given Uri and fix the rotation using the Exif interface
    // Source -> https://stackoverflow.com/questions/14066038/why-does-an-image-captured-using-camera-intent-gets-rotated-on-some-devices-on-a
    public static Bitmap getFixedBitmap(Context context, Uri imageFileUri) {
        try {
            Bitmap imageBitmap = getBitmapFromUri(context.getContentResolver(), imageFileUri);
            if (imageBitmap == null) return null;

            ExifInterface exifInterface = new ExifInterface(
                    context.getContentResolver().openInputStream(imageFileUri));

            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateBitmap(imageBitmap, 90f);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateBitmap(imageBitmap, 180f);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateBitmap(imageBitmap, 270f);
                default:
                    return imageBitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}