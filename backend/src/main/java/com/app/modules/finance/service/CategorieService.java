package com.app.modules.finance.service;

import com.app.modules.finance.dto.CategorieRequest;
import com.app.modules.finance.dto.CategorieResponse;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.TypeCategorie;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategorieService {

    private final CategorieDepenseRepository categorieDepenseRepository;

    @Transactional(readOnly = true)
    public List<CategorieResponse> listActives(UUID orgId) {
        return categorieDepenseRepository.findByOrganisationIdAndActifTrueOrderByLibelleAsc(orgId).stream()
                .map(
                        c ->
                                new CategorieResponse(
                                        c.getId(),
                                        c.getLibelle(),
                                        c.getCode(),
                                        c.getType().name(),
                                        c.getCouleur(),
                                        c.isActif()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategorieResponse> list(UUID orgId, boolean includeInactive) {
        List<CategorieDepense> list =
                includeInactive
                        ? categorieDepenseRepository.findByOrganisationIdOrderByLibelleAsc(orgId)
                        : categorieDepenseRepository.findByOrganisationIdAndActifTrueOrderByLibelleAsc(orgId);
        return list.stream()
                .map(
                        c ->
                                new CategorieResponse(
                                        c.getId(),
                                        c.getLibelle(),
                                        c.getCode(),
                                        c.getType().name(),
                                        c.getCouleur(),
                                        c.isActif()))
                .toList();
    }

    @Transactional
    public CategorieResponse creer(CategorieRequest req, UUID orgId) {
        if (categorieDepenseRepository.existsByOrganisationIdAndCode(orgId, req.code().trim())) {
            throw BusinessException.badRequest("CATEGORIE_CODE_DUPLIQUE");
        }
        TypeCategorie type = TypeCategorie.valueOf(req.type().trim().toUpperCase());
        CategorieDepense c = new CategorieDepense();
        c.setOrganisationId(orgId);
        c.setLibelle(req.libelle().trim());
        c.setCode(req.code().trim());
        c.setType(type);
        c.setCouleur(req.couleur() != null && !req.couleur().isBlank() ? req.couleur().trim() : "#6B7280");
        c.setActif(true);
        c = categorieDepenseRepository.save(c);
        return new CategorieResponse(c.getId(), c.getLibelle(), c.getCode(), c.getType().name(), c.getCouleur(), c.isActif());
    }

    @Transactional
    public CategorieResponse modifier(UUID id, CategorieRequest req, UUID orgId) {
        CategorieDepense c =
                categorieDepenseRepository
                        .findByIdAndOrganisationId(id, orgId)
                        .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));

        String newCode = req.code().trim();
        if (!c.getCode().equalsIgnoreCase(newCode)
                && categorieDepenseRepository.existsByOrganisationIdAndCode(orgId, newCode)) {
            throw BusinessException.badRequest("CATEGORIE_CODE_DUPLIQUE");
        }

        TypeCategorie type = TypeCategorie.valueOf(req.type().trim().toUpperCase());
        c.setLibelle(req.libelle().trim());
        c.setCode(newCode);
        c.setType(type);
        c.setCouleur(req.couleur() != null && !req.couleur().isBlank() ? req.couleur().trim() : "#6B7280");
        c = categorieDepenseRepository.save(c);
        return new CategorieResponse(c.getId(), c.getLibelle(), c.getCode(), c.getType().name(), c.getCouleur(), c.isActif());
    }

    @Transactional
    public void supprimer(UUID id, UUID orgId) {
        CategorieDepense c =
                categorieDepenseRepository
                        .findByIdAndOrganisationId(id, orgId)
                        .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
        // Soft-delete conforme au schéma (actif boolean)
        c.setActif(false);
        categorieDepenseRepository.save(c);
    }

    @Transactional
    public void reactiver(UUID id, UUID orgId) {
        CategorieDepense c =
                categorieDepenseRepository
                        .findByIdAndOrganisationId(id, orgId)
                        .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
        c.setActif(true);
        categorieDepenseRepository.save(c);
    }
}
