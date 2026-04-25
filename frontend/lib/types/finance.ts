export type FactureResponse = {
  id: string;
  reference: string;
  fournisseur: string;
  dateFacture: string;
  montantHt: string | number;
  tva: string | number;
  montantTtc: string | number;
  devise: string;
  tauxChangeEur: string | number;
  montantTtcEur: string | number;
  categorieId: string | null;
  categorieLibelle: string | null;
  statut: string;
  justificatifUrl: string | null;
  montantPaye: string | number;
  montantRestant: string | number;
  createdAt: string | null;
};

export type FactureRequest = {
  fournisseur: string;
  dateFacture: string;
  montantHt: number;
  tva: number;
  devise: string;
  categorieId: string | null;
  statut: string;
  notes: string | null;
};

export type PaiementLigneRequest = { factureId: string; montant: number };

export type PaiementRequest = {
  datePaiement: string;
  montantTotal: number;
  devise: string;
  compte: string | null;
  moyenPaiement: string;
  factures: PaiementLigneRequest[];
  notes: string | null;
};

export type PaiementResponse = {
  id: string;
  datePaiement: string;
  montantTotal: string | number;
  devise: string;
  compte: string | null;
  moyenPaiement: string;
  notes: string | null;
  createdAt: string | null;
};

export type RecetteResponse = {
  id: string;
  dateRecette: string;
  montant: string | number;
  devise: string;
  tauxChangeEur: string | number;
  montantEur: string | number;
  typeRecette: string;
  description: string | null;
  modeEncaissement: string | null;
  justificatifUrl: string | null;
  categorieId?: string | null;
  categorieLibelle: string | null;
  createdAt: string | null;
};

export type RecetteRequest = {
  dateRecette: string;
  montant: number;
  devise: string;
  typeRecette: string;
  description: string | null;
  modeEncaissement: string | null;
  categorieId: string | null;
};

export type CategorieResponse = {
  id: string;
  libelle: string;
  code: string;
  type: string;
  couleur: string;
  actif: boolean;
};

export type CategorieRequest = {
  libelle: string;
  code: string;
  type: string;
  couleur?: string | null;
};

export type StatsResponse = {
  annee: number;
  mois: number;
  totalDepenses: string | number;
  totalRecettes: string | number;
  solde: string | number;
  devise: string;
  nbFactures: number;
  nbFacturesEnAttente: number;
  gainPerteChange: string | number;
  repartitionDevises: { devise: string; montantOriginal: string | number; montantEur: string | number }[];
  depensesParCategorie: { categorie: string; montant: string | number }[];
  recettesParCategorie: { categorie: string; montant: string | number }[];
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};
