package com.example.sampleiwatts;

public class TipCard {
    private String title;
    private String description;
    private int imageResource;
    private String category;

    public TipCard(String title, String description, int imageResource, String category) {
        this.title = title;
        this.description = description;
        this.imageResource = imageResource;
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getImageResource() {
        return imageResource;
    }

    public void setImageResource(int imageResource) {
        this.imageResource = imageResource;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}


