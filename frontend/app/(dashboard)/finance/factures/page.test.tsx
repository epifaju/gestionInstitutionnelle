import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FacturesPage from "./page";

const listFactures = vi.fn();
const listCategories = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock("@/lib/store", () => ({
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) =>
    sel({ user: { role: "FINANCIER" } }),
}));

vi.mock("@/lib/categorie-i18n", () => ({
  translateCategorieLabel: (_t: unknown, c: { libelle?: string | null }) => c.libelle ?? "",
}));

vi.mock("@/components/finance/FactureModal", () => ({
  FactureModal: () => null,
}));

vi.mock("@/services/finance.service", () => ({
  listFactures: (...a: unknown[]) => listFactures(...a),
  listCategories: (...a: unknown[]) => listCategories(...a),
  createFacture: vi.fn(),
}));

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <FacturesPage />
    </QueryClientProvider>
  );
}

describe("FacturesPage", () => {
  beforeEach(() => {
    listFactures.mockReset();
    listCategories.mockReset();
    listCategories.mockResolvedValue([]);
    listFactures.mockResolvedValue({
      content: [
        {
          id: "f1",
          reference: "FAC-001",
          fournisseur: "ACME",
          dateFacture: "2026-04-01",
          montantHt: 100,
          tva: 20,
          montantTtc: 120,
          devise: "EUR",
          tauxChangeEur: 1,
          montantTtcEur: 120,
          categorieId: null,
          categorieLibelle: "Fournitures",
          statut: "A_PAYER",
          justificatifUrl: null,
          montantPaye: 0,
          montantRestant: 120,
          createdAt: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });
  });

  it("affiche la liste des factures", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("FAC-001")).toBeInTheDocument();
      expect(screen.getByText("ACME")).toBeInTheDocument();
    });
    expect(listFactures).toHaveBeenCalled();
  });

  it("propose la création pour FINANCIER", async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText("FAC-001")).toBeInTheDocument());
    expect(screen.getAllByText("+ create").length).toBeGreaterThan(0);
  });
});
