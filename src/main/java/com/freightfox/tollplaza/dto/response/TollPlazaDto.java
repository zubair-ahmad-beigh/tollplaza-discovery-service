package com.freightfox.tollplaza.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a highway toll plaza encountered along the route")
public class TollPlazaDto {

    @Schema(description = "Name of the toll plaza", example = "Chalageri Toll Plaza")
    private String name;

    @Schema(description = "Latitude of the toll plaza", example = "14.6158")
    private double latitude;

    @Schema(description = "Longitude of the toll plaza", example = "75.7621")
    private double longitude;

    @Schema(description = "Driving distance from the trip source along the route in kilometers", example = "315.4")
    private double distanceFromSource;
}

