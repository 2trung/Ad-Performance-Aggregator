package com.trung;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

	private static final String USAGE = "Usage: java -jar ad-performance-aggregator.jar --input <input.csv> --output <output-directory>";

	public static void main(String[] args) {
		int exitCode = new Main().run(args, System.out, System.err);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	int run(String[] args, PrintStream out, PrintStream err) {
		try {
			CliArguments cliArguments = parseArguments(args);
			if (cliArguments.helpRequested) {
				out.println(USAGE);
				return 0;
			}

			if (cliArguments.inputPath == null || cliArguments.outputDir == null) {
				err.println("Missing required arguments.");
				err.println(USAGE);
				return 1;
			}

			if (!Files.exists(cliArguments.inputPath)) {
				err.println("Input file does not exist: " + cliArguments.inputPath);
				return 1;
			}

			if (!Files.isRegularFile(cliArguments.inputPath)) {
				err.println("Input path is not a file: " + cliArguments.inputPath);
				return 1;
			}

			long startNanos = System.nanoTime();
			MemoryMonitor memoryMonitor = new MemoryMonitor();
			memoryMonitor.start();

			AggregationResult result;
			try {
				result = new Aggregator().aggregate(cliArguments.inputPath);
				ReportGenerator.writeTopCtr(result.getStats(), cliArguments.outputDir);
				ReportGenerator.writeTopCpa(result.getStats(), cliArguments.outputDir);
			} finally {
				memoryMonitor.stop();
			}

			long elapsedNanos = System.nanoTime() - startNanos;
			out.printf(Locale.US, "Processed rows: %d%n", result.getProcessedRows());
			out.printf(Locale.US, "Skipped rows: %d%n", result.getSkippedRows());
			out.printf(Locale.US, "Elapsed time: %.3f seconds%n", elapsedNanos / 1_000_000_000.0);
			out.printf(Locale.US, "Peak heap usage: %.2f MB%n", memoryMonitor.getPeakUsedBytes() / 1024.0 / 1024.0);
			out.println("Output directory: " + cliArguments.outputDir.toAbsolutePath());
			return 0;
		} catch (IllegalArgumentException e) {
			err.println(e.getMessage());
			err.println(USAGE);
			return 1;
		} catch (IOException e) {
			err.println("Failed to process report: " + e.getMessage());
			return 1;
		}
	}

	private CliArguments parseArguments(String[] args) {
		CliArguments cliArguments = new CliArguments();

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "--help":
				case "-h":
					cliArguments.helpRequested = true;
					return cliArguments;
				case "--input":
					cliArguments.inputPath = readPathArgument(args, ++i, "--input");
					break;
				case "--output":
					cliArguments.outputDir = readPathArgument(args, ++i, "--output");
					break;
				default:
					throw new IllegalArgumentException("Unknown argument: " + arg);
			}
		}

		if (cliArguments.outputDir != null) {
			try {
				Files.createDirectories(cliArguments.outputDir);
			} catch (IOException e) {
				throw new IllegalArgumentException("Unable to create output directory: " + cliArguments.outputDir, e);
			}
		}

		return cliArguments;
	}

	private Path readPathArgument(String[] args, int index, String optionName) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Missing value for " + optionName);
		}
		return Paths.get(args[index]);
	}

	private static class CliArguments {
		private Path inputPath;
		private Path outputDir;
		private boolean helpRequested;
	}

	private static class MemoryMonitor {
		private final AtomicLong peakUsedBytes = new AtomicLong();
		private volatile boolean running;
		private Thread samplerThread;

		void start() {
			running = true;
			sample();
			samplerThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (running) {
						sample();
						try {
							Thread.sleep(50L);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
				}
			}, "heap-monitor");
			samplerThread.setDaemon(true);
			samplerThread.start();
		}

		void stop() {
			running = false;
			if (samplerThread != null) {
				samplerThread.interrupt();
				try {
					samplerThread.join(200L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			sample();
		}

		long getPeakUsedBytes() {
			return peakUsedBytes.get();
		}

		private void sample() {
			Runtime runtime = Runtime.getRuntime();
			long usedBytes = runtime.totalMemory() - runtime.freeMemory();
			long currentPeak;
			do {
				currentPeak = peakUsedBytes.get();
				if (usedBytes <= currentPeak) {
					return;
				}
			} while (!peakUsedBytes.compareAndSet(currentPeak, usedBytes));
		}
	}

}