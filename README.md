# Ad Performance Aggregator

A Java CLI application that aggregates advertising performance data from a CSV file and writes two reports:

- `top10_ctr.csv`
- `top10_cpa.csv`

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

The peak memory value is sampled from the JVM heap during the run.


## Benchmark results

| Approach                  | Description | Elapsed time | Peak heap usage | Notes |
|---------------------------|---|---:|---:|---|
| Approach 1                | `BufferedReader` + batched concurrent workers (original implementation) | 4.334 s | 2,633.62 MB | Fastest, high memory usage |
| Approach 2<br/>(AI Optimized) | Streaming single-pass parser (single-thread) | 10.524 s | 17.99 MB | Memory-efficient single-threaded streaming |
| Approach 3                | Streaming single-pass parser + 10 worker threads | 4.505 s | 1,011.20 MB | Much faster, higher memory usage (trade-off) |

## Test machine (hardware)

The benchmark runs were executed on the following machine:

- CPU: Intel(R) Xeon(R) CPU E5-2680 v4 @ 2.40GHz
- RAM: 32 GB
- OS: Windows 11 (build 25H2)
- Java runtime: Java(TM) SE Runtime Environment (build 1.8.0_491-b10)

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


