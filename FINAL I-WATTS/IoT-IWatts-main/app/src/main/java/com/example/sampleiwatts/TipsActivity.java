package com.example.sampleiwatts;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TipsActivity extends AppCompatActivity {

    ImageView icBack;
    private TextView tvSwipeHint;
    private ImageView ivHintLeft;
    private ImageView ivHintRight;
    private FrameLayout[] categories;
    private LinearLayout categoryIndicator;
    private int currentIndex = 0;
    private float downX;
    Button btnCategory1, btnCategory2, btnCategory3, btnCategory4, btnCategory5, 
           btnCategory6, btnCategory7, btnCategory8, btnCategory9, btnCategory10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tips);

        icBack = findViewById(R.id.ic_back);
        icBack.setOnClickListener(v -> {
            boolean fromDrawer = getIntent().getBooleanExtra("from_drawer", false);
            String sourceActivity = getIntent().getStringExtra("source_activity");
            
            if (fromDrawer && sourceActivity != null) {
                try {
                    Class<?> targetClass = getSourceActivityClass(sourceActivity);
                    if (targetClass != null) {
                        Intent intent = new Intent(TipsActivity.this, targetClass);
            startActivity(intent);
            finish();
                        return;
                    }
                } catch (Exception e) {
                    // Fallback to default behavior
                }
            }
            
            // Default behavior: go back to previous activity
            finish();
        });

        // Initialize swipe hint
        tvSwipeHint = findViewById(R.id.tv_swipe_hint);
        ivHintLeft = findViewById(R.id.iv_hint_left);
        ivHintRight = findViewById(R.id.iv_hint_right);
        categoryIndicator = findViewById(R.id.category_indicator);
        maybeShowSwipeCoachmark();
        
        // Setup touch handling for swipe gestures
        setupTouchHandling();
        
        // Category buttons are initialized below

        categories = new FrameLayout[]{
                findViewById(R.id.categoryLayout1),
                findViewById(R.id.categoryLayout2),
                findViewById(R.id.categoryLayout3),
                findViewById(R.id.categoryLayout4),
                findViewById(R.id.categoryLayout5),
                findViewById(R.id.categoryLayout6),
                findViewById(R.id.categoryLayout7),
                findViewById(R.id.categoryLayout8),
                findViewById(R.id.categoryLayout9),
                findViewById(R.id.categoryLayout10)
        };

        // Handle return from SwipeableTipsActivity
        String returnCategory = getIntent().getStringExtra("return_to_category");
        if (returnCategory != null) {
            showCategoryForName(returnCategory);
        }
        
        // Setup category indicator
        setupCategoryIndicator();
        
        btnCategory1 = findViewById(R.id.category1);
        btnCategory1.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Energy Saving");
            startActivity(intent);
            finish();
        });

        btnCategory2 = findViewById(R.id.category2);
        btnCategory2.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Water Saving");
            startActivity(intent);
            finish();
        });

        btnCategory3 = findViewById(R.id.category3);
        btnCategory3.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Smart Home");
            startActivity(intent);
            finish();
        });

        btnCategory4 = findViewById(R.id.category4);
        btnCategory4.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Sustainability");
            startActivity(intent);
            finish();
        });

        btnCategory5 = findViewById(R.id.category5);
        btnCategory5.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Financial");
            startActivity(intent);
            finish();
        });

        btnCategory6 = findViewById(R.id.category6);
        btnCategory6.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Health");
            startActivity(intent);
            finish();
        });

        btnCategory7 = findViewById(R.id.category7);
        btnCategory7.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Productivity");
            startActivity(intent);
            finish();
        });

        btnCategory8 = findViewById(R.id.category8);
        btnCategory8.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Learning");
            startActivity(intent);
            finish();
        });

        btnCategory9 = findViewById(R.id.category9);
        btnCategory9.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Communication");
            startActivity(intent);
            finish();
        });

        btnCategory10 = findViewById(R.id.category10);
        btnCategory10.setOnClickListener(v -> {
            Intent intent = new Intent(TipsActivity.this, SwipeableTipsActivity.class);
            intent.putExtra("category", "Security");
            startActivity(intent);
            finish();
        });

    }

    private void showCategoryForName(String categoryName) {
        int categoryIndex = -1;
        switch (categoryName.toLowerCase()) {
            case "energy saving":
            case "a":
                categoryIndex = 0;
                break;
            case "water saving":
            case "b":
                categoryIndex = 1;
                break;
            case "smart home":
            case "c":
                categoryIndex = 2;
                break;
            case "sustainability":
            case "d":
                categoryIndex = 3;
                break;
            case "financial":
            case "e":
                categoryIndex = 4;
                break;
            case "health":
            case "f":
                categoryIndex = 5;
                break;
            case "productivity":
            case "g":
                categoryIndex = 6;
                break;
            case "learning":
            case "h":
                categoryIndex = 7;
                break;
            case "communication":
            case "i":
                categoryIndex = 8;
                break;
            case "security":
            case "j":
                categoryIndex = 9;
                break;
        }
        
        if (categoryIndex >= 0 && categoryIndex < categories.length) {
            currentIndex = categoryIndex;
            for (int i = 0; i < categories.length; i++) {
                categories[i].setVisibility(i == currentIndex ? View.VISIBLE : View.GONE);
            }
            updateCategoryIndicator();
        }
    }
    
    private void maybeShowSwipeCoachmark() {
        ivHintLeft.setAlpha(0f);
        ivHintRight.setAlpha(0f);
        tvSwipeHint.setAlpha(0f);
        ivHintLeft.animate().alpha(0.8f).setDuration(400).start();
        ivHintRight.animate().alpha(0.8f).setDuration(400).start();
        tvSwipeHint.animate().alpha(1f).setDuration(400).start();

        animateChevron(ivHintLeft, -20);
        animateChevron(ivHintRight, 20);

        tvSwipeHint.postDelayed(this::dismissCoachmark, 3500);
    }
    
    private void animateChevron(View view, float deltaX) {
        PropertyValuesHolder pvhX1 = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0, deltaX);
        PropertyValuesHolder pvhA1 = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0.2f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view, pvhX1, pvhA1);
        animator.setDuration(700);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setRepeatCount(4);
        animator.start();
    }
    
    private void dismissCoachmark() {
        if (ivHintLeft.getAlpha() == 0f && ivHintRight.getAlpha() == 0f && tvSwipeHint.getAlpha() == 0f) return;
        ivHintLeft.animate().alpha(0f).setDuration(250).start();
        ivHintRight.animate().alpha(0f).setDuration(250).start();
        tvSwipeHint.animate().alpha(0f).setDuration(250).start();
    }

    private void setupTouchHandling() {
        findViewById(R.id.main).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    return true;

                case MotionEvent.ACTION_UP:
                    float upX = event.getX();
                    float deltaX = downX - upX;

                    if (Math.abs(deltaX) > 150) {
                        dismissCoachmark();
                        
                        if (deltaX > 0) {
                            showNextCategory();
                        } else {
                            showPreviousCategory();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void showNextCategory() {
        int newIndex = (currentIndex + 1) % categories.length;
        animateToCategory(newIndex, true);
    }

    private void showPreviousCategory() {
        int newIndex = (currentIndex - 1 + categories.length) % categories.length;
        animateToCategory(newIndex, false);
    }

    private void animateToCategory(int newIndex, boolean toLeft) {
        final View currentView = categories[currentIndex];
        final View nextView = categories[newIndex];

        nextView.setVisibility(View.VISIBLE);
        nextView.setAlpha(0f);
        final int direction = toLeft ? 1 : -1;
        nextView.setTranslationX(direction * nextView.getWidth());

        if (nextView.getWidth() == 0) {
            nextView.post(() -> animateToCategory(newIndex, toLeft));
            return;
        }

        currentView.animate()
                .translationX(-direction * currentView.getWidth())
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> {
                    currentView.setVisibility(View.GONE);
                    currentView.setAlpha(1f);
                    currentView.setTranslationX(0f);
                })
                .start();

        nextView.setTranslationX(direction * nextView.getWidth());
        nextView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(320)
                .start();

        currentIndex = newIndex;
        updateCategoryIndicator();
    }
    
    private Class<?> getSourceActivityClass(String sourceActivity) {
        // Return appropriate activity class based on source
        // You'll need to implement this based on your app's activities
        return null; // Placeholder
    }

    private void setupCategoryIndicator() {
        if (categoryIndicator != null) {
            PageIndicatorView indicator = new PageIndicatorView(this);
            indicator.setTotalPages(categories.length);
            indicator.setCurrentPage(currentIndex);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            indicator.setLayoutParams(params);
            
            categoryIndicator.addView(indicator);
        }
    }

    private void updateCategoryIndicator() {
        if (categoryIndicator != null && categoryIndicator.getChildCount() > 0) {
            PageIndicatorView indicator = (PageIndicatorView) categoryIndicator.getChildAt(0);
            if (indicator != null) {
                indicator.setCurrentPage(currentIndex);
            }
        }
    }
}