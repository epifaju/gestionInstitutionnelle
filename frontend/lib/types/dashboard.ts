export type DashboardResponse = {
  moisCourant: { annee: number; mois: number };
  kpis: {
    totalDepenses: string;
    totalRecettes: string;
    solde: string;
    effectifsActifs: number;
    congesEnCours: number;
    valeurParcMateriel: string;
  };
  evolution6Mois: { mois: string; depenses: string; recettes: string }[];
  alertesBudget: { categorie: string; tauxExecution: string; alerte: boolean }[];
  top5Fournisseurs: { fournisseur: string; montant: string }[];
  congesEnCours: { salarieNomComplet: string; dateDebut: string; dateFin: string }[];
};

export type BilanMensuelCompletResponse = {
  annee: number;
  mois: number;
  totalDepenses: string;
  totalRecettes: string;
  solde: string;
  devise: string;
  nbFactures: number;
  nbFacturesEnAttente: number;
  depensesParCategorie: { categorie: string; montant: string }[];
  recettesParCategorie: { categorie: string; montant: string }[];
  effectifsActifs: number;
  nbCongesDuMois: number;
};
