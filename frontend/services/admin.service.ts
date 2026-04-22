import { get, post, put } from "@/lib/api";
import type {
  AdminUserCreateRequest,
  AdminUserResponse,
  AdminUserUpdateRequest,
  AuditLogResponse,
} from "@/lib/types/admin";
import type { PageResponse } from "@/lib/types/rh";

export async function listAdminUsers(params: { page?: number; size?: number }) {
  return get<PageResponse<AdminUserResponse>>("admin/users", { params });
}

export async function createAdminUser(body: AdminUserCreateRequest) {
  return post<AdminUserResponse>("admin/users", body);
}

export async function updateAdminUser(id: string, body: AdminUserUpdateRequest) {
  return put<AdminUserResponse>(`admin/users/${id}`, body);
}

export async function listAuditLogs(params: { page?: number; size?: number }) {
  return get<PageResponse<AuditLogResponse>>("admin/audit-logs", { params });
}
