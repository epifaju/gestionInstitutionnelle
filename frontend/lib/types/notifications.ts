export type NotificationType =
  | "CONGE_SOUMIS"
  | "CONGE_VALIDE"
  | "CONGE_REJETE"
  | "FACTURE_EN_RETARD"
  | "BUDGET_ALERTE_80PCT"
  | "CONTRAT_EXPIRE_BIENTOT"
  | "BIEN_MAINTENANCE"
  | "SALAIRE_DU";

export type Notification = {
  id: string;
  type: NotificationType;
  titre: string;
  message: string;
  lien: string | null;
  lu: boolean;
  createdAt: string | null; // ISO string from backend (LocalDateTime)
};

