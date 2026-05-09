package com.trung;

public class CampaignStats {
    private final String campaignId;
    private long totalImpressions;
    private long totalClicks;
    private double totalSpend;
    private long totalConversions;

    public CampaignStats(String campaignId) {
        this.campaignId = campaignId;
    }

    public void add(long impressions, long clicks, double spend, long conversions) {
        this.totalImpressions += impressions;
        this.totalClicks += clicks;
        this.totalSpend += spend;
        this.totalConversions += conversions;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public long getTotalImpressions() {
        return totalImpressions;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public double getTotalSpend() {
        return totalSpend;
    }

    public long getTotalConversions() {
        return totalConversions;
    }

    public double getCtr() {
        return totalImpressions == 0 ? 0 : (double) totalClicks / totalImpressions;
    }

    public Double getCpa() {
        return totalConversions == 0 ? null : totalSpend / totalConversions;
    }
}
