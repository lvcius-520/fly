import type { ApiState, Device, Mission, RouteTemplate } from "@fly/shared";

const now = new Date().toISOString();

const routeTemplate: RouteTemplate = {
  id: "route-demo-suzhou",
  name: "苏州园区演示航线",
  description: "用于前后端联调的默认航线，字段与移动端 RoutePoint 对齐。",
  createdAt: now,
  updatedAt: now,
  waypoints: [
    { lat: 31.2992, lon: 120.6313, altitudeMeters: 30, speedMetersPerSecond: 5 },
    { lat: 31.2998, lon: 120.6324, altitudeMeters: 35, speedMetersPerSecond: 5 },
    { lat: 31.3006, lon: 120.6331, altitudeMeters: 35, speedMetersPerSecond: 5 },
    { lat: 31.3014, lon: 120.6322, altitudeMeters: 32, speedMetersPerSecond: 4 },
    { lat: 31.3007, lon: 120.6310, altitudeMeters: 28, speedMetersPerSecond: 4 }
  ]
};

const device: Device = {
  id: "device-flight-01",
  name: "演示无人机 01",
  code: "FLY-01",
  status: "ready",
  batteryPercent: 92,
  satelliteCount: 14,
  lastSeenAt: now,
  currentMissionId: "mission-demo-001",
  followEnabled: true
};

const mission: Mission = {
  id: "mission-demo-001",
  name: "校园巡检演示任务",
  description: "电脑端创建，移动端拉取并执行。",
  routeTemplateId: routeTemplate.id,
  assignedDeviceId: device.id,
  status: "ready",
  plannedAt: now,
  createdAt: now,
  updatedAt: now
};

export function createSeedState(): ApiState {
  return {
    routes: [routeTemplate],
    missions: [mission],
    devices: [device],
    telemetry: [],
    events: [],
    mediaFiles: [],
    detections: []
  };
}
