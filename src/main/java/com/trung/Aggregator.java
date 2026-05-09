package com.trung;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Aggregator {
    private static final int EXPECTED_COLUMN_COUNT = 6;
    private static final int LAST_COLUMN_INDEX = EXPECTED_COLUMN_COUNT - 1;
    private static final int BUFFER_SIZE = 1 << 16;

    public AggregationResult aggregate(Path csvPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8),
                BUFFER_SIZE)) {
            Map<String, CampaignStats> statsByCampaign = new HashMap<>();
            RowAccumulator row = new RowAccumulator();
            char[] buffer = new char[BUFFER_SIZE];
            boolean firstLine = true;
            boolean sawCarriageReturn = false;
            int read;

            while ((read = reader.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    char c = buffer[i];

                    if (sawCarriageReturn && c == '\n') {
                        sawCarriageReturn = false;
                        continue;
                    }
                    sawCarriageReturn = false;

                    if (c == ',') {
                        row.endField();
                        continue;
                    }

                    if (c == '\r' || c == '\n') {
                        if (firstLine) {
                            firstLine = false;
                        } else if (row.hasData()) {
                            row.finish(statsByCampaign);
                        }
                        row.reset();
                        if (c == '\r') {
                            sawCarriageReturn = true;
                        }
                        continue;
                    }

                    row.accept(c);
                }
            }

            if (row.hasData()) {
                if (!firstLine) {
                    row.finish(statsByCampaign);
                }
            }

            return new AggregationResult(statsByCampaign, row.getProcessedRows(), row.getSkippedRows());
        }
    }

    private static final class RowAccumulator {
        private final StringBuilder campaignId = new StringBuilder(32);
        private final LongField impressions = new LongField();
        private final LongField clicks = new LongField();
        private final DecimalField spend = new DecimalField();
        private final LongField conversions = new LongField();
        private final CampaignLookupKey campaignLookupKey = new CampaignLookupKey();

        private int columnIndex;
        private boolean rowHasData;
        private boolean rowMalformed;
        private int processedRows;
        private int skippedRows;

        void accept(char c) {
            rowHasData = true;

            switch (columnIndex) {
                case 0:
                    campaignId.append(c);
                    break;
                case 1:
                    break;
                case 2:
                    impressions.accept(c);
                    break;
                case 3:
                    clicks.accept(c);
                    break;
                case 4:
                    spend.accept(c);
                    break;
                case 5:
                    conversions.accept(c);
                    break;
                default:
                    rowMalformed = true;
            }
        }

        void finish(Map<String, CampaignStats> statsByCampaign) {
            if (rowMalformed || columnIndex != LAST_COLUMN_INDEX) {
                skippedRows++;
                return;
            }

            if (impressions.isInvalid() || clicks.isInvalid() || spend.isInvalid() || conversions.isInvalid()) {
                skippedRows++;
                return;
            }

            int start = 0;
            int end = campaignId.length();
            while (start < end && campaignId.charAt(start) <= ' ') {
                start++;
            }
            while (end > start && campaignId.charAt(end - 1) <= ' ') {
                end--;
            }

            campaignLookupKey.set(campaignId, start, end);
            CampaignStats stats = lookupCampaign(statsByCampaign, campaignLookupKey);
            if (stats == null) {
                String campaignKey = campaignId.substring(start, end);
                stats = new CampaignStats(campaignKey);
                statsByCampaign.put(campaignKey, stats);
            }

            stats.add(impressions.longValue(), clicks.longValue(), spend.doubleValue(), conversions.longValue());
            processedRows++;
        }

        int getProcessedRows() {
            return processedRows;
        }

        int getSkippedRows() {
            return skippedRows;
        }

        boolean hasData() {
            return rowHasData;
        }

        void reset() {
            campaignId.setLength(0);
            impressions.reset();
            clicks.reset();
            spend.reset();
            conversions.reset();
            columnIndex = 0;
            rowHasData = false;
            rowMalformed = false;
        }

        void endField() {
            rowHasData = true;
            if (columnIndex >= LAST_COLUMN_INDEX) {
                rowMalformed = true;
                return;
            }
            columnIndex++;
        }
    }

    private static final class CampaignLookupKey {
        private StringBuilder value;
        private int start;
        private int end;
        private int hash;

        void set(StringBuilder value, int start, int end) {
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

            if (obj instanceof String) {
                String other = (String) obj;
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

    private static final class LongField {
        private long value;
        private boolean valid = true;
        private boolean seenDigit;
        private boolean trailingWhitespace;
        private boolean negative;
        private boolean signSeen;

        void accept(char c) {
            if (!valid) {
                return;
            }

            if (c <= ' ') {
                if (seenDigit || signSeen) {
                    trailingWhitespace = true;
                }
                return;
            }

            if (c == '+' || c == '-') {
                if (signSeen || seenDigit || trailingWhitespace) {
                    valid = false;
                    return;
                }
                signSeen = true;
                negative = c == '-';
                return;
            }

            if (c < '0' || c > '9' || trailingWhitespace) {
                valid = false;
                return;
            }

            seenDigit = true;
            value = value * 10 + (c - '0');
        }

        boolean isInvalid() {
            return !valid || !seenDigit;
        }

        long longValue() {
            return negative ? -value : value;
        }

        void reset() {
            value = 0L;
            valid = true;
            seenDigit = false;
            trailingWhitespace = false;
            negative = false;
            signSeen = false;
        }
    }

    private static final class DecimalField {
        private long integerPart;
        private double fractionalPart;
        private double fractionMultiplier;
        private boolean valid = true;
        private boolean seenDigit;
        private boolean trailingWhitespace;
        private boolean negative;
        private boolean signSeen;
        private boolean decimalSeen;

        void accept(char c) {
            if (!valid) {
                return;
            }

            if (c <= ' ') {
                if (seenDigit || signSeen || decimalSeen) {
                    trailingWhitespace = true;
                }
                return;
            }

            if (c == '+' || c == '-') {
                if (signSeen || seenDigit || decimalSeen || trailingWhitespace) {
                    valid = false;
                    return;
                }
                signSeen = true;
                negative = c == '-';
                return;
            }

            if (c == '.') {
                if (decimalSeen || trailingWhitespace) {
                    valid = false;
                    return;
                }
                decimalSeen = true;
                fractionMultiplier = 0.1d;
                return;
            }

            if (c < '0' || c > '9' || trailingWhitespace) {
                valid = false;
                return;
            }

            seenDigit = true;
            int digit = c - '0';
            if (decimalSeen) {
                fractionalPart += digit * fractionMultiplier;
                fractionMultiplier *= 0.1d;
            } else {
                integerPart = integerPart * 10 + digit;
            }
        }

        boolean isInvalid() {
            return !valid || !seenDigit;
        }

        double doubleValue() {
            double value = integerPart + fractionalPart;
            return negative ? -value : value;
        }

        void reset() {
            integerPart = 0L;
            fractionalPart = 0.0d;
            fractionMultiplier = 0.0d;
            valid = true;
            seenDigit = false;
            trailingWhitespace = false;
            negative = false;
            signSeen = false;
            decimalSeen = false;
        }
    }

    private static CampaignStats lookupCampaign(Map<String, CampaignStats> statsByCampaign, CampaignLookupKey key) {
        return (CampaignStats) ((Map) statsByCampaign).get(key);
    }

}