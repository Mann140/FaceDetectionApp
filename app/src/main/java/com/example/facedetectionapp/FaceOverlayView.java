package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {

    private static final String TAG = "FaceOverlayView";

    // Paint objects for different drawing elements
    private Paint faceBoundingBoxPaint;
    private Paint faceRealPaint;
    private Paint faceFakePaint;
    private Paint landmarkPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint;

    // Face data
    private List<FaceOverlayData> faceOverlayDataList = new ArrayList<>();

    // Scaling factors for coordinate transformation
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private int previewWidth = 640;
    private int previewHeight = 480;
    private boolean isFrontCamera = true;

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paint objects
        faceBoundingBoxPaint = new Paint();
        faceBoundingBoxPaint.setStyle(Paint.Style.STROKE);
        faceBoundingBoxPaint.setStrokeWidth(8f);
        faceBoundingBoxPaint.setAntiAlias(true);
        faceBoundingBoxPaint.setColor(Color.GREEN);

        faceRealPaint = new Paint();
        faceRealPaint.setStyle(Paint.Style.STROKE);
        faceRealPaint.setStrokeWidth(8f);
        faceRealPaint.setAntiAlias(true);
        faceRealPaint.setColor(Color.GREEN);

        faceFakePaint = new Paint();
        faceFakePaint.setStyle(Paint.Style.STROKE);
        faceFakePaint.setStrokeWidth(8f);
        faceFakePaint.setAntiAlias(true);
        faceFakePaint.setColor(Color.RED);

        landmarkPaint = new Paint();
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(4f);
        landmarkPaint.setAntiAlias(true);
        landmarkPaint.setColor(Color.YELLOW);

        textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(40f);
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);

        textBackgroundPaint = new Paint();
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setAlpha(180);

        Log.d(TAG, "✅ FaceOverlayView initialized");
    }

    /**
     * Update face overlay data from ML Kit Face objects
     */
    public void updateFaces(List<Face> faces) {
        faceOverlayDataList.clear();

        for (Face face : faces) {
            FaceOverlayData overlayData = new FaceOverlayData();
            overlayData.boundingBox = face.getBoundingBox();
            overlayData.trackingId = face.getTrackingId();
            overlayData.headEulerAngleY = face.getHeadEulerAngleY();
            overlayData.headEulerAngleZ = face.getHeadEulerAngleZ();
            overlayData.smilingProbability = face.getSmilingProbability();
            overlayData.leftEyeOpenProbability = face.getLeftEyeOpenProbability();
            overlayData.rightEyeOpenProbability = face.getRightEyeOpenProbability();
            overlayData.landmarks = face.getAllLandmarks();

            // Default values for custom analysis
            overlayData.isReal = true;
            overlayData.confidence = 85.0f;
            overlayData.label = "Face Detected";
            overlayData.isRecognized = false;

            faceOverlayDataList.add(overlayData);
        }

        invalidate(); // Trigger redraw
    }

    /**
     * Update face overlay data from custom FaceData objects (from MainActivity)
     */
    public void updateFaceData(List<MainActivity.FaceData> faceDataList) {
        faceOverlayDataList.clear();

        for (MainActivity.FaceData faceData : faceDataList) {
            FaceOverlayData overlayData = new FaceOverlayData();
            overlayData.boundingBox = faceData.boundingBox;
            overlayData.isReal = faceData.isReal;
            overlayData.confidence = faceData.confidence;
            overlayData.label = faceData.label;
            overlayData.isRecognized = faceData.isRecognized;

            // Default values for ML Kit properties
            overlayData.trackingId = null;
            overlayData.headEulerAngleY = 0.0f;
            overlayData.headEulerAngleZ = 0.0f;
            overlayData.smilingProbability = null;
            overlayData.leftEyeOpenProbability = null;
            overlayData.rightEyeOpenProbability = null;
            overlayData.landmarks = new ArrayList<>();

            faceOverlayDataList.add(overlayData);
        }

        invalidate(); // Trigger redraw
    }

    /**
     * Set camera preview dimensions for coordinate scaling
     */
    public void setPreviewSize(int width, int height) {
        this.previewWidth = width;
        this.previewHeight = height;
        updateScaleFactors();
    }

    /**
     * Set whether using front camera (for mirroring)
     */
    public void setFrontCamera(boolean isFrontCamera) {
        this.isFrontCamera = isFrontCamera;
    }

    /**
     * Update scaling factors based on view and preview dimensions
     */
    private void updateScaleFactors() {
        if (getWidth() > 0 && getHeight() > 0) {
            scaleX = (float) getWidth() / previewWidth;
            scaleY = (float) getHeight() / previewHeight;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScaleFactors();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faceOverlayDataList.isEmpty()) {
            return;
        }

        updateScaleFactors();

        for (FaceOverlayData overlayData : faceOverlayDataList) {
            drawFace(canvas, overlayData);
        }
    }

    /**
     * Draw a single face with all its elements
     */
    private void drawFace(Canvas canvas, FaceOverlayData overlayData) {
        // Transform bounding box coordinates
        RectF transformedRect = transformRect(overlayData.boundingBox);

        // Choose paint based on face analysis
        Paint paint = overlayData.isReal ? faceRealPaint : faceFakePaint;

        // Set color based on recognition status
        if (overlayData.isRecognized) {
            paint.setColor(Color.GREEN);
        } else if (overlayData.isReal) {
            if (overlayData.label.contains("Unknown")) {
                paint.setColor(Color.YELLOW);
            } else {
                paint.setColor(Color.CYAN);
            }
        } else {
            paint.setColor(Color.RED);
        }

        // Draw bounding box
        canvas.drawRect(transformedRect, paint);

        // Draw landmarks if available
        if (overlayData.landmarks != null && !overlayData.landmarks.isEmpty()) {
            drawLandmarks(canvas, overlayData.landmarks);
        }

        // Draw face information text
        drawFaceInfo(canvas, transformedRect, overlayData);

        // Draw additional analysis info
        drawAnalysisInfo(canvas, transformedRect, overlayData);
    }

    /**
     * Transform rectangle coordinates for display
     */
    private RectF transformRect(Rect originalRect) {
        RectF transformedRect = new RectF();

        if (isFrontCamera) {
            // Mirror for front camera
            transformedRect.left = getWidth() - (originalRect.right * scaleX);
            transformedRect.right = getWidth() - (originalRect.left * scaleX);
        } else {
            transformedRect.left = originalRect.left * scaleX;
            transformedRect.right = originalRect.right * scaleX;
        }

        transformedRect.top = originalRect.top * scaleY;
        transformedRect.bottom = originalRect.bottom * scaleY;

        return transformedRect;
    }

    /**
     * Draw face landmarks
     */
    private void drawLandmarks(Canvas canvas, List<FaceLandmark> landmarks) {
        for (FaceLandmark landmark : landmarks) {
            float x = landmark.getPosition().x * scaleX;
            float y = landmark.getPosition().y * scaleY;

            // Mirror x coordinate for front camera
            if (isFrontCamera) {
                x = getWidth() - x;
            }

            canvas.drawCircle(x, y, 6f, landmarkPaint);
        }
    }

    /**
     * Draw face information text
     */
    private void drawFaceInfo(Canvas canvas, RectF rect, FaceOverlayData overlayData) {
        // Prepare label text
        String label = overlayData.label;
        if (overlayData.confidence > 0) {
            label += " (" + String.format("%.1f", overlayData.confidence) + "%)";
        }

        // Measure text for background
        Rect textBounds = new Rect();
        textPaint.getTextBounds(label, 0, label.length(), textBounds);

        // Draw text background
        RectF textBackground = new RectF(
                rect.left - 10,
                rect.top - textBounds.height() - 20,
                rect.left + textBounds.width() + 20,
                rect.top - 5
        );
        canvas.drawRect(textBackground, textBackgroundPaint);

        // Draw text
        canvas.drawText(label, rect.left, rect.top - 10, textPaint);
    }

    /**
     * Draw additional analysis information
     */
    private void drawAnalysisInfo(Canvas canvas, RectF rect, FaceOverlayData overlayData) {
        List<String> infoLines = new ArrayList<>();

        // Add tracking ID if available
        if (overlayData.trackingId != null) {
            infoLines.add("ID: " + overlayData.trackingId);
        }

        // Add head rotation info
        if (Math.abs(overlayData.headEulerAngleY) > 5 || Math.abs(overlayData.headEulerAngleZ) > 5) {
            infoLines.add("Head: " + String.format("%.1f°, %.1f°",
                    overlayData.headEulerAngleY, overlayData.headEulerAngleZ));
        }

        // Add expression info
        if (overlayData.smilingProbability != null && overlayData.smilingProbability > 0.5f) {
            infoLines.add("😊 Smiling: " + String.format("%.0f%%", overlayData.smilingProbability * 100));
        }

        // Add eye state info
        if (overlayData.leftEyeOpenProbability != null && overlayData.rightEyeOpenProbability != null) {
            if (overlayData.leftEyeOpenProbability < 0.5f || overlayData.rightEyeOpenProbability < 0.5f) {
                infoLines.add("😴 Eyes: " + String.format("L%.0f%% R%.0f%%",
                        overlayData.leftEyeOpenProbability * 100, overlayData.rightEyeOpenProbability * 100));
            }
        }

        // Draw info lines
        float textY = rect.bottom + 30;
        textPaint.setTextSize(30f);

        for (String info : infoLines) {
            // Measure text for background
            Rect textBounds = new Rect();
            textPaint.getTextBounds(info, 0, info.length(), textBounds);

            // Draw text background
            RectF textBackground = new RectF(
                    rect.left - 5,
                    textY - textBounds.height() - 5,
                    rect.left + textBounds.width() + 10,
                    textY + 5
            );
            canvas.drawRect(textBackground, textBackgroundPaint);

            // Draw text
            canvas.drawText(info, rect.left, textY, textPaint);
            textY += textBounds.height() + 10;
        }

        textPaint.setTextSize(40f); // Reset text size
    }

    /**
     * Clear all face overlays
     */
    public void clearFaces() {
        faceOverlayDataList.clear();
        invalidate();
    }

    /**
     * Get number of faces currently being displayed
     */
    public int getFaceCount() {
        return faceOverlayDataList.size();
    }

    // ===== FACE OVERLAY DATA CLASS =====

    /**
     * Internal class to hold all face overlay information
     */
    private static class FaceOverlayData {
        // Basic face data
        Rect boundingBox;
        boolean isReal = true;
        float confidence = 0.0f;
        String label = "";
        boolean isRecognized = false;

        // ML Kit Face properties
        Integer trackingId;
        float headEulerAngleY = 0.0f;
        float headEulerAngleZ = 0.0f;
        Float smilingProbability;
        Float leftEyeOpenProbability;
        Float rightEyeOpenProbability;
        List<FaceLandmark> landmarks = new ArrayList<>();

        @Override
        public String toString() {
            return "FaceOverlayData{" +
                    "isReal=" + isReal +
                    ", confidence=" + confidence +
                    ", label='" + label + '\'' +
                    ", isRecognized=" + isRecognized +
                    ", trackingId=" + trackingId +
                    '}';
        }
    }
}