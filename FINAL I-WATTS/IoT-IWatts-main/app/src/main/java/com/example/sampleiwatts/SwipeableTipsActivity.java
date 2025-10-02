package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

public class SwipeableTipsActivity extends AppCompatActivity {
    
    private ViewPager2 tipsViewPager;
    private LinearLayout pageIndicator;
    private ProgressBar swipeProgress;
    private TextView tipCounter;
    private ImageView icBack;
    
    private TipCardPagerAdapter adapter;
    private List<TipCard> tipCards;
    private int currentPosition = 0;
    private int totalTips = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_swipeable_tips);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        initializeViews();
        setupViewPager();
        setupPageIndicator();
        setupNavigation();
        updateUI();
    }
    
    private void initializeViews() {
        tipsViewPager = findViewById(R.id.tips_viewpager);
        pageIndicator = findViewById(R.id.page_indicator);
        swipeProgress = findViewById(R.id.swipe_progress);
        tipCounter = findViewById(R.id.tip_counter);
        icBack = findViewById(R.id.ic_back);
        
        String category = getIntent().getStringExtra("category");
        if (category == null) category = "Energy Saving";
        
        loadTipsForCategory(category);
    }
    
    private void loadTipsForCategory(String category) {
        switch (category.toLowerCase()) {
            case "energy saving":
            case "a":
                tipCards = TipDataManager.getCategoryATips();
                break;
            case "water saving":
            case "b":
                tipCards = TipDataManager.getCategoryBTips();
                break;
            case "smart home":
            case "c":
                tipCards = TipDataManager.getCategoryCTips();
                break;
            case "sustainability":
            case "d":
                tipCards = TipDataManager.getCategoryDTips();
                break;
            case "financial":
            case "e":
                tipCards = TipDataManager.getCategoryETips();
                break;
            case "health":
            case "f":
                tipCards = TipDataManager.getCategoryFTips();
                break;
            case "productivity":
            case "g":
                tipCards = TipDataManager.getCategoryGTips();
                break;
            case "learning":
            case "h":
                tipCards = TipDataManager.getCategoryHTips();
                break;
            case "communication":
            case "i":
                tipCards = TipDataManager.getCategoryITips();
                break;
            case "security":
            case "j":
                tipCards = TipDataManager.getCategoryJTips();
                break;
            default:
                tipCards = TipDataManager.getCategoryATips();
                break;
        }
        
        totalTips = tipCards.size();
        swipeProgress.setMax(totalTips);
    }
    
    private void setupViewPager() {
        if (tipCards != null && tipsViewPager != null) {
            adapter = new TipCardPagerAdapter(tipCards);
            tipsViewPager.setAdapter(adapter);
            tipsViewPager.setPageTransformer(new CardPageTransformer());
            
            tipsViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    currentPosition = position;
                    updateUI();
                }
            });
        }
    }
    
    private void setupPageIndicator() {
        if (totalTips > 1) {
            for (int i = 0; i < totalTips; i++) {
                PageIndicatorView dot = new PageIndicatorView(this);
                dot.setTotalPages(1);
                dot.setCurrentPage(i == 0 ? 0 : -1);
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(8, 0, 8, 0);
                dot.setLayoutParams(params);
                
                pageIndicator.addView(dot);
            }
        }
    }
    
    private void updateUI() {
        if (tipCounter != null) {
            tipCounter.setText((currentPosition + 1) + " of " + totalTips);
        }
        
        if (swipeProgress != null) {
            swipeProgress.setProgress(currentPosition + 1);
        }
        
        if (pageIndicator != null) {
            for (int i = 0; i < pageIndicator.getChildCount(); i++) {
                PageIndicatorView dot = (PageIndicatorView) pageIndicator.getChildAt(i);
                if (dot != null) {
                    dot.setCurrentPage(i == currentPosition ? 0 : -1);
                }
            }
        }
    }
    
    private void setupNavigation() {
        if (icBack != null) {
            icBack.setOnClickListener(v -> {
                String category = getIntent().getStringExtra("category");
                
                Intent intent = new Intent(SwipeableTipsActivity.this, TipsActivity.class);
                intent.putExtra("return_to_category", category);
                startActivity(intent);
                finish();
            });
        }
    }
}