package com.freightfox.tollplaza.importer;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class CsvImportSummary {
    private int totalRowsProcessed;
    private int successfullyImported;
    private int duplicatesSkipped;
    private int invalidRowsSkipped;
    private long durationMillis;
}

