package com.app.modules.payroll.service;

import com.app.audit.AuditLogService;
import com.app.modules.payroll.entity.BulletinLigne;
import com.app.modules.payroll.entity.BulletinPaie;
import com.app.modules.payroll.entity.EmployeePayrollProfile;
import com.app.modules.payroll.entity.PayrollEmployerSettings;
import com.app.modules.payroll.entity.PayrollLegalConstant;
import com.app.modules.payroll.repository.BulletinLigneRepository;
import com.app.modules.payroll.repository.BulletinPaieRepository;
import com.app.modules.payroll.repository.EmployeePayrollProfileRepository;
import com.app.modules.payroll.repository.PayrollCotisationRepository;
import com.app.modules.payroll.repository.PayrollEmployerSettingsRepository;
import com.app.modules.payroll.repository.PayrollLegalConstantRepository;
import com.app.modules.payroll.repository.PayrollRubriqueRepository;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.PaiementSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BulletinPaieService {

    private final BulletinPaieRepository bulletinPaieRepository;
    private final BulletinLigneRepository bulletinLigneRepository;
    private final PayrollEmployerSettingsRepository employerSettingsRepository;
    private final EmployeePayrollProfileRepository employeePayrollProfileRepository;
    private final PayrollLegalConstantRepository payrollLegalConstantRepository;
    private final PayrollRubriqueRepository payrollRubriqueRepository;
    private final PayrollCotisationRepository payrollCotisationRepository;
    private final HistoriqueSalaireRepository historiqueSalaireRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;

    private final PayslipPdfService pdfService = new PayslipPdfService();

    @Transactional
    public BulletinPaie generateForPaiementSalaire(UUID paiementSalaireId, UUID orgId, UUID userId) throws Exception {
        PaiementSalaire p = paiementSalaireRepository.findById(paiementSalaireId)
                .orElseThrow(() -> BusinessException.notFound("PAIEMENT_ABSENT"));
        if (!orgId.equals(p.getOrganisationId())) throw BusinessException.forbidden("PAIEMENT_ORG_MISMATCH");
        if (p.getBulletinId() != null) throw BusinessException.badRequest("BULLETIN_DEJA_GENERE");
        if (p.getDatePaiement() == null) throw BusinessException.badRequest("PAIEMENT_SANS_DATE");

        Salarie salarie = p.getSalarie();
        int annee = p.getAnnee();
        int mois = p.getMois();
        LocalDate datePaiement = p.getDatePaiement();

        // Avoid duplicates by unique constraint
        bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(orgId, salarie.getId(), annee, mois)
                .ifPresent(existing -> { throw BusinessException.badRequest("BULLETIN_DEJA_GENERE"); });

        EmployeePayrollProfile profile = employeePayrollProfileRepository
                .findByOrganisationIdAndSalarie_Id(orgId, salarie.getId())
                .orElse(null);

        HistoriqueSalaire grille = grilleActiveAu(salarie.getId(), datePaiement);
        if (grille == null) throw BusinessException.badRequest("GRILLE_SALAIRE_ABSENTE");

        var rubriques = payrollRubriqueRepository.listEffective(orgId, datePaiement);
        var cotisations = payrollCotisationRepository.listEffective(orgId, datePaiement);

        Map<String, PayrollLegalConstant> constants = loadConstants(orgId, datePaiement, cotisations);

        PayrollEngine.Result computed = PayrollEngine.compute(
                datePaiement,
                grille.getMontantBrut(),
                p.getMontant(),
                p.getDevise(),
                profile,
                rubriques,
                cotisations,
                constants
        );

        BulletinPaie b = new BulletinPaie();
        b.setOrganisationId(orgId);
        b.setSalarie(salarie);
        b.setAnnee(annee);
        b.setMois(mois);
        b.setDatePaiement(datePaiement);
        b.setDevise(p.getDevise());
        b.setCadre(profile != null && profile.isCadre());
        b.setConventionCode(profile != null ? profile.getConventionCode() : null);
        b.setConventionLibelle(profile != null ? profile.getConventionLibelle() : null);
        b.setBrut(computed.brut());
        b.setTotalCotSal(computed.totalCotSal());
        b.setTotalCotPat(computed.totalCotPat());
        b.setNetImposable(computed.netImposable());
        b.setPasTaux(computed.pasTaux());
        b.setPasMontant(computed.pasMontant());
        b.setNetAPayer(computed.netAPayer());
        bulletinPaieRepository.save(b);

        for (PayrollEngine.Line l : computed.lines()) {
            BulletinLigne bl = new BulletinLigne();
            bl.setBulletin(b);
            bl.setSection(l.section());
            bl.setCode(l.code());
            bl.setLibelle(l.libelle());
            bl.setBase(l.base());
            bl.setTauxSalarial(l.tauxSal());
            bl.setMontantSalarial(l.montantSal());
            bl.setTauxPatronal(l.tauxPat());
            bl.setMontantPatronal(l.montantPat());
            bl.setOrdreAffichage(l.ordre());
            bulletinLigneRepository.save(bl);
        }

        PayrollEmployerSettings employer = employerSettingsRepository.findById(orgId).orElse(null);
        List<BulletinLigne> lignes = bulletinLigneRepository.findByBulletin_IdOrderByOrdreAffichageAsc(b.getId());
        byte[] pdf = pdfService.renderPdf(employer, b, lignes);

        String objectName = objectNameFor(orgId, salarie.getId(), annee, mois, b.getId());
        minioStorageService.upload(objectName, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");

        b.setPdfObjectName(objectName);
        b.setPdfGeneratedAt(Instant.now());
        bulletinPaieRepository.save(b);

        p.setBulletinId(b.getId());
        paiementSalaireRepository.save(p);

        auditLogService.logRh(orgId, userId, "CREATE", "BulletinPaie", b.getId(), null, Map.of(
                "paiementSalaireId", paiementSalaireId,
                "annee", annee,
                "mois", mois,
                "pdfObjectName", objectName
        ));

        return b;
    }

    @Transactional(readOnly = true)
    public MinioStorageService.Download downloadMyBulletinForSalarie(UUID orgId, UUID salarieId, int annee, int mois) throws Exception {
        BulletinPaie b = bulletinPaieRepository
                .findByOrganisationIdAndSalarie_IdAndAnneeAndMois(orgId, salarieId, annee, mois)
                .orElseThrow(() -> BusinessException.notFound("BULLETIN_NOT_FOUND"));
        if (b.getPdfObjectName() == null || b.getPdfObjectName().isBlank()) {
            throw BusinessException.notFound("BULLETIN_PDF_NOT_FOUND");
        }
        return minioStorageService.download(b.getPdfObjectName());
    }

    @Transactional(readOnly = true)
    public String presignedUrlForSalarie(UUID orgId, UUID salarieId, int annee, int mois) throws Exception {
        BulletinPaie b = bulletinPaieRepository
                .findByOrganisationIdAndSalarie_IdAndAnneeAndMois(orgId, salarieId, annee, mois)
                .orElseThrow(() -> BusinessException.notFound("BULLETIN_NOT_FOUND"));
        if (b.getPdfObjectName() == null || b.getPdfObjectName().isBlank()) {
            throw BusinessException.notFound("BULLETIN_PDF_NOT_FOUND");
        }
        return minioStorageService.presignedGetUrl(b.getPdfObjectName());
    }

    private HistoriqueSalaire grilleActiveAu(UUID salarieId, LocalDate date) {
        List<HistoriqueSalaire> hist = historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(salarieId);
        if (hist == null || hist.isEmpty()) return null;
        for (HistoriqueSalaire h : hist) {
            if (h.getDateDebut() == null) continue;
            if (h.getDateDebut().isAfter(date)) continue;
            LocalDate fin = h.getDateFin();
            if (fin == null || !fin.isBefore(date)) return h;
        }
        return null;
    }

    private static String objectNameFor(UUID orgId, UUID salarieId, int annee, int mois, UUID bulletinId) {
        return "payslips/" + orgId + "/" + salarieId + "/" + annee + "-" + (mois < 10 ? "0" + mois : mois) + "-" + bulletinId + ".pdf";
    }

    private Map<String, PayrollLegalConstant> loadConstants(UUID orgId, LocalDate date, List<com.app.modules.payroll.entity.PayrollCotisation> cotisations) {
        Set<String> codes = new HashSet<>();
        // Always useful for BASE_BRUT_PLAFONNE
        codes.add("PMSS");
        for (var c : cotisations) {
            if (c.getPlafondCode() != null && !c.getPlafondCode().isBlank()) {
                codes.add(c.getPlafondCode().trim());
            }
        }
        Map<String, PayrollLegalConstant> out = new HashMap<>();
        for (String code : codes) {
            payrollLegalConstantRepository.findEffective(orgId, code, date).ifPresent(v -> out.put(code, v));
        }
        return out;
    }
}

