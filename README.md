# Ad Performance Aggregator

A Java CLI application that aggregates advertising performance data from a CSV file and writes two reports:

- `top10_ctr.csv`
- `top10_cpa.csv`

It also prints basic runtime metrics after each run:

- elapsed time
- sampled peak heap usage

## Requirements

- Java 8+
- Maven 3.8+

## Build and test

```bash
mvn test
mvn package
```

After packaging, Maven creates an executable jar at:

```text
target/ad-performance-aggregator-1.0-SNAPSHOT.jar
```

## Run from the CLI

```bash
java -jar target/ad-performance-aggregator-1.0-SNAPSHOT.jar --input ad_data.csv --output results/
```

You can also pass an absolute or relative path for the input file and output directory.

### Example

```bash
java -jar target/ad-performance-aggregator-1.0-SNAPSHOT.jar --input ./data/ad_data.csv --output ./results
```

The output directory will be created automatically if it does not exist.

### Output format

The generated CSV files share this header:

```text
campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA
```

- CTR is formatted to 4 decimal places.
- CPA is formatted to 2 decimal places, or `null` when a campaign has zero conversions.

## Performance metrics

At the end of each run, the CLI prints:

- processed and skipped row counts
- elapsed time in seconds
- peak heap usage in MB

The peak memory value is sampled from the JVM heap during the run, so it is best treated as a practical runtime estimate rather than a precise JVM profiler reading.

## Docker

Build the image:

```bash
docker build -t ad-performance-aggregator .
```

Run the container:

```bash
docker run --rm \
  -v "%cd%/data:/data" \
  -v "%cd%/results:/results" \
  ad-performance-aggregator \
  --input /data/ad_data.csv \
  --output /results
```

If you are using PowerShell or a Unix shell, adjust the volume syntax accordingly.

## CSV expectations

The input file should contain a header row followed by rows with 6 comma-separated columns:

1. campaign ID
2. campaign name
3. impressions
4. clicks
5. spend
6. conversions

Rows with the wrong number of columns or invalid numeric values are skipped.

