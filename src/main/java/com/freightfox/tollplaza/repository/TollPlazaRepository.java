package com.freightfox.tollplaza.repository;

import com.freightfox.tollplaza.entity.TollPlaza;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for TollPlaza entities.
 * Provides standard CRUD and indexed spatial bounding box queries.
 */
@Repository
public interface TollPlazaRepository extends JpaRepository<TollPlaza, Long> {

    /**
     * Bounding box spatial pre-filter.
     * Selects all toll plazas whose latitude and longitude fall within the route envelope.
     * Uses idx_toll_lat_lon for O(log N) retrieval instead of full-table O(N) scan.
     */
    @Query("""
        SELECT t FROM TollPlaza t
        WHERE t.latitude BETWEEN :minLat AND :maxLat
          AND t.longitude BETWEEN :minLon AND :maxLon
    """)
    List<TollPlaza> findWithinBoundingBox(
        @Param("minLat") Double minLat,
        @Param("maxLat") Double maxLat,
        @Param("minLon") Double minLon,
        @Param("maxLon") Double maxLon
    );

    /**
     * Check if a toll plaza with the same coordinates and name already exists.
     * Used during CSV ingestion to prevent duplicate inserts.
     */
    boolean existsByLatitudeAndLongitudeAndName(Double latitude, Double longitude, String name);
}

