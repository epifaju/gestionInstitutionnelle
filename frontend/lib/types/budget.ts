export type LigneBudgetResponse = {
  id: string;
  categorieId: string;
  categorieLibelle: string;
  type: string;
  montantPrevu: string;
  montantRealise: string;
  ecart: string;
  tauxExecutionPct: string;
  alerteDepassement: boolean;
};

export type BudgetResponse = {
  id: string;
  annee: number;
  statut: string;
  dateValidation: string | null;
  lignes: LigneBudgetResponse[];
  totalDepensesPrevu: string;
  totalDepensesRealise: string;
  totalRecettesPrevu: string;
  totalRecettesRealise: string;
};

export type LigneBudgetRequest = {
  categorieId: string;
  type: string;
  montantPrevu: string;
};

export type BudgetRequest = {
  annee: number;
  lignes: LigneBudgetRequest[];
  notes?: string | null;
};
