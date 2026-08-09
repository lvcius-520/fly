import { describe, expect, it } from "vitest";
import { formatDistance, formatRate, occupancyTone } from "./parking";

describe("parking utils", () => {
  it("formats occupancy rate with one decimal", () => {
    expect(formatRate(82.345)).toBe("82.3%");
  });

  it("maps occupancy rate to proper tone", () => {
    expect(occupancyTone(38)).toBe("low");
    expect(occupancyTone(72)).toBe("medium");
    expect(occupancyTone(91)).toBe("high");
  });

  it("formats short and long distances", () => {
    expect(formatDistance(0.42)).toBe("420 米");
    expect(formatDistance(3.26)).toBe("3.3 公里");
  });
});
