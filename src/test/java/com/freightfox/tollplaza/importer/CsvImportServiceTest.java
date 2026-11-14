package com.freightfox.tollplaza.importer;

import com.freightfox.tollplaza.repository.TollPlazaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CsvImportServiceTest {

    @Autowired
    private TollPlazaRepository repository;

    @Test
    @DisplayName("Should parse, deduplicate and import toll plazas from actual dataset")
    void shouldImportTollPlazasFromActualDataset() {
        CsvImportService importService = new CsvImportService(repository, new DefaultResourceLoader());

        // Act: import the actual CSV file
        CsvImportSummary summary = importService.importTollPlazas("classpath:data/toll_plaza_india.csv");

        // Assert
        assertThat(summary.getTotalRowsProcessed()).isEqualTo(2385);
        assertThat(summary.getSuccessfullyImported()).isGreaterThan(1500);
        assertThat(summary.getDuplicatesSkipped()).isGreaterThan(500);
        assertThat(summary.getInvalidRowsSkipped()).isEqualTo(0);

        long dbCount = repository.count();
        assertThat(dbCount).isEqualTo(summary.getSuccessfullyImported());
    }
}

