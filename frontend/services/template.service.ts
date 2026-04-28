import { api, get } from "@/lib/api";

export type TemplateOutputFormat = "DOCX" | "PDF" | "HTML";

export type GeneratedDocumentDto = {
  id: string;
  templateRevisionId: string;
  subjectType: string;
  subjectId: string;
  outputDocumentId: string | null;
  outputFormat: string;
  createdBy: string | null;
  createdAt: string;
};

export async function generateFromTemplate(code: string, payload: { subjectType: string; subjectId: string; outputFormat: TemplateOutputFormat }) {
  const res = await api.post<{ success: boolean; data: GeneratedDocumentDto }>(`/templates/${encodeURIComponent(code)}/generate`, {
    ...payload,
    saveToGed: true,
    locale: null,
    overrides: null,
  });
  return res.data.data;
}

export async function listGeneratedDocuments(subjectType: string, subjectId: string) {
  return get<GeneratedDocumentDto[]>("/templates/generated", { params: { subjectType, subjectId } });
}

