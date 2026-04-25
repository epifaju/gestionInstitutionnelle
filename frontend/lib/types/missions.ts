export type FraisResponse = {
  id: string;
  typeFrais: string;
  description: string;
  dateFrais: string;
  montant: string | number;
  devise: string;
  montantEur: string | number;
  justificatifUrl: string | null;
  statut: string;
};

export type MissionResponse = {
  id: string;
  titre: string;
  destination: string;
  paysDestination: string | null;
  objectif: string | null;
  dateDepart: string;
  dateRetour: string;
  nbJours: number;
  statut: string;
  salarieNomComplet: string | null;
  avanceDemandee: string | number | null;
  avanceVersee: string | number | null;
  totalFraisValides: string | number;
  soldeARegler: string | number;
  ordreMissionUrl: string | null;
  frais: FraisResponse[];
  createdAt: string | null;
};

export type MissionRequest = {
  titre: string;
  destination: string;
  paysDestination: string | null;
  objectif: string | null;
  dateDepart: string;
  dateRetour: string;
  avanceDemandee: number | null;
  avanceDevise: string | null;
};

export type FraisRequest = {
  typeFrais: string;
  description: string;
  dateFrais: string;
  montant: number;
  devise: string;
};

