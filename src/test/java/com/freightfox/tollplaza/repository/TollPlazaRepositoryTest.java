package com.freightfox.tollplaza.repository;

import com.freightfox.tollplaza.entity.TollPlaza;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TollPlazaRepositoryTest {

    @Autowired
    private TollPlazaRepository repository;

    @Test
    @DisplayName("Should save and retrieve toll plaza by bounding box")
    void shouldFindWithinBoundingBox() {
        // Arrange: 3 tolls - 2 inside box (Pune region), 1 outside (Bengaluru region)
        TollPlaza toll1 = TollPlaza.builder()
                .name("Khed Shivapur Toll Plaza")
                .latitude(18.350)
                .longitude(73.850)
                .state("Maharashtra")
                .build();

        TollPlaza toll2 = TollPlaza.builder()
                .name("Somatane Toll Plaza")
                .latitude(18.720)
                .longitude(73.680)
                .state("Maharashtra")
                .build();

        TollPlaza tollFar = TollPlaza.builder()
                .name("Navalgund Toll Plaza")
                .latitude(15.540)
                .longitude(75.360)
                .state("Karnataka")
                .build();

        repository.saveAll(List.of(toll1, toll2, tollFar));

        // Act: Query bounding box around Pune [lat: 18.0 to 19.0, lon: 73.0 to 74.0]
        List<TollPlaza> results = repository.findWithinBoundingBox(18.0, 19.0, 73.0, 74.0);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results).extracting(TollPlaza::getName)
                .containsExactlyInAnyOrder("Khed Shivapur Toll Plaza", "Somatane Toll Plaza");
    }

    @Test
    @DisplayName("Should detect existence of toll plaza for deduplication")
    void shouldDetectDuplicateTollPlaza() {
        TollPlaza toll = TollPlaza.builder()
                .name("Dhamnod Toll Plaza")
                .latitude(23.41805978)
                .longitude(74.99684426)
                .state("Madhya Pradesh")
                .build();
        repository.save(toll);

        boolean exists = repository.existsByLatitudeAndLongitudeAndName(
                23.41805978, 74.99684426, "Dhamnod Toll Plaza");
        boolean notExists = repository.existsByLatitudeAndLongitudeAndName(
                23.41805978, 74.99684426, "Different Name");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}

