package com.trung;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
public class Aggregator {
    private static final int EXPECTED_COLUMN_COUNT = 6;
    private static final int BATCH_SIZE = 10000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    public AggregationResult aggregate(Path csvPath) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<AggregationResult>> partialResultList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            // Skip header
            reader.readLine();
            String line;
            List<String> batch = new ArrayList<>(BATCH_SIZE);
            // Chia batch để xử lý song song
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                batch.add(line);
                if (batch.size() == BATCH_SIZE) {
                    partialResultList.add(executor.submit(new BatchProcessor(batch)));
                    batch = new ArrayList<>(BATCH_SIZE);
                }
            }
            if (!batch.isEmpty()) {
                partialResultList.add(executor.submit(new BatchProcessor(batch)));
            }
        }
        executor.shutdown();
        Map<String, CampaignStats> finalStats = new HashMap<>();
        int totalProcessed = 0;
        int totalSkipped = 0;
        // Tổng hợp kết quả
        for (Future<AggregationResult> f : partialResultList) {
            try {
                AggregationResult pr = f.get();
                totalProcessed += pr.getProcessedRows();
                totalSkipped += pr.getSkippedRows();
                for (CampaignStats cs : pr.getStats().values()) {
                    CampaignStats fs = finalStats.computeIfAbsent(cs.getCampaignId(), CampaignStats::new);
                    fs.add(cs.getTotalImpressions(), cs.getTotalClicks(), cs.getTotalSpend(), cs.getTotalConversions());
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException("Error during concurrent aggregation", e);
            }
        }
        return new AggregationResult(finalStats, totalProcessed, totalSkipped);
    }
    private static class BatchProcessor implements Callable<AggregationResult> {
        private final List<String> lines;
        public BatchProcessor(List<String> lines) {
            this.lines = lines;
        }
        @Override
        public AggregationResult call() {
            Map<String, CampaignStats> stats = new HashMap<>();
            int processedRows = 0;
            int skippedRows = 0;
            for (String line : lines) {
                String[] columns = line.split(",", -1);
                if (columns.length != EXPECTED_COLUMN_COUNT) {
                    skippedRows++;
                    continue;
                }
                try {
                    String campaignId = columns[0].trim();
                    long impressions = Long.parseLong(columns[2].trim());
                    long clicks = Long.parseLong(columns[3].trim());
                    double spend = Double.parseDouble(columns[4].trim());
                    long conversions = Long.parseLong(columns[5].trim());
                    stats.computeIfAbsent(campaignId, CampaignStats::new)
                            .add(impressions, clicks, spend, conversions);
                    processedRows++;
                } catch (NumberFormatException e) {
                    skippedRows++;
                }
            }
            return new AggregationResult(stats, processedRows, skippedRows);
        }
    }
}
