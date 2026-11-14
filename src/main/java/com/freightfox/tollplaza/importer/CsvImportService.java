package com.freightfox.tollplaza.importer;

import com.freightfox.tollplaza.entity.TollPlaza;
import com.freightfox.tollplaza.repository.TollPlazaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for parsing, validating, deduplicating,
 * and batch-importing toll plazas from CSV files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final TollPlazaRepository tollPlazaRepository;
    private final ResourceLoader resourceLoader;

    private static final int BATCH_SIZE = 50;

    /**
     * Imports toll plaza data from the configured resource path.
     *
     * @param location Resource path (e.g., "classpath:data/toll_plaza_india.csv" or "file:toll_plaza_india.csv")
     * @return CsvImportSummary detailing import statistics
     */
    @Transactional
    public CsvImportSummary importTollPlazas(String location) {
        long startTime = System.currentTimeMillis();
        log.info("Starting toll plaza CSV import from location: {}", location);

        Resource resource = resolveResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("CSV resource not found at location: " + location);
        }

        int totalProcessed = 0;
        int imported = 0;
        int duplicates = 0;
        int invalidRows = 0;

        Set<String> seenKeys = new HashSet<>();
        List<TollPlaza> batch = new ArrayList<>(BATCH_SIZE);

        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : csvParser) {
                totalProcessed++;

                try {
                    String lonStr = record.get("longitude");
                    String latStr = record.get("latitude");
                    String name = record.get("toll_name");
                    String state = record.isMapped("geo_state") ? record.get("geo_state") : "";

                    if (lonStr == null || latStr == null || name == null || name.isBlank()) {
                        invalidRows++;
                        continue;
                    }

                    double longitude = Double.parseDouble(lonStr);
                    double latitude = Double.parseDouble(latStr);

                    // India coordinate bounds validation (Lat: 6.0 to 38.0, Lon: 68.0 to 98.0)
                    if (!isValidIndianCoordinate(latitude, longitude)) {
                        log.warn("Row {} skipped: invalid coordinates ({}, {}) for toll '{}'",
                                totalProcessed, latitude, longitude, name);
                        invalidRows++;
                        continue;
                    }

                    // Deduplication key
                    String deduplicationKey = String.format("%.6f:%.6f:%s", latitude, longitude, name.toLowerCase());
                    if (seenKeys.contains(deduplicationKey)) {
                        duplicates++;
                        continue;
                    }
                    seenKeys.add(deduplicationKey);

                    TollPlaza tollPlaza = TollPlaza.builder()
                            .name(name)
                            .latitude(latitude)
                            .longitude(longitude)
                            .state(state.isBlank() ? "Unknown" : state)
                            .build();

                    batch.add(tollPlaza);
                    imported++;

                    if (batch.size() >= BATCH_SIZE) {
                        tollPlazaRepository.saveAll(batch);
                        batch.clear();
                    }

                } catch (NumberFormatException e) {
                    log.warn("Row {} skipped: invalid number format", totalProcessed);
                    invalidRows++;
                } catch (Exception e) {
                    log.warn("Row {} skipped: {}", totalProcessed, e.getMessage());
                    invalidRows++;
                }
            }

            if (!batch.isEmpty()) {
                tollPlazaRepository.saveAll(batch);
                batch.clear();
            }

        } catch (Exception e) {
            log.error("Fatal error while importing toll plaza CSV: {}", e.getMessage(), e);
            throw new RuntimeException("CSV import failed: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        CsvImportSummary summary = CsvImportSummary.builder()
                .totalRowsProcessed(totalProcessed)
                .successfullyImported(imported)
                .duplicatesSkipped(duplicates)
                .invalidRowsSkipped(invalidRows)
                .durationMillis(duration)
                .build();

        log.info("CSV Import completed successfully: {}", summary);
        return summary;
    }

    private Resource resolveResource(String location) {
        // 1. Direct resource resolution (e.g. classpath: or file:)
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            return resource;
        }

        // 2. Check if it's a direct file on disk
        java.io.File file = new java.io.File(location);
        if (file.exists()) {
            return new org.springframework.core.io.FileSystemResource(file);
        }

        // 3. Fallback: try prefixing with classpath:
        if (!location.startsWith("classpath:")) {
            resource = resourceLoader.getResource("classpath:" + location);
            if (resource.exists()) {
                return resource;
            }
        }

        return resource;
    }

    private boolean isValidIndianCoordinate(double lat, double lon) {
        return lat >= 6.0 && lat <= 38.0 && lon >= 68.0 && lon <= 98.0;
    }
}

