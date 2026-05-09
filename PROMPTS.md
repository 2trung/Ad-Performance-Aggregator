# Project Prompts & Development Log

This document records the key prompts and requests that guided the development of the Ad Performance Aggregator CLI application. Each prompt includes its purpose and the action taken.

---

## Prompt 1: Initial project setup and requirements

**Request:**
> I am building a CLI application to process a large CSV file (~1GB) containing advertising performance data. I've implemented the main logic. I need you to do something:
> - Make sure the application can run from the CLI, for example: `java ... --input ad_data.csv --output results/`
> - Create tests to verify correctness
> - Include a README with setup and run instructions
> - Create Dockerfile to containerize application
> - Measure time and peak memory usage

## Prompt 2: Optimize performance and memory usage

**Request:**
> Both Elapsed time and Peak heap usage is high.  
> Find a solution to optimize and implement it?

**Outcome:**
- Replaced concurrent batch pipeline with a streaming single-pass parser to reduce memory
- Removed per-row string allocations using reusable lookup keys
- Introduced manual numeric parsing (avoiding `Integer.parseInt()` overhead)
- Achieved 129.57 MB peak heap with 8.77s runtime (single-threaded approach)

---

## Prompt 3: Add multi-threading capability

**Purpose:** Improve throughput on multi-core systems while keeping memory usage under control.

**Request:**
> Keep current approach but add an option to enable multi-threading for processing batches of rows concurrently. Ensure thread safety and measure performance impact.