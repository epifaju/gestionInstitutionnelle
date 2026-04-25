export type PayrollEmployerSettings = {
  organisationId: string;
  raisonSociale: string;
  adresseLigne1?: string | null;
  adresseLigne2?: string | null;
  codePostal?: string | null;
  ville?: string | null;
  pays?: string | null;
  siret?: string | null;
  naf?: string | null;
  urssaf?: string | null;
  conventionCode?: string | null;
  conventionLibelle?: string | null;
};

export type PayrollEmployerSettingsRequest = Omit<PayrollEmployerSettings, "organisationId">;

export type PayrollLegalConstant = {
  id: string;
  code: string;
  libelle: string;
  valeur: string | number;
  effectiveFrom: string; // YYYY-MM-DD
  effectiveTo?: string | null; // YYYY-MM-DD
};

export type PayrollLegalConstantRequest = Omit<PayrollLegalConstant, "id">;

export type PayrollRubrique = {
  id: string;
  code: string;
  libelle: string;
  type: string;
  modeCalcul: string;
  baseCode?: string | null;
  tauxSalarial?: string | number | null;
  tauxPatronal?: string | number | null;
  montantFixe?: string | number | null;
  ordreAffichage?: number | null;
  actif: boolean;
  effectiveFrom: string; // YYYY-MM-DD
  effectiveTo?: string | null; // YYYY-MM-DD
};

export type PayrollRubriqueRequest = Omit<PayrollRubrique, "id">;

export type PayrollCotisation = {
  id: string;
  code: string;
  libelle: string;
  organisme?: string | null;
  assietteBaseCode: string;
  tauxSalarial?: string | number | null;
  tauxPatronal?: string | number | null;
  plafondCode?: string | null;
  appliesCadreOnly: boolean;
  appliesNonCadreOnly: boolean;
  ordreAffichage?: number | null;
  actif: boolean;
  effectiveFrom: string; // YYYY-MM-DD
  effectiveTo?: string | null; // YYYY-MM-DD
};

export type PayrollCotisationRequest = Omit<PayrollCotisation, "id">;

export type EmployeePayrollProfile = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  cadre: boolean;
  conventionCode?: string | null;
  conventionLibelle?: string | null;
  tauxPas?: string | number | null;
};

export type EmployeePayrollProfileRequest = {
  salarieId: string;
  cadre: boolean;
  conventionCode?: string | null;
  conventionLibelle?: string | null;
  tauxPas?: string | number | null;
};

