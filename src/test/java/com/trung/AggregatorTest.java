package com.trung;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AggregatorTest {

    @Test
    void aggregatesCampaignMetricsAndSkipsMalformedRows() throws IOException {
        Path inputFile = Files.createTempFile("ad-data", ".csv");
        try (InputStream inputStream = AggregatorTest.class.getClassLoader().getResourceAsStream("fixtures/sample-ad-data.csv")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing test resource: fixtures/sample-ad-data.csv");
            }
            Files.copy(inputStream, inputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        AggregationResult result = new Aggregator().aggregate(inputFile);

        assertEquals(5, result.getProcessedRows());
        assertEquals(1, result.getSkippedRows());
        assertEquals(4, result.getStats().size());

        Map<String, CampaignStats> stats = result.getStats();

        CampaignStats campA = stats.get("camp-a");
        assertEquals(150L, campA.getTotalImpressions());
        assertEquals(15L, campA.getTotalClicks());
        assertEquals(60.0, campA.getTotalSpend(), 0.0001);
        assertEquals(6L, campA.getTotalConversions());
        assertEquals(0.1, campA.getCtr(), 0.000001);
        assertEquals(10.0, campA.getCpa(), 0.000001);

        CampaignStats campB = stats.get("camp-b");
        assertEquals(200L, campB.getTotalImpressions());
        assertEquals(10L, campB.getTotalClicks());
        assertEquals(20.0, campB.getTotalSpend(), 0.0001);
        assertEquals(10L, campB.getTotalConversions());
        assertEquals(0.05, campB.getCtr(), 0.000001);
        assertEquals(2.0, campB.getCpa(), 0.000001);

        CampaignStats campC = stats.get("camp-c");
        assertEquals(80L, campC.getTotalImpressions());
        assertEquals(0L, campC.getTotalClicks());
        assertEquals(8.0, campC.getTotalSpend(), 0.0001);
        assertEquals(0L, campC.getTotalConversions());
        assertEquals(0.0, campC.getCtr(), 0.000001);
        assertNull(campC.getCpa());
    }
}


