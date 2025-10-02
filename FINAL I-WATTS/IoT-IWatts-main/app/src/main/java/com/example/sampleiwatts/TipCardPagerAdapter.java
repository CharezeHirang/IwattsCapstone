package com.example.sampleiwatts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TipCardPagerAdapter extends RecyclerView.Adapter<TipCardPagerAdapter.TipCardViewHolder> {
    
    private List<TipCard> tipCards;
    
    public TipCardPagerAdapter(List<TipCard> tipCards) {
        this.tipCards = tipCards;
    }

    @NonNull
    @Override
    public TipCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tip_card_layout, parent, false);
        return new TipCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TipCardViewHolder holder, int position) {
        TipCard tipCard = tipCards.get(position);
        holder.bind(tipCard);
    }

    @Override
    public int getItemCount() {
        return tipCards.size();
    }

    public static class TipCardViewHolder extends RecyclerView.ViewHolder {
        private ImageView tipImage;

        public TipCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tipImage = itemView.findViewById(R.id.tip_image);
        }

        public void bind(TipCard tipCard) {
            tipImage.setImageResource(tipCard.getImageResource());
        }
    }
}


