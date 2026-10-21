import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore, type UserInfo } from "./store";

vi.mock("./locale-cookie", () => ({
  clearLocaleCookie: vi.fn(),
}));

const user: UserInfo = {
  id: "u1",
  email: "a@test.com",
  nom: "Dupont",
  prenom: "Alice",
  role: "RH",
  organisationId: "o1",
  organisationNom: "Org",
};

describe("useAuthStore", () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null });
    localStorage.clear();
  });

  it("setAuth stocke user + token", () => {
    useAuthStore.getState().setAuth(user, "token-a");
    expect(useAuthStore.getState().user?.email).toBe("a@test.com");
    expect(useAuthStore.getState().accessToken).toBe("token-a");
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
  });

  it("updateToken remplace le token", () => {
    useAuthStore.getState().setAuth(user, "old");
    useAuthStore.getState().updateToken("new");
    expect(useAuthStore.getState().accessToken).toBe("new");
  });

  it("hasRole / hasAnyRole", () => {
    useAuthStore.getState().setAuth(user, "t");
    expect(useAuthStore.getState().hasRole("RH")).toBe(true);
    expect(useAuthStore.getState().hasRole("ADMIN")).toBe(false);
    expect(useAuthStore.getState().hasAnyRole(["ADMIN", "RH"])).toBe(true);
    expect(useAuthStore.getState().hasAnyRole(["ADMIN"])).toBe(false);
  });

  it("logout nettoie auth + localStorage legacy", () => {
    localStorage.setItem("app_theme", "dark");
    localStorage.setItem("app_locale", "en");
    useAuthStore.getState().setAuth(user, "t");
    useAuthStore.getState().logout();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(localStorage.getItem("app_theme")).toBeNull();
    expect(localStorage.getItem("app_locale")).toBeNull();
  });
});
