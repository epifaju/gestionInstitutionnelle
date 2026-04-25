import { del, get, post, put } from "@/lib/api";
import type {
  EmployeePayrollProfile,
  EmployeePayrollProfileRequest,
  PayrollCotisation,
  PayrollCotisationRequest,
  PayrollEmployerSettings,
  PayrollEmployerSettingsRequest,
  PayrollLegalConstant,
  PayrollLegalConstantRequest,
  PayrollRubrique,
  PayrollRubriqueRequest,
} from "@/lib/types/payroll";

export function getEmployerSettings() {
  return get<PayrollEmployerSettings>("admin/payroll/employer");
}

export function upsertEmployerSettings(body: PayrollEmployerSettingsRequest) {
  return put<PayrollEmployerSettings>("admin/payroll/employer", body);
}

export function listLegalConstants() {
  return get<PayrollLegalConstant[]>("admin/payroll/constants");
}

export function createLegalConstant(body: PayrollLegalConstantRequest) {
  return post<PayrollLegalConstant>("admin/payroll/constants", body);
}

export function deleteLegalConstant(id: string) {
  return del<void>(`admin/payroll/constants/${id}`);
}

export function listRubriques() {
  return get<PayrollRubrique[]>("admin/payroll/rubriques");
}

export function createRubrique(body: PayrollRubriqueRequest) {
  return post<PayrollRubrique>("admin/payroll/rubriques", body);
}

export function updateRubrique(id: string, body: PayrollRubriqueRequest) {
  return put<PayrollRubrique>(`admin/payroll/rubriques/${id}`, body);
}

export function deleteRubrique(id: string) {
  return del<void>(`admin/payroll/rubriques/${id}`);
}

export function listCotisations() {
  return get<PayrollCotisation[]>("admin/payroll/cotisations");
}

export function createCotisation(body: PayrollCotisationRequest) {
  return post<PayrollCotisation>("admin/payroll/cotisations", body);
}

export function updateCotisation(id: string, body: PayrollCotisationRequest) {
  return put<PayrollCotisation>(`admin/payroll/cotisations/${id}`, body);
}

export function deleteCotisation(id: string) {
  return del<void>(`admin/payroll/cotisations/${id}`);
}

export function listEmployeeProfiles() {
  return get<EmployeePayrollProfile[]>("admin/payroll/profiles");
}

export function upsertEmployeeProfile(body: EmployeePayrollProfileRequest) {
  return put<EmployeePayrollProfile>("admin/payroll/profiles", body);
}

export function deleteEmployeeProfile(id: string) {
  return del<void>(`admin/payroll/profiles/${id}`);
}

