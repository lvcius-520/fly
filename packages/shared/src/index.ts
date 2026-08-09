export type MissionStatus = "draft" | "pending" | "ready" | "running" | "paused" | "completed" | "failed";
export type DeviceStatus = "offline" | "idle" | "ready" | "executing";
export type MediaType = "photo" | "video";

export interface RoutePoint {
  lat: number;
  lon: number;
  altitudeMeters: number;
  speedMetersPerSecond: number;
}

export interface RouteTemplate {
  id: string;
  name: string;
  description: string;
  waypoints: RoutePoint[];
  createdAt: string;
  updatedAt: string;
}

export interface Mission {
  id: string;
  name: string;
  description: string;
  routeTemplateId: string;
  assignedDeviceId: string | null;
  status: MissionStatus;
  plannedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface Device {
  id: string;
  name: string;
  code: string;
  status: DeviceStatus;
  batteryPercent: number;
  satelliteCount: number;
  lastSeenAt: string | null;
  currentMissionId: string | null;
  followEnabled: boolean;
}

export interface TelemetryPoint {
  id: string;
  deviceId: string;
  missionId: string | null;
  timestamp: string;
  lat: number;
  lon: number;
  altitudeMeters: number;
  speedMetersPerSecond: number;
  verticalSpeedMetersPerSecond: number;
  batteryPercent: number;
  satelliteCount: number;
  flightMode: string;
}

export interface MissionEvent {
  id: string;
  missionId: string | null;
  deviceId: string;
  type: "mission_status" | "camera" | "warning" | "info";
  message: string;
  status?: MissionStatus;
  timestamp: string;
}

export interface MediaFileItem {
  id: string;
  missionId: string | null;
  deviceId: string;
  type: MediaType;
  name: string;
  url: string;
  createdAt: string;
}

export interface DetectionItem {
  id: string;
  missionId: string | null;
  deviceId: string;
  label: string;
  score: number;
  mediaId: string | null;
  createdAt: string;
}

export interface DashboardOverview {
  deviceCount: number;
  onlineDeviceCount: number;
  missionCount: number;
  activeMissionCount: number;
  mediaCount: number;
  detectionCount: number;
}

export interface MobileSyncPayload {
  deviceId: string;
}

export interface MobileSyncResponse {
  device: Device | null;
  pendingMission: Mission | null;
  routeTemplate: RouteTemplate | null;
}

export interface MobileTelemetryPayload {
  deviceId: string;
  missionId: string | null;
  lat: number;
  lon: number;
  altitudeMeters: number;
  speedMetersPerSecond: number;
  verticalSpeedMetersPerSecond: number;
  batteryPercent: number;
  satelliteCount: number;
  flightMode: string;
}

export interface MobileMissionEventPayload {
  deviceId: string;
  missionId: string | null;
  type: MissionEvent["type"];
  message: string;
  status?: MissionStatus;
}

export interface MobileMediaPayload {
  missionId: string | null;
  deviceId: string;
  type: MediaType;
  name: string;
  url: string;
}

export interface MobileDetectionPayload {
  missionId: string | null;
  deviceId: string;
  label: string;
  score: number;
  mediaId: string | null;
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  displayName: string;
}

export interface ApiState {
  routes: RouteTemplate[];
  missions: Mission[];
  devices: Device[];
  telemetry: TelemetryPoint[];
  events: MissionEvent[];
  mediaFiles: MediaFileItem[];
  detections: DetectionItem[];
}
