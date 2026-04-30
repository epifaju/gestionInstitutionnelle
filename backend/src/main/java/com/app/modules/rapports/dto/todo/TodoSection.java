package com.app.modules.rapports.dto.todo;

import java.util.List;

public record TodoSection(
        String roleLabel,
        String icone,
        long totalActions,
        long actionsUrgentes,
        List<TodoActionGroup> groupes
) {}

