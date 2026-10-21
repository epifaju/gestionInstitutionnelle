import { describe, expect, it } from "vitest";
import { decodeJwtPayload } from "./jwt";

function encodePart(obj: unknown): string {
  const json = JSON.stringify(obj);
  const b64 = btoa(json).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return b64;
}

describe("decodeJwtPayload", () => {
  it("décode un payload JWT valide", () => {
    const token = `hdr.${encodePart({ sub: "u1", role: "ADMIN", orgId: "o1", exp: 999 })}.sig`;
    expect(decodeJwtPayload(token)).toEqual({
      sub: "u1",
      role: "ADMIN",
      orgId: "o1",
      exp: 999,
    });
  });

  it("retourne null si segment payload absent", () => {
    expect(decodeJwtPayload("onlyonepart")).toBeNull();
  });

  it("retourne null si JSON invalide", () => {
    expect(decodeJwtPayload("a.%%%notb64%%%.c")).toBeNull();
  });

  it("accepte le padding base64 manquant", () => {
    const token = `x.${encodePart({ role: "RH" })}.y`;
    expect(decodeJwtPayload(token)?.role).toBe("RH");
  });
});
