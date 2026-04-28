import type { PageResponse } from "@/lib/types/rh";

export type { PageResponse };

/** Aligné sur les records Java du module contrats / échéances RH */
export type ContratResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  matricule: string;
  service: string | null;
  typeContrat: string;
  dateDebutContrat: string;
  dateFinContrat: string | null;
  dateFinPeriodeEssai: string | null;
  dureeEssaiMois: number | null;
  numeroContrat: string | null;
  intitulePoste: string | null;
  motifCdd: string | null;
  conventionCollective: string | null;
  renouvellementNumero: number | null;
  contratParentId: string | null;
  decisionFin: string | null;
  dateDecision: string | null;
  commentaireDecision: string | null;
  contratSigneUrl: string | null;
  actif: boolean;
  joursAvantFin: number | null;
  niveauUrgence: string | null;
  createdAt: string | null;
};

export type ContratRequest = {
  typeContrat: string;
  dateDebutContrat: string;
  dateFinContrat?: string | null;
  dateFinPeriodeEssai?: string | null;
  dureeEssaiMois?: number | null;
  numeroContrat?: string | null;
  intitulePoste?: string | null;
  motifCdd?: string | null;
  conventionCollective?: string | null;
};

export type RenouvellementCddRequest = {
  nouvelleDateFin: string;
  motif?: string | null;
  commentaire?: string | null;
};

export type DecisionFinCddRequest = {
  decision: "RENOUVELLEMENT" | "CDI" | "NON_RENOUVELE" | string;
  dateDecision?: string | null;
  commentaire?: string | null;
};

export type EcheanceResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  service: string | null;
  matricule: string | null;
  typeEcheance: string;
  titre: string;
  description: string | null;
  dateEcheance: string;
  statut: string;
  priorite: number | null;
  responsableNomComplet: string | null;
  dateTraitement: string | null;
  commentaireTraitement: string | null;
  traitePar: string | null;
  documentPreuveUrl: string | null;
  joursRestants: number | null;
  niveauUrgence: string | null;
  createdAt: string | null;
};

export type EcheanceRequest = {
  salarieId: string;
  contratId?: string | null;
  typeEcheance: string;
  titre: string;
  description?: string | null;
  dateEcheance: string;
  priorite?: number | null;
  responsableId?: string | null;
};

export type EcheanceDashboardResponse = {
  totalActives: number;
  critiques: number;
  urgentes: number;
  attention: number;
  finCddProchaines30j: number;
  periodeEssaiProchaines15j: number;
  visitesAPrevoir: number;
  titresExpirantBientot: number;
  formationsARenouveler: number;
  prochainesEcheances: EcheanceResponse[];
};

export type TraiterEcheanceRequest = {
  dateTraitement: string;
  commentaire: string;
};

export type VisiteMedicaleRequest = {
  typeVisite: string;
  datePlanifiee?: string | null;
  dateRealisee?: string | null;
  medecin?: string | null;
  centreMedical?: string | null;
  resultat: string;
  restrictions?: string | null;
  periodiciteMois?: number | null;
};

export type VisiteMedicaleResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  typeVisite: string;
  datePlanifiee: string | null;
  dateRealisee: string | null;
  medecin: string | null;
  centreMedical: string | null;
  statut: string;
  resultat: string | null;
  restrictions: string | null;
  prochaineVisite: string | null;
  periodiciteMois: number | null;
  compteRenduUrl: string | null;
  createdAt: string | null;
};

export type TitreSejourRequest = {
  typeDocument: string;
  numeroDocument?: string | null;
  paysEmetteur?: string | null;
  dateEmission?: string | null;
  dateExpiration: string;
  autoriteEmettrice?: string | null;
};

export type TitreSejourResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  typeDocument: string;
  numeroDocument: string | null;
  paysEmetteur: string | null;
  dateEmission: string | null;
  dateExpiration: string;
  autoriteEmettrice: string | null;
  documentUrl: string | null;
  statutRenouvellement: string | null;
  joursAvantExpiration: number | null;
  niveauUrgence: string | null;
  createdAt: string | null;
};

export type FormationObligatoireRequest = {
  intitule: string;
  typeFormation: string;
  organisme?: string | null;
  dateRealisation?: string | null;
  dateExpiration: string;
  periodiciteMois?: number | null;
  numeroCertificat?: string | null;
  cout?: string | number | null;
};

export type FormationObligatoireResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  intitule: string;
  typeFormation: string;
  organisme: string | null;
  dateRealisation: string | null;
  dateExpiration: string;
  periodiciteMois: number | null;
  numeroCertificat: string | null;
  certificatUrl: string | null;
  statut: string;
  joursAvantExpiration: number | null;
  cout: string | number | null;
  createdAt: string | null;
};
