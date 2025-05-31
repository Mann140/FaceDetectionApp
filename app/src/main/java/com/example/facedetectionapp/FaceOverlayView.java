package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
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
    private float scaleX = 1f;
    private float scaleY = 1f;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Paint for 3D verified faces (gradient green)
        realFacePaint = new Paint();
        realFacePaint.setStyle(Paint.Style.STROKE);
        realFacePaint.setStrokeWidth(6f);
        realFacePaint.setAntiAlias(true);

        // Paint for 2D/fake faces (red)
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
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        // Paint for badges
        badgePaint = new Paint();
        badgePaint.setAntiAlias(true);
    }

    public void setFacesWithDepth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        faceDataList.clear();
        faceDataList.addAll(faceData);

        // Calculate scale factors
        scaleX = (float) getWidth() / imageHeight;
        scaleY = (float) getHeight() / imageWidth;

        invalidate();
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

            // Choose paint based on 3D detection
            Paint strokePaint;
            if (faceData.has3DStructure) {
                // Create gradient for 3D faces
                LinearGradient gradient = new LinearGradient(
                        left, top, right, bottom,
                        new int[]{0xFF00FF00, 0xFF00AA00, 0xFF008800},
                        null, Shader.TileMode.CLAMP
                );
                realFacePaint.setShader(gradient);
                strokePaint = realFacePaint;
            } else {
                strokePaint = fakeFacePaint;
            }

            // Draw main rectangle with rounded corners
            canvas.drawRoundRect(left, top, right, bottom, 20f, 20f, strokePaint);

            // Draw 3D depth indicator badge
            draw3DBadge(canvas, right - 40, top + 10, faceData.depthStatus, faceData.has3DStructure);

            // Draw depth status text
            canvas.drawText(faceData.depthStatus, (left + right) / 2, top - 10, textPaint);

            // Draw 3D visualization effects for real faces
            if (faceData.has3DStructure) {
                draw3DEffects(canvas, left, top, right, bottom);
            }
        }
    }

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

        // Depth grid effect
        effectPaint.setStrokeWidth(1f);
        effectPaint.setColor(0x2200FF00);

        float gridSpacing = 30f;
        for (float x = left + gridSpacing; x < right; x += gridSpacing) {
            canvas.drawLine(x, top, x, bottom, effectPaint);
        }
        for (float y = top + gridSpacing; y < bottom; y += gridSpacing) {
            canvas.drawLine(left, y, right, y, effectPaint);
        }
    }
}