package com.app.modules.payroll.service;

import com.app.modules.payroll.entity.BulletinPaie;
import com.app.modules.rh.entity.Salarie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayslipPdfServiceTest {

    @Test
    void renderPdf_returnsPdfBytes() {
        PayslipPdfService svc = new PayslipPdfService();

        Salarie s = new Salarie();
        s.setNom("Doe");
        s.setPrenom("Jane");
        s.setMatricule("M-001");

        BulletinPaie b = new BulletinPaie();
        b.setSalarie(s);
        b.setAnnee(2026);
        b.setMois(4);
        b.setDevise("EUR");

        byte[] bytes = svc.renderPdf(null, b, List.of());
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);
        // PDF header typically starts with "%PDF"
        String header = new String(bytes, 0, Math.min(bytes.length, 4));
        assertThat(header).startsWith("%PDF");
    }
}

