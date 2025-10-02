package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

public class I_TipsActivity extends AppCompatActivity {

    private ImageView icBack;
    private ViewPager2 tipsViewPager;
    private TextView tipCounter;
    private TextView categoryTitle;
    private ProgressBar swipeProgress;
    private TipCardPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tip_cards);

        icBack = findViewById(R.id.ic_back);
        tipsViewPager = findViewById(R.id.tips_viewpager);
        tipCounter = findViewById(R.id.tip_counter);
        categoryTitle = findViewById(R.id.category_title);
        swipeProgress = findViewById(R.id.swipe_progress);

        icBack.setOnClickListener(v -> {
            Intent intent = new Intent(I_TipsActivity.this, TipsActivity.class);
            startActivity(intent);
            finish();
        });

        setupViewPager();
    }

    private void setupViewPager() {
        List<TipCard> tips = TipDataManager.getCategoryITips();
        adapter = new TipCardPagerAdapter(tips);
        tipsViewPager.setAdapter(adapter);

        tipsViewPager.setPageTransformer(new CardPageTransformer());

        tipCounter.setText("1 of " + tips.size());
        categoryTitle.setText(getString(R.string.text81) + " Tips");

        swipeProgress.setMax(tips.size());
        swipeProgress.setProgress(1);

        tipsViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                tipCounter.setText((position + 1) + " of " + tips.size());
                swipeProgress.setProgress(position + 1);
            }
        });
    }
}