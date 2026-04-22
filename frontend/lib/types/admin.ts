export type AdminUserResponse = {
  id: string;
  email: string;
  nom: string | null;
  prenom: string | null;
  role: string;
  actif: boolean;
  createdAt: string | null;
};

export type AdminUserCreateRequest = {
  email: string;
  password: string;
  nom?: string | null;
  prenom?: string | null;
  role: string;
};

export type AdminUserUpdateRequest = {
  nom?: string | null;
  prenom?: string | null;
  role?: string | null;
  actif?: boolean | null;
  password?: string | null;
};

export type AuditLogResponse = {
  id: string;
  dateAction: string;
  action: string;
  entite: string;
  entiteId: string | null;
  avant: unknown;
  apres: unknown;
  utilisateurId: string | null;
  utilisateurEmail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
};
