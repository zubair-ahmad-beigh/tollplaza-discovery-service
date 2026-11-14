package com.freightfox.tollplaza.service;

import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;

/**
 * Service contract for route planning and toll plaza identification.
 */
public interface TollPlazaService {

    /**
     * Finds and sequences all highway toll plazas along the driving route between two Indian pincodes.
     *
     * @param request TollPlazaRequest containing source and destination pincodes
     * @return TollPlazaResponse with route summary and ordered toll plazas
     */
    TollPlazaResponse findTollPlazasBetweenPincodes(TollPlazaRequest request);
}

