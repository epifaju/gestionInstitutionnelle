export type BienResponse = {
  id: string;
  codeInventaire: string;
  libelle: string;
  categorie: string;
  codeCategorie: string;
  dateAcquisition: string | null;
  valeurAchat: string;
  devise: string;
  localisation: string | null;
  etat: string;
  responsableNomComplet: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type BienRequest = {
  libelle: string;
  categorie: string;
  codeCategorie: string;
  dateAcquisition?: string | null;
  valeurAchat: string;
  devise?: string | null;
  localisation?: string | null;
  etat: string;
  responsableId?: string | null;
  description?: string | null;
};

export type MouvementResponse = {
  id: string;
  typeMouvement: string;
  champModifie: string | null;
  ancienneValeur: string | null;
  nouvelleValeur: string | null;
  motif: string | null;
  auteurNomComplet: string | null;
  dateMouvement: string | null;
};

export type StatsInventaireResponse = {
  valeurTotaleParc: string;
  repartitionEtat: { etat: string; count: number }[];
  repartitionCategorie: { categorie: string; count: number }[];
};
