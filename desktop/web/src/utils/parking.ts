export function formatRate(value: number): string {
  return `${value.toFixed(1)}%`;
}

export function occupancyTone(rate: number): "low" | "medium" | "high" {
  if (rate >= 85) return "high";
  if (rate >= 60) return "medium";
  return "low";
}

export function formatDistance(distanceKm: number): string {
  if (distanceKm < 1) {
    return `${Math.round(distanceKm * 1000)} 米`;
  }
  return `${distanceKm.toFixed(1)} 公里`;
}

export function formatTime(value: string | null): string {
  if (!value) return "暂无";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

export function estimateTravelMinutes(distanceKm: number, mode: "walk" | "ride" | "drive" = "drive"): number {
  const speedKmPerHour = mode === "walk" ? 4.5 : mode === "ride" ? 15 : 28;
  const minutes = (distanceKm / speedKmPerHour) * 60;
  return Math.max(2, Math.round(minutes));
}

export function buildAmapNavigationUrl(
  destinationLat: number,
  destinationLon: number,
  destinationName: string,
  originLat?: number,
  originLon?: number
): string {
  const params = new URLSearchParams({
    to: `${destinationLon},${destinationLat},${destinationName}`,
    mode: "car",
    policy: "1",
    src: "fly-gis",
    callnative: "0"
  });

  if (originLat !== undefined && originLon !== undefined) {
    params.set("from", `${originLon},${originLat},当前位置`);
  }

  return `https://uri.amap.com/navigation?${params.toString()}`;
}
