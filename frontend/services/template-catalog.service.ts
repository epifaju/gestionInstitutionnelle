import { get } from "@/lib/api";

export type TemplateOutputFormat = "DOCX" | "PDF" | "HTML";
export type TemplateFormat = "DOCX" | "HTML";
export type TemplateCategory = "MISSION" | "FRAIS" | "CONTRAT" | "COURRIER" | "PV";

export type TemplateAvailableDto = {
  code: string;
  label: string;
  category: TemplateCategory;
  format: TemplateFormat;
  allowedOutputs: TemplateOutputFormat[];
  hasRevision: boolean;
  latestVersion: number | null;
};

export async function listAvailableTemplates(subjectType: string) {
  return get<TemplateAvailableDto[]>("/templates/catalog", { params: { subjectType } });
}

