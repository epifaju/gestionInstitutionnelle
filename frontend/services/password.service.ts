import { post } from "@/lib/api";

export type MessageResponse = { message: string };

export async function forgotPassword(email: string) {
  return post<MessageResponse>("auth/forgot-password", { email });
}

export async function resetPassword(token: string, newPassword: string) {
  return post<MessageResponse>("auth/reset-password", { token, newPassword });
}
