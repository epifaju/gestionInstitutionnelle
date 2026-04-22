export type SalaireActuel = {
  montantBrut: string;
  montantNet: string;
  devise: string;
  dateDebut: string;
};

export type DroitsCongesDto = {
  annee: number;
  joursDroit: string;
  joursPris: string;
  joursRestants: string;
};

export type SalarieResponse = {
  id: string;
  matricule: string;
  nom: string;
  prenom: string;
  email: string | null;
  telephone: string | null;
  poste: string;
  service: string;
  dateEmbauche: string;
  typeContrat: string;
  statut: string;
  nationalite: string | null;
  adresse?: string | null;
  salaireActuel: SalaireActuel | null;
  droitsConges: DroitsCongesDto | null;
  createdAt: string | null;
};

export type SalarieRequest = {
  nom: string;
  prenom: string;
  email?: string | null;
  telephone?: string | null;
  poste: string;
  service: string;
  dateEmbauche: string;
  typeContrat: string;
  nationalite?: string | null;
  adresse?: string | null;
  montantBrut: number;
  montantNet: number;
  devise: string;
};

export type CongeRequest = {
  salarieId: string;
  typeConge: string;
  dateDebut: string;
  dateFin: string;
  commentaire?: string | null;
};

export type CongeResponse = {
  id: string;
  salarieId: string;
  salarieNomComplet: string;
  service: string;
  typeConge: string;
  dateDebut: string;
  dateFin: string;
  nbJours: string;
  statut: string;
  valideurNomComplet: string | null;
  dateValidation: string | null;
  motifRejet: string | null;
  commentaire: string | null;
  createdAt: string | null;
};

export type CongeValidationRequest = {
  motifRejet: string;
};

export type PaieResponse = {
  id: string;
  salarieNomComplet: string;
  matricule: string;
  mois: number;
  annee: number;
  montant: string;
  devise: string;
  datePaiement: string | null;
  modePaiement: string | null;
  statut: string;
};

export type MarquerPayeRequest = {
  datePaiement: string;
  modePaiement: string;
  notes?: string | null;
};

export type HistoriqueSalaireResponse = {
  montantBrut: string;
  montantNet: string;
  devise: string;
  dateDebut: string;
  dateFin: string | null;
};

export type DocumentUrlResponse = {
  nomFichier: string;
  url: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};
