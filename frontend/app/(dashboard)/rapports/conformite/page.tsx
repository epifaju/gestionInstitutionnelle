"use client";

import { useEffect, useMemo, useState } from "react";
import { useAuthStore } from "@/lib/store";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { listMissions } from "@/services/missions.service";
import type { MissionResponse } from "@/lib/types/missions";
import { listSalaries } from "@/services/salarie.service";
import type { SalarieResponse } from "@/lib/types/rh";
import { listPaieOrganisation } from "@/services/paie.service";
import { getBudget } from "@/services/budget.service";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ExportButton } from "@/components/exports/ExportButton";
import { MesExportsRecents } from "@/components/exports/MesExportsRecents";
import {
  exportBudgetExcel,
  exportBudgetPdf,
  exportEtatPaieExcel,
  exportEtatPaiePdf,
  exportJournalAuditCsv,
  exportJournalAuditExcel,
  exportJournalAuditPdf,
  exportNoteFrais,
  getConfig,
  updateConfig,
  uploadCachet,
  uploadLogo,
  uploadSignatureDg,
  type ConfigExport,
} from "@/services/export-conformite.service";
import { toast } from "sonner";
import { FileSpreadsheet, FileText, ListChecks } from "lucide-react";

function uniq<T>(arr: T[]) {
  return Array.from(new Set(arr));
}

function prevMonthYear() {
  const d = new Date();
  d.setMonth(d.getMonth() - 1);
  return { annee: d.getFullYear(), mois: d.getMonth() + 1 };
}

const BUDGET_STATUTS = ["BROUILLON", "VALIDE", "CLOTURE", "ARCHIVE"] as const;

function isBudgetStatut(v: string): v is (typeof BUDGET_STATUTS)[number] {
  return (BUDGET_STATUTS as readonly string[]).includes(v);
}

export default function ExportsConformitePage() {
  const t = useTranslations("ExportsConformite");
  const user = useAuthStore((s) => s.user);
  const role = user?.role ?? null;
  const isAdmin = role === "ADMIN";
  const isFin = role === "FINANCIER";
  const isRh = role === "RH";

  const showNoteFrais = isAdmin || isFin || isRh;
  const showPaie = isAdmin || isFin || isRh;
  const showBudget = isAdmin || isFin;

  const [missionSearch, setMissionSearch] = useState("");
  const [missionId, setMissionId] = useState<string | null>(null);

  const missionsQuery = useQuery({
    queryKey: ["missions", "list", "exports-conformite"],
    queryFn: () => listMissions({ page: 0, size: 50 }),
    enabled: showNoteFrais,
    staleTime: 30_000,
  });

  const missions = useMemo(() => missionsQuery.data?.content ?? [], [missionsQuery.data]);
  const filteredMissions = useMemo(() => {
    const q = missionSearch.trim().toLowerCase();
    if (!q) return missions;
    return missions.filter((m) => `${m.titre} ${m.salarieNomComplet ?? ""} ${m.destination}`.toLowerCase().includes(q));
  }, [missions, missionSearch]);

  const selectedMission = useMemo(() => missions.find((m) => m.id === missionId) ?? null, [missions, missionId]);

  const def = useMemo(() => prevMonthYear(), []);
  const [paieAnnee, setPaieAnnee] = useState(def.annee);
  const [paieMois, setPaieMois] = useState(def.mois);
  const [service, setService] = useState<string>("__ALL__");

  const salariesQuery = useQuery({
    queryKey: ["rh", "salaries", "services", "exports-conformite"],
    queryFn: async () => {
      const page = await listSalaries({ page: 0, size: 200 });
      return page.content;
    },
    enabled: showPaie,
    staleTime: 60_000,
  });

  const services = useMemo(() => {
    const s = (salariesQuery.data ?? []).map((x: SalarieResponse) => x.service).filter(Boolean);
    return uniq(s).sort((a, b) => a.localeCompare(b));
  }, [salariesQuery.data]);

  const paieCountQuery = useQuery({
    queryKey: ["rh", "paie", "count", paieAnnee, paieMois, service],
    queryFn: async () => {
      const page = await listPaieOrganisation(paieAnnee, { page: 0, size: 1 });
      return page.totalElements ?? 0;
    },
    enabled: showPaie,
    staleTime: 10_000,
  });

  const [budgetYear, setBudgetYear] = useState(new Date().getFullYear());
  const budgetQuery = useQuery({
    queryKey: ["budget", budgetYear, "exports-conformite"],
    queryFn: () => getBudget(budgetYear),
    enabled: showBudget,
    staleTime: 30_000,
  });

  const [auditDebut, setAuditDebut] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
  });
  const [auditFin, setAuditFin] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  });
  const [auditEntite, setAuditEntite] = useState<string>("");
  const [auditAction, setAuditAction] = useState<string>("");
  const [auditUserId, setAuditUserId] = useState<string>("");

  const configQuery = useQuery({
    queryKey: ["exports", "config"],
    queryFn: () => getConfig(),
    enabled: isAdmin,
    staleTime: 30_000,
  });
  const [cfg, setCfg] = useState<ConfigExport | null>(null);
  useEffect(() => {
    if (configQuery.data) setCfg(configQuery.data);
  }, [configQuery.data]);

  async function saveConfig() {
    if (!cfg) return;
    const saved = await updateConfig(cfg);
    setCfg(saved);
    toast.success(t("config.toastSaved"));
  }

  async function upload(kind: "logo" | "cachet" | "signature", file: File) {
    try {
      if (kind === "logo") await uploadLogo(file);
      if (kind === "cachet") await uploadCachet(file);
      if (kind === "signature") await uploadSignatureDg(file);
      toast.success(t("config.toastUploaded"));
      await configQuery.refetch();
    } catch {
      toast.error(t("config.toastUploadError"));
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {showNoteFrais ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>📄 {t("noteFrais.title")}</span>
                  <Badge>PDF</Badge>
                </CardTitle>
                <CardDescription>{t("noteFrais.description")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {missionsQuery.isError ? (
                  <p className="text-sm text-muted-foreground">{t("noteFrais.missionsUnavailable")}</p>
                ) : (
                  <>
                    <div className="grid gap-2">
                      <Label>{t("noteFrais.searchLabel")}</Label>
                      <Input
                        value={missionSearch}
                        onChange={(e) => setMissionSearch(e.target.value)}
                        placeholder={t("noteFrais.searchPlaceholder")}
                      />
                    </div>
                    <div className="grid gap-2">
                      <Label>{t("noteFrais.missionLabel")}</Label>
                      <select
                        className="h-10 rounded-md border bg-background px-3 text-sm"
                        value={missionId ?? ""}
                        onChange={(e) => setMissionId(e.target.value || null)}
                      >
                        <option value="">{t("noteFrais.selectPlaceholder")}</option>
                        {filteredMissions.map((m: MissionResponse) => (
                          <option key={m.id} value={m.id}>
                            {m.titre} — {m.salarieNomComplet ?? "—"} — {m.destination} ({m.dateDepart} → {m.dateRetour})
                          </option>
                        ))}
                      </select>
                    </div>

                    {selectedMission ? (
                      <Card className="bg-muted/30" size="sm">
                        <CardContent className="grid gap-2">
                          <div className="flex flex-wrap gap-2 text-sm">
                            <Badge variant="secondary">{selectedMission.salarieNomComplet ?? "—"}</Badge>
                            <Badge variant="outline">{selectedMission.destination}</Badge>
                            <Badge variant="outline">
                              {selectedMission.dateDepart} → {selectedMission.dateRetour}
                            </Badge>
                            <Badge variant="outline">
                              {t("noteFrais.fraisCount", { count: selectedMission.frais?.length ?? 0 })}
                            </Badge>
                          </div>
                          <div className="text-sm text-muted-foreground">
                            {t("noteFrais.summaryLine", {
                              avance: String(selectedMission.avanceVersee ?? "0"),
                              total: String(selectedMission.totalFraisValides ?? "0"),
                              solde: String(selectedMission.soldeARegler ?? "0"),
                            })}
                          </div>
                        </CardContent>
                      </Card>
                    ) : null}

                    <ExportButton
                      label={t("noteFrais.generateBtn")}
                      icon={<FileText className="mr-2 h-4 w-4" />}
                      onExport={() => exportNoteFrais(missionId!)}
                      variant="pdf"
                      disabled={!missionId}
                    />
                  </>
                )}
              </CardContent>
            </Card>
          ) : null}

          {showPaie ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>💰 {t("paie.title")}</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                </CardTitle>
                <CardDescription>{t("paie.description")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="grid gap-2">
                    <Label>{t("paie.year")}</Label>
                    <Input type="number" value={paieAnnee} onChange={(e) => setPaieAnnee(Number(e.target.value))} />
                  </div>
                  <div className="grid gap-2">
                    <Label>{t("paie.month")}</Label>
                    <Input type="number" min={1} max={12} value={paieMois} onChange={(e) => setPaieMois(Number(e.target.value))} />
                  </div>
                  <div className="grid gap-2">
                    <Label>{t("paie.service")}</Label>
                    <select className="h-10 rounded-md border bg-background px-3 text-sm" value={service} onChange={(e) => setService(e.target.value)}>
                      <option value="__ALL__">{t("paie.allServices")}</option>
                      {services.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <p className="text-sm text-muted-foreground">
                  {paieCountQuery.isLoading
                    ? t("paie.countLoading")
                    : t("paie.countLine", {
                        count: paieCountQuery.data ?? 0,
                        mois: String(paieMois).padStart(2, "0"),
                        annee: paieAnnee,
                      })}
                  {service !== "__ALL__" ? ` — ${service}` : ""}
                </p>

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label={t("paie.exportPdf")}
                    icon={<FileText className="mr-2 h-4 w-4" />}
                    variant="pdf"
                    onExport={() =>
                      exportEtatPaiePdf({ annee: paieAnnee, mois: paieMois, service: service === "__ALL__" ? null : service })
                    }
                  />
                  <ExportButton
                    label={t("paie.exportExcel")}
                    icon={<FileSpreadsheet className="mr-2 h-4 w-4" />}
                    variant="excel"
                    onExport={() =>
                      exportEtatPaieExcel({ annee: paieAnnee, mois: paieMois, service: service === "__ALL__" ? null : service })
                    }
                  />
                </div>
              </CardContent>
            </Card>
          ) : null}

          {showBudget ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>📊 {t("budget.title")}</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                </CardTitle>
                <CardDescription>{t("budget.description")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-2 md:max-w-xs">
                  <Label>{t("budget.year")}</Label>
                  <Input type="number" value={budgetYear} onChange={(e) => setBudgetYear(Number(e.target.value))} />
                </div>

                {budgetQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">{t("budget.loading")}</p>
                ) : budgetQuery.data ? (
                  <p className="text-sm text-muted-foreground">
                    {t("budget.statutLabel")}{" "}
                    <Badge variant="outline">
                      {isBudgetStatut(budgetQuery.data.statut)
                        ? t(`budget.statuts.${budgetQuery.data.statut}`)
                        : budgetQuery.data.statut}
                    </Badge>
                  </p>
                ) : (
                  <p className="text-sm text-muted-foreground">{t("budget.empty")}</p>
                )}

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label={t("budget.exportPdf")}
                    icon={<FileText className="mr-2 h-4 w-4" />}
                    variant="pdf"
                    onExport={() => exportBudgetPdf(budgetYear)}
                  />
                  <ExportButton
                    label={t("budget.exportExcel")}
                    icon={<FileSpreadsheet className="mr-2 h-4 w-4" />}
                    variant="excel"
                    onExport={() => exportBudgetExcel(budgetYear)}
                  />
                </div>
              </CardContent>
            </Card>
          ) : null}

          {isAdmin ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>🔍 {t("audit.title")}</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                  <Badge variant="outline">CSV</Badge>
                </CardTitle>
                <CardDescription>{t("audit.description")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="grid gap-2">
                    <Label>{t("audit.dateDebut")}</Label>
                    <Input type="date" value={auditDebut} onChange={(e) => setAuditDebut(e.target.value)} />
                  </div>
                  <div className="grid gap-2">
                    <Label>{t("audit.dateFin")}</Label>
                    <Input type="date" value={auditFin} onChange={(e) => setAuditFin(e.target.value)} />
                  </div>
                </div>
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="grid gap-2">
                    <Label>{t("audit.entite")}</Label>
                    <Input value={auditEntite} onChange={(e) => setAuditEntite(e.target.value)} placeholder={t("audit.entitePlaceholder")} />
                  </div>
                  <div className="grid gap-2">
                    <Label>{t("audit.action")}</Label>
                    <Input value={auditAction} onChange={(e) => setAuditAction(e.target.value)} placeholder={t("audit.actionPlaceholder")} />
                  </div>
                  <div className="grid gap-2">
                    <Label>{t("audit.userId")}</Label>
                    <Input value={auditUserId} onChange={(e) => setAuditUserId(e.target.value)} placeholder={t("audit.userIdPlaceholder")} />
                  </div>
                </div>

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label={t("audit.pdf")}
                    icon={<FileText className="mr-2 h-4 w-4" />}
                    variant="pdf"
                    onExport={() =>
                      exportJournalAuditPdf({
                        dateDebut: auditDebut,
                        dateFin: auditFin,
                        entite: auditEntite || null,
                        action: auditAction || null,
                        utilisateurId: auditUserId || null,
                      })
                    }
                  />
                  <ExportButton
                    label={t("audit.excel")}
                    icon={<FileSpreadsheet className="mr-2 h-4 w-4" />}
                    variant="excel"
                    onExport={() =>
                      exportJournalAuditExcel({
                        dateDebut: auditDebut,
                        dateFin: auditFin,
                        entite: auditEntite || null,
                        action: auditAction || null,
                        utilisateurId: auditUserId || null,
                      })
                    }
                  />
                  <ExportButton
                    label={t("audit.csv")}
                    icon={<ListChecks className="mr-2 h-4 w-4" />}
                    variant="csv"
                    onExport={() =>
                      exportJournalAuditCsv({
                        dateDebut: auditDebut,
                        dateFin: auditFin,
                        entite: auditEntite || null,
                        action: auditAction || null,
                        utilisateurId: auditUserId || null,
                      })
                    }
                  />
                </div>
              </CardContent>
            </Card>
          ) : null}

          {isAdmin ? (
            <Card>
              <CardHeader>
                <CardTitle>⚙️ {t("config.title")}</CardTitle>
                <CardDescription>{t("config.description")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {!cfg ? (
                  <p className="text-sm text-muted-foreground">{t("config.loading")}</p>
                ) : (
                  <>
                    <div className="grid gap-3 md:grid-cols-2">
                      <div className="grid gap-2">
                        <Label>{t("config.piedPage")}</Label>
                        <Input value={cfg.piedPageMention ?? ""} onChange={(e) => setCfg({ ...cfg, piedPageMention: e.target.value })} />
                      </div>
                      <div className="grid gap-2">
                        <Label>{t("config.couleur")}</Label>
                        <Input
                          value={cfg.couleurPrincipale ?? ""}
                          onChange={(e) => setCfg({ ...cfg, couleurPrincipale: e.target.value })}
                          placeholder="#1B3A5C"
                        />
                      </div>
                      <div className="grid gap-2">
                        <Label>{t("config.seuilPdf")}</Label>
                        <Input
                          type="number"
                          value={cfg.seuilLignesSyncPdf ?? 500}
                          onChange={(e) => setCfg({ ...cfg, seuilLignesSyncPdf: Number(e.target.value) })}
                        />
                      </div>
                      <div className="grid gap-2">
                        <Label>{t("config.seuilExcel")}</Label>
                        <Input
                          type="number"
                          value={cfg.seuilLignesSyncExcel ?? 5000}
                          onChange={(e) => setCfg({ ...cfg, seuilLignesSyncExcel: Number(e.target.value) })}
                        />
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <input
                        id="wm"
                        type="checkbox"
                        checked={!!cfg.watermarkActif}
                        onChange={(e) => setCfg({ ...cfg, watermarkActif: e.target.checked })}
                      />
                      <Label htmlFor="wm">{t("config.watermarkActive")}</Label>
                    </div>
                    {cfg.watermarkActif ? (
                      <div className="grid gap-2 md:max-w-sm">
                        <Label>{t("config.watermarkText")}</Label>
                        <Input value={cfg.watermarkTexte ?? ""} onChange={(e) => setCfg({ ...cfg, watermarkTexte: e.target.value })} />
                      </div>
                    ) : null}

                    <div className="grid gap-3 md:grid-cols-3">
                      <div className="grid gap-2">
                        <Label>{t("config.uploadLogo")}</Label>
                        <Input type="file" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && upload("logo", e.target.files[0])} />
                      </div>
                      <div className="grid gap-2">
                        <Label>{t("config.uploadCachet")}</Label>
                        <Input type="file" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && upload("cachet", e.target.files[0])} />
                      </div>
                      <div className="grid gap-2">
                        <Label>{t("config.uploadSignature")}</Label>
                        <Input
                          type="file"
                          accept="image/png,image/jpeg"
                          onChange={(e) => e.target.files?.[0] && upload("signature", e.target.files[0])}
                        />
                      </div>
                    </div>

                    <div className="flex justify-end">
                      <Button type="button" onClick={() => saveConfig()}>
                        {t("config.save")}
                      </Button>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          ) : null}
        </div>

        <div className="lg:col-span-1">
          <MesExportsRecents />
        </div>
      </div>
    </div>
  );
}
