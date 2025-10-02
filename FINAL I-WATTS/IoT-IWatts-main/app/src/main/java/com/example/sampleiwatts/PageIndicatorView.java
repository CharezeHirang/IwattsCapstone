package com.example.sampleiwatts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PageIndicatorView extends View {
    private Paint activePaint;
    private Paint inactivePaint;
    private int totalPages = 1;
    private int currentPage = 0;
    private int dotRadius = 8;
    private int dotSpacing = 16;
    
    public PageIndicatorView(Context context) {
        super(context);
        init();
    }
    
    public PageIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(0xFF863B17);
        activePaint.setStyle(Paint.Style.FILL);
        
        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(0xFFE0E0E0);
        inactivePaint.setStyle(Paint.Style.FILL);
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
        requestLayout();
        invalidate();
    }
    
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
        invalidate();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (totalPages * (dotRadius * 2 + dotSpacing)) - dotSpacing;
        int height = dotRadius * 2;
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int centerY = getHeight() / 2;
        int startX = dotRadius;
        
        for (int i = 0; i < totalPages; i++) {
            int centerX = startX + i * (dotRadius * 2 + dotSpacing);
            Paint paint = (i == currentPage) ? activePaint : inactivePaint;
            canvas.drawCircle(centerX, centerY, dotRadius, paint);
        }
    }
}
