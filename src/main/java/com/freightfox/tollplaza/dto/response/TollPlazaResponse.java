package com.freightfox.tollplaza.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing route summary and ordered list of toll plazas")
public class TollPlazaResponse {

    @Schema(description = "Route summary details")
    private RouteDto route;

    @Schema(description = "Ordered list of toll plazas encountered sequentially from source to destination")
    private List<TollPlazaDto> tollPlazas;
}

