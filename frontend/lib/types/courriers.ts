export type SensCourrier = "ENTREE" | "SORTIE";
export type StatutCourrier = "BROUILLON" | "ENREGISTRE" | "ENVOYE" | "RECU" | "CLASSE" | "ANNULE";
export type CanalCourrier = "COURRIER_POSTAL" | "EMAIL" | "MAIN_PROPRE" | "AUTRE";

export type CourrierResponse = {
  id: string;
  sens: SensCourrier;
  statut: StatutCourrier;
  reference: string | null;
  objet: string;
  expediteur: string | null;
  destinataire: string | null;
  dateCourrier: string;
  dateReception: string | null;
  dateEnvoi: string | null;
  canal: CanalCourrier;
  notes: string | null;
  documentId: string | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CourrierRequest = {
  sens: SensCourrier;
  statut?: StatutCourrier | null;
  reference?: string | null;
  objet: string;
  expediteur?: string | null;
  destinataire?: string | null;
  dateCourrier: string;
  dateReception?: string | null;
  dateEnvoi?: string | null;
  canal?: CanalCourrier | null;
  notes?: string | null;
  documentId?: string | null;
};
