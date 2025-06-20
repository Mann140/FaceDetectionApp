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
    private final List<MainActivity.FaceData> faceDataList = new ArrayList<>();
    private final Paint realFacePaint;
    private final Paint fakeFacePaint;
    private final Paint depthPaint;
    private final Paint textPaint;
    private final Paint badgePaint;
    private final Paint confidencePaint;
    private final Paint methodPaint;
    private float scaleX = 1f;
    private float scaleY = 1f;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Paint for real faces (gradient green)
        realFacePaint = new Paint();
        realFacePaint.setStyle(Paint.Style.STROKE);
        realFacePaint.setStrokeWidth(6f);
        realFacePaint.setAntiAlias(true);

        // Paint for fake faces (red)
        fakeFacePaint = new Paint();
        fakeFacePaint.setStyle(Paint.Style.STROKE);
        fakeFacePaint.setStrokeWidth(6f);
        fakeFacePaint.setColor(0xFFFF4444);
        fakeFacePaint.setAntiAlias(true);

        // Paint for depth indicator
        depthPaint = new Paint();
        depthPaint.setStyle(Paint.Style.FILL);
        depthPaint.setAntiAlias(true);

        // Paint for text
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        // Paint for badges
        badgePaint = new Paint();
        badgePaint.setAntiAlias(true);

        // Paint for confidence meter
        confidencePaint = new Paint();
        confidencePaint.setAntiAlias(true);

        // Paint for detection method
        methodPaint = new Paint();
        methodPaint.setColor(Color.WHITE);
        methodPaint.setTextSize(20f);
        methodPaint.setAntiAlias(true);
        methodPaint.setTextAlign(Paint.Align.CENTER);
        methodPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);
    }

    // Method for ML detection
    public void setFacesWithML(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        faceDataList.clear();
        faceDataList.addAll(faceData);

        // Calculate scale factors
        scaleX = (float) getWidth() / imageHeight;
        scaleY = (float) getHeight() / imageWidth;

        invalidate();
    }

    // Method for depth detection (backward compatibility)
    public void setFacesWithDepth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        setFacesWithML(faceData, imageWidth, imageHeight);
    }

    // Method for basic auth (backward compatibility)
    public void setFacesWithAuth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        setFacesWithML(faceData, imageWidth, imageHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (MainActivity.FaceData faceData : faceDataList) {
            Rect face = faceData.boundingBox;

            // Transform coordinates
            float left = face.left * scaleX - 20;
            float top = face.top * scaleY - 20;
            float right = face.right * scaleX + 20;
            float bottom = face.bottom * scaleY + 20;

            // Draw based on detection result
            if (faceData.isReal) {
                drawRealFace(canvas, left, top, right, bottom, faceData);
            } else {
                drawFakeFace(canvas, left, top, right, bottom, faceData);
            }

            // Draw confidence meter
            drawConfidenceMeter(canvas, right + 10, top, faceData.confidence);

            // Draw detection method
            canvas.drawText(faceData.detectionMethod,
                    (left + right) / 2, top - 10, methodPaint);
        }
    }

    private void drawRealFace(Canvas canvas, float left, float top, float right, float bottom,
                              MainActivity.FaceData faceData) {
        // Create gradient based on confidence
        int startColor = Color.GREEN;
        int endColor = Color.argb(255, 0, (int)(255 * faceData.confidence / 100f), 0);

        LinearGradient gradient = new LinearGradient(
                left, top, right, bottom,
                new int[]{startColor, endColor},
                null, Shader.TileMode.CLAMP
        );
        realFacePaint.setShader(gradient);

        // Draw main frame
        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, 20f, 20f, realFacePaint);

        // Draw "REAL" label with confidence
        String label = String.format("REAL %.0f%%", faceData.confidence);
        canvas.drawText(label, (left + right) / 2, bottom + 30, textPaint);

        // Draw ML indicator
        drawMLIndicator(canvas, left - 10, top - 10, true);

        // Draw 3D effects if has depth info
        if (faceData.has3DStructure) {
            draw3DEffects(canvas, left, top, right, bottom);
        }
    }

    private void drawFakeFace(Canvas canvas, float left, float top, float right, float bottom,
                              MainActivity.FaceData faceData) {
        // Draw with dashed line for fake
        float[] intervals = {20f, 10f};
        fakeFacePaint.setPathEffect(new android.graphics.DashPathEffect(intervals, 0));

        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, 20f, 20f, fakeFacePaint);

        // Draw "FAKE" label
        String label = String.format("FAKE %.0f%%", 100 - faceData.confidence);
        canvas.drawText(label, (left + right) / 2, bottom + 30, textPaint);

        // Draw warning icon
        drawWarningIcon(canvas, right - 30, top + 10);

        // Draw ML indicator
        drawMLIndicator(canvas, left - 10, top - 10, false);
    }

    private void drawConfidenceMeter(Canvas canvas, float x, float y, float confidence) {
        float meterWidth = 10f;
        float meterHeight = 100f;

        // Background
        Paint bgPaint = new Paint();
        bgPaint.setColor(0x44FFFFFF);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + meterWidth, y + meterHeight, bgPaint);

        // Confidence level
        float fillHeight = (confidence / 100f) * meterHeight;
        int color = getConfidenceColor(confidence);
        confidencePaint.setColor(color);
        confidencePaint.setStyle(Paint.Style.FILL);

        canvas.drawRect(x, y + meterHeight - fillHeight, x + meterWidth, y + meterHeight, confidencePaint);

        // Border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        canvas.drawRect(x, y, x + meterWidth, y + meterHeight, borderPaint);
    }

    private int getConfidenceColor(float confidence) {
        if (confidence > 80) {
            return 0xFF00FF00; // Green
        } else if (confidence > 60) {
            return 0xFFFFFF00; // Yellow
        } else if (confidence > 40) {
            return 0xFFFF9900; // Orange
        } else {
            return 0xFFFF0000; // Red
        }
    }

    private void drawMLIndicator(Canvas canvas, float x, float y, boolean isReal) {
        Paint mlPaint = new Paint();
        mlPaint.setAntiAlias(true);
        mlPaint.setTextSize(16f);
        mlPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        if (isReal) {
            mlPaint.setColor(0xFF00FF00);
            canvas.drawText("ML✓", x, y, mlPaint);
        } else {
            mlPaint.setColor(0xFFFF0000);
            canvas.drawText("ML✗", x, y, mlPaint);
        }
    }

    private void drawWarningIcon(Canvas canvas, float x, float y) {
        Paint warningPaint = new Paint();
        warningPaint.setColor(0xFFFF9900);
        warningPaint.setStyle(Paint.Style.FILL);
        warningPaint.setAntiAlias(true);

        // Draw triangle warning
        Path path = new Path();
        path.moveTo(x, y + 20);
        path.lineTo(x - 15, y);
        path.lineTo(x + 15, y);
        path.close();

        canvas.drawPath(path, warningPaint);

        // Draw exclamation mark
        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(16f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        canvas.drawText("!", x, y + 15, textPaint);
    }

    // 3D effects from depth detection
    private void draw3DEffects(Canvas canvas, float left, float top, float right, float bottom) {
        // Draw subtle depth lines
        Paint effectPaint = new Paint();
        effectPaint.setStyle(Paint.Style.STROKE);
        effectPaint.setStrokeWidth(2f);
        effectPaint.setColor(0x4400FF00); // Semi-transparent green
        effectPaint.setAntiAlias(true);

        // Corner depth indicators
        float cornerSize = 20f;

        // Top-left corner
        Path tlPath = new Path();
        tlPath.moveTo(left, top + cornerSize);
        tlPath.lineTo(left, top);
        tlPath.lineTo(left + cornerSize, top);
        canvas.drawPath(tlPath, effectPaint);

        // Top-right corner
        Path trPath = new Path();
        trPath.moveTo(right - cornerSize, top);
        trPath.lineTo(right, top);
        trPath.lineTo(right, top + cornerSize);
        canvas.drawPath(trPath, effectPaint);

        // Bottom-left corner
        Path blPath = new Path();
        blPath.moveTo(left, bottom - cornerSize);
        blPath.lineTo(left, bottom);
        blPath.lineTo(left + cornerSize, bottom);
        canvas.drawPath(blPath, effectPaint);

        // Bottom-right corner
        Path brPath = new Path();
        brPath.moveTo(right - cornerSize, bottom);
        brPath.lineTo(right, bottom);
        brPath.lineTo(right, bottom - cornerSize);
        canvas.drawPath(brPath, effectPaint);
    }

    // 3D badge from depth detection
    private void draw3DBadge(Canvas canvas, float x, float y, String status, boolean is3D) {
        // Badge background
        badgePaint.setStyle(Paint.Style.FILL);
        if (is3D) {
            badgePaint.setColor(0xFF00AA00); // Green
        } else if (status.contains("Partial")) {
            badgePaint.setColor(0xFFFF9800); // Orange
        } else {
            badgePaint.setColor(0xFFFF4444); // Red
        }

        // Draw circular badge
        canvas.drawCircle(x, y + 15, 30, badgePaint);

        // Draw 3D icon
        Paint iconPaint = new Paint();
        iconPaint.setColor(Color.WHITE);
        iconPaint.setTextSize(24f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setAntiAlias(true);
        iconPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        canvas.drawText("3D", x, y + 22, iconPaint);
    }
}