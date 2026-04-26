"use client";

import { useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { useAuthStore } from "@/lib/store";
import { intlLocaleTag } from "@/lib/intl-locale";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

import type {
  EmployeePayrollProfileRequest,
  PayrollCotisationRequest,
  PayrollEmployerSettingsRequest,
  PayrollLegalConstantRequest,
  PayrollRubriqueRequest,
} from "@/lib/types/payroll";
import {
  createCotisation,
  createLegalConstant,
  createRubrique,
  deleteCotisation,
  deleteEmployeeProfile,
  deleteLegalConstant,
  deleteRubrique,
  getEmployerSettings,
  listCotisations,
  listEmployeeProfiles,
  listLegalConstants,
  listRubriques,
  updateCotisation,
  updateRubrique,
  upsertEmployeeProfile,
  upsertEmployerSettings,
} from "@/services/payroll-admin.service";
import { listSalaries } from "@/services/salarie.service";

type TabKey = "employer" | "profiles" | "constants" | "rubriques" | "cotisations";

function tabButton(active: boolean) {
  return `rounded-md px-3 py-2 text-sm font-medium ${
    active ? "bg-indigo-600 text-white" : "bg-card text-foreground hover:bg-muted"
  }`;
}

function todayYmd() {
  return new Date().toISOString().slice(0, 10);
}

function CheckboxField({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (next: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 rounded-md border border-border bg-card px-3 py-2 text-sm text-card-foreground">
      <input
        type="checkbox"
        className="h-4 w-4 accent-indigo-600"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

export default function PayrollSettingsPage() {
  const t = useTranslations("RH.paieSettings");
  const tc = useTranslations("Common");
  const localeTag = intlLocaleTag(useLocale());
  const router = useRouter();
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const role = user?.role;
  const allowed = role === "ADMIN" || role === "RH";

  useEffect(() => {
    if (user && !allowed) router.replace("/dashboard");
  }, [allowed, router, user]);

  const [tab, setTab] = useState<TabKey>("employer");

  // ---------------- Employer
  const employerQ = useQuery({
    queryKey: ["payroll-admin", "employer"],
    queryFn: () => getEmployerSettings(),
    enabled: allowed,
  });

  const [employerForm, setEmployerForm] = useState<PayrollEmployerSettingsRequest>({
    raisonSociale: "",
    adresseLigne1: "",
    adresseLigne2: "",
    codePostal: "",
    ville: "",
    pays: "",
    siret: "",
    naf: "",
    urssaf: "",
    conventionCode: "",
    conventionLibelle: "",
  });

  useEffect(() => {
    if (!employerQ.data) return;
    setEmployerForm({
      raisonSociale: employerQ.data.raisonSociale ?? "",
      adresseLigne1: employerQ.data.adresseLigne1 ?? "",
      adresseLigne2: employerQ.data.adresseLigne2 ?? "",
      codePostal: employerQ.data.codePostal ?? "",
      ville: employerQ.data.ville ?? "",
      pays: employerQ.data.pays ?? "",
      siret: employerQ.data.siret ?? "",
      naf: employerQ.data.naf ?? "",
      urssaf: employerQ.data.urssaf ?? "",
      conventionCode: employerQ.data.conventionCode ?? "",
      conventionLibelle: employerQ.data.conventionLibelle ?? "",
    });
  }, [employerQ.data]);

  const employerMut = useMutation({
    mutationFn: (body: PayrollEmployerSettingsRequest) => upsertEmployerSettings(body),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      qc.invalidateQueries({ queryKey: ["payroll-admin", "employer"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  // ---------------- Constants
  const constantsQ = useQuery({
    queryKey: ["payroll-admin", "constants"],
    queryFn: () => listLegalConstants(),
    enabled: allowed,
  });

  const [constantForm, setConstantForm] = useState<PayrollLegalConstantRequest>({
    code: "",
    libelle: "",
    valeur: "",
    effectiveFrom: todayYmd(),
    effectiveTo: "",
  });

  const constantMut = useMutation({
    mutationFn: (body: PayrollLegalConstantRequest) => createLegalConstant(body),
    onSuccess: () => {
      toast.success(tc("successCreated"));
      setConstantForm({ code: "", libelle: "", valeur: "", effectiveFrom: todayYmd(), effectiveTo: "" });
      qc.invalidateQueries({ queryKey: ["payroll-admin", "constants"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const constantDeleteMut = useMutation({
    mutationFn: (id: string) => deleteLegalConstant(id),
    onSuccess: () => {
      toast.success(tc("successDeleted"));
      qc.invalidateQueries({ queryKey: ["payroll-admin", "constants"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  // ---------------- Rubriques
  const rubriquesQ = useQuery({
    queryKey: ["payroll-admin", "rubriques"],
    queryFn: () => listRubriques(),
    enabled: allowed,
  });

  const emptyRubrique: PayrollRubriqueRequest = useMemo(
    () => ({
      code: "",
      libelle: "",
      type: "EARNING",
      modeCalcul: "MONTANT_FIXE",
      baseCode: "",
      tauxSalarial: "",
      tauxPatronal: "",
      montantFixe: "",
      ordreAffichage: 100,
      actif: true,
      effectiveFrom: todayYmd(),
      effectiveTo: "",
    }),
    []
  );

  const [rubriqueDraft, setRubriqueDraft] = useState<PayrollRubriqueRequest>(emptyRubrique);
  const [rubriqueEditId, setRubriqueEditId] = useState<string | null>(null);

  const rubriqueCreateMut = useMutation({
    mutationFn: (body: PayrollRubriqueRequest) => createRubrique(body),
    onSuccess: () => {
      toast.success(tc("successCreated"));
      setRubriqueDraft(emptyRubrique);
      qc.invalidateQueries({ queryKey: ["payroll-admin", "rubriques"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const rubriqueUpdateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: PayrollRubriqueRequest }) => updateRubrique(id, body),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      setRubriqueEditId(null);
      setRubriqueDraft(emptyRubrique);
      qc.invalidateQueries({ queryKey: ["payroll-admin", "rubriques"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const rubriqueDeleteMut = useMutation({
    mutationFn: (id: string) => deleteRubrique(id),
    onSuccess: () => {
      toast.success(tc("successDeleted"));
      qc.invalidateQueries({ queryKey: ["payroll-admin", "rubriques"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  // ---------------- Cotisations
  const cotisationsQ = useQuery({
    queryKey: ["payroll-admin", "cotisations"],
    queryFn: () => listCotisations(),
    enabled: allowed,
  });

  const emptyCotisation: PayrollCotisationRequest = useMemo(
    () => ({
      code: "",
      libelle: "",
      organisme: "",
      assietteBaseCode: "BASE_BRUT",
      tauxSalarial: "",
      tauxPatronal: "",
      plafondCode: "",
      appliesCadreOnly: false,
      appliesNonCadreOnly: false,
      ordreAffichage: 100,
      actif: true,
      effectiveFrom: todayYmd(),
      effectiveTo: "",
    }),
    []
  );

  const [cotisationDraft, setCotisationDraft] = useState<PayrollCotisationRequest>(emptyCotisation);
  const [cotisationEditId, setCotisationEditId] = useState<string | null>(null);

  const cotisationCreateMut = useMutation({
    mutationFn: (body: PayrollCotisationRequest) => createCotisation(body),
    onSuccess: () => {
      toast.success(tc("successCreated"));
      setCotisationDraft(emptyCotisation);
      qc.invalidateQueries({ queryKey: ["payroll-admin", "cotisations"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const cotisationUpdateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: PayrollCotisationRequest }) => updateCotisation(id, body),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      setCotisationEditId(null);
      setCotisationDraft(emptyCotisation);
      qc.invalidateQueries({ queryKey: ["payroll-admin", "cotisations"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const cotisationDeleteMut = useMutation({
    mutationFn: (id: string) => deleteCotisation(id),
    onSuccess: () => {
      toast.success(tc("successDeleted"));
      qc.invalidateQueries({ queryKey: ["payroll-admin", "cotisations"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  // ---------------- Profiles
  const profilesQ = useQuery({
    queryKey: ["payroll-admin", "profiles"],
    queryFn: () => listEmployeeProfiles(),
    enabled: allowed,
  });

  const salariesQ = useQuery({
    queryKey: ["payroll-admin", "salaries-mini"],
    queryFn: async () => {
      const page = await listSalaries({ page: 0, size: 200 });
      return page.content ?? [];
    },
    enabled: allowed,
  });

  const [profileDraft, setProfileDraft] = useState<EmployeePayrollProfileRequest>({
    salarieId: "",
    cadre: false,
    conventionCode: "",
    conventionLibelle: "",
    tauxPas: "",
  });

  const profileMut = useMutation({
    mutationFn: (body: EmployeePayrollProfileRequest) => upsertEmployeeProfile(body),
    onSuccess: () => {
      toast.success(tc("successSaved"));
      setProfileDraft({ salarieId: "", cadre: false, conventionCode: "", conventionLibelle: "", tauxPas: "" });
      qc.invalidateQueries({ queryKey: ["payroll-admin", "profiles"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  const profileDeleteMut = useMutation({
    mutationFn: (id: string) => deleteEmployeeProfile(id),
    onSuccess: () => {
      toast.success(tc("successDeleted"));
      qc.invalidateQueries({ queryKey: ["payroll-admin", "profiles"] });
    },
    onError: () => toast.error(tc("errorGeneric")),
  });

  if (!allowed) {
    return (
      <div className="rounded-lg border border-border bg-card p-6 text-sm text-card-foreground">
        {tc("errorGeneric")}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <div className="text-sm text-muted-foreground">{localeTag}</div>
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="button" className={tabButton(tab === "employer")} onClick={() => setTab("employer")}>
          {t("tabEmployer")}
        </button>
        <button type="button" className={tabButton(tab === "profiles")} onClick={() => setTab("profiles")}>
          {t("tabProfiles")}
        </button>
        <button type="button" className={tabButton(tab === "constants")} onClick={() => setTab("constants")}>
          {t("tabConstants")}
        </button>
        <button type="button" className={tabButton(tab === "rubriques")} onClick={() => setTab("rubriques")}>
          {t("tabRubriques")}
        </button>
        <button type="button" className={tabButton(tab === "cotisations")} onClick={() => setTab("cotisations")}>
          {t("tabCotisations")}
        </button>
      </div>

      {tab === "employer" ? (
        <div className="rounded-lg border border-border bg-card p-4 text-card-foreground">
          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <Label>{t("employer.raisonSociale")}</Label>
              <Input value={employerForm.raisonSociale} onChange={(e) => setEmployerForm((f) => ({ ...f, raisonSociale: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.siret")}</Label>
              <Input value={employerForm.siret ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, siret: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.adresse1")}</Label>
              <Input value={employerForm.adresseLigne1 ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, adresseLigne1: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.adresse2")}</Label>
              <Input value={employerForm.adresseLigne2 ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, adresseLigne2: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.codePostal")}</Label>
              <Input value={employerForm.codePostal ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, codePostal: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.ville")}</Label>
              <Input value={employerForm.ville ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, ville: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.pays")}</Label>
              <Input value={employerForm.pays ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, pays: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.naf")}</Label>
              <Input value={employerForm.naf ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, naf: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.urssaf")}</Label>
              <Input value={employerForm.urssaf ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, urssaf: e.target.value }))} />
            </div>
            <div>
              <Label>{t("employer.conventionCode")}</Label>
              <Input value={employerForm.conventionCode ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, conventionCode: e.target.value }))} />
            </div>
            <div className="md:col-span-2">
              <Label>{t("employer.conventionLibelle")}</Label>
              <Input value={employerForm.conventionLibelle ?? ""} onChange={(e) => setEmployerForm((f) => ({ ...f, conventionLibelle: e.target.value }))} />
            </div>
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <Button
              type="button"
              variant="secondary"
              disabled={employerMut.isPending || employerQ.isLoading}
              onClick={() => employerMut.mutate(employerForm)}
            >
              {employerMut.isPending ? tc("loading") : tc("save")}
            </Button>
          </div>
        </div>
      ) : null}

      {tab === "constants" ? (
        <div className="space-y-4 rounded-lg border border-border bg-card p-4 text-card-foreground">
          <div className="grid gap-3 md:grid-cols-5">
            <div>
              <Label>{t("constants.code")}</Label>
              <Input value={constantForm.code} onChange={(e) => setConstantForm((f) => ({ ...f, code: e.target.value }))} />
            </div>
            <div className="md:col-span-2">
              <Label>{t("constants.libelle")}</Label>
              <Input value={constantForm.libelle} onChange={(e) => setConstantForm((f) => ({ ...f, libelle: e.target.value }))} />
            </div>
            <div>
              <Label>{t("constants.valeur")}</Label>
              <Input value={String(constantForm.valeur ?? "")} onChange={(e) => setConstantForm((f) => ({ ...f, valeur: e.target.value }))} />
            </div>
            <div>
              <Label>{t("constants.effectiveFrom")}</Label>
              <Input type="date" value={constantForm.effectiveFrom} onChange={(e) => setConstantForm((f) => ({ ...f, effectiveFrom: e.target.value }))} />
            </div>
            <div>
              <Label>{t("constants.effectiveTo")}</Label>
              <Input type="date" value={constantForm.effectiveTo ?? ""} onChange={(e) => setConstantForm((f) => ({ ...f, effectiveTo: e.target.value }))} />
            </div>
            <div className="md:col-span-5 flex justify-end">
              <Button type="button" variant="secondary" disabled={constantMut.isPending} onClick={() => constantMut.mutate(constantForm)}>
                {constantMut.isPending ? tc("loading") : tc("create")}
              </Button>
            </div>
          </div>

          <div className="rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("constants.thCode")}</TableHead>
                  <TableHead>{t("constants.thLibelle")}</TableHead>
                  <TableHead>{t("constants.thValeur")}</TableHead>
                  <TableHead>{t("constants.thFrom")}</TableHead>
                  <TableHead>{t("constants.thTo")}</TableHead>
                  <TableHead className="text-right">{tc("actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {constantsQ.isLoading ? (
                  <TableRow>
                    <TableCell colSpan={6}>{tc("loading")}</TableCell>
                  </TableRow>
                ) : (constantsQ.data?.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>{tc("emptyTable")}</TableCell>
                  </TableRow>
                ) : (
                  (constantsQ.data ?? []).map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>{c.code}</TableCell>
                      <TableCell>{c.libelle}</TableCell>
                      <TableCell>{String(c.valeur)}</TableCell>
                      <TableCell>{c.effectiveFrom}</TableCell>
                      <TableCell>{c.effectiveTo ?? tc("emDash")}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          type="button"
                          size="sm"
                          variant="destructive"
                          disabled={constantDeleteMut.isPending}
                          onClick={() => {
                            if (!confirm(`${tc("delete")} ${c.code} ?`)) return;
                            constantDeleteMut.mutate(c.id);
                          }}
                        >
                          {tc("delete")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : null}

      {tab === "rubriques" ? (
        <div className="space-y-4 rounded-lg border border-border bg-card p-4 text-card-foreground">
          <div className="grid gap-3 md:grid-cols-6">
            <div>
              <Label>{t("rubriques.code")}</Label>
              <Input value={rubriqueDraft.code} onChange={(e) => setRubriqueDraft((f) => ({ ...f, code: e.target.value }))} />
            </div>
            <div className="md:col-span-2">
              <Label>{t("rubriques.libelle")}</Label>
              <Input value={rubriqueDraft.libelle} onChange={(e) => setRubriqueDraft((f) => ({ ...f, libelle: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.type")}</Label>
              <Input value={rubriqueDraft.type} onChange={(e) => setRubriqueDraft((f) => ({ ...f, type: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.modeCalcul")}</Label>
              <Input value={rubriqueDraft.modeCalcul} onChange={(e) => setRubriqueDraft((f) => ({ ...f, modeCalcul: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.baseCode")}</Label>
              <Input value={rubriqueDraft.baseCode ?? ""} onChange={(e) => setRubriqueDraft((f) => ({ ...f, baseCode: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.montantFixe")}</Label>
              <Input value={String(rubriqueDraft.montantFixe ?? "")} onChange={(e) => setRubriqueDraft((f) => ({ ...f, montantFixe: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.tauxSal")}</Label>
              <Input value={String(rubriqueDraft.tauxSalarial ?? "")} onChange={(e) => setRubriqueDraft((f) => ({ ...f, tauxSalarial: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.tauxPat")}</Label>
              <Input value={String(rubriqueDraft.tauxPatronal ?? "")} onChange={(e) => setRubriqueDraft((f) => ({ ...f, tauxPatronal: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.ordre")}</Label>
              <Input
                type="number"
                value={rubriqueDraft.ordreAffichage ?? 0}
                onChange={(e) => setRubriqueDraft((f) => ({ ...f, ordreAffichage: Number(e.target.value) }))}
              />
            </div>
            <div>
              <CheckboxField
                label={t("rubriques.actif")}
                checked={rubriqueDraft.actif}
                onChange={(next) => setRubriqueDraft((f) => ({ ...f, actif: next }))}
              />
            </div>
            <div>
              <Label>{t("rubriques.effectiveFrom")}</Label>
              <Input type="date" value={rubriqueDraft.effectiveFrom} onChange={(e) => setRubriqueDraft((f) => ({ ...f, effectiveFrom: e.target.value }))} />
            </div>
            <div>
              <Label>{t("rubriques.effectiveTo")}</Label>
              <Input type="date" value={rubriqueDraft.effectiveTo ?? ""} onChange={(e) => setRubriqueDraft((f) => ({ ...f, effectiveTo: e.target.value }))} />
            </div>
            <div className="md:col-span-6 flex justify-end gap-2">
              {rubriqueEditId ? (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      setRubriqueEditId(null);
                      setRubriqueDraft(emptyRubrique);
                    }}
                  >
                    {tc("cancel")}
                  </Button>
                  <Button type="button" variant="secondary" disabled={rubriqueUpdateMut.isPending} onClick={() => rubriqueUpdateMut.mutate({ id: rubriqueEditId, body: rubriqueDraft })}>
                    {rubriqueUpdateMut.isPending ? tc("loading") : tc("save")}
                  </Button>
                </>
              ) : (
                <Button type="button" variant="secondary" disabled={rubriqueCreateMut.isPending} onClick={() => rubriqueCreateMut.mutate(rubriqueDraft)}>
                  {rubriqueCreateMut.isPending ? tc("loading") : tc("create")}
                </Button>
              )}
            </div>
          </div>

          <div className="rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("rubriques.thCode")}</TableHead>
                  <TableHead>{t("rubriques.thLibelle")}</TableHead>
                  <TableHead>{t("rubriques.thType")}</TableHead>
                  <TableHead>{t("rubriques.thMode")}</TableHead>
                  <TableHead>{t("rubriques.thActif")}</TableHead>
                  <TableHead className="text-right">{tc("actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rubriquesQ.isLoading ? (
                  <TableRow>
                    <TableCell colSpan={6}>{tc("loading")}</TableCell>
                  </TableRow>
                ) : (rubriquesQ.data?.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>{tc("emptyTable")}</TableCell>
                  </TableRow>
                ) : (
                  (rubriquesQ.data ?? []).map((r) => (
                    <TableRow key={r.id}>
                      <TableCell>{r.code}</TableCell>
                      <TableCell>{r.libelle}</TableCell>
                      <TableCell>{r.type}</TableCell>
                      <TableCell>{r.modeCalcul}</TableCell>
                      <TableCell>{r.actif ? "✓" : "—"}</TableCell>
                      <TableCell className="text-right">
                        <div className="inline-flex items-center justify-end gap-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setRubriqueEditId(r.id);
                              setRubriqueDraft({
                                code: r.code,
                                libelle: r.libelle,
                                type: r.type,
                                modeCalcul: r.modeCalcul,
                                baseCode: r.baseCode ?? "",
                                tauxSalarial: r.tauxSalarial ?? "",
                                tauxPatronal: r.tauxPatronal ?? "",
                                montantFixe: r.montantFixe ?? "",
                                ordreAffichage: r.ordreAffichage ?? 100,
                                actif: r.actif,
                                effectiveFrom: r.effectiveFrom,
                                effectiveTo: r.effectiveTo ?? "",
                              });
                            }}
                          >
                            {tc("modify")}
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            disabled={rubriqueDeleteMut.isPending}
                            onClick={() => {
                              if (!confirm(`${tc("delete")} ${r.code} ?`)) return;
                              rubriqueDeleteMut.mutate(r.id);
                            }}
                          >
                            {tc("delete")}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : null}

      {tab === "cotisations" ? (
        <div className="space-y-4 rounded-lg border border-border bg-card p-4 text-card-foreground">
          <div className="grid gap-3 md:grid-cols-6">
            <div>
              <Label>{t("cotisations.code")}</Label>
              <Input value={cotisationDraft.code} onChange={(e) => setCotisationDraft((f) => ({ ...f, code: e.target.value }))} />
            </div>
            <div className="md:col-span-2">
              <Label>{t("cotisations.libelle")}</Label>
              <Input value={cotisationDraft.libelle} onChange={(e) => setCotisationDraft((f) => ({ ...f, libelle: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.organisme")}</Label>
              <Input value={cotisationDraft.organisme ?? ""} onChange={(e) => setCotisationDraft((f) => ({ ...f, organisme: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.assiette")}</Label>
              <Input value={cotisationDraft.assietteBaseCode} onChange={(e) => setCotisationDraft((f) => ({ ...f, assietteBaseCode: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.plafond")}</Label>
              <Input value={cotisationDraft.plafondCode ?? ""} onChange={(e) => setCotisationDraft((f) => ({ ...f, plafondCode: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.tauxSal")}</Label>
              <Input value={String(cotisationDraft.tauxSalarial ?? "")} onChange={(e) => setCotisationDraft((f) => ({ ...f, tauxSalarial: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.tauxPat")}</Label>
              <Input value={String(cotisationDraft.tauxPatronal ?? "")} onChange={(e) => setCotisationDraft((f) => ({ ...f, tauxPatronal: e.target.value }))} />
            </div>
            <div>
              <CheckboxField
                label={t("cotisations.cadreOnly")}
                checked={cotisationDraft.appliesCadreOnly}
                onChange={(next) => setCotisationDraft((f) => ({ ...f, appliesCadreOnly: next }))}
              />
            </div>
            <div>
              <CheckboxField
                label={t("cotisations.nonCadreOnly")}
                checked={cotisationDraft.appliesNonCadreOnly}
                onChange={(next) => setCotisationDraft((f) => ({ ...f, appliesNonCadreOnly: next }))}
              />
            </div>
            <div>
              <Label>{t("cotisations.ordre")}</Label>
              <Input
                type="number"
                value={cotisationDraft.ordreAffichage ?? 0}
                onChange={(e) => setCotisationDraft((f) => ({ ...f, ordreAffichage: Number(e.target.value) }))}
              />
            </div>
            <div>
              <CheckboxField
                label={t("cotisations.actif")}
                checked={cotisationDraft.actif}
                onChange={(next) => setCotisationDraft((f) => ({ ...f, actif: next }))}
              />
            </div>
            <div>
              <Label>{t("cotisations.effectiveFrom")}</Label>
              <Input type="date" value={cotisationDraft.effectiveFrom} onChange={(e) => setCotisationDraft((f) => ({ ...f, effectiveFrom: e.target.value }))} />
            </div>
            <div>
              <Label>{t("cotisations.effectiveTo")}</Label>
              <Input type="date" value={cotisationDraft.effectiveTo ?? ""} onChange={(e) => setCotisationDraft((f) => ({ ...f, effectiveTo: e.target.value }))} />
            </div>
            <div className="md:col-span-6 flex justify-end gap-2">
              {cotisationEditId ? (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      setCotisationEditId(null);
                      setCotisationDraft(emptyCotisation);
                    }}
                  >
                    {tc("cancel")}
                  </Button>
                  <Button type="button" variant="secondary" disabled={cotisationUpdateMut.isPending} onClick={() => cotisationUpdateMut.mutate({ id: cotisationEditId, body: cotisationDraft })}>
                    {cotisationUpdateMut.isPending ? tc("loading") : tc("save")}
                  </Button>
                </>
              ) : (
                <Button type="button" variant="secondary" disabled={cotisationCreateMut.isPending} onClick={() => cotisationCreateMut.mutate(cotisationDraft)}>
                  {cotisationCreateMut.isPending ? tc("loading") : tc("create")}
                </Button>
              )}
            </div>
          </div>

          <div className="rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("cotisations.thCode")}</TableHead>
                  <TableHead>{t("cotisations.thLibelle")}</TableHead>
                  <TableHead>{t("cotisations.thAssiette")}</TableHead>
                  <TableHead>{t("cotisations.thActif")}</TableHead>
                  <TableHead className="text-right">{tc("actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cotisationsQ.isLoading ? (
                  <TableRow>
                    <TableCell colSpan={5}>{tc("loading")}</TableCell>
                  </TableRow>
                ) : (cotisationsQ.data?.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>{tc("emptyTable")}</TableCell>
                  </TableRow>
                ) : (
                  (cotisationsQ.data ?? []).map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>{c.code}</TableCell>
                      <TableCell>{c.libelle}</TableCell>
                      <TableCell>{c.assietteBaseCode}</TableCell>
                      <TableCell>{c.actif ? "✓" : "—"}</TableCell>
                      <TableCell className="text-right">
                        <div className="inline-flex items-center justify-end gap-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setCotisationEditId(c.id);
                              setCotisationDraft({
                                code: c.code,
                                libelle: c.libelle,
                                organisme: c.organisme ?? "",
                                assietteBaseCode: c.assietteBaseCode,
                                tauxSalarial: c.tauxSalarial ?? "",
                                tauxPatronal: c.tauxPatronal ?? "",
                                plafondCode: c.plafondCode ?? "",
                                appliesCadreOnly: c.appliesCadreOnly,
                                appliesNonCadreOnly: c.appliesNonCadreOnly,
                                ordreAffichage: c.ordreAffichage ?? 100,
                                actif: c.actif,
                                effectiveFrom: c.effectiveFrom,
                                effectiveTo: c.effectiveTo ?? "",
                              });
                            }}
                          >
                            {tc("modify")}
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            disabled={cotisationDeleteMut.isPending}
                            onClick={() => {
                              if (!confirm(`${tc("delete")} ${c.code} ?`)) return;
                              cotisationDeleteMut.mutate(c.id);
                            }}
                          >
                            {tc("delete")}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : null}

      {tab === "profiles" ? (
        <div className="space-y-4 rounded-lg border border-border bg-card p-4 text-card-foreground">
          <div className="grid gap-3 md:grid-cols-5">
            <div className="md:col-span-2">
              <Label>{t("profiles.salarie")}</Label>
              <select
                className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
                value={profileDraft.salarieId}
                onChange={(e) => setProfileDraft((f) => ({ ...f, salarieId: e.target.value }))}
              >
                <option value="">{t("profiles.selectSalarie")}</option>
                {(salariesQ.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nom} {s.prenom} ({s.matricule})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <CheckboxField
                label={t("profiles.cadre")}
                checked={profileDraft.cadre}
                onChange={(next) => setProfileDraft((f) => ({ ...f, cadre: next }))}
              />
            </div>
            <div>
              <Label>{t("profiles.tauxPas")}</Label>
              <Input value={String(profileDraft.tauxPas ?? "")} onChange={(e) => setProfileDraft((f) => ({ ...f, tauxPas: e.target.value }))} />
            </div>
            <div>
              <Label>{t("profiles.conventionCode")}</Label>
              <Input value={profileDraft.conventionCode ?? ""} onChange={(e) => setProfileDraft((f) => ({ ...f, conventionCode: e.target.value }))} />
            </div>
            <div className="md:col-span-4">
              <Label>{t("profiles.conventionLibelle")}</Label>
              <Input value={profileDraft.conventionLibelle ?? ""} onChange={(e) => setProfileDraft((f) => ({ ...f, conventionLibelle: e.target.value }))} />
            </div>
            <div className="md:col-span-5 flex justify-end">
              <Button type="button" variant="secondary" disabled={profileMut.isPending || !profileDraft.salarieId} onClick={() => profileMut.mutate(profileDraft)}>
                {profileMut.isPending ? tc("loading") : tc("save")}
              </Button>
            </div>
          </div>

          <div className="rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("profiles.thSalarie")}</TableHead>
                  <TableHead>{t("profiles.thCadre")}</TableHead>
                  <TableHead>{t("profiles.thTauxPas")}</TableHead>
                  <TableHead className="text-right">{tc("actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {profilesQ.isLoading ? (
                  <TableRow>
                    <TableCell colSpan={4}>{tc("loading")}</TableCell>
                  </TableRow>
                ) : (profilesQ.data?.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4}>{tc("emptyTable")}</TableCell>
                  </TableRow>
                ) : (
                  (profilesQ.data ?? []).map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{p.salarieNomComplet}</TableCell>
                      <TableCell>{p.cadre ? "✓" : "—"}</TableCell>
                      <TableCell>{p.tauxPas ?? tc("emDash")}</TableCell>
                      <TableCell className="text-right">
                        <div className="inline-flex items-center justify-end gap-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() =>
                              setProfileDraft({
                                salarieId: p.salarieId,
                                cadre: p.cadre,
                                conventionCode: p.conventionCode ?? "",
                                conventionLibelle: p.conventionLibelle ?? "",
                                tauxPas: p.tauxPas ?? "",
                              })
                            }
                          >
                            {tc("modify")}
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            disabled={profileDeleteMut.isPending}
                            onClick={() => {
                              if (!confirm(`${tc("delete")} ${p.salarieNomComplet} ?`)) return;
                              profileDeleteMut.mutate(p.id);
                            }}
                          >
                            {tc("delete")}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : null}
    </div>
  );
}

