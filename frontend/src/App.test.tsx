import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("App", () => {
  it("explains the product purpose and analysis principles", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Repository Evolution Risk Explorer" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Evidence before scores")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Application foundation ready",
    );
  });
});
