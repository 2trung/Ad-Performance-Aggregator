package com.trung;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

public class ReportGenerator {
    private static final String HEADER = "campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA";
    private static final String OUTPUT_CTR_FILE = "top10_ctr.csv";
    private static final String OUTPUT_CPA_FILE = "top10_cpa.csv";
    private static final int TOP_N = 10;
    private static final Comparator<CampaignStats> CTR_ORDER = Comparator
            .comparingDouble(CampaignStats::getCtr)
            .reversed()
            .thenComparing(CampaignStats::getCampaignId);
    private static final Comparator<CampaignStats> CPA_ORDER = Comparator
            .comparingDouble(CampaignStats::getCpa)
            .thenComparing(CampaignStats::getCampaignId);


    public static void writeTopCtr(Map<String, CampaignStats> statsByCampaign, Path outputDir) throws IOException {
        List<CampaignStats> top = topCampaigns(statsByCampaign.values(), CTR_ORDER, false);

        writeCsv(outputDir.resolve(OUTPUT_CTR_FILE), top);
    }

    public static void writeTopCpa(Map<String, CampaignStats> statsByCampaign, Path outputDir) throws IOException {
        List<CampaignStats> top = topCampaigns(statsByCampaign.values(), CPA_ORDER, true);

        writeCsv(outputDir.resolve(OUTPUT_CPA_FILE), top);
    }

    private static List<CampaignStats> topCampaigns(Collection<CampaignStats> campaigns,
                                                    Comparator<CampaignStats> order,
                                                    boolean filterNullCpa) {
        PriorityQueue<CampaignStats> heap = new PriorityQueue<>(TOP_N, order.reversed());

        for (CampaignStats stats : campaigns) {
            if (filterNullCpa && stats.getCpa() == null) {
                continue;
            }

            if (heap.size() < TOP_N) {
                heap.offer(stats);
            } else if (order.compare(stats, heap.peek()) < 0) {
                heap.poll();
                heap.offer(stats);
            }
        }

        List<CampaignStats> top = new ArrayList<>(heap);
        top.sort(order);
        return top;
    }

    private static void writeCsv(Path outputFile, List<CampaignStats> statsList) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();

            for (CampaignStats stats : statsList) {
                writer.write(formatRow(stats));
                writer.newLine();
            }
        }
    }

    private static String formatRow(CampaignStats stats) {
        String cpaText = stats.getCpa() == null
                ? "null"
                : String.format(Locale.US, "%.2f", stats.getCpa());

        return String.format(
                Locale.US,
                "%s,%d,%d,%.2f,%d,%.4f,%s",
                stats.getCampaignId(),
                stats.getTotalImpressions(),
                stats.getTotalClicks(),
                stats.getTotalSpend(),
                stats.getTotalConversions(),
                stats.getCtr(),
                cpaText
        );
    }
}
