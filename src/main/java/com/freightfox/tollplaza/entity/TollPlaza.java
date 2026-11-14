package com.freightfox.tollplaza.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

/**
 * JPA Entity representing an Indian Highway Toll Plaza.
 * Matches the actual dataset schema: longitude, latitude, toll_name, geo_state.
 *
 * Database Optimizations:
 * 1. Indexes on latitude and longitude for high-speed spatial bounding box filtering.
 * 2. Composite unique constraint on (latitude, longitude, toll_name) to prevent duplicate toll entries.
 */
@Entity
@Table(
    name = "toll_plazas",
    indexes = {
        @Index(name = "idx_toll_lat_lon", columnList = "latitude, longitude"),
        @Index(name = "idx_toll_name", columnList = "toll_name")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_toll_coords_name", columnNames = {"latitude", "longitude", "toll_name"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TollPlaza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "toll_name", nullable = false, length = 255)
    private String name;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "geo_state", length = 100)
    private String state;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TollPlaza tollPlaza = (TollPlaza) o;
        return Objects.equals(latitude, tollPlaza.latitude) &&
               Objects.equals(longitude, tollPlaza.longitude) &&
               Objects.equals(name, tollPlaza.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, name);
    }
}

