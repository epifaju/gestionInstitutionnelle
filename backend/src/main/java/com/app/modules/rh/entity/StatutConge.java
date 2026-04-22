package com.app.modules.rh.entity;

public enum StatutConge {
    BROUILLON,
    EN_ATTENTE,
    VALIDE,
    REJETE,
    /** Congé validé puis annulé (droits restaurés si congé payé / solde). */
    ANNULE
}
