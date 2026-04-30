package com.app.modules.rapports.dto.todo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TodoDashboardResponse(
        LocalDateTime generatedAt,
        String roleUtilisateur,
        long totalActionsGlobal,
        long totalActionsUrgentes,
        List<TodoSection> sections,
        Map<String, Long> comptages
) {}

