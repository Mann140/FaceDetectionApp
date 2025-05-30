package com.example.facedetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {
    private final List<MainActivity.FaceData> faceDataList = new ArrayList<>();
    private final Paint realFacePaint;
    private final Paint fakeFacePaint;
    private final Paint textPaint;
    private float scaleX = 1f;
    private float scaleY = 1f;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Paint for real faces (green)
        realFacePaint = new Paint();
        realFacePaint.setStyle(Paint.Style.STROKE);
        realFacePaint.setStrokeWidth(8f);
        realFacePaint.setColor(Color.GREEN);

        // Paint for fake faces (red)
        fakeFacePaint = new Paint();
        fakeFacePaint.setStyle(Paint.Style.STROKE);
        fakeFacePaint.setStrokeWidth(8f);
        fakeFacePaint.setColor(Color.RED);

        // Paint for text labels
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
    }

    public void setFacesWithAuth(List<MainActivity.FaceData> faceData, int imageWidth, int imageHeight) {
        faceDataList.clear();
        faceDataList.addAll(faceData);

        // Calculate scale factors (accounting for rotation on front camera)
        scaleX = (float) getWidth() / imageHeight;
        scaleY = (float) getHeight() / imageWidth;

        invalidate(); // Trigger redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (MainActivity.FaceData faceData : faceDataList) {
            Rect face = faceData.boundingBox;
            boolean isReal = faceData.isReal;

            // Choose paint based on authenticity
            Paint paint = isReal ? realFacePaint : fakeFacePaint;

            // Transform coordinates to match screen
            float left = face.left * scaleX;
            float top = face.top * scaleY;
            float right = face.right * scaleX;
            float bottom = face.bottom * scaleY;

            // Draw rectangle
            canvas.drawRect(left, top, right, bottom, paint);

            // Draw label
            String label = isReal ? "REAL" : "FAKE";
            float textX = left + 10;
            float textY = top - 10;
            canvas.drawText(label, textX, textY, textPaint);
        }
    }
}