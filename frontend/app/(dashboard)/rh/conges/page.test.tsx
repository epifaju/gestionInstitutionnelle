import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CongesPage from "./page";

const listConges = vi.fn();
const getCalendrier = vi.fn();
const getMySalarie = vi.fn();

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => "fr",
}));

vi.mock("@/lib/store", () => ({
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) =>
    sel({ user: { role: "RH" } }),
}));

vi.mock("@/services/conge.service", () => ({
  listConges: (...a: unknown[]) => listConges(...a),
  getCalendrier: (...a: unknown[]) => getCalendrier(...a),
  soumettreConge: vi.fn(),
  validerConge: vi.fn(),
  rejeterConge: vi.fn(),
  annulerCongeValide: vi.fn(),
}));

vi.mock("@/services/salarie.service", () => ({
  getMySalarie: (...a: unknown[]) => getMySalarie(...a),
}));

vi.mock("@/components/forms/CongeForm", () => ({
  CongeForm: () => <div data-testid="conge-form" />,
}));

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <CongesPage />
    </QueryClientProvider>
  );
}

describe("CongesPage", () => {
  beforeEach(() => {
    listConges.mockReset();
    getCalendrier.mockReset();
    getMySalarie.mockReset();
    listConges.mockResolvedValue({
      content: [
        {
          id: "c1",
          salarieId: "s1",
          salarieNomComplet: "Dupont Alice",
          service: "IT",
          typeConge: "ANNUEL",
          dateDebut: "2026-05-01",
          dateFin: "2026-05-05",
          nbJours: 5,
          statut: "EN_ATTENTE",
          valideurNomComplet: null,
          dateValidation: null,
          motifRejet: null,
          commentaire: null,
          createdAt: "2026-04-01T00:00:00",
        },
      ],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
  });

  it("affiche la liste des congés et actions RH", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Dupont Alice")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "validate" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "reject" })).toBeInTheDocument();
    expect(listConges).toHaveBeenCalled();
  });
});
