import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./page";

const push = vi.fn();
const setAuth = vi.fn();
const setLocale = vi.fn();
const setTheme = vi.fn();
const post = vi.fn();
const get = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => (key: string) => `${ns}.${key}`,
}));

vi.mock("@/lib/api", () => ({
  post: (...args: unknown[]) => post(...args),
  get: (...args: unknown[]) => get(...args),
}));

vi.mock("@/lib/store", () => ({
  useAuthStore: (sel: (s: { setAuth: typeof setAuth }) => unknown) =>
    sel({ setAuth }),
}));

vi.mock("@/lib/locale-cookie", () => ({
  setLocaleCookie: vi.fn(),
}));

vi.mock("@/lib/locale-context", () => ({
  useAppLocale: () => ({ locale: "fr", setLocale }),
}));

vi.mock("@/lib/theme-context", () => ({
  useAppTheme: () => ({ setTheme }),
}));

describe("LoginPage", () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    push.mockReset();
    setAuth.mockReset();
    setLocale.mockReset();
    setTheme.mockReset();
    post.mockReset();
    get.mockReset();
    get.mockResolvedValue({ theme: "light" });
  });

  async function fillAndSubmit(email: string, password: string) {
    const user = userEvent.setup();
    await user.clear(screen.getByLabelText("Login.email"));
    await user.clear(screen.getByLabelText("Login.password"));
    await user.type(screen.getByLabelText("Login.email"), email);
    await user.type(screen.getByLabelText("Login.password"), password);
    await user.click(submitButton());
    return user;
  }

  function submitButton() {
    return document.querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  it("affiche le formulaire de connexion", () => {
    render(<LoginPage />);
    expect(screen.getByLabelText("Login.email")).toBeInTheDocument();
    expect(screen.getByLabelText("Login.password")).toBeInTheDocument();
    expect(submitButton()).toBeTruthy();
  });

  it("valide les champs requis", async () => {
    const user = userEvent.setup();
    render(<LoginPage />);
    await user.click(submitButton());
    await waitFor(() => {
      expect(screen.getAllByText("Validation.required").length).toBeGreaterThan(0);
    });
    expect(post).not.toHaveBeenCalled();
  });

  it("connecte et redirige vers le dashboard", async () => {
    post.mockResolvedValue({
      accessToken: "tok",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: {
        id: "u1",
        email: "admin@test.com",
        nom: "A",
        prenom: "B",
        role: "ADMIN",
        organisationId: "o1",
        organisationNom: "Org",
        langue: "fr",
      },
    });

    render(<LoginPage />);
    await fillAndSubmit("admin@test.com", "password1");

    await waitFor(() => {
      expect(setAuth).toHaveBeenCalled();
      expect(push).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("affiche l’erreur identifiants incorrects", async () => {
    const err = new AxiosError("Unauthorized");
    err.response = {
      data: { code: "IDENTIFIANTS_INCORRECTS" },
      status: 401,
      statusText: "Unauthorized",
      headers: {},
      config: { headers: {} } as never,
    };
    post.mockRejectedValue(err);

    render(<LoginPage />);
    await fillAndSubmit("admin@test.com", "password1");

    await waitFor(() => {
      expect(post).toHaveBeenCalled();
      expect(screen.getByText("Login.errorCredentials")).toBeInTheDocument();
    });
  });

  it("redirige EMPLOYE vers /rh/conges", async () => {
    post.mockResolvedValue({
      accessToken: "tok",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: {
        id: "u2",
        email: "e@test.com",
        nom: null,
        prenom: null,
        role: "EMPLOYE",
        organisationId: "o1",
        organisationNom: null,
      },
    });

    render(<LoginPage />);
    await fillAndSubmit("e@test.com", "password1");

    await waitFor(() => {
      expect(setAuth).toHaveBeenCalled();
      expect(push).toHaveBeenCalledWith("/rh/conges");
    });
  });
});
