package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {

    // Performance optimization fields
    private final List<MainActivity.FaceData> currentFaceDataList = new ArrayList<>();
    private final List<MainActivity.FaceData> previousFaceDataList = new ArrayList<>();
    private volatile boolean needsRedraw = false;
    private volatile long lastDrawTime = 0;

    // Scale factors cached for performance
    private volatile float scaleX = 1f;
    private volatile float scaleY = 1f;
    private volatile int lastImageWidth = 0;
    private volatile int lastImageHeight = 0;

    // Performance configuration
    private static final long MIN_REDRAW_INTERVAL = 33; // ~30 FPS max
    private static final float SCALE_EPSILON = 0.001f; // Threshold for scale changes

    // Pre-allocated paint objects for performance
    private final Paint realFacePaint;
    private final Paint fakeFacePaint;
    private final Paint textPaint;
    private final Paint badgePaint;
    private final Paint confidencePaint;
    private final Paint methodPaint;
    private final Paint borderPaint;
    private final Paint bgPaint;
    private final Paint warningPaint;
    private final Paint iconPaint;
    private final Paint effectPaint;
    private final Paint mlPaint;

    // New paints for face recognition
    private final Paint recognitionPaint;
    private final Paint recognitionBgPaint;
    private final Paint unknownPaint;

    // Pre-allocated objects for drawing efficiency
    private final RectF tempRectF = new RectF();
    private final Path tempPath = new Path();
    private final float[] intervals = {20f, 10f};

    // Gradient cache for performance
    private LinearGradient cachedGradient;
    private float lastGradientLeft = Float.NaN;
    private float lastGradientTop = Float.NaN;
    private float lastGradientRight = Float.NaN;
    private float lastGradientBottom = Float.NaN;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize all paint objects once for performance
        realFacePaint = createRealFacePaint();
        fakeFacePaint = createFakeFacePaint();
        textPaint = createTextPaint();
        badgePaint = createBadgePaint();
        confidencePaint = createConfidencePaint();
        methodPaint = createMethodPaint();
        borderPaint = createBorderPaint();
        bgPaint = createBackgroundPaint();
        warningPaint = createWarningPaint();
        iconPaint = createIconPaint();
        effectPaint = createEffectPaint();
        mlPaint = createMLPaint();

        // Initialize recognition paints
        recognitionPaint = createRecognitionPaint();
        recognitionBgPaint = createRecognitionBackgroundPaint();
        unknownPaint = createUnknownPaint();
    }

    // ========== PAINT FACTORY METHODS ==========

    private Paint createRealFacePaint() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createFakeFacePaint() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setColor(0xFFFF4444);
        paint.setAntiAlias(true);
        paint.setPathEffect(new android.graphics.DashPathEffect(intervals, 0));
        return paint;
    }

    private Paint createTextPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(28f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
        return paint;
    }

    private Paint createBadgePaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private Paint createConfidencePaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private Paint createMethodPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(20f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        return paint;
    }

    private Paint createBorderPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createBackgroundPaint() {
        Paint paint = new Paint();
        paint.setColor(0x44FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createWarningPaint() {
        Paint paint = new Paint();
        paint.setColor(0xFFFF9900);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createIconPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(16f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createEffectPaint() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x4400FF00);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint createMLPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(16f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return paint;
    }

    // New recognition paint methods
    private Paint createRecognitionPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(24f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(3f, 1f, 1f, Color.BLACK);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return paint;
    }

    private Paint createRecognitionBackgroundPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private Paint createUnknownPaint() {
        Paint paint = new Paint();
        paint.setColor(0xFFFFFF66); // Light yellow
        paint.setTextSize(22f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(3f, 1f, 1f, Color.BLACK);
        return paint;
    }

    // ========== OPTIMIZED PUBLIC METHODS ==========

    public void setFacesWithMLOptimized(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        // Check if we actually need to update
        if (!hasSignificantChanges(faceData, imageWidth, imageHeight)) {
            return;
        }

        // Update face data efficiently
        synchronized (currentFaceDataList) {
            currentFaceDataList.clear();
            if (faceData != null) {
                currentFaceDataList.addAll(faceData);
            }
        }

        // Update scale factors only if dimensions changed significantly
        updateScaleFactorsIfNeeded(imageWidth, imageHeight);

        // Mark for redraw with throttling
        scheduleRedrawIfNeeded();
    }

    // Backward compatibility methods
    public void setFacesWithML(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        setFacesWithMLOptimized(faceData, imageWidth, imageHeight);
    }

    public void setFacesWithDepth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        setFacesWithMLOptimized(faceData, imageWidth, imageHeight);
    }

    public void setFacesWithAuth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        setFacesWithMLOptimized(faceData, imageWidth, imageHeight);
    }

    // ========== PERFORMANCE OPTIMIZATION METHODS ==========

    private boolean hasSignificantChanges(List<MainActivity.FaceData> newFaceData, int imageWidth, int imageHeight) {
        // Check dimension changes
        if (lastImageWidth != imageWidth || lastImageHeight != imageHeight) {
            return true;
        }

        // Check face count changes
        synchronized (currentFaceDataList) {
            if (currentFaceDataList.size() != (newFaceData != null ? newFaceData.size() : 0)) {
                return true;
            }

            // Quick comparison of face data (including recognition data)
            if (newFaceData != null) {
                for (int i = 0; i < newFaceData.size() && i < currentFaceDataList.size(); i++) {
                    if (hasFaceDataChanged(currentFaceDataList.get(i), newFaceData.get(i))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasFaceDataChanged(MainActivity.FaceData oldData, MainActivity.FaceData newData) {
        if (oldData == null || newData == null) return true;

        return oldData.isReal != newData.isReal ||
                Math.abs(oldData.confidence - newData.confidence) > 1.0f ||
                !oldData.boundingBox.equals(newData.boundingBox) ||
                !oldData.detectionMethod.equals(newData.detectionMethod) ||
                !oldData.recognizedName.equals(newData.recognizedName) ||
                Math.abs(oldData.recognitionConfidence - newData.recognitionConfidence) > 1.0f;
    }

    private void updateScaleFactorsIfNeeded(int imageWidth, int imageHeight) {
        if (lastImageWidth != imageWidth || lastImageHeight != imageHeight) {
            lastImageWidth = imageWidth;
            lastImageHeight = imageHeight;

            if (getWidth() > 0 && getHeight() > 0 && imageWidth > 0 && imageHeight > 0) {
                float newScaleX = (float) getWidth() / imageHeight;
                float newScaleY = (float) getHeight() / imageWidth;

                // Only update if the change is significant
                if (Math.abs(scaleX - newScaleX) > SCALE_EPSILON ||
                        Math.abs(scaleY - newScaleY) > SCALE_EPSILON) {
                    scaleX = newScaleX;
                    scaleY = newScaleY;

                    // Clear gradient cache when scale changes
                    cachedGradient = null;
                }
            }
        }
    }

    private void scheduleRedrawIfNeeded() {
        long currentTime = System.currentTimeMillis();

        // Throttle redraws for performance
        if (currentTime - lastDrawTime >= MIN_REDRAW_INTERVAL) {
            needsRedraw = true;
            invalidate();
            lastDrawTime = currentTime;
        } else {
            // Schedule a delayed redraw
            if (!needsRedraw) {
                needsRedraw = true;
                postDelayed(() -> {
                    if (needsRedraw) {
                        invalidate();
                        lastDrawTime = System.currentTimeMillis();
                        needsRedraw = false;
                    }
                }, MIN_REDRAW_INTERVAL - (currentTime - lastDrawTime));
            }
        }
    }

    // ========== OPTIMIZED DRAWING WITH RECOGNITION ==========

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Copy face data to avoid blocking other threads
        List<MainActivity.FaceData> faceDataCopy = new ArrayList<>();
        synchronized (currentFaceDataList) {
            faceDataCopy.addAll(currentFaceDataList);
        }

        // Draw faces efficiently with recognition info
        for (MainActivity.FaceData faceData : faceDataCopy) {
            drawFaceWithRecognition(canvas, faceData);
        }

        needsRedraw = false;
    }

    private void drawFaceWithRecognition(Canvas canvas, MainActivity.FaceData faceData) {
        Rect face = faceData.boundingBox;

        // Transform coordinates efficiently
        float left = face.left * scaleX - 20;
        float top = face.top * scaleY - 20;
        float right = face.right * scaleX + 20;
        float bottom = face.bottom * scaleY + 20;

        // Draw based on detection result
        if (faceData.isReal) {
            drawRealFaceWithRecognition(canvas, left, top, right, bottom, faceData);
        } else {
            drawFakeFaceOptimized(canvas, left, top, right, bottom, faceData);
        }

        // Draw additional elements
        drawConfidenceMeterOptimized(canvas, right + 10, top, faceData.confidence);

        // Draw detection method (smaller text to make room for recognition)
        methodPaint.setTextSize(16f);
        canvas.drawText(faceData.detectionMethod, (left + right) / 2, top - 5, methodPaint);
    }

    private void drawRealFaceWithRecognition(Canvas canvas, float left, float top, float right, float bottom,
                                             MainActivity.FaceData faceData) {

        // Choose border color based on recognition status
        int borderColor;
        if (faceData.recognizedName != null && !faceData.recognizedName.equals("Unknown")) {
            // Known person - green gradient
            borderColor = Color.GREEN;
        } else {
            // Unknown person - blue gradient
            borderColor = 0xFF4CAF50; // Light green
        }

        // Use cached gradient or create new one
        LinearGradient gradient = getCachedGradientForRecognition(left, top, right, bottom,
                faceData.confidence, borderColor);
        realFacePaint.setShader(gradient);

        // Draw main frame efficiently
        tempRectF.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRectF, 20f, 20f, realFacePaint);

        // Draw anti-spoofing label (smaller)
        String antispoofLabel = String.format("REAL %.0f%%", faceData.confidence);
        textPaint.setTextSize(22f);
        canvas.drawText(antispoofLabel, (left + right) / 2, bottom + 25, textPaint);

        // Draw recognition information
        drawRecognitionInfo(canvas, left, top, right, bottom + 45, faceData);

        // Draw ML indicator
        drawMLIndicatorOptimized(canvas, left - 10, top - 10, true);

        // Draw 3D effects if has depth info
        if (faceData.has3DStructure) {
            draw3DEffectsOptimized(canvas, left, top, right, bottom);
        }
    }

    private void drawRecognitionInfo(Canvas canvas, float left, float top, float right, float baseY,
                                     MainActivity.FaceData faceData) {

        if (faceData.recognizedName == null) return;

        float centerX = (left + right) / 2;
        float currentY = baseY;

        if (!faceData.recognizedName.equals("Unknown")) {
            // Draw recognized person's name with background
            String personName = faceData.recognizedName;

            // Draw background for name
            recognitionBgPaint.setColor(0xCC2E7D32); // Semi-transparent green
            float textWidth = recognitionPaint.measureText(personName);
            tempRectF.set(centerX - textWidth/2 - 10, currentY - 25,
                    centerX + textWidth/2 + 10, currentY + 5);
            canvas.drawRoundRect(tempRectF, 8f, 8f, recognitionBgPaint);

            // Draw person name
            recognitionPaint.setColor(Color.WHITE);
            canvas.drawText(personName, centerX, currentY - 5, recognitionPaint);

            // Draw recognition confidence
            if (faceData.recognitionConfidence > 0) {
                String confidenceText = String.format("%.0f%% match", faceData.recognitionConfidence);
                recognitionPaint.setTextSize(18f);
                recognitionPaint.setColor(0xFFE8F5E8); // Light green
                canvas.drawText(confidenceText, centerX, currentY + 20, recognitionPaint);
                recognitionPaint.setTextSize(24f); // Reset size
            }

        } else {
            // Draw "Unknown" with different styling
            String unknownText = "Unknown Person";

            // Draw background for unknown
            recognitionBgPaint.setColor(0xCCFF9800); // Semi-transparent orange
            float textWidth = unknownPaint.measureText(unknownText);
            tempRectF.set(centerX - textWidth/2 - 8, currentY - 23,
                    centerX + textWidth/2 + 8, currentY + 3);
            canvas.drawRoundRect(tempRectF, 6f, 6f, recognitionBgPaint);

            // Draw unknown text
            canvas.drawText(unknownText, centerX, currentY - 3, unknownPaint);
        }
    }

    private void drawFakeFaceOptimized(Canvas canvas, float left, float top, float right, float bottom,
                                       MainActivity.FaceData faceData) {

        // Draw with pre-configured dashed line
        tempRectF.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRectF, 20f, 20f, fakeFacePaint);

        // Draw "FAKE" label
        String label = String.format("FAKE %.0f%%", 100 - faceData.confidence);
        canvas.drawText(label, (left + right) / 2, bottom + 25, textPaint);

        // Draw "Spoof Detected" for fake faces
        String spoofText = "SPOOF DETECTED";
        recognitionBgPaint.setColor(0xCCF44336); // Semi-transparent red
        float textWidth = recognitionPaint.measureText(spoofText);
        float centerX = (left + right) / 2;
        float currentY = bottom + 50;

        tempRectF.set(centerX - textWidth/2 - 10, currentY - 25,
                centerX + textWidth/2 + 10, currentY + 5);
        canvas.drawRoundRect(tempRectF, 8f, 8f, recognitionBgPaint);

        recognitionPaint.setColor(Color.WHITE);
        canvas.drawText(spoofText, centerX, currentY - 5, recognitionPaint);

        // Draw warning icon
        drawWarningIconOptimized(canvas, right - 30, top + 10);

        // Draw ML indicator
        drawMLIndicatorOptimized(canvas, left - 10, top - 10, false);
    }

    private LinearGradient getCachedGradientForRecognition(float left, float top, float right, float bottom,
                                                           float confidence, int baseColor) {
        // Check if we can reuse the cached gradient
        if (cachedGradient != null &&
                Math.abs(lastGradientLeft - left) < 1f &&
                Math.abs(lastGradientTop - top) < 1f &&
                Math.abs(lastGradientRight - right) < 1f &&
                Math.abs(lastGradientBottom - bottom) < 1f) {
            return cachedGradient;
        }

        // Create new gradient based on recognition status
        int startColor = baseColor;
        int endColor = Color.argb(255,
                Color.red(baseColor),
                (int)(Color.green(baseColor) * confidence / 100f),
                Color.blue(baseColor));

        cachedGradient = new LinearGradient(
                left, top, right, bottom,
                new int[]{startColor, endColor},
                null, Shader.TileMode.CLAMP
        );

        // Cache the parameters
        lastGradientLeft = left;
        lastGradientTop = top;
        lastGradientRight = right;
        lastGradientBottom = bottom;

        return cachedGradient;
    }

    private LinearGradient getCachedGradient(float left, float top, float right, float bottom, float confidence) {
        return getCachedGradientForRecognition(left, top, right, bottom, confidence, Color.GREEN);
    }

    private void drawConfidenceMeterOptimized(Canvas canvas, float x, float y, float confidence) {
        float meterWidth = 10f;
        float meterHeight = 100f;

        // Background
        canvas.drawRect(x, y, x + meterWidth, y + meterHeight, bgPaint);

        // Confidence level
        float fillHeight = (confidence / 100f) * meterHeight;
        int color = getConfidenceColorOptimized(confidence);
        confidencePaint.setColor(color);

        canvas.drawRect(x, y + meterHeight - fillHeight, x + meterWidth, y + meterHeight, confidencePaint);

        // Border
        canvas.drawRect(x, y, x + meterWidth, y + meterHeight, borderPaint);
    }

    private int getConfidenceColorOptimized(float confidence) {
        // Pre-computed color values for performance
        if (confidence > 80) return 0xFF00FF00; // Green
        if (confidence > 60) return 0xFFFFFF00; // Yellow
        if (confidence > 40) return 0xFFFF9900; // Orange
        return 0xFFFF0000; // Red
    }

    private void drawMLIndicatorOptimized(Canvas canvas, float x, float y, boolean isReal) {
        if (isReal) {
            mlPaint.setColor(0xFF00FF00);
            canvas.drawText("ML✓", x, y, mlPaint);
        } else {
            mlPaint.setColor(0xFFFF0000);
            canvas.drawText("ML✗", x, y, mlPaint);
        }
    }

    private void drawWarningIconOptimized(Canvas canvas, float x, float y) {
        // Draw triangle warning using pre-allocated path
        tempPath.reset();
        tempPath.moveTo(x, y + 20);
        tempPath.lineTo(x - 15, y);
        tempPath.lineTo(x + 15, y);
        tempPath.close();

        canvas.drawPath(tempPath, warningPaint);

        // Draw exclamation mark
        canvas.drawText("!", x, y + 15, iconPaint);
    }

    private void draw3DEffectsOptimized(Canvas canvas, float left, float top, float right, float bottom) {
        // Draw corner depth indicators efficiently
        float cornerSize = 20f;

        // Top-left corner
        tempPath.reset();
        tempPath.moveTo(left, top + cornerSize);
        tempPath.lineTo(left, top);
        tempPath.lineTo(left + cornerSize, top);
        canvas.drawPath(tempPath, effectPaint);

        // Top-right corner
        tempPath.reset();
        tempPath.moveTo(right - cornerSize, top);
        tempPath.lineTo(right, top);
        tempPath.lineTo(right, top + cornerSize);
        canvas.drawPath(tempPath, effectPaint);

        // Bottom-left corner
        tempPath.reset();
        tempPath.moveTo(left, bottom - cornerSize);
        tempPath.lineTo(left, bottom);
        tempPath.lineTo(left + cornerSize, bottom);
        canvas.drawPath(tempPath, effectPaint);

        // Bottom-right corner
        tempPath.reset();
        tempPath.moveTo(right - cornerSize, bottom);
        tempPath.lineTo(right, bottom);
        tempPath.lineTo(right, bottom - cornerSize);
        canvas.drawPath(tempPath, effectPaint);
    }

    // ========== UTILITY METHODS ==========

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Recalculate scale factors when view size changes
        if (lastImageWidth > 0 && lastImageHeight > 0) {
            updateScaleFactorsIfNeeded(lastImageWidth, lastImageHeight);
        }

        // Clear cached gradient on size change
        cachedGradient = null;
    }

    public void clearCache() {
        synchronized (currentFaceDataList) {
            currentFaceDataList.clear();
        }
        cachedGradient = null;
        invalidate();
    }
}