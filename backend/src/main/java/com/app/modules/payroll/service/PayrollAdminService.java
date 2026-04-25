package com.app.modules.payroll.service;

import com.app.audit.AuditLogService;
import com.app.modules.payroll.dto.EmployeePayrollProfileRequest;
import com.app.modules.payroll.dto.EmployeePayrollProfileResponse;
import com.app.modules.payroll.dto.PayrollCotisationRequest;
import com.app.modules.payroll.dto.PayrollCotisationResponse;
import com.app.modules.payroll.dto.PayrollEmployerSettingsRequest;
import com.app.modules.payroll.dto.PayrollEmployerSettingsResponse;
import com.app.modules.payroll.dto.PayrollLegalConstantRequest;
import com.app.modules.payroll.dto.PayrollLegalConstantResponse;
import com.app.modules.payroll.dto.PayrollRubriqueRequest;
import com.app.modules.payroll.dto.PayrollRubriqueResponse;
import com.app.modules.payroll.entity.EmployeePayrollProfile;
import com.app.modules.payroll.entity.PayrollCotisation;
import com.app.modules.payroll.entity.PayrollEmployerSettings;
import com.app.modules.payroll.entity.PayrollLegalConstant;
import com.app.modules.payroll.entity.PayrollRubrique;
import com.app.modules.payroll.repository.EmployeePayrollProfileRepository;
import com.app.modules.payroll.repository.PayrollCotisationRepository;
import com.app.modules.payroll.repository.PayrollEmployerSettingsRepository;
import com.app.modules.payroll.repository.PayrollLegalConstantRepository;
import com.app.modules.payroll.repository.PayrollRubriqueRepository;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollAdminService {

    private final PayrollEmployerSettingsRepository employerSettingsRepository;
    private final PayrollLegalConstantRepository legalConstantRepository;
    private final PayrollRubriqueRepository rubriqueRepository;
    private final PayrollCotisationRepository cotisationRepository;
    private final EmployeePayrollProfileRepository profileRepository;
    private final SalarieRepository salarieRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public PayrollEmployerSettingsResponse upsertEmployerSettings(UUID orgId, UUID userId, PayrollEmployerSettingsRequest req) {
        PayrollEmployerSettings e = employerSettingsRepository.findById(orgId).orElseGet(() -> {
            PayrollEmployerSettings x = new PayrollEmployerSettings();
            x.setOrganisationId(orgId);
            x.setCreatedAt(Instant.now());
            return x;
        });
        e.setRaisonSociale(req.raisonSociale());
        e.setAdresseLigne1(req.adresseLigne1());
        e.setAdresseLigne2(req.adresseLigne2());
        e.setCodePostal(req.codePostal());
        e.setVille(req.ville());
        e.setPays(req.pays());
        e.setSiret(req.siret());
        e.setNaf(req.naf());
        e.setUrssaf(req.urssaf());
        e.setConventionCode(req.conventionCode());
        e.setConventionLibelle(req.conventionLibelle());
        e.setUpdatedAt(Instant.now());
        employerSettingsRepository.save(e);
        auditLogService.log(orgId, userId, "UPDATE", "PayrollEmployerSettings", orgId, null, req);
        return toEmployerResponse(e);
    }

    @Transactional(readOnly = true)
    public PayrollEmployerSettingsResponse getEmployerSettings(UUID orgId) {
        return employerSettingsRepository.findById(orgId).map(this::toEmployerResponse).orElse(null);
    }

    @Transactional
    public PayrollLegalConstantResponse createLegalConstant(UUID orgId, UUID userId, PayrollLegalConstantRequest req) {
        PayrollLegalConstant c = new PayrollLegalConstant();
        c.setOrganisationId(orgId);
        c.setCode(req.code());
        c.setLibelle(req.libelle());
        c.setValeur(req.valeur());
        c.setEffectiveFrom(req.effectiveFrom());
        c.setEffectiveTo(req.effectiveTo());
        legalConstantRepository.save(c);
        auditLogService.log(orgId, userId, "CREATE", "PayrollLegalConstant", c.getId(), null, req);
        return toLegalResponse(c);
    }

    @Transactional(readOnly = true)
    public List<PayrollLegalConstantResponse> listLegalConstants(UUID orgId) {
        return legalConstantRepository.findAll().stream()
                .filter(x -> orgId.equals(x.getOrganisationId()))
                .map(this::toLegalResponse)
                .toList();
    }

    @Transactional
    public void deleteLegalConstant(UUID id, UUID orgId, UUID userId) {
        PayrollLegalConstant c = legalConstantRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("CONSTANT_NOT_FOUND"));
        if (!orgId.equals(c.getOrganisationId())) throw BusinessException.forbidden("CONSTANT_ORG_MISMATCH");
        legalConstantRepository.delete(c);
        auditLogService.log(orgId, userId, "DELETE", "PayrollLegalConstant", id, null, null);
    }

    @Transactional
    public PayrollRubriqueResponse createRubrique(UUID orgId, UUID userId, PayrollRubriqueRequest req) {
        PayrollRubrique r = new PayrollRubrique();
        r.setOrganisationId(orgId);
        applyRubrique(r, req);
        rubriqueRepository.save(r);
        auditLogService.log(orgId, userId, "CREATE", "PayrollRubrique", r.getId(), null, req);
        return toRubriqueResponse(r);
    }

    @Transactional
    public PayrollRubriqueResponse updateRubrique(UUID id, UUID orgId, UUID userId, PayrollRubriqueRequest req) {
        PayrollRubrique r = rubriqueRepository.findById(id).orElseThrow(() -> BusinessException.notFound("RUBRIQUE_NOT_FOUND"));
        if (!orgId.equals(r.getOrganisationId())) throw BusinessException.forbidden("RUBRIQUE_ORG_MISMATCH");
        applyRubrique(r, req);
        rubriqueRepository.save(r);
        auditLogService.log(orgId, userId, "UPDATE", "PayrollRubrique", r.getId(), null, req);
        return toRubriqueResponse(r);
    }

    @Transactional(readOnly = true)
    public List<PayrollRubriqueResponse> listRubriques(UUID orgId) {
        return rubriqueRepository.findAll().stream()
                .filter(x -> orgId.equals(x.getOrganisationId()))
                .map(this::toRubriqueResponse)
                .toList();
    }

    @Transactional
    public void deleteRubrique(UUID id, UUID orgId, UUID userId) {
        PayrollRubrique r = rubriqueRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("RUBRIQUE_NOT_FOUND"));
        if (!orgId.equals(r.getOrganisationId())) throw BusinessException.forbidden("RUBRIQUE_ORG_MISMATCH");
        rubriqueRepository.delete(r);
        auditLogService.log(orgId, userId, "DELETE", "PayrollRubrique", id, null, null);
    }

    @Transactional
    public PayrollCotisationResponse createCotisation(UUID orgId, UUID userId, PayrollCotisationRequest req) {
        PayrollCotisation c = new PayrollCotisation();
        c.setOrganisationId(orgId);
        applyCotisation(c, req);
        cotisationRepository.save(c);
        auditLogService.log(orgId, userId, "CREATE", "PayrollCotisation", c.getId(), null, req);
        return toCotisationResponse(c);
    }

    @Transactional
    public PayrollCotisationResponse updateCotisation(UUID id, UUID orgId, UUID userId, PayrollCotisationRequest req) {
        PayrollCotisation c = cotisationRepository.findById(id).orElseThrow(() -> BusinessException.notFound("COTISATION_NOT_FOUND"));
        if (!orgId.equals(c.getOrganisationId())) throw BusinessException.forbidden("COTISATION_ORG_MISMATCH");
        applyCotisation(c, req);
        cotisationRepository.save(c);
        auditLogService.log(orgId, userId, "UPDATE", "PayrollCotisation", c.getId(), null, req);
        return toCotisationResponse(c);
    }

    @Transactional(readOnly = true)
    public List<PayrollCotisationResponse> listCotisations(UUID orgId) {
        return cotisationRepository.findAll().stream()
                .filter(x -> orgId.equals(x.getOrganisationId()))
                .map(this::toCotisationResponse)
                .toList();
    }

    @Transactional
    public void deleteCotisation(UUID id, UUID orgId, UUID userId) {
        PayrollCotisation c = cotisationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("COTISATION_NOT_FOUND"));
        if (!orgId.equals(c.getOrganisationId())) throw BusinessException.forbidden("COTISATION_ORG_MISMATCH");
        cotisationRepository.delete(c);
        auditLogService.log(orgId, userId, "DELETE", "PayrollCotisation", id, null, null);
    }

    @Transactional
    public EmployeePayrollProfileResponse upsertEmployeeProfile(UUID orgId, UUID userId, EmployeePayrollProfileRequest req) {
        Salarie s = salarieRepository.findById(req.salarieId()).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!orgId.equals(s.getOrganisationId())) throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        EmployeePayrollProfile p = profileRepository.findByOrganisationIdAndSalarie_Id(orgId, s.getId())
                .orElseGet(() -> {
                    EmployeePayrollProfile x = new EmployeePayrollProfile();
                    x.setOrganisationId(orgId);
                    x.setSalarie(s);
                    return x;
                });
        p.setCadre(req.cadre());
        p.setConventionCode(req.conventionCode());
        p.setConventionLibelle(req.conventionLibelle());
        p.setTauxPas(req.tauxPas());
        profileRepository.save(p);
        auditLogService.log(orgId, userId, "UPDATE", "EmployeePayrollProfile", p.getId(), null, req);
        return toProfileResponse(p);
    }

    @Transactional(readOnly = true)
    public List<EmployeePayrollProfileResponse> listEmployeeProfiles(UUID orgId) {
        return profileRepository.findAll().stream()
                .filter(x -> orgId.equals(x.getOrganisationId()))
                .map(this::toProfileResponse)
                .toList();
    }

    @Transactional
    public void deleteEmployeeProfile(UUID id, UUID orgId, UUID userId) {
        EmployeePayrollProfile p = profileRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("PROFILE_NOT_FOUND"));
        if (!orgId.equals(p.getOrganisationId())) throw BusinessException.forbidden("PROFILE_ORG_MISMATCH");
        profileRepository.delete(p);
        auditLogService.log(orgId, userId, "DELETE", "EmployeePayrollProfile", id, null, null);
    }

    private PayrollEmployerSettingsResponse toEmployerResponse(PayrollEmployerSettings e) {
        return new PayrollEmployerSettingsResponse(
                e.getOrganisationId(),
                e.getRaisonSociale(),
                e.getAdresseLigne1(),
                e.getAdresseLigne2(),
                e.getCodePostal(),
                e.getVille(),
                e.getPays(),
                e.getSiret(),
                e.getNaf(),
                e.getUrssaf(),
                e.getConventionCode(),
                e.getConventionLibelle()
        );
    }

    private PayrollLegalConstantResponse toLegalResponse(PayrollLegalConstant c) {
        return new PayrollLegalConstantResponse(c.getId(), c.getCode(), c.getLibelle(), c.getValeur(), c.getEffectiveFrom(), c.getEffectiveTo());
    }

    private void applyRubrique(PayrollRubrique r, PayrollRubriqueRequest req) {
        r.setCode(req.code());
        r.setLibelle(req.libelle());
        r.setType(req.type());
        r.setModeCalcul(req.modeCalcul());
        r.setBaseCode(req.baseCode());
        r.setTauxSalarial(req.tauxSalarial());
        r.setTauxPatronal(req.tauxPatronal());
        r.setMontantFixe(req.montantFixe());
        r.setOrdreAffichage(req.ordreAffichage() != null ? req.ordreAffichage() : 100);
        r.setActif(req.actif());
        r.setEffectiveFrom(req.effectiveFrom());
        r.setEffectiveTo(req.effectiveTo());
    }

    private PayrollRubriqueResponse toRubriqueResponse(PayrollRubrique r) {
        return new PayrollRubriqueResponse(r.getId(), r.getCode(), r.getLibelle(), r.getType(), r.getModeCalcul(), r.getBaseCode(),
                r.getTauxSalarial(), r.getTauxPatronal(), r.getMontantFixe(), r.getOrdreAffichage(), r.isActif(), r.getEffectiveFrom(), r.getEffectiveTo());
    }

    private void applyCotisation(PayrollCotisation c, PayrollCotisationRequest req) {
        c.setCode(req.code());
        c.setLibelle(req.libelle());
        c.setOrganisme(req.organisme());
        c.setAssietteBaseCode(req.assietteBaseCode());
        c.setTauxSalarial(req.tauxSalarial());
        c.setTauxPatronal(req.tauxPatronal());
        c.setPlafondCode(req.plafondCode());
        c.setAppliesCadreOnly(req.appliesCadreOnly());
        c.setAppliesNonCadreOnly(req.appliesNonCadreOnly());
        c.setOrdreAffichage(req.ordreAffichage() != null ? req.ordreAffichage() : 100);
        c.setActif(req.actif());
        c.setEffectiveFrom(req.effectiveFrom());
        c.setEffectiveTo(req.effectiveTo());
    }

    private PayrollCotisationResponse toCotisationResponse(PayrollCotisation c) {
        return new PayrollCotisationResponse(c.getId(), c.getCode(), c.getLibelle(), c.getOrganisme(), c.getAssietteBaseCode(),
                c.getTauxSalarial(), c.getTauxPatronal(), c.getPlafondCode(), c.isAppliesCadreOnly(), c.isAppliesNonCadreOnly(),
                c.getOrdreAffichage(), c.isActif(), c.getEffectiveFrom(), c.getEffectiveTo());
    }

    private EmployeePayrollProfileResponse toProfileResponse(EmployeePayrollProfile p) {
        Salarie s = p.getSalarie();
        String nom = s != null ? ((s.getNom() + " " + s.getPrenom()).trim()) : null;
        return new EmployeePayrollProfileResponse(p.getId(), s != null ? s.getId() : null, nom, p.isCadre(), p.getConventionCode(), p.getConventionLibelle(), p.getTauxPas());
    }
}

