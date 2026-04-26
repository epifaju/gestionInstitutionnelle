export type DocumentResponse = {
  id: string;
  titre: string;
  description: string | null;
  typeDocument: string;
  tags: string[] | null;
  nomFichier: string;
  tailleOctets: number;
  mimeType: string;
  version: number;
  documentParentId: string | null;
  visibilite: string;
  serviceCible: string | null;
  entiteLieeType: string | null;
  entiteLieeId: string | null;
  dateExpiration: string | null;
  uploadeParNomComplet: string | null;
  createdAt: string | null;
  presignedUrl: string | null;
};

export type DocumentUploadRequest = {
  titre: string;
  description?: string | null;
  typeDocument: string;
  tags?: string[] | null;
  visibilite?: string | null;
  serviceCible?: string | null;
  entiteLieeType?: string | null;
  entiteLieeId?: string | null;
  dateExpiration?: string | null;
  documentParentId?: string | null;
};

export type DocumentUpdateRequest = {
  titre: string;
  description?: string | null;
  tags?: string[] | null;
  visibilite?: string | null;
  serviceCible?: string | null;
  entiteLieeType?: string | null;
  entiteLieeId?: string | null;
  dateExpiration?: string | null;
};

export type DocumentShareRequest = {
  utilisateurId: string;
  peutModifier: boolean;
  peutSupprimer: boolean;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

