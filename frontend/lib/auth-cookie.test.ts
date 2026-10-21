import { afterEach, describe, expect, it } from "vitest";
import { ACCESS_COOKIE, clearAccessTokenCookie, setAccessTokenCookie } from "./auth-cookie";

describe("auth-cookie", () => {
  afterEach(() => {
    document.cookie = `${ACCESS_COOKIE}=; path=/; max-age=0`;
  });

  it("pose le cookie access_token", () => {
    setAccessTokenCookie("tok-123", 3600);
    expect(document.cookie).toContain(`${ACCESS_COOKIE}=tok-123`);
  });

  it("efface le cookie", () => {
    setAccessTokenCookie("tok-123", 3600);
    clearAccessTokenCookie();
    expect(document.cookie).not.toContain("tok-123");
  });
});
