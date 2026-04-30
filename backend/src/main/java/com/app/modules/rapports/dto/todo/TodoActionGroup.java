package com.app.modules.rapports.dto.todo;

import java.util.List;

public record TodoActionGroup(
        String categorie,
        String label,
        String icone,
        long count,
        long countUrgent,
        String lienVoirTout,
        List<TodoActionItem> items
) {}

