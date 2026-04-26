import { api, get, put } from "@/lib/api";
import type {
  DocumentResponse,
  DocumentShareRequest,
  DocumentUpdateRequest,
  DocumentUploadRequest,
  PageResponse,
} from "@/lib/types/ged";

export async function searchDocuments(params: {
  page?: number;
  size?: number;
  query?: string;
  type?: string;
  tags?: string[];
  service?: string;
  expirantBientot?: boolean;
}) {
  return get<PageResponse<DocumentResponse>>("documents", { params });
}

export async function uploadDocument(req: DocumentUploadRequest, file: File) {
  const fd = new FormData();
  fd.append("document", new Blob([JSON.stringify(req)], { type: "application/json" }));
  fd.append("file", file);
  const res = await api.post<{ success: boolean; data: DocumentResponse }>("documents", fd, {
    headers: { "Content-Type": undefined },
  });
  return res.data.data;
}

export async function getDocument(id: string) {
  return get<DocumentResponse>(`documents/${id}`);
}

export async function getPresignedUrl(id: string) {
  return get<{ url: string }>(`documents/${id}/url`);
}

export async function updateDocument(id: string, body: DocumentUpdateRequest) {
  return put<DocumentResponse>(`documents/${id}`, body);
}

export async function deleteDocument(id: string) {
  await api.delete(`documents/${id}`);
}

export async function getVersions(id: string) {
  return get<DocumentResponse[]>(`documents/${id}/versions`);
}

export async function shareDocument(id: string, body: DocumentShareRequest) {
  await api.post(`documents/${id}/partager`, body);
}

export async function getExpiringSoon(nbJours = 30) {
  return get<DocumentResponse[]>("documents/expiration-prochaine", { params: { nbJours } });
}

