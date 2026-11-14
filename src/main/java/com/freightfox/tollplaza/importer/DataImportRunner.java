package com.freightfox.tollplaza.importer;

import com.freightfox.tollplaza.repository.TollPlazaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Automatically seeds the database from the CSV dataset on application startup
 * if the toll_plazas table is empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataImportRunner implements CommandLineRunner {

    private final CsvImportService csvImportService;
    private final TollPlazaRepository tollPlazaRepository;

    @Value("${app.toll-data.csv-path:classpath:data/toll_plaza_india.csv}")
    private String csvPath;

    @Value("${app.toll-data.auto-import-on-startup:true}")
    private boolean autoImportEnabled;

    @Override
    public void run(String... args) {
        if (!autoImportEnabled) {
            log.info("Automatic CSV toll data import is disabled via configuration.");
            return;
        }

        long existingCount = tollPlazaRepository.count();
        if (existingCount > 0) {
            log.info("Database already contains {} toll plazas. Skipping initial CSV import.", existingCount);
            return;
        }

        log.info("Toll plaza database is empty. Executing initial CSV import from '{}'...", csvPath);
        try {
            CsvImportSummary summary = csvImportService.importTollPlazas(csvPath);
            log.info("Startup CSV import finished successfully: {}", summary);
        } catch (Exception e) {
            log.error("Failed to seed toll plaza data on startup: {}", e.getMessage(), e);
        }
    }
}

