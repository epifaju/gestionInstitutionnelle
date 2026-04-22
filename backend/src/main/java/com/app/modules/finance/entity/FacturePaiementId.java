package com.app.modules.finance.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
public class FacturePaiementId implements Serializable {
    private UUID factureId;
    private UUID paiementId;
}
