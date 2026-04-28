import { del, get, post, put } from "@/lib/api";

export type WorkflowRuleDto = {
  id: string;
  minAmountEur: number | string | null;
  maxAmountEur: number | string | null;
  levels: number;
  level1Role: string;
  level2Role: string | null;
};

export type WorkflowDefinitionDto = {
  id: string;
  processKey: string;
  enabled: boolean;
  rules: WorkflowRuleDto[];
};

export type UpsertWorkflowRuleRequest = {
  minAmountEur?: number | null;
  maxAmountEur?: number | null;
  levels: number;
  level1Role: string;
  level2Role?: string | null;
};

export type UpsertWorkflowDefinitionRequest = {
  processKey: string;
  enabled: boolean;
  rules?: UpsertWorkflowRuleRequest[];
};

export async function listWorkflowDefinitions() {
  return get<WorkflowDefinitionDto[]>("admin/workflows");
}

export async function getWorkflowDefinition(processKey: string) {
  return get<WorkflowDefinitionDto>(`admin/workflows/${processKey}`);
}

export async function upsertWorkflowDefinition(body: UpsertWorkflowDefinitionRequest) {
  return put<WorkflowDefinitionDto>("admin/workflows", body);
}

export async function createDelegation(body: {
  fromRole: string;
  toUserId: string;
  startAt: string; // ISO
  endAt: string; // ISO
  reason?: string | null;
}) {
  return post("admin/workflows/delegations", body);
}

export async function deleteDelegation(id: string) {
  return del(`admin/workflows/delegations/${id}`);
}

