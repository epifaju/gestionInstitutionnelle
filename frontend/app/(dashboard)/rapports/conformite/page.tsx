"use client";

import { useEffect, useMemo, useState } from "react";
import { useAuthStore } from "@/lib/store";
import { useQuery } from "@tanstack/react-query";
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

export default function ExportsConformitePage() {
  const user = useAuthStore((s) => s.user);
  const role = user?.role ?? null;
  const isAdmin = role === "ADMIN";
  const isFin = role === "FINANCIER";
  const isRh = role === "RH";

  const showNoteFrais = isAdmin || isFin || isRh;
  const showPaie = isAdmin || isFin || isRh;
  const showBudget = isAdmin || isFin;

  // ── NOTE DE FRAIS ──────────────────────────────────────────────
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

  // ── ÉTAT DE PAIE ───────────────────────────────────────────────
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

  // ── BUDGET ─────────────────────────────────────────────────────
  const [budgetYear, setBudgetYear] = useState(new Date().getFullYear());
  const budgetQuery = useQuery({
    queryKey: ["budget", budgetYear, "exports-conformite"],
    queryFn: () => getBudget(budgetYear),
    enabled: showBudget,
    staleTime: 30_000,
  });

  // ── JOURNAL D'AUDIT ────────────────────────────────────────────
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

  // ── CONFIG ─────────────────────────────────────────────────────
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
    toast.success("Configuration enregistrée.");
  }

  async function upload(kind: "logo" | "cachet" | "signature", file: File) {
    try {
      if (kind === "logo") await uploadLogo(file);
      if (kind === "cachet") await uploadCachet(file);
      if (kind === "signature") await uploadSignatureDg(file);
      toast.success("Fichier uploadé.");
      await configQuery.refetch();
    } catch {
      toast.error("Upload impossible.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">Exports Conformité</h1>
        <p className="text-sm text-muted-foreground">Notes de frais, états de paie, budget, journal d’audit.</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {/* SECTION NOTE DE FRAIS */}
          {showNoteFrais ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>📄 Note de Frais Mission</span>
                  <Badge>PDF</Badge>
                </CardTitle>
                <CardDescription>Génère une note de frais PDF prête à transmettre.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {missionsQuery.isError ? (
                  <p className="text-sm text-muted-foreground">Module Missions non installé ou inaccessible.</p>
                ) : (
                  <>
                    <div className="grid gap-2">
                      <Label>Rechercher une mission</Label>
                      <Input value={missionSearch} onChange={(e) => setMissionSearch(e.target.value)} placeholder="Titre, salarié, destination..." />
                    </div>
                    <div className="grid gap-2">
                      <Label>Mission</Label>
                      <select
                        className="h-10 rounded-md border bg-background px-3 text-sm"
                        value={missionId ?? ""}
                        onChange={(e) => setMissionId(e.target.value || null)}
                      >
                        <option value="">Sélectionner...</option>
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
                            <Badge variant="outline">{selectedMission.frais?.length ?? 0} frais</Badge>
                          </div>
                          <div className="text-sm text-muted-foreground">
                            Avance: {String(selectedMission.avanceVersee ?? "0")} — Total frais validés: {String(selectedMission.totalFraisValides ?? "0")} — Solde:{" "}
                            {String(selectedMission.soldeARegler ?? "0")}
                          </div>
                        </CardContent>
                      </Card>
                    ) : null}

                    <ExportButton
                      label="Générer la note de frais PDF"
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

          {/* SECTION PAIE */}
          {showPaie ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>💰 État de Paie</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                </CardTitle>
                <CardDescription>Exporte l’état de paie mensuel.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="grid gap-2">
                    <Label>Année</Label>
                    <Input type="number" value={paieAnnee} onChange={(e) => setPaieAnnee(Number(e.target.value))} />
                  </div>
                  <div className="grid gap-2">
                    <Label>Mois</Label>
                    <Input type="number" min={1} max={12} value={paieMois} onChange={(e) => setPaieMois(Number(e.target.value))} />
                  </div>
                  <div className="grid gap-2">
                    <Label>Service</Label>
                    <select className="h-10 rounded-md border bg-background px-3 text-sm" value={service} onChange={(e) => setService(e.target.value)}>
                      <option value="__ALL__">Tous les services</option>
                      {services.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <p className="text-sm text-muted-foreground">
                  {paieCountQuery.isLoading ? "Chargement..." : `${paieCountQuery.data ?? 0} salariés concernés en ${String(paieMois).padStart(2, "0")}/${paieAnnee}`}
                  {service !== "__ALL__" ? ` — ${service}` : ""}
                </p>

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label="Exporter PDF"
                    icon={<FileText className="mr-2 h-4 w-4" />}
                    variant="pdf"
                    onExport={() =>
                      exportEtatPaiePdf({ annee: paieAnnee, mois: paieMois, service: service === "__ALL__" ? null : service })
                    }
                  />
                  <ExportButton
                    label="Exporter Excel"
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

          {/* SECTION BUDGET */}
          {showBudget ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>📊 Budget Prévisionnel</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                </CardTitle>
                <CardDescription>Budget prévu vs réalisé.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-2 md:max-w-xs">
                  <Label>Année</Label>
                  <Input type="number" value={budgetYear} onChange={(e) => setBudgetYear(Number(e.target.value))} />
                </div>

                {budgetQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">Chargement budget…</p>
                ) : budgetQuery.data ? (
                  <p className="text-sm text-muted-foreground">
                    Statut: <Badge variant="outline">{budgetQuery.data.statut}</Badge>
                  </p>
                ) : (
                  <p className="text-sm text-muted-foreground">Aucun budget pour cette année.</p>
                )}

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label="Exporter PDF"
                    icon={<FileText className="mr-2 h-4 w-4" />}
                    variant="pdf"
                    onExport={() => exportBudgetPdf(budgetYear)}
                  />
                  <ExportButton
                    label="Exporter Excel"
                    icon={<FileSpreadsheet className="mr-2 h-4 w-4" />}
                    variant="excel"
                    onExport={() => exportBudgetExcel(budgetYear)}
                  />
                </div>
              </CardContent>
            </Card>
          ) : null}

          {/* SECTION JOURNAL D'AUDIT */}
          {isAdmin ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <span>🔍 Journal d’Audit</span>
                  <Badge>PDF</Badge>
                  <Badge variant="secondary">Excel</Badge>
                  <Badge variant="outline">CSV</Badge>
                </CardTitle>
                <CardDescription>Exporter les événements d’audit (ADMIN uniquement).</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="grid gap-2">
                    <Label>Date début</Label>
                    <Input type="date" value={auditDebut} onChange={(e) => setAuditDebut(e.target.value)} />
                  </div>
                  <div className="grid gap-2">
                    <Label>Date fin</Label>
                    <Input type="date" value={auditFin} onChange={(e) => setAuditFin(e.target.value)} />
                  </div>
                </div>
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="grid gap-2">
                    <Label>Entité (optionnel)</Label>
                    <Input value={auditEntite} onChange={(e) => setAuditEntite(e.target.value)} placeholder="Facture, Salarie..." />
                  </div>
                  <div className="grid gap-2">
                    <Label>Action (optionnel)</Label>
                    <Input value={auditAction} onChange={(e) => setAuditAction(e.target.value)} placeholder="CREATE, UPDATE..." />
                  </div>
                  <div className="grid gap-2">
                    <Label>UtilisateurId (optionnel)</Label>
                    <Input value={auditUserId} onChange={(e) => setAuditUserId(e.target.value)} placeholder="UUID" />
                  </div>
                </div>

                <div className="flex flex-wrap gap-2">
                  <ExportButton
                    label="PDF"
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
                    label="Excel"
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
                    label="Export CSV brut"
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

          {/* SECTION CONFIG */}
          {isAdmin ? (
            <Card>
              <CardHeader>
                <CardTitle>⚙️ Configuration des exports</CardTitle>
                <CardDescription>Paramètres par organisation.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {!cfg ? (
                  <p className="text-sm text-muted-foreground">Chargement…</p>
                ) : (
                  <>
                    <div className="grid gap-3 md:grid-cols-2">
                      <div className="grid gap-2">
                        <Label>Mention pied de page</Label>
                        <Input value={cfg.piedPageMention ?? ""} onChange={(e) => setCfg({ ...cfg, piedPageMention: e.target.value })} />
                      </div>
                      <div className="grid gap-2">
                        <Label>Couleur principale (hex)</Label>
                        <Input value={cfg.couleurPrincipale ?? ""} onChange={(e) => setCfg({ ...cfg, couleurPrincipale: e.target.value })} placeholder="#1B3A5C" />
                      </div>
                      <div className="grid gap-2">
                        <Label>Seuil sync PDF</Label>
                        <Input
                          type="number"
                          value={cfg.seuilLignesSyncPdf ?? 500}
                          onChange={(e) => setCfg({ ...cfg, seuilLignesSyncPdf: Number(e.target.value) })}
                        />
                      </div>
                      <div className="grid gap-2">
                        <Label>Seuil sync Excel</Label>
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
                      <Label htmlFor="wm">Watermark actif</Label>
                    </div>
                    {cfg.watermarkActif ? (
                      <div className="grid gap-2 md:max-w-sm">
                        <Label>Texte watermark</Label>
                        <Input value={cfg.watermarkTexte ?? ""} onChange={(e) => setCfg({ ...cfg, watermarkTexte: e.target.value })} />
                      </div>
                    ) : null}

                    <div className="grid gap-3 md:grid-cols-3">
                      <div className="grid gap-2">
                        <Label>Upload logo (PNG/JPG 2Mo)</Label>
                        <Input type="file" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && upload("logo", e.target.files[0])} />
                      </div>
                      <div className="grid gap-2">
                        <Label>Upload cachet</Label>
                        <Input type="file" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && upload("cachet", e.target.files[0])} />
                      </div>
                      <div className="grid gap-2">
                        <Label>Upload signature DG</Label>
                        <Input type="file" accept="image/png,image/jpeg" onChange={(e) => e.target.files?.[0] && upload("signature", e.target.files[0])} />
                      </div>
                    </div>

                    <div className="flex justify-end">
                      <Button type="button" onClick={() => saveConfig()}>
                        Enregistrer la configuration
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

