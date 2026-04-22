package com.app.modules.finance.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class TauxChangeId implements Serializable {
    public UUID organisationId;
    public LocalDate date;
    public String devise;

    public TauxChangeId() {}

    public TauxChangeId(UUID organisationId, LocalDate date, String devise) {
        this.organisationId = organisationId;
        this.date = date;
        this.devise = devise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TauxChangeId that)) return false;
        return Objects.equals(organisationId, that.organisationId)
                && Objects.equals(date, that.date)
                && Objects.equals(devise, that.devise);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organisationId, date, devise);
    }
}

