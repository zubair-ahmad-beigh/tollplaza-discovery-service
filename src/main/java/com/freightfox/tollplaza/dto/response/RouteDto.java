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
@Schema(description = "Calculated route metadata")
public class RouteDto {

    @Schema(description = "Source Indian pincode", example = "560064")
    private String sourcePincode;

    @Schema(description = "Destination Indian pincode", example = "411045")
    private String destinationPincode;

    @Schema(description = "Total driving highway distance in kilometers", example = "850.5")
    private double distanceInKm;
}

