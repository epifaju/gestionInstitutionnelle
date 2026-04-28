package com.app.modules.rh.dto;

import java.util.List;

public record EcheanceDashboardResponse(
        long totalActives,
        long critiques,          // date dépassée
        long urgentes,           // J-7
        long attention,          // J-30
        long finCddProchaines30j,
        long periodeEssaiProchaines15j,
        long visitesAPrevoir,
        long titresExpirantBientot,
        long formationsARenouveler,
        List<EcheanceResponse> prochainesEcheances  // top 10 les plus urgentes
) {}

