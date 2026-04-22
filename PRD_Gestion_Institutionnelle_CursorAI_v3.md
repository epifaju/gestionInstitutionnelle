# PRODUCT REQUIREMENTS DOCUMENT — v3.0
## Application de Gestion Institutionnelle
### Ambassade · Consulat · Association

---

| Champ | Valeur |
|---|---|
| **Version** | 3.0 — Cursor AI Ready (enrichi) |
| **Statut** | ✅ PRÊT POUR DÉVELOPPEMENT |
| **Stack cible** | Next.js 14 · Spring Boot 3 · PostgreSQL 16 · Docker |
| **Outil IA** | Cursor AI (Claude Sonnet) |
| **Date** | Mars 2026 |

---

## Table des matières

1. [Contexte & Vision Produit](#1-contexte--vision-produit)
2. [Architecture Technique](#2-architecture-technique)
3. [Schéma SQL Complet](#3-schéma-sql-complet)
4. [DTOs — Structures de données exactes](#4-dtos--structures-de-données-exactes)
5. [Spécification API REST avec réponses JSON](#5-spécification-api-rest-avec-réponses-json)
6. [Cas d'erreur & Codes HTTP](#6-cas-derreur--codes-http)
7. [Configuration Spring Security & JWT](#7-configuration-spring-security--jwt)
8. [Règles Métier Détaillées](#8-règles-métier-détaillées)
9. [Matrice RBAC](#9-matrice-rbac)
10. [Wireframes Textuels](#10-wireframes-textuels)
11. [Roadmap MVP](#11-roadmap-mvp)
12. [Déploiement Docker Compose](#12-déploiement-docker-compose)
13. [Prompts Cursor AI — Démarrage Rapide](#13-prompts-cursor-ai--démarrage-rapide)
14. [Glossaire](#14-glossaire)

---

## 1. Contexte & Vision Produit

ERP léger **multi-tenant** pour institutions diplomatiques et associations. Centralise finances, personnel, inventaire et reporting dans une interface sécurisée accessible au personnel non technique.

### 1.1 Problèmes à résoudre

- Dépenses et factures éparpillées dans des fichiers Excel non partagés
- Gestion des congés/absences par email sans traçabilité ni compteurs
- Inventaire physique non numérisé, sans historique de mouvements
- Absence de bilan financier mensuel consolidé en temps réel
- Suivi budgétaire impossible : aucun comparatif prévu / réalisé

### 1.2 Valeur par persona

| Persona | Valeur principale |
|---|---|
| **Directeur / Directrice** | Tableau de bord global, alertes budget, rapports en 1 clic |
| **Comptable / Resp. Financier** | Saisie rapide factures/recettes, export CSV/Excel, bilan automatisé |
| **Responsable RH** | Workflow congés digitalisé, compteurs automatiques, fiches salariés |
| **Responsable Logistique** | Inventaire centralisé, historique mouvements, valeur totale du parc |
| **Employé** | Demande de congé en ligne, solde visible, accès à sa fiche de paie |

---

## 2. Architecture Technique

> 📌 **CURSOR AI :** Utilise cette section pour générer la structure de dossiers, les fichiers de config Docker et le code boilerplate initial.

### 2.1 Stack technique

| Couche | Technologie | Justification |
|---|---|---|
| **Frontend** | Next.js 14 (App Router) + TypeScript | SSR natif, routing file-based, excellent DX Cursor |
| **UI / Design** | Tailwind CSS + shadcn/ui + Recharts | Composants accessibles, graphiques légers |
| **State** | Zustand + React Query (TanStack) | Cache serveur, mutations optimistes, état global |
| **Formulaires** | React Hook Form + Zod | Validation type-safe côté client |
| **Backend** | Spring Boot 3.x (Java 21) | Robuste, sécurité enterprise, JPA natif |
| **Auth** | Spring Security + JWT (RS256) | Stateless, RBAC, refresh tokens HttpOnly |
| **Base de données** | PostgreSQL 16 | ACID, JSON support, full-text search |
| **ORM / Migrations** | Spring Data JPA + Flyway | Migrations versionnées V1__, V2__... |
| **Fichiers** | MinIO (S3-compatible, self-hosted) | PDFs, justificatifs, contrats (10 Mo max) |
| **Export** | iText 7 (PDF) + Apache POI (Excel) | Bilans, rapports, bulletins de paie |
| **Infra** | Docker + Docker Compose | Déploiement 1 commande, multi-environnement |
| **i18n** | next-intl (FR/EN) + Spring MessageSource | Interface bilingue complète dès le départ |

### 2.2 Structure Frontend

```
frontend/
├── app/
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   └── reset-password/page.tsx
│   └── (dashboard)/
│       ├── layout.tsx              # Sidebar + auth guard
│       ├── page.tsx                # Dashboard principal
│       ├── finance/
│       │   ├── factures/page.tsx
│       │   ├── factures/[id]/page.tsx
│       │   ├── paiements/page.tsx
│       │   └── recettes/page.tsx
│       ├── rh/
│       │   ├── salaries/page.tsx
│       │   ├── salaries/[id]/page.tsx
│       │   ├── conges/page.tsx
│       │   └── paie/page.tsx
│       ├── budget/page.tsx
│       ├── inventaire/page.tsx
│       └── rapports/page.tsx
├── components/
│   ├── ui/                         # shadcn/ui + overrides
│   ├── forms/
│   │   ├── FactureForm.tsx
│   │   ├── SalarieForm.tsx
│   │   ├── CongeForm.tsx
│   │   └── BienForm.tsx
│   ├── tables/
│   │   └── DataTable.tsx           # Générique TanStack Table
│   └── charts/
│       ├── BarChartFinance.tsx
│       └── BudgetProgress.tsx
├── lib/
│   ├── api.ts                      # Axios instance + intercepteurs JWT
│   ├── store.ts                    # Zustand (user, org, theme)
│   └── utils.ts
├── services/
│   ├── auth.service.ts
│   ├── facture.service.ts
│   ├── salarie.service.ts
│   ├── conge.service.ts
│   ├── budget.service.ts
│   └── inventaire.service.ts
└── messages/
    ├── fr.json
    └── en.json
```

### 2.3 Structure Backend

```
backend/src/main/java/com/app/
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── CorsConfig.java
│   └── MinioConfig.java
├── modules/
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── JwtService.java
│   │   ├── AuthService.java
│   │   └── dto/LoginRequest.java, LoginResponse.java
│   ├── rh/
│   │   ├── entity/Salarie.java, CongeAbsence.java, DroitsConges.java
│   │   ├── repository/SalarieRepository.java, CongeRepository.java
│   │   ├── service/SalarieService.java, CongeService.java, PaieService.java
│   │   ├── controller/RhController.java
│   │   └── dto/  (voir section 4)
│   ├── finance/
│   │   ├── entity/Facture.java, Paiement.java, Recette.java
│   │   ├── service/FactureService.java, PaiementService.java, StatsService.java
│   │   ├── controller/FinanceController.java
│   │   └── dto/  (voir section 4)
│   ├── budget/
│   │   ├── entity/BudgetAnnuel.java, LigneBudget.java
│   │   ├── service/BudgetService.java
│   │   └── controller/BudgetController.java
│   ├── inventaire/
│   │   ├── entity/BienMateriel.java, MouvementBien.java
│   │   ├── service/InventaireService.java
│   │   └── controller/InventaireController.java
│   └── rapports/
│       ├── service/ReportService.java, ExportPdfService.java, ExportExcelService.java
│       └── controller/RapportController.java
├── shared/
│   ├── entity/BaseEntity.java
│   ├── dto/ApiResponse.java, PageResponse.java, ErrorResponse.java
│   └── exception/GlobalExceptionHandler.java, BusinessException.java
└── audit/
    ├── AuditLog.java
    └── AuditListener.java
```

---

## 3. Schéma SQL Complet

> 📌 **CURSOR AI :** Colle ce SQL dans `V1__init.sql` (Flyway). Exécuté automatiquement au démarrage.

```sql
-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- full-text search

-- ============================================================
-- ENUMS
-- ============================================================
CREATE TYPE role_enum         AS ENUM ('ADMIN','FINANCIER','RH','LOGISTIQUE','EMPLOYE');
CREATE TYPE statut_salarie    AS ENUM ('ACTIF','EN_CONGE','SORTI');
CREATE TYPE statut_conge      AS ENUM ('BROUILLON','EN_ATTENTE','VALIDE','REJETE');
CREATE TYPE type_conge        AS ENUM ('ANNUEL','MALADIE','EXCEPTIONNEL','SANS_SOLDE');
CREATE TYPE statut_paie       AS ENUM ('EN_ATTENTE','PAYE','ANNULE');
CREATE TYPE statut_facture    AS ENUM ('BROUILLON','A_PAYER','PAYE','ANNULE');
CREATE TYPE type_recette      AS ENUM ('FRAIS_SERVICE','ADHESION','DON','SUBVENTION','PRESTATION');
CREATE TYPE statut_budget     AS ENUM ('BROUILLON','VALIDE','CLOTURE');
CREATE TYPE etat_bien         AS ENUM ('BON','USE','DEFAILLANT','HORS_SERVICE');
CREATE TYPE type_mouvement    AS ENUM ('CREATION','AFFECTATION','DEPLACEMENT','ETAT','REFORME');
CREATE TYPE type_categorie    AS ENUM ('DEPENSE','RECETTE');

-- ============================================================
-- ORGANISATIONS (multi-tenant root)
-- ============================================================
CREATE TABLE organisations (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom               VARCHAR(200) NOT NULL,
    type              VARCHAR(50)  NOT NULL,  -- 'AMBASSADE','CONSULAT','ASSOCIATION'
    pays              VARCHAR(100),
    devise_defaut     VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    logo_url          VARCHAR(500),
    seuil_justificatif NUMERIC(12,2) NOT NULL DEFAULT 500.00,
    alerte_budget_pct  INTEGER     NOT NULL DEFAULT 80,
    actif             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- UTILISATEURS
-- ============================================================
CREATE TABLE utilisateurs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            role_enum   NOT NULL DEFAULT 'EMPLOYE',
    nom             VARCHAR(100),
    prenom          VARCHAR(100),
    actif           BOOLEAN     NOT NULL DEFAULT TRUE,
    dernier_login   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, email)
);

-- ============================================================
-- SALARIÉS
-- ============================================================
CREATE TABLE salaries (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         NOT NULL REFERENCES organisations(id),
    utilisateur_id  UUID         REFERENCES utilisateurs(id),
    matricule       VARCHAR(50)  NOT NULL,
    nom             VARCHAR(100) NOT NULL,
    prenom          VARCHAR(100) NOT NULL,
    email           VARCHAR(255),
    telephone       VARCHAR(30),
    poste           VARCHAR(150) NOT NULL,
    service         VARCHAR(150) NOT NULL,
    date_embauche   DATE         NOT NULL,
    type_contrat    VARCHAR(50)  NOT NULL,  -- 'CDI','CDD','STAGE','CONSULTANT'
    statut          statut_salarie NOT NULL DEFAULT 'ACTIF',
    nationalite     VARCHAR(100),
    adresse         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, matricule)
);

CREATE TABLE historique_salaires (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    salarie_id   UUID         NOT NULL REFERENCES salaries(id),
    montant_brut NUMERIC(12,2) NOT NULL,
    montant_net  NUMERIC(12,2) NOT NULL,
    devise       VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    date_debut   DATE         NOT NULL,
    date_fin     DATE,  -- NULL = grille actuelle
    notes        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE paiements_salaires (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    salarie_id      UUID        NOT NULL REFERENCES salaries(id),
    mois            INTEGER     NOT NULL CHECK (mois BETWEEN 1 AND 12),
    annee           INTEGER     NOT NULL,
    montant         NUMERIC(12,2) NOT NULL,
    devise          VARCHAR(3)  NOT NULL DEFAULT 'EUR',
    date_paiement   DATE,
    mode_paiement   VARCHAR(50),  -- 'VIREMENT','CHEQUE','ESPECES'
    statut          statut_paie NOT NULL DEFAULT 'EN_ATTENTE',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(salarie_id, mois, annee)
);

-- ============================================================
-- CONGÉS & ABSENCES
-- ============================================================
CREATE TABLE conges_absences (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID           NOT NULL REFERENCES organisations(id),
    salarie_id      UUID           NOT NULL REFERENCES salaries(id),
    type_conge      type_conge     NOT NULL,
    date_debut      DATE           NOT NULL,
    date_fin        DATE           NOT NULL,
    nb_jours        NUMERIC(5,1)   NOT NULL,
    statut          statut_conge   NOT NULL DEFAULT 'BROUILLON',
    valideur_id     UUID           REFERENCES utilisateurs(id),
    date_validation TIMESTAMPTZ,
    motif_rejet     TEXT,
    commentaire     TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CHECK (date_fin >= date_debut)
);

CREATE TABLE droits_conges (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID          NOT NULL REFERENCES organisations(id),
    salarie_id      UUID          NOT NULL REFERENCES salaries(id),
    annee           INTEGER       NOT NULL,
    jours_droit     NUMERIC(5,1)  NOT NULL DEFAULT 30,
    jours_pris      NUMERIC(5,1)  NOT NULL DEFAULT 0,
    jours_restants  NUMERIC(5,1)  NOT NULL DEFAULT 30,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(salarie_id, annee)
);

-- ============================================================
-- FINANCE — Catégories
-- ============================================================
CREATE TABLE categories_depenses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID           NOT NULL REFERENCES organisations(id),
    libelle         VARCHAR(150)   NOT NULL,
    code            VARCHAR(20)    NOT NULL,
    type            type_categorie NOT NULL,
    couleur         VARCHAR(7)     DEFAULT '#6B7280',  -- hex
    actif           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code)
);

-- ============================================================
-- FINANCE — Factures
-- ============================================================
CREATE TABLE factures (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID           NOT NULL REFERENCES organisations(id),
    reference        VARCHAR(30)    NOT NULL,
    fournisseur      VARCHAR(200)   NOT NULL,
    date_facture     DATE           NOT NULL,
    montant_ht       NUMERIC(12,2)  NOT NULL,
    tva              NUMERIC(5,2)   NOT NULL DEFAULT 0,
    montant_ttc      NUMERIC(12,2)  NOT NULL,
    devise           VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    taux_change_eur  NUMERIC(10,6)  NOT NULL DEFAULT 1,
    categorie_id     UUID           REFERENCES categories_depenses(id),
    statut           statut_facture NOT NULL DEFAULT 'BROUILLON',
    justificatif_url VARCHAR(500),
    notes            TEXT,
    created_by       UUID           REFERENCES utilisateurs(id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, reference)
);

-- Séquence de numérotation par org+année
CREATE TABLE facture_sequences (
    organisation_id UUID    NOT NULL REFERENCES organisations(id),
    annee           INTEGER NOT NULL,
    derniere_seq    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (organisation_id, annee)
);

-- ============================================================
-- FINANCE — Paiements
-- ============================================================
CREATE TABLE paiements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID          NOT NULL REFERENCES organisations(id),
    date_paiement   DATE          NOT NULL,
    montant_total   NUMERIC(12,2) NOT NULL,
    devise          VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    compte          VARCHAR(100),  -- 'CAISSE','BANQUE_PRINCIPALE'
    moyen_paiement  VARCHAR(50)   NOT NULL,  -- 'VIREMENT','CHEQUE','ESPECES','CARTE'
    notes           TEXT,
    created_by      UUID          REFERENCES utilisateurs(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE facture_paiements (
    facture_id  UUID          NOT NULL REFERENCES factures(id),
    paiement_id UUID          NOT NULL REFERENCES paiements(id),
    montant     NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (facture_id, paiement_id)
);

-- ============================================================
-- FINANCE — Recettes
-- ============================================================
CREATE TABLE recettes (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID         NOT NULL REFERENCES organisations(id),
    date_recette     DATE         NOT NULL,
    montant          NUMERIC(12,2) NOT NULL,
    devise           VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    taux_change_eur  NUMERIC(10,6) NOT NULL DEFAULT 1,
    type_recette     type_recette NOT NULL,
    description      TEXT,
    mode_encaissement VARCHAR(50),
    justificatif_url VARCHAR(500),
    categorie_id     UUID         REFERENCES categories_depenses(id),
    created_by       UUID         REFERENCES utilisateurs(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- BUDGET
-- ============================================================
CREATE TABLE budgets_annuels (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         NOT NULL REFERENCES organisations(id),
    annee           INTEGER      NOT NULL,
    statut          statut_budget NOT NULL DEFAULT 'BROUILLON',
    date_validation TIMESTAMPTZ,
    valideur_id     UUID         REFERENCES utilisateurs(id),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, annee, statut)  -- un seul VALIDE/CLOTURE par an
);

CREATE TABLE lignes_budget (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    budget_id    UUID           NOT NULL REFERENCES budgets_annuels(id) ON DELETE CASCADE,
    categorie_id UUID           NOT NULL REFERENCES categories_depenses(id),
    type         type_categorie NOT NULL,
    montant_prevu NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(budget_id, categorie_id)
);

-- Vue d'exécution budgétaire (calculée en live)
CREATE VIEW v_execution_budget AS
SELECT
    lb.id                AS ligne_id,
    lb.budget_id,
    lb.categorie_id,
    cd.libelle           AS categorie_libelle,
    lb.type,
    lb.montant_prevu,
    COALESCE(realise.montant, 0) AS montant_realise,
    lb.montant_prevu - COALESCE(realise.montant, 0) AS ecart,
    CASE WHEN lb.montant_prevu > 0
         THEN ROUND(COALESCE(realise.montant, 0) / lb.montant_prevu * 100, 1)
         ELSE 0 END      AS taux_execution_pct
FROM lignes_budget lb
JOIN categories_depenses cd ON cd.id = lb.categorie_id
LEFT JOIN (
    SELECT categorie_id, SUM(montant_ttc) AS montant
    FROM factures WHERE statut IN ('A_PAYER','PAYE')
    GROUP BY categorie_id
) realise ON realise.categorie_id = lb.categorie_id AND lb.type = 'DEPENSE'
UNION ALL
SELECT
    lb.id, lb.budget_id, lb.categorie_id, cd.libelle, lb.type,
    lb.montant_prevu,
    COALESCE(rec.montant, 0),
    lb.montant_prevu - COALESCE(rec.montant, 0),
    CASE WHEN lb.montant_prevu > 0
         THEN ROUND(COALESCE(rec.montant, 0) / lb.montant_prevu * 100, 1)
         ELSE 0 END
FROM lignes_budget lb
JOIN categories_depenses cd ON cd.id = lb.categorie_id
LEFT JOIN (
    SELECT categorie_id, SUM(montant) AS montant
    FROM recettes GROUP BY categorie_id
) rec ON rec.categorie_id = lb.categorie_id AND lb.type = 'RECETTE';

-- ============================================================
-- INVENTAIRE
-- ============================================================
CREATE TABLE biens_materiels (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID        NOT NULL REFERENCES organisations(id),
    code_inventaire  VARCHAR(30) NOT NULL,
    libelle          VARCHAR(200) NOT NULL,
    categorie        VARCHAR(100) NOT NULL,  -- 'INFORMATIQUE','MOBILIER','VEHICULE','AUTRE'
    code_categorie   VARCHAR(10)  NOT NULL,  -- pour génération code : INF, MOB, VEH...
    date_acquisition DATE,
    valeur_achat     NUMERIC(12,2) NOT NULL DEFAULT 0,
    devise           VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    localisation     VARCHAR(200),
    etat             etat_bien    NOT NULL DEFAULT 'BON',
    responsable_id   UUID         REFERENCES salaries(id),
    description      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code_inventaire)
);

CREATE TABLE bien_sequences (
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    code_categorie  VARCHAR(10) NOT NULL,
    annee           INTEGER     NOT NULL,
    derniere_seq    INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (organisation_id, code_categorie, annee)
);

CREATE TABLE mouvements_biens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bien_id         UUID           NOT NULL REFERENCES biens_materiels(id),
    date_mouvement  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    type_mouvement  type_mouvement NOT NULL,
    champ_modifie   VARCHAR(100),
    ancienne_valeur TEXT,
    nouvelle_valeur TEXT,
    motif           TEXT,
    auteur_id       UUID           REFERENCES utilisateurs(id)
);

-- ============================================================
-- AUDIT LOGS
-- ============================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         REFERENCES organisations(id),
    utilisateur_id  UUID         REFERENCES utilisateurs(id),
    action          VARCHAR(20)  NOT NULL,  -- 'CREATE','UPDATE','DELETE','LOGIN'
    entite          VARCHAR(100) NOT NULL,  -- 'Facture','Salarie'...
    entite_id       UUID,
    avant           JSONB,
    apres           JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    date_action     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_utilisateurs_org      ON utilisateurs(organisation_id);
CREATE INDEX idx_salaries_org          ON salaries(organisation_id);
CREATE INDEX idx_salaries_statut       ON salaries(organisation_id, statut);
CREATE INDEX idx_salaries_service      ON salaries(organisation_id, service);
CREATE INDEX idx_conges_salarie        ON conges_absences(salarie_id, statut);
CREATE INDEX idx_conges_dates          ON conges_absences(date_debut, date_fin);
CREATE INDEX idx_conges_org_periode    ON conges_absences(organisation_id, date_debut, date_fin);
CREATE INDEX idx_factures_org_date     ON factures(organisation_id, date_facture DESC);
CREATE INDEX idx_factures_statut       ON factures(organisation_id, statut);
CREATE INDEX idx_factures_categorie    ON factures(categorie_id);
CREATE INDEX idx_recettes_org_date     ON recettes(organisation_id, date_recette DESC);
CREATE INDEX idx_recettes_categorie    ON recettes(categorie_id);
CREATE INDEX idx_biens_org_etat        ON biens_materiels(organisation_id, etat);
CREATE INDEX idx_biens_categorie       ON biens_materiels(organisation_id, categorie);
CREATE INDEX idx_audit_entite          ON audit_logs(entite, entite_id, date_action DESC);
CREATE INDEX idx_audit_utilisateur     ON audit_logs(utilisateur_id, date_action DESC);
CREATE INDEX idx_paiements_salaires    ON paiements_salaires(salarie_id, annee, mois);

-- Full-text search
CREATE INDEX idx_salaries_search       ON salaries USING gin(to_tsvector('french', nom || ' ' || prenom || ' ' || COALESCE(matricule,'')));
CREATE INDEX idx_factures_search       ON factures USING gin(to_tsvector('french', fournisseur || ' ' || reference));

-- ============================================================
-- DONNÉES INITIALES (seed)
-- ============================================================
-- Catégories de dépenses par défaut
INSERT INTO categories_depenses (id, organisation_id, libelle, code, type, couleur)
-- Note : à remplacer par l'UUID réel de l'organisation au moment du seed
VALUES
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Fonctionnement',   'FONCT',   'DEPENSE', '#3B82F6'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Missions',         'MISSION', 'DEPENSE', '#8B5CF6'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Événements',       'EVENT',   'DEPENSE', '#F59E0B'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Logistique',       'LOGIST',  'DEPENSE', '#10B981'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Salaires',         'SAL',     'DEPENSE', '#EF4444'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Frais de service', 'FRSRV',   'RECETTE', '#06B6D4'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Adhésions',        'ADHES',   'RECETTE', '#84CC16'),
    (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'Subventions',      'SUBV',    'RECETTE', '#F97316');
```

---

## 4. DTOs — Structures de données exactes

> 📌 **CURSOR AI :** Crée ces classes Java dans `modules/{module}/dto/`. Utilise `record` Java 17+ pour les Request. Annote avec Bean Validation (`@NotBlank`, `@NotNull`, etc.).

### 4.1 Auth DTOs

```java
// LoginRequest.java
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}

// LoginResponse.java
public record LoginResponse(
    String accessToken,
    String tokenType,     // "Bearer"
    long expiresIn,       // secondes (900 = 15 min)
    UserInfo user
) {}

// UserInfo.java
public record UserInfo(
    UUID id,
    String email,
    String nom,
    String prenom,
    String role,          // "ADMIN", "FINANCIER"...
    UUID organisationId,
    String organisationNom
) {}
```

### 4.2 RH — Salarié DTOs

```java
// SalarieRequest.java
public record SalarieRequest(
    @NotBlank String nom,
    @NotBlank String prenom,
    @Email    String email,
    String telephone,
    @NotBlank String poste,
    @NotBlank String service,
    @NotNull  @PastOrPresent LocalDate dateEmbauche,
    @NotBlank String typeContrat,   // CDI, CDD, STAGE, CONSULTANT
    String nationalite,
    String adresse,
    // Première grille salariale (obligatoire à la création)
    @NotNull  BigDecimal montantBrut,
    @NotNull  BigDecimal montantNet,
    @NotBlank String devise
) {}

// SalarieResponse.java
public record SalarieResponse(
    UUID id,
    String matricule,
    String nom,
    String prenom,
    String email,
    String telephone,
    String poste,
    String service,
    LocalDate dateEmbauche,
    String typeContrat,
    String statut,               // ACTIF, EN_CONGE, SORTI
    String nationalite,
    SalaireActuel salaireActuel,
    DroitsCongesDto droitsConges,
    LocalDateTime createdAt
) {}

// SalaireActuel.java
public record SalaireActuel(
    BigDecimal montantBrut,
    BigDecimal montantNet,
    String devise,
    LocalDate dateDebut
) {}

// DroitsCongesDto.java
public record DroitsCongesDto(
    int annee,
    BigDecimal joursDroit,
    BigDecimal joursPris,
    BigDecimal joursRestants
) {}
```

### 4.3 RH — Congé DTOs

```java
// CongeRequest.java
public record CongeRequest(
    @NotNull  UUID salarieId,
    @NotNull  String typeConge,   // ANNUEL, MALADIE, EXCEPTIONNEL, SANS_SOLDE
    @NotNull  LocalDate dateDebut,
    @NotNull  LocalDate dateFin,
    String commentaire
) {}

// CongeResponse.java
public record CongeResponse(
    UUID id,
    UUID salarieId,
    String salarieNomComplet,
    String service,
    String typeConge,
    LocalDate dateDebut,
    LocalDate dateFin,
    BigDecimal nbJours,
    String statut,              // BROUILLON, EN_ATTENTE, VALIDE, REJETE
    String valideurNomComplet,
    LocalDateTime dateValidation,
    String motifRejet,
    String commentaire,
    LocalDateTime createdAt
) {}

// CongeValidationRequest.java
public record CongeValidationRequest(
    String motifRejet   // requis seulement si rejet
) {}
```

### 4.4 Finance — Facture DTOs

```java
// FactureRequest.java
public record FactureRequest(
    @NotBlank String fournisseur,
    @NotNull  @PastOrPresent LocalDate dateFacture,
    @NotNull  @Positive BigDecimal montantHt,
    @NotNull  @PositiveOrZero BigDecimal tva,
    @NotBlank String devise,
    UUID categorieId,
    @NotBlank String statut,   // BROUILLON ou A_PAYER
    String notes
    // Le justificatif est uploadé séparément via multipart
) {}

// FactureResponse.java
public record FactureResponse(
    UUID id,
    String reference,           // FAC-2026-0001
    String fournisseur,
    LocalDate dateFacture,
    BigDecimal montantHt,
    BigDecimal tva,
    BigDecimal montantTtc,
    String devise,
    BigDecimal tauxChangeEur,
    BigDecimal montantTtcEur,   // montantTtc * tauxChangeEur
    String categorieLibelle,
    String statut,
    String justificatifUrl,
    BigDecimal montantPaye,     // somme des paiements liés
    BigDecimal montantRestant,
    String notes,
    LocalDateTime createdAt
) {}

// StatutUpdateRequest.java
public record StatutUpdateRequest(
    @NotBlank String statut  // transition validée uniquement
) {}
```

### 4.5 Finance — Paiement DTOs

```java
// PaiementRequest.java
public record PaiementRequest(
    @NotNull @PastOrPresent LocalDate datePaiement,
    @NotNull @Positive BigDecimal montantTotal,
    @NotBlank String devise,
    String compte,             // CAISSE, BANQUE_PRINCIPALE
    @NotBlank String moyenPaiement,  // VIREMENT, CHEQUE, ESPECES, CARTE
    @NotEmpty List<PaiementLigneRequest> factures,
    String notes
) {}

// PaiementLigneRequest.java
public record PaiementLigneRequest(
    @NotNull UUID factureId,
    @NotNull @Positive BigDecimal montant
) {}

// PaiementResponse.java
public record PaiementResponse(
    UUID id,
    LocalDate datePaiement,
    BigDecimal montantTotal,
    String devise,
    String compte,
    String moyenPaiement,
    List<FactureResume> facturesLiees,
    LocalDateTime createdAt
) {}
```

### 4.6 Finance — Recette DTOs

```java
// RecetteRequest.java
public record RecetteRequest(
    @NotNull @PastOrPresent LocalDate dateRecette,
    @NotNull @Positive BigDecimal montant,
    @NotBlank String devise,
    @NotBlank String typeRecette,  // FRAIS_SERVICE, ADHESION, DON, SUBVENTION, PRESTATION
    String description,
    String modeEncaissement,
    UUID categorieId
) {}

// RecetteResponse.java
public record RecetteResponse(
    UUID id,
    LocalDate dateRecette,
    BigDecimal montant,
    String devise,
    BigDecimal tauxChangeEur,
    BigDecimal montantEur,
    String typeRecette,
    String description,
    String modeEncaissement,
    String justificatifUrl,
    String categorieLibelle,
    LocalDateTime createdAt
) {}
```

### 4.7 Budget DTOs

```java
// BudgetRequest.java
public record BudgetRequest(
    @NotNull @Min(2020) Integer annee,
    @NotEmpty List<LigneBudgetRequest> lignes,
    String notes
) {}

// LigneBudgetRequest.java
public record LigneBudgetRequest(
    @NotNull UUID categorieId,
    @NotNull String type,           // DEPENSE ou RECETTE
    @NotNull @PositiveOrZero BigDecimal montantPrevu
) {}

// BudgetResponse.java
public record BudgetResponse(
    UUID id,
    int annee,
    String statut,
    LocalDateTime dateValidation,
    List<LigneBudgetResponse> lignes,
    BigDecimal totalDepensesPrevu,
    BigDecimal totalDepensesRealise,
    BigDecimal totalRecettesPrevu,
    BigDecimal totalRecettesRealise
) {}

// LigneBudgetResponse.java
public record LigneBudgetResponse(
    UUID id,
    UUID categorieId,
    String categorieLibelle,
    String type,
    BigDecimal montantPrevu,
    BigDecimal montantRealise,
    BigDecimal ecart,
    BigDecimal tauxExecutionPct,
    boolean alerteDepassement  // true si tauxExecutionPct >= seuil org
) {}
```

### 4.8 Inventaire DTOs

```java
// BienRequest.java
public record BienRequest(
    @NotBlank String libelle,
    @NotBlank String categorie,      // INFORMATIQUE, MOBILIER, VEHICULE, AUTRE
    @NotBlank String codeCategorie,  // INF, MOB, VEH, AUT
    LocalDate dateAcquisition,
    @NotNull @PositiveOrZero BigDecimal valeurAchat,
    String devise,
    String localisation,
    @NotBlank String etat,           // BON, USE, DEFAILLANT, HORS_SERVICE
    UUID responsableId,
    String description
) {}

// BienResponse.java
public record BienResponse(
    UUID id,
    String codeInventaire,          // INF-2026-0001
    String libelle,
    String categorie,
    LocalDate dateAcquisition,
    BigDecimal valeurAchat,
    String devise,
    String localisation,
    String etat,
    String responsableNomComplet,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// MouvementResponse.java
public record MouvementResponse(
    UUID id,
    String typeMouvement,
    String champModifie,
    String ancienneValeur,
    String nouvelleValeur,
    String motif,
    String auteurNomComplet,
    LocalDateTime dateMouvement
) {}
```

### 4.9 Wrappers génériques

```java
// ApiResponse.java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, LocalDateTime.now());
    }
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }
}

// PageResponse.java
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {}

// ErrorResponse.java
public record ErrorResponse(
    int status,
    String code,       // ex: "FACTURE_NOT_FOUND", "CONGE_CHEVAUCHEMENT"
    String message,
    Map<String, String> fieldErrors,  // pour erreurs de validation
    LocalDateTime timestamp
) {}
```

---

## 5. Spécification API REST avec réponses JSON

> 📌 **CURSOR AI :** Préfixe global `/api/v1`. Toutes les réponses sont encapsulées dans `ApiResponse<T>`. Retourner `Content-Type: application/json`.

### 5.1 Authentification

#### POST `/auth/login`

**Request :**
```json
{
  "email": "comptable@ambassade.fr",
  "password": "monMotDePasse123"
}
```

**Response 200 :**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "comptable@ambassade.fr",
      "nom": "Dupont",
      "prenom": "Marie",
      "role": "FINANCIER",
      "organisationId": "660e8400-e29b-41d4-a716-446655440001",
      "organisationNom": "Ambassade de France à Dakar"
    }
  },
  "timestamp": "2026-03-15T10:30:00"
}
```
Le `refreshToken` est envoyé via cookie HttpOnly `Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800`.

---

### 5.2 Module RH — exemples clés

#### GET `/rh/salaries?page=0&size=20&service=Comptabilité&statut=ACTIF`

**Response 200 :**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "aa1...",
        "matricule": "EMP-0042",
        "nom": "Martin",
        "prenom": "Jean",
        "poste": "Comptable senior",
        "service": "Comptabilité",
        "statut": "ACTIF",
        "salaireActuel": {
          "montantBrut": 3500.00,
          "montantNet": 2800.00,
          "devise": "EUR",
          "dateDebut": "2024-01-01"
        },
        "droitsConges": {
          "annee": 2026,
          "joursDroit": 30.0,
          "joursPris": 5.0,
          "joursRestants": 25.0
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

#### POST `/rh/conges` — Demande de congé

**Request :**
```json
{
  "salarieId": "aa1...",
  "typeConge": "ANNUEL",
  "dateDebut": "2026-04-14",
  "dateFin": "2026-04-18",
  "commentaire": "Vacances de printemps"
}
```

**Response 201 :**
```json
{
  "success": true,
  "message": "Demande de congé soumise avec succès",
  "data": {
    "id": "bb2...",
    "salarieId": "aa1...",
    "salarieNomComplet": "Jean Martin",
    "service": "Comptabilité",
    "typeConge": "ANNUEL",
    "dateDebut": "2026-04-14",
    "dateFin": "2026-04-18",
    "nbJours": 5.0,
    "statut": "EN_ATTENTE",
    "commentaire": "Vacances de printemps",
    "createdAt": "2026-03-15T10:30:00"
  }
}
```

---

### 5.3 Module Finance — exemples clés

#### GET `/finance/factures?page=0&size=20&statut=A_PAYER&dateDebut=2026-01-01&dateFin=2026-03-31`

**Response 200 :**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "cc3...",
        "reference": "FAC-2026-0023",
        "fournisseur": "Office Pro Sénégal",
        "dateFacture": "2026-02-10",
        "montantHt": 1000.00,
        "tva": 18.00,
        "montantTtc": 1180.00,
        "devise": "EUR",
        "montantTtcEur": 1180.00,
        "categorieLibelle": "Fonctionnement",
        "statut": "A_PAYER",
        "justificatifUrl": "https://minio.local/documents/factures/FAC-2026-0023.pdf",
        "montantPaye": 0.00,
        "montantRestant": 1180.00,
        "createdAt": "2026-02-10T09:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

#### GET `/finance/stats/2026/3` — Stats mensuelles

**Response 200 :**
```json
{
  "success": true,
  "data": {
    "annee": 2026,
    "mois": 3,
    "totalDepenses": 45230.50,
    "totalRecettes": 62000.00,
    "solde": 16769.50,
    "devise": "EUR",
    "nbFactures": 12,
    "nbFacturesEnAttente": 3,
    "depensesParCategorie": [
      { "categorie": "Salaires",       "montant": 28000.00 },
      { "categorie": "Fonctionnement", "montant": 12000.00 },
      { "categorie": "Logistique",     "montant": 5230.50 }
    ],
    "recettesParCategorie": [
      { "categorie": "Frais de service", "montant": 45000.00 },
      { "categorie": "Subventions",      "montant": 17000.00 }
    ]
  }
}
```

---

### 5.4 Dashboard

#### GET `/rapports/dashboard`

**Response 200 :**
```json
{
  "success": true,
  "data": {
    "moisCourant": { "annee": 2026, "mois": 3 },
    "kpis": {
      "totalDepenses":  45230.50,
      "totalRecettes":  62000.00,
      "solde":          16769.50,
      "effectifsActifs": 24,
      "congesEnCours":   3,
      "valeurParcMateriel": 187500.00
    },
    "evolution6Mois": [
      { "mois": "2025-10", "depenses": 38000, "recettes": 55000 },
      { "mois": "2025-11", "depenses": 41000, "recettes": 58000 },
      { "mois": "2025-12", "depenses": 52000, "recettes": 60000 },
      { "mois": "2026-01", "depenses": 39000, "recettes": 61000 },
      { "mois": "2026-02", "depenses": 43000, "recettes": 59000 },
      { "mois": "2026-03", "depenses": 45231, "recettes": 62000 }
    ],
    "alertesBudget": [
      { "categorie": "Fonctionnement", "tauxExecution": 85.2, "alerte": true }
    ],
    "top5Fournisseurs": [
      { "fournisseur": "Office Pro",   "montant": 12000 },
      { "fournisseur": "Telecom Plus", "montant": 8500 }
    ]
  }
}
```

---

## 6. Cas d'erreur & Codes HTTP

> 📌 **CURSOR AI :** Implémente ces cas dans `GlobalExceptionHandler.java`. Chaque code métier doit être loggué.

### 6.1 Codes d'erreur métier

| Code HTTP | Code métier | Déclencheur |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Bean validation échoue (champs invalides) |
| `400` | `CONGE_CHEVAUCHEMENT` | Congé chevauche un congé VALIDE existant |
| `400` | `CONGE_SOLDE_INSUFFISANT` | `joursRestants < nbJours` demandés |
| `400` | `CONGE_DATE_INVALIDE` | `dateFin < dateDebut` |
| `400` | `FACTURE_STATUT_TRANSITION` | Transition de statut non autorisée |
| `400` | `FACTURE_DEJA_PAYEE` | Modification impossible sur facture PAYEE |
| `400` | `PAIEMENT_MONTANT_DEPASSE` | `sum(paiements) > montantTtc` facture |
| `400` | `BUDGET_ANNEE_EXISTE` | Budget VALIDE ou CLOTURE déjà présent pour cette année |
| `400` | `JUSTIFICATIF_REQUIS` | Facture > seuil sans justificatif joint |
| `400` | `FICHIER_TYPE_INVALIDE` | Upload : MIME non autorisé (attendu: application/pdf) |
| `400` | `FICHIER_TROP_GRAND` | Upload : taille > 10 Mo |
| `401` | `TOKEN_INVALIDE` | JWT expiré, malformé ou signature invalide |
| `401` | `IDENTIFIANTS_INCORRECTS` | Email/password incorrect |
| `403` | `ACCES_REFUSE` | Rôle insuffisant pour cette action |
| `403` | `ACCES_DONNEES_TIERCES` | EMPLOYE tente d'accéder aux données d'un autre |
| `404` | `SALARIE_NOT_FOUND` | UUID salarié inexistant dans l'organisation |
| `404` | `FACTURE_NOT_FOUND` | UUID facture inexistant |
| `404` | `CONGE_NOT_FOUND` | UUID congé inexistant |
| `404` | `BIEN_NOT_FOUND` | UUID bien matériel inexistant |
| `404` | `BUDGET_NOT_FOUND` | Aucun budget pour cette année |
| `409` | `EMAIL_DEJA_UTILISE` | Email utilisateur déjà pris dans l'organisation |
| `409` | `MATRICULE_DEJA_UTILISE` | Matricule salarié déjà pris |
| `413` | `PAYLOAD_TOO_LARGE` | Body > limite configurée |
| `500` | `EXPORT_PDF_ERREUR` | Erreur génération PDF iText 7 |
| `500` | `STOCKAGE_FICHIER_ERREUR` | Erreur upload MinIO |

### 6.2 Format de réponse d'erreur

```json
{
  "status": 400,
  "code": "CONGE_CHEVAUCHEMENT",
  "message": "Un congé validé existe déjà du 14/04/2026 au 18/04/2026 pour ce salarié.",
  "fieldErrors": null,
  "timestamp": "2026-03-15T10:30:00"
}
```

**Erreur de validation (400 VALIDATION_ERROR) :**
```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Données de la requête invalides",
  "fieldErrors": {
    "montantHt":   "doit être positif",
    "fournisseur": "ne doit pas être vide",
    "dateFacture": "doit être une date passée ou présente"
  },
  "timestamp": "2026-03-15T10:30:00"
}
```

### 6.3 Implémentation GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
            .body(new ErrorResponse(ex.getHttpStatus().value(),
                ex.getCode(), ex.getMessage(), null, LocalDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField,
                fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "invalide")));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(400, "VALIDATION_ERROR",
                "Données invalides", errors, LocalDateTime.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(403)
            .body(new ErrorResponse(403, "ACCES_REFUSE",
                "Vous n'avez pas les droits pour cette action.",
                null, LocalDateTime.now()));
    }
}

// BusinessException.java
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;
    // constructeurs, getters...
}
```

---

## 7. Configuration Spring Security & JWT

> 📌 **CURSOR AI :** Crée ces classes dans `config/`. Elles sont critiques — ne pas les modifier sans comprendre l'impact sur le RBAC.

### 7.1 SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // active @PreAuthorize sur les méthodes
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtFilter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics
                .requestMatchers("/api/v1/auth/login",
                                 "/api/v1/auth/refresh",
                                 "/actuator/health").permitAll()
                // Tout le reste : authentifié
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("""
                        {"status":401,"code":"TOKEN_INVALIDE",
                         "message":"Token manquant ou expiré.","timestamp":"%s"}
                        """.formatted(LocalDateTime.now()));
                })
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

### 7.2 JwtService.java (RS256)

```java
@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey  publicKey;

    // Durées
    private static final long ACCESS_TOKEN_VALIDITY  = 15 * 60;       // 15 min en secondes
    private static final long REFRESH_TOKEN_VALIDITY = 7 * 24 * 3600; // 7 jours

    public String generateAccessToken(UserDetails user, UUID orgId, String role) {
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("orgId", orgId)
            .claim("role",  role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY * 1000))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    // generateRefreshToken, isExpired, extractUsername...
}
```

### 7.3 JwtAuthFilter.java

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            Claims claims = jwtService.validateAndExtract(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            // Laisse passer sans auth → Spring rejettera avec 401 via l'entry point
        }
        chain.doFilter(request, response);
    }
}
```

### 7.4 Exemples `@PreAuthorize` sur controllers

```java
// RhController.java
@RestController
@RequestMapping("/api/v1/rh")
@RequiredArgsConstructor
public class RhController {

    @GetMapping("/salaries")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<SalarieResponse>>> listSalaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String statut) { ... }

    @PostMapping("/conges")
    @PreAuthorize("isAuthenticated()")  // tous les rôles
    public ResponseEntity<ApiResponse<CongeResponse>> soumettreCongé(
            @Valid @RequestBody CongeRequest request) { ... }

    @PostMapping("/conges/{id}/valider")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<ApiResponse<CongeResponse>> validerCongé(
            @PathVariable UUID id,
            @RequestBody CongeValidationRequest request) { ... }

    @GetMapping("/salaries/{id}/droits-conges/{annee}")
    @PreAuthorize("hasAnyRole('RH','ADMIN') or " +
                  "@securityService.isSelf(#id, authentication)")
    public ResponseEntity<ApiResponse<DroitsCongesDto>> getDroitsConges(
            @PathVariable UUID id,
            @PathVariable int annee) { ... }
}
```

### 7.5 Refresh token — flux complet

```
1. Client → POST /auth/login  →  { accessToken } + cookie refreshToken
2. accessToken expire après 15 min
3. Client → POST /auth/refresh (envoie cookie refreshToken automatiquement)
4. Serveur vérifie refreshToken en base (table refresh_tokens)
5. Serveur invalide l'ancien refreshToken (rotation)
6. Serveur →  nouveau { accessToken } + nouveau cookie refreshToken
7. Si refreshToken expiré/invalide → 401 → redirection /login
```

```sql
-- Table refresh_tokens (à ajouter dans V1__init.sql)
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    utilisateur_id  UUID        NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,  -- SHA-256 du token
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens ON refresh_tokens(token_hash, used);
```

---

## 8. Règles Métier Détaillées

> 📌 **CURSOR AI :** Implémente ces règles dans les Services (`@Service`), jamais dans les Controllers. Chaque violation lève une `BusinessException` avec le code correspondant (section 6.1).

### 8.1 Module RH

- **Droits congés** : 2,5 jours/mois travaillé. Recalculés au 1er janvier via `@Scheduled`. Valeur par défaut à la création : 30 jours (12 mois × 2,5).
- **Chevauchement** : avant validation, vérifier `SELECT COUNT(*) FROM conges_absences WHERE salarie_id = ? AND statut = 'VALIDE' AND date_debut <= :dateFin AND date_fin >= :dateDebut`. Si > 0 → `CONGE_CHEVAUCHEMENT`.
- **Solde insuffisant** : vérifier `joursRestants >= nbJours` → sinon `CONGE_SOLDE_INSUFFISANT`.
- **Workflow** : `BROUILLON → EN_ATTENTE` (soumission) → `VALIDE` ou `REJETE` (RH). Pas d'autres transitions.
- **Validation** : dans une transaction : décrémenter `droits_conges.jours_restants` et incrémenter `jours_pris`.
- **Annulation d'un congé VALIDE** : restaurer les jours en base. Seulement si `date_debut > aujourd'hui`.
- **Upload contrat** : vérifier MIME côté serveur (`Tika.detect(inputStream)`), refuser si non `application/pdf`. Max 10 Mo.
- **Historique salaires** : à chaque changement de grille, mettre `date_fin = nouvelle.dateDebut - 1 jour` sur l'entrée actuelle, puis créer la nouvelle avec `date_fin = null`.
- **Paie mensuelle** : job `@Scheduled(cron = "0 0 1 1 * *")` → créer 12 lignes `EN_ATTENTE` pour chaque salarié `ACTIF` au 1er janvier.

### 8.2 Module Finance

- **Numérotation facture** : dans une transaction, faire `UPDATE facture_sequences SET derniere_seq = derniere_seq + 1 WHERE organisation_id = ? AND annee = ? RETURNING derniere_seq`, puis formater `FAC-{annee}-{seq:04d}`. Initialiser la ligne si absente (`INSERT ... ON CONFLICT DO NOTHING`).
- **Paiements partiels** : `sum(facture_paiements.montant) <= facture.montant_ttc`. Si `sum >= montant_ttc` après insertion → mettre statut facture à `PAYE` automatiquement.
- **Justificatif obligatoire** : si `facture.montantTtc > organisation.seuilJustificatif` et `statut != BROUILLON` et `justificatifUrl == null` → `JUSTIFICATIF_REQUIS`.
- **Transitions autorisées** :
  - `BROUILLON → A_PAYER` ✅
  - `A_PAYER → PAYE` ✅ (via paiement, automatique)
  - `A_PAYER → ANNULE` ✅
  - `BROUILLON → ANNULE` ✅
  - Toute autre → `FACTURE_STATUT_TRANSITION`
- **Devises** : stocker `taux_change_eur` au taux du jour de saisie. Calculer `montant_ttc_eur = montant_ttc * taux_change_eur`. Pour les stats, toujours agréger en EUR.

### 8.3 Module Budget

- **Unicité** : contrainte PostgreSQL `UNIQUE(organisation_id, annee, statut)` + vérification applicative avant insertion.
- **Exécution LIVE** : utiliser la vue `v_execution_budget`. Ne jamais stocker `montant_realise` en base.
- **Alerte** : dans `LigneBudgetResponse`, calculer `alerte = taux_execution_pct >= organisation.alerteBudgetPct`.
- **Révision** : garder l'ancien budget `VALIDE`, créer un nouveau avec statut `BROUILLON`. À la validation du nouveau → basculer l'ancien en `CLOTURE`.

### 8.4 Module Inventaire

- **Code auto** : `UPDATE bien_sequences SET derniere_seq = derniere_seq + 1 WHERE ... RETURNING derniere_seq`, formater `{CODE_CAT}-{annee}-{seq:04d}`.
- **Mouvement automatique** : utiliser `@PreUpdate` JPA dans `BienMateriel`. Comparer les champs modifiés et créer une entrée `MouvementBien` pour chaque champ changé.
- **Réforme** : `etat = HORS_SERVICE` + entrée mouvement `type = REFORME`. Ne jamais supprimer un bien.

---

## 9. Matrice RBAC

| Permission | ADMIN | FINANCIER | RH | LOGISTIQUE | EMPLOYE |
|---|---|---|---|---|---|
| **Gestion utilisateurs** | ✅ Complet | ❌ | ❌ | ❌ | ❌ |
| **Finance (factures, recettes)** | 👁 Lecture | ✅ Complet | 👁 Paie seule | ❌ | ❌ |
| **Module RH (salariés)** | ✅ Complet | 👁 Lecture | ✅ Complet | ❌ | 👁 Ses données |
| **Congés (soumettre les siens)** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Congés (valider/rejeter)** | ✅ | ❌ | ✅ | ❌ | ❌ |
| **Inventaire (biens)** | ✅ Complet | 👁 Lecture | ❌ | ✅ Complet | ❌ |
| **Budget annuel (saisie)** | ✅ | ✅ | 👁 Lecture | ❌ | ❌ |
| **Budget annuel (validation)** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Dashboard** | ✅ Complet | ✅ Finance | ✅ RH | ✅ Inventaire | ❌ |
| **Export PDF/Excel** | ✅ | ✅ | ✅ RH | ✅ Inventaire | ❌ |
| **Audit logs** | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## 10. Wireframes Textuels

> 📌 **CURSOR AI :** Utilise ces wireframes pour générer les composants React. Respecte la structure de layout indiquée.

### 10.1 Layout global (dashboard)

```
┌─────────────────────────────────────────────────────────────┐
│  HEADER : Logo + Nom organisation + Rôle + Avatar + Logout  │
├──────────────────┬──────────────────────────────────────────┤
│  SIDEBAR         │  CONTENU PRINCIPAL                       │
│                  │                                          │
│  📊 Dashboard    │                                          │
│  💰 Finance  ▼   │                                          │
│    Factures      │                                          │
│    Paiements     │                                          │
│    Recettes      │                                          │
│  👥 RH       ▼   │                                          │
│    Salariés      │                                          │
│    Congés        │                                          │
│    Paie          │                                          │
│  📦 Budget       │                                          │
│  🏭 Inventaire   │                                          │
│  📄 Rapports     │                                          │
│                  │                                          │
│  ──────────────  │                                          │
│  ⚙️ Paramètres   │                                          │
└──────────────────┴──────────────────────────────────────────┘
```

### 10.2 Dashboard principal

```
┌─────────────────────────────────────────────────────────────────┐
│  Dashboard — Mars 2026                        [Sélecteur mois]  │
├─────────────┬──────────────┬──────────────┬──────────────────── ┤
│  💸 Dépenses │ 💰 Recettes  │ 📈 Solde     │  👥 Effectifs       │
│  45 230 €   │  62 000 €    │ +16 770 €    │  24 actifs          │
│  ↑ +8%      │  ↑ +3%       │ ████████     │  3 congés en cours  │
├─────────────┴──────────────┴──────────────┴────────────────────┤
│  Évolution 6 mois (BarChart groupé)   │  Budget — Alertes       │
│                                        │                         │
│  [Recharts BarChart]                   │  Fonctionnement  85% 🔴 │
│   Oct Nov Dec Jan Fev Mar              │  Missions        42% 🟢 │
│   ■ Dépenses  ■ Recettes              │  Logistique      61% 🟡 │
├────────────────────────────────────────┴─────────────────────── ┤
│  Top 5 Fournisseurs                   │  Congés en cours        │
│  ─────────────────────                │  ─────────────────────  │
│  1. Office Pro        12 000 €        │  J. Martin (14-18 avr)  │
│  2. Telecom Plus       8 500 €        │  A. Diallo (21-25 avr)  │
└────────────────────────────────────────┴────────────────────────┘
```

### 10.3 Liste Factures

```
┌──────────────────────────────────────────────────────────────────────┐
│  Factures                                  [+ Nouvelle facture]       │
├──────────────────────────────────────────────────────────────────────┤
│  🔍 Recherche...    [Période ▼] [Catégorie ▼] [Statut ▼] [Export CSV]│
├────────────────────────────────────────────────────────────────────── ┤
│ Référence      │ Fournisseur      │ Date       │ TTC €   │ Statut     │
│ ─────────────  │ ────────────     │ ────────── │ ─────── │ ───────    │
│ FAC-2026-0023  │ Office Pro       │ 10/02/2026 │ 1 180 € │ 🟡 À PAYER │
│ FAC-2026-0022  │ Telecom Plus     │ 08/02/2026 │ 2 400 € │ ✅ PAYÉ    │
│ FAC-2026-0021  │ Garage Central   │ 05/02/2026 │   350 € │ 📝 BROUILL.│
├──────────────────────────────────────────────────────────────────────┤
│  ← 1 2 3 →   Affichage 1-20 / 47 factures                           │
└──────────────────────────────────────────────────────────────────────┘

[Clic ligne] → Panneau latéral :
┌─────────────────────────────────┐
│  FAC-2026-0023                  │
│  Office Pro — 10/02/2026        │
│  HT: 1 000 €  TVA: 18%  TTC: 1 180 €
│  Catégorie: Fonctionnement      │
│  Statut: À PAYER                │
│  ─────────────────────          │
│  [📄 Voir justificatif]         │
│  [💳 Enregistrer paiement]      │
│  [✏️ Modifier]  [🚫 Annuler]    │
└─────────────────────────────────┘
```

### 10.4 Fiche Salarié

```
┌──────────────────────────────────────────────────────────────────────┐
│  ← Salariés   Jean Martin — EMP-0042                  [Valider dossier│
├──────────────────────────────────────────────────────────────────────┤
│  [Infos] [Congés] [Paie] [Documents]                                 │
├──────────────────────────────────────────────────────────────────────┤
│  TAB INFOS :                                                          │
│  ┌─────────────────────┬──────────────────────┐                      │
│  │ Nom : Jean Martin   │ Poste : Comptable sr  │                      │
│  │ Service : Compta    │ Contrat : CDI         │                      │
│  │ Embauché : 01/03/20 │ Statut : 🟢 ACTIF     │                      │
│  └─────────────────────┴──────────────────────┘                      │
│  Historique salaires :                                                │
│  ├── 01/01/2026 → auj.  Brut: 3 500 € / Net: 2 800 €                │
│  └── 01/03/2020 → 31/12/2025  Brut: 3 000 € / Net: 2 400 €          │
│                                          [+ Ajouter grille salariale] │
│                                                                       │
│  TAB CONGÉS :                                                         │
│  Solde 2026 : 25 jours restants / 30 droits  (5 pris)               │
│  ┌──────────────┬──────────────┬────────────┬──────────────┐         │
│  │ Type         │ Période      │ Jours      │ Statut       │         │
│  │ Annuel       │ 01-05 mars   │ 5 j        │ ✅ VALIDÉ    │         │
│  └──────────────┴──────────────┴────────────┴──────────────┘         │
└──────────────────────────────────────────────────────────────────────┘
```

### 10.5 Suivi Budget

```
┌──────────────────────────────────────────────────────────────────────┐
│  Budget Annuel  [2026 ▼]   Statut: ✅ VALIDÉ    [Réviser le budget]  │
├──────────────────────────────────────────────────────────────────────┤
│  DÉPENSES                                                             │
│  Catégorie        Prévu      Réalisé    Écart      Exécution          │
│  ─────────────    ────────   ────────   ────────   ────────────────   │
│  Salaires         280 000 €  84 000 €   196 000 €  ████░░░░░ 30%      │
│  Fonctionnement    80 000 €  68 200 €    11 800 €  ████████░ 85% 🔴   │
│  Missions          30 000 €  12 600 €    17 400 €  ████░░░░░ 42%      │
│  Logistique        20 000 €  12 200 €     7 800 €  ██████░░░ 61%      │
├──────────────────────────────────────────────────────────────────────┤
│  RECETTES                                                             │
│  Frais de service 400 000 €ˬ 186 000 €  214 000 €  ████░░░░░ 47%     │
│  Subventions       50 000 €   50 000 €        0 €  ██████████100% ✅  │
└──────────────────────────────────────────────────────────────────────┘
```

### 10.6 Formulaire — Nouvelle Facture (Modal)

```
┌─────────────────────────────────────────────────────────┐
│  Nouvelle facture                               [✕]      │
├─────────────────────────────────────────────────────────┤
│  Fournisseur *    [________________________]             │
│  Date *           [JJ/MM/AAAA]                          │
│                                                         │
│  Montant HT *     [____________]  TVA *  [___]%         │
│  → Montant TTC :  Calculé automatiquement               │
│                                                         │
│  Devise *         [EUR ▼]                               │
│  Catégorie        [Fonctionnement ▼]                    │
│  Statut *         [À PAYER ▼]                           │
│                                                         │
│  Justificatif     [📎 Glisser-déposer ou parcourir]     │
│                   PDF uniquement — max 10 Mo            │
│                                                         │
│  Notes            [________________________]             │
│                   [________________________]             │
├─────────────────────────────────────────────────────────┤
│            [Annuler]    [Enregistrer →]                 │
└─────────────────────────────────────────────────────────┘
```

---

## 11. Roadmap MVP

| Ph. | Module | Livrables clés | Durée | Priorité |
|---|---|---|---|---|
| **1** | **Auth + Infra** | Login JWT RS256, refresh token HttpOnly, RBAC middleware Next.js, Docker Compose complet, `.env` | 1 sem. | 🔴 P0 |
| **2** | **Module RH** | CRUD salariés + matricule auto, upload contrat, workflow congé, compteurs droits, calendrier, paie mensuelle | 2 sem. | 🔴 P0 |
| **3** | **Module Finance** | Factures + numérotation auto, paiements partiels, recettes, catégories, stats mensuelles | 2 sem. | 🔴 P0 |
| **4** | **Budget & Rapports** | Budget annuel, vue `v_execution_budget`, dashboard KPIs, bilan PDF, bilan Excel | 1,5 sem. | 🟡 P1 |
| **5** | **Inventaire** | CRUD biens + code auto, `@PreUpdate` mouvements, historique, stats parc | 1 sem. | 🟡 P1 |
| **6** | **Optimisations** | Graphiques Recharts avancés, export CSV, alertes budget, i18n EN complet, tests E2E Playwright | 1,5 sem. | 🟢 P2 |

---

## 12. Déploiement Docker Compose

> 📌 **CURSOR AI :** Génère `docker-compose.yml` (dev) et `docker-compose.prod.yml` (prod) à partir de cette spec.

### 12.1 Services

| Service | Port | Configuration |
|---|---|---|
| **postgres** | 5432 | PostgreSQL 16, volume `postgres_data`, healthcheck `pg_isready`, credentials `.env` |
| **backend** | 8080 | Spring Boot JAR, `depends_on: postgres (healthy) + minio`, healthcheck `/actuator/health` |
| **frontend** | 3000 | Next.js build multi-stage, `NEXT_PUBLIC_API_URL=http://nginx/api` |
| **minio** | 9000/9001 | MinIO latest, volume `minio_data`, bucket `documents` créé via `mc` au démarrage |
| **nginx** | 80/443 | Reverse proxy `/` → frontend, `/api` → backend, SSL Certbot (prod), Gzip |

### 12.2 Variables d'environnement (`.env`)

```env
# Base de données
DB_HOST=postgres
DB_PORT=5432
DB_NAME=gestion_institutionnelle
DB_USER=app_user
DB_PASSWORD=<CHANGE_ME>

# JWT — générer : openssl genrsa -out private.pem 2048 && openssl rsa -in private.pem -pubout -out public.pem
JWT_PRIVATE_KEY_B64=<base64 -w0 private.pem>
JWT_PUBLIC_KEY_B64=<base64 -w0 public.pem>

# MinIO
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=<CHANGE_ME>
MINIO_SECRET_KEY=<CHANGE_ME>
MINIO_BUCKET=documents

# App
FRONTEND_URL=http://localhost:3000
APP_ENV=development
FACTURE_SEUIL_JUSTIFICATIF=500
BUDGET_ALERTE_POURCENTAGE=80

# Rate limiting
AUTH_RATE_LIMIT_MAX=100
AUTH_RATE_LIMIT_WINDOW_SECONDS=60
```

### 12.3 `docker-compose.yml` (dev)

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB:       ${DB_NAME}
      POSTGRES_USER:     ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER:     ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes:
      - minio_data:/data
    ports:
      - "9001:9001"

  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL:      jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_PRIVATE_KEY_B64:        ${JWT_PRIVATE_KEY_B64}
      JWT_PUBLIC_KEY_B64:         ${JWT_PUBLIC_KEY_B64}
      MINIO_ENDPOINT:             ${MINIO_ENDPOINT}
      MINIO_ACCESS_KEY:           ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY:           ${MINIO_SECRET_KEY}
      MINIO_BUCKET:               ${MINIO_BUCKET}
    depends_on:
      postgres: { condition: service_healthy }
      minio:    { condition: service_started }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      retries: 5

  frontend:
    build: ./frontend
    environment:
      NEXT_PUBLIC_API_URL: http://nginx/api
    depends_on:
      backend: { condition: service_healthy }

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx/nginx.dev.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - frontend
      - backend

volumes:
  postgres_data:
  minio_data:
```

---

## 13. Prompts Cursor AI — Démarrage Rapide

> 💡 **USAGE :** Copier-coller dans Cursor AI (mode **Agent**). Commencer par le Prompt 1, attendre la fin, puis enchaîner.

### Prompt 1 — Infrastructure complète

```
En te basant sur le PRD v3 de l'application de gestion institutionnelle, crée :

FRONTEND (Next.js 14, TypeScript, Tailwind, shadcn/ui) :
- Structure de dossiers complète (voir section 2.2 du PRD)
- Fichier lib/api.ts : instance Axios avec intercepteur Bearer token et refresh auto sur 401
- Store Zustand store.ts : { user: UserInfo | null, accessToken, setAuth, logout }
- Middleware Next.js (middleware.ts) : protège /dashboard/**, redirige vers /login si pas de token, vérifie le rôle pour chaque route
- Composant DataTable.tsx générique avec TanStack Table : tri, pagination, filtre texte global
- next-intl : messages/fr.json et messages/en.json avec toutes les clés UI

BACKEND (Spring Boot 3, Java 21, Maven) :
- Structure modules (voir section 2.3 du PRD)
- BaseEntity.java avec id UUID, createdAt, updatedAt, organisationId
- ApiResponse<T>, PageResponse<T>, ErrorResponse records (section 4.9 du PRD)
- BusinessException.java + GlobalExceptionHandler.java (section 6.3 du PRD)
- SecurityConfig.java + JwtService.java RS256 + JwtAuthFilter.java (section 7 du PRD)
- application.yml avec toutes les variables d'environnement (lire depuis .env via Docker)
- V1__init.sql Flyway : SQL complet de la section 3 du PRD
- docker-compose.yml complet (section 12.3 du PRD)
- Fichier .env.example
```

### Prompt 2 — Module RH complet

```
En suivant exactement le PRD v3 sections 4.2, 4.2 DTOs, et 8.1 :

Crée le module RH Spring Boot complet :
- Entités JPA : Salarie, HistoriqueSalaire, PaiementSalaire, CongeAbsence, DroitsConges
  (champs exacts de la section 3 SQL)
- Repositories JPA avec requêtes custom (JPQL) pour :
  * Rechercher salariés par service/statut/nom
  * Détecter les chevauchements de congés
  * Récupérer droits congés par salarié+année
- Services avec toutes les règles métier de la section 8.1 :
  * SalarieService : CRUD, validation dossier, upload contrat MinIO
  * CongeService : soumettre (chevauchement + solde), valider, rejeter, restaurer jours
  * PaieService : création 12 lignes annuelles (@Scheduled), marquer-paye
- RhController avec tous les endpoints section 4.2 et @PreAuthorize corrects
- DTOs exacts de la section 4.2 et 4.3 du PRD (SalarieRequest, SalarieResponse, etc.)
- AuditListener sur Salarie et CongeAbsence

Côté Next.js, crée :
- services/salarie.service.ts et services/conge.service.ts
- Page app/(dashboard)/rh/salaries/page.tsx avec DataTable + filtres
- Page app/(dashboard)/rh/salaries/[id]/page.tsx avec Tabs (Infos/Congés/Paie/Documents)
- Page app/(dashboard)/rh/conges/page.tsx avec calendrier mensuel + liste des demandes
- Formulaires SalarieForm.tsx et CongeForm.tsx avec React Hook Form + Zod
```

### Prompt 3 — Module Finance complet

```
En suivant exactement le PRD v3 sections 4.3, 4.4-4.6 DTOs, et 8.2 :

Crée le module Finance Spring Boot complet :
- Entités : Facture, FactureSequence, Paiement, FacturePaiement, Recette, CategorieDepense
- Services avec toutes les règles métier section 8.2 :
  * FactureService : numérotation auto FAC-{ANNEE}-{SEQ:04}, upload justificatif, transitions statut, calcul statut PAYE auto
  * PaiementService : lier factures avec vérification montants partiels
  * RecetteService : CRUD + taux_change_eur au jour J
  * StatsService : totaux mensuels EUR (section 5.3 format JSON exact)
- FinanceController avec tous les endpoints section 4.3 + gestion erreurs section 6.1
- DTOs exacts sections 4.4 à 4.6

Côté Next.js :
- Page factures avec DataTable + panneau latéral détail (wireframe section 10.3)
- Modal NewFactureForm.tsx (wireframe section 10.6)
- Page recettes et paiements
- Tous les appels API via services/facture.service.ts etc.
```

### Prompt 4 — Budget, Inventaire & Rapports

```
En suivant le PRD v3 sections 4.4, 4.5, 4.6, 8.3, 8.4 et les wireframes 10.2 et 10.5 :

Spring Boot :
- Module Budget : BudgetAnnuel, LigneBudget, vue SQL v_execution_budget (section 3),
  BudgetService avec règles section 8.3, BudgetController section 4.4
- Module Inventaire : BienMateriel, MouvementBien, séquences, @PreUpdate listener,
  InventaireService règles section 8.4, InventaireController section 4.5
- Module Rapports : RapportController tous endpoints section 4.6,
  ExportPdfService (iText 7) pour bilan mensuel,
  ExportExcelService (Apache POI, multi-onglets) pour bilan annuel,
  Dashboard endpoint avec format JSON exact section 5.4

Next.js :
- Page budget (wireframe 10.5) : tableau prévu/réalisé + ProgressBar + BarChart Recharts
- Page inventaire : DataTable biens + modal ajout + historique mouvements
- Page dashboard (wireframe 10.2) : 4 KPI cards + BarChart 6 mois + alertes budget
- Page rapports : sélecteur mois/année + boutons export PDF et Excel
```

---

## 14. Glossaire

| Terme | Définition |
|---|---|
| **RBAC** | Role-Based Access Control — contrôle d'accès basé sur les rôles utilisateur |
| **JWT** | JSON Web Token — standard d'authentification stateless (RFC 7519) |
| **RS256** | Algorithme de signature JWT utilisant une paire de clés RSA asymétriques |
| **MinIO** | Serveur de stockage objet haute performance, API compatible Amazon S3, auto-hébergé |
| **Flyway** | Outil de migration de schéma SQL versionné (V1__, V2__...) |
| **Multi-tenant** | Architecture où plusieurs organisations partagent la même instance avec isolation `organisation_id` |
| **iText 7** | Librairie Java pour la génération programmatique de PDFs |
| **Apache POI** | Librairie Java pour la lecture/écriture de fichiers Excel .xlsx |
| **shadcn/ui** | Collection de composants React accessibles basée sur Radix UI + Tailwind |
| **TanStack Table** | Librairie headless pour tables React avec tri, pagination et filtres |
| **Bean Validation** | Standard Java (JSR-380) pour la validation des DTOs (`@NotBlank`, `@Positive`...) |
| **`@PreUpdate` JPA** | Callback JPA exécuté automatiquement avant tout `UPDATE` sur une entité |
| **`@Scheduled`** | Annotation Spring pour les tâches planifiées (cron jobs) |
| **Refresh Token Rotation** | Chaque utilisation du refresh token génère un nouveau token et invalide l'ancien |

---

*— FIN DU DOCUMENT PRD v3.0 — Cursor AI Ready —*
