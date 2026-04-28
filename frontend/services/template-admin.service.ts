import { api } from "@/lib/api";

export type TemplateCategory = "MISSION" | "FRAIS" | "CONTRAT" | "COURRIER" | "PV";
export type TemplateFormat = "DOCX" | "HTML";
export type TemplateStatus = "DRAFT" | "ACTIVE" | "DISABLED";

export type TemplateDefinitionDto = {
  id: string;
  code: string;
  label: string;
  category: TemplateCategory;
  format: TemplateFormat;
  status: TemplateStatus;
  defaultLocale: string | null;
  createdAt: string;
  updatedAt: string | null;
};

export type TemplateRevisionDto = {
  id: string;
  version: number;
  contentDocumentId: string | null;
  contentObjectName: string | null;
  contentMime: string | null;
  checksum: string | null;
  comment: string | null;
  createdBy: string | null;
  createdAt: string;
};

export async function listTemplatesAdmin(): Promise<TemplateDefinitionDto[]> {
  const res = await api.get<{ success: boolean; data: TemplateDefinitionDto[] }>("/templates/admin");
  return res.data.data ?? [];
}

export async function createTemplateAdmin(payload: {
  code: string;
  label: string;
  category: TemplateCategory;
  format: TemplateFormat;
  defaultLocale?: string | null;
}): Promise<TemplateDefinitionDto> {
  const res = await api.post<{ success: boolean; data: TemplateDefinitionDto }>("/templates/admin", payload);
  return res.data.data;
}

export async function updateTemplateAdmin(
  id: string,
  payload: { label: string; status: TemplateStatus; defaultLocale?: string | null }
): Promise<TemplateDefinitionDto> {
  const res = await api.put<{ success: boolean; data: TemplateDefinitionDto }>(`/templates/admin/${id}`, payload);
  return res.data.data;
}

export async function listTemplateRevisionsAdmin(templateId: string): Promise<TemplateRevisionDto[]> {
  const res = await api.get<{ success: boolean; data: TemplateRevisionDto[] }>(`/templates/admin/${templateId}/revisions`);
  return res.data.data ?? [];
}

export async function uploadTemplateRevisionAdmin(templateId: string, file: File, comment?: string | null): Promise<TemplateRevisionDto> {
  const form = new FormData();
  form.append("file", file);
  if (comment) form.append("comment", comment);
  const res = await api.post<{ success: boolean; data: TemplateRevisionDto }>(`/templates/admin/${templateId}/revisions`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return res.data.data;
}

