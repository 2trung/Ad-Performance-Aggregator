package com.trung;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Aggregator {
    private static final int BUFFER_SIZE = 1 << 16;
    private static final int BATCH_SIZE = 20000;
    private static final int THREAD_POOL_SIZE = 5;
    private static final int MAX_IN_FLIGHT_BATCHES = THREAD_POOL_SIZE * 2;

    public AggregationResult aggregate(Path csvPath) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<AggregationResult> completionService = new ExecutorCompletionService<>(executor);
        Map<String, CampaignStats> finalStats = new HashMap<>();
        int processedRows = 0;
        int skippedRows = 0;
        int submittedBatches = 0;
        int completedBatches = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8),
                BUFFER_SIZE)) {
            reader.readLine();

            List<String> batch = new ArrayList<>(BATCH_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                batch.add(line);
                if (batch.size() == BATCH_SIZE) {
                    completionService.submit(new BatchProcessor(new ArrayList<>(batch)));
                    submittedBatches++;
                    batch.clear();

                    if (submittedBatches - completedBatches >= MAX_IN_FLIGHT_BATCHES) {
                        AggregationResult partial = takeNextResult(completionService);
                        completedBatches++;
                        processedRows += partial.getProcessedRows();
                        skippedRows += partial.getSkippedRows();
                        mergeStats(finalStats, partial.getStats());
                    }
                }
            }

            if (!batch.isEmpty()) {
                completionService.submit(new BatchProcessor(new ArrayList<>(batch)));
                submittedBatches++;
            }

            while (completedBatches < submittedBatches) {
                AggregationResult partial = takeNextResult(completionService);
                completedBatches++;
                processedRows += partial.getProcessedRows();
                skippedRows += partial.getSkippedRows();
                mergeStats(finalStats, partial.getStats());
            }

            return new AggregationResult(finalStats, processedRows, skippedRows);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for batch processing to complete", e);
        } catch (ExecutionException e) {
            throw new IOException("Error during concurrent aggregation", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static AggregationResult takeNextResult(CompletionService<AggregationResult> completionService)
            throws InterruptedException, ExecutionException {
        Future<AggregationResult> future = completionService.take();
        return future.get();
    }

    private static void mergeStats(Map<String, CampaignStats> target, Map<String, CampaignStats> source) {
        for (CampaignStats cs : source.values()) {
            CampaignStats aggregate = target.computeIfAbsent(cs.getCampaignId(), CampaignStats::new);
            aggregate.add(cs.getTotalImpressions(), cs.getTotalClicks(), cs.getTotalSpend(), cs.getTotalConversions());
        }
    }

    private static final class BatchProcessor implements Callable<AggregationResult> {
        private final List<String> lines;
        private final CampaignLookupKey campaignLookupKey = new CampaignLookupKey();

        private BatchProcessor(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public AggregationResult call() {
            Map<String, CampaignStats> statsByCampaign = new HashMap<>();
            int processedRows = 0;
            int skippedRows = 0;

            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    continue;
                }

                try {
                    if (processLine(line, statsByCampaign)) {
                        processedRows++;
                    } else {
                        skippedRows++;
                    }
                } catch (NumberFormatException e) {
                    skippedRows++;
                }
            }

            return new AggregationResult(statsByCampaign, processedRows, skippedRows);
        }

        private boolean processLine(String line, Map<String, CampaignStats> statsByCampaign) {
            int comma1 = line.indexOf(',');
            if (comma1 < 0) return false;
            int comma2 = line.indexOf(',', comma1 + 1);
            if (comma2 < 0) return false;
            int comma3 = line.indexOf(',', comma2 + 1);
            if (comma3 < 0) return false;
            int comma4 = line.indexOf(',', comma3 + 1);
            if (comma4 < 0) return false;
            int comma5 = line.indexOf(',', comma4 + 1);
            if (comma5 < 0) return false;
            if (line.indexOf(',', comma5 + 1) >= 0) return false;

            int campaignStart = trimLeft(line, 0, comma1);
            int campaignEnd = trimRight(line, campaignStart, comma1);

            long impressions = parseLong(line, comma2 + 1, comma3);
            long clicks = parseLong(line, comma3 + 1, comma4);
            double spend = parseDouble(line, comma4 + 1, comma5);
            long conversions = parseLong(line, comma5 + 1, line.length());

            campaignLookupKey.set(line, campaignStart, campaignEnd);
            CampaignStats stats = lookupCampaign(statsByCampaign, campaignLookupKey);
            if (stats == null) {
                String campaignId = line.substring(campaignStart, campaignEnd);
                stats = new CampaignStats(campaignId);
                statsByCampaign.put(campaignId, stats);
            }

            stats.add(impressions, clicks, spend, conversions);
            return true;
        }

        private int trimLeft(String value, int start, int end) {
            while (start < end && value.charAt(start) <= ' ') {
                start++;
            }
            return start;
        }

        private int trimRight(String value, int start, int end) {
            while (end > start && value.charAt(end - 1) <= ' ') {
                end--;
            }
            return end;
        }

        private long parseLong(String value, int start, int end) {
            start = trimLeft(value, start, end);
            end = trimRight(value, start, end);
            if (start >= end) {
                throw new NumberFormatException("Empty long field");
            }

            boolean negative = false;
            char first = value.charAt(start);
            if (first == '+' || first == '-') {
                negative = first == '-';
                start++;
            }
            if (start >= end) {
                throw new NumberFormatException("Empty long field");
            }

            long result = 0L;
            for (int i = start; i < end; i++) {
                char c = value.charAt(i);
                if (c < '0' || c > '9') {
                    throw new NumberFormatException("Invalid long field");
                }
                result = result * 10L + (c - '0');
            }
            return negative ? -result : result;
        }

        private double parseDouble(String value, int start, int end) {
            start = trimLeft(value, start, end);
            end = trimRight(value, start, end);
            if (start >= end) {
                throw new NumberFormatException("Empty double field");
            }

            boolean negative = false;
            char first = value.charAt(start);
            if (first == '+' || first == '-') {
                negative = first == '-';
                start++;
            }
            if (start >= end) {
                throw new NumberFormatException("Empty double field");
            }

            long integerPart = 0L;
            double fractionalPart = 0.0d;
            double divisor = 1.0d;
            boolean seenDigit = false;
            boolean decimalSeen = false;

            for (int i = start; i < end; i++) {
                char c = value.charAt(i);
                if (c == '.') {
                    if (decimalSeen) {
                        throw new NumberFormatException("Invalid double field");
                    }
                    decimalSeen = true;
                    continue;
                }
                if (c < '0' || c > '9') {
                    throw new NumberFormatException("Invalid double field");
                }

                seenDigit = true;
                int digit = c - '0';
                if (decimalSeen) {
                    fractionalPart = fractionalPart * 10.0d + digit;
                    divisor *= 10.0d;
                } else {
                    integerPart = integerPart * 10L + digit;
                }
            }

            if (!seenDigit) {
                throw new NumberFormatException("Empty double field");
            }

            double result = integerPart + (fractionalPart / divisor);
            return negative ? -result : result;
        }
    }

    private static final class CampaignLookupKey {
        private CharSequence value;
        private int start;
        private int end;
        private int hash;

        void set(CharSequence value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;

            int result = 0;
            for (int i = start; i < end; i++) {
                result = 31 * result + value.charAt(i);
            }
            this.hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof CharSequence) {
                CharSequence other = (CharSequence) obj;
                int length = end - start;
                if (other.length() != length) {
                    return false;
                }
                for (int i = 0; i < length; i++) {
                    if (value.charAt(start + i) != other.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }

            if (obj instanceof CampaignLookupKey) {
                CampaignLookupKey other = (CampaignLookupKey) obj;
                int length = end - start;
                if (other.end - other.start != length) {
                    return false;
                }
                for (int i = 0; i < length; i++) {
                    if (value.charAt(start + i) != other.value.charAt(other.start + i)) {
                        return false;
                    }
                }
                return true;
            }

            return false;
        }
    }

    private static CampaignStats lookupCampaign(Map<String, CampaignStats> statsByCampaign, CampaignLookupKey key) {
        return (CampaignStats) ((Map<?, ?>) statsByCampaign).get(key);
    }

}