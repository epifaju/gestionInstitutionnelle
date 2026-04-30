package com.app.modules.rapports.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "config_exports")
@AttributeOverride(name = "organisationId", column = @Column(name = "organisation_id", nullable = false, unique = true))
@Getter
@Setter
public class ConfigExport extends BaseEntity {

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "pied_page_mention", length = 300)
    private String piedPageMention;

    @Column(name = "couleur_principale", length = 7)
    private String couleurPrincipale;

    @Column(name = "seuil_lignes_sync_pdf")
    private Integer seuilLignesSyncPdf;

    @Column(name = "seuil_lignes_sync_excel")
    private Integer seuilLignesSyncExcel;

    @Column(name = "watermark_actif")
    private Boolean watermarkActif;

    @Column(name = "watermark_texte", length = 100)
    private String watermarkTexte;

    @Column(name = "signature_dg_url", length = 500)
    private String signatureDgUrl;

    @Column(name = "cachet_org_url", length = 500)
    private String cachetOrgUrl;
}

