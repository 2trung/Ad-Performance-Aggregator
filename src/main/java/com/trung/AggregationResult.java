package com.trung;

import java.util.HashMap;
import java.util.Map;

public class AggregationResult {
    private final Map<String, CampaignStats> stats;
    private final int processedRows;
    private final int skippedRows;


    public AggregationResult(Map<String, CampaignStats> statsByCampaign, int processedRows, int skippedRows) {
        this.stats = statsByCampaign;
        this.processedRows = processedRows;
        this.skippedRows = skippedRows;
    }

    public Map<String, CampaignStats> getStats() {
        return stats;
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }
}
