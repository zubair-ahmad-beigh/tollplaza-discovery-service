package com.freightfox.tollplaza.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload containing source and destination Indian pincodes")
public class TollPlazaRequest {

    @NotBlank(message = "sourcePincode is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "sourcePincode must be a valid 6-digit Indian pincode")
    @Schema(description = "Source 6-digit Indian pincode (e.g., Bengaluru)", example = "560064", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourcePincode;

    @NotBlank(message = "destinationPincode is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "destinationPincode must be a valid 6-digit Indian pincode")
    @Schema(description = "Destination 6-digit Indian pincode (e.g., Pune)", example = "411045", requiredMode = Schema.RequiredMode.REQUIRED)
    private String destinationPincode;
}

