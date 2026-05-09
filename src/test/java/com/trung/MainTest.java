package com.trung;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void cliRunsAndWritesExpectedReports() throws IOException {
        Path tempDir = Files.createTempDirectory("ad-performance-cli");
        Path inputFile = tempDir.resolve("input.csv");
        Path outputDir = tempDir.resolve("results");
        copyResourceTo("fixtures/sample-ad-data.csv", inputFile);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new Main().run(
                new String[]{"--input", inputFile.toString(), "--output", outputDir.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderr.toString(StandardCharsets.UTF_8.name()));
        assertTrue(stdout.toString(StandardCharsets.UTF_8.name()).contains("Elapsed time:"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8.name()).contains("Peak heap usage:"));
        assertTrue(Files.exists(outputDir.resolve("top10_ctr.csv")));
        assertTrue(Files.exists(outputDir.resolve("top10_cpa.csv")));

        List<String> ctrLines = Files.readAllLines(outputDir.resolve("top10_ctr.csv"), StandardCharsets.UTF_8);
        List<String> cpaLines = Files.readAllLines(outputDir.resolve("top10_cpa.csv"), StandardCharsets.UTF_8);

        assertEquals("campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA", ctrLines.get(0));
        assertEquals("camp-d,25,5,5.00,0,0.2000,null", ctrLines.get(1));
        assertEquals("camp-a,150,15,60.00,6,0.1000,10.00", ctrLines.get(2));
        assertEquals("camp-b,200,10,20.00,10,0.0500,2.00", ctrLines.get(3));
        assertEquals("camp-c,80,0,8.00,0,0.0000,null", ctrLines.get(4));

        assertEquals("campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA", cpaLines.get(0));
        assertEquals("camp-b,200,10,20.00,10,0.0500,2.00", cpaLines.get(1));
        assertEquals("camp-a,150,15,60.00,6,0.1000,10.00", cpaLines.get(2));
    }

    @Test
    void cliReportsUsageWhenArgumentsAreMissing() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new Main().run(new String[0], new PrintStream(stdout), new PrintStream(stderr));

        assertEquals(1, exitCode);
        assertTrue(stderr.toString().contains("Missing required arguments."));
        assertTrue(stderr.toString().contains("Usage:"));
    }

    private static void copyResourceTo(String resourcePath, Path destination) throws IOException {
        try (InputStream inputStream = MainTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing test resource: " + resourcePath);
            }
            Files.copy(inputStream, destination);
        }
    }
}

