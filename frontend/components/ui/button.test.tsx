import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "./button";

describe("Button", () => {
  it("rend le libellé", () => {
    render(<Button>Connexion</Button>);
    expect(screen.getByRole("button", { name: "Connexion" })).toBeInTheDocument();
  });

  it("propage le clic", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>OK</Button>);
    await user.click(screen.getByRole("button", { name: "OK" }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("respecte disabled", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Bloqué
      </Button>
    );
    await user.click(screen.getByRole("button", { name: "Bloqué" }));
    expect(onClick).not.toHaveBeenCalled();
  });
});
