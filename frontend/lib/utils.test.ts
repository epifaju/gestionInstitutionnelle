import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("fusionne des classes", () => {
    expect(cn("px-2", "py-1")).toBe("px-2 py-1");
  });

  it("résout les conflits tailwind (twMerge)", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("ignore les valeurs falsy", () => {
    expect(cn("a", false && "b", null, undefined, "c")).toBe("a c");
  });
});
