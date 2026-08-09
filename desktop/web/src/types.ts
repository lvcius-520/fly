export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  role: "ADMIN" | "OPERATOR";
  enabled: boolean;
  lastLoginAt: string | null;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
}

export interface Region {
  id: string;
  name: string;
  code: string;
  description: string;
}

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
  parkingLotId: string | null;
  status: "draft" | "pending" | "ready" | "running" | "paused" | "completed" | "failed";
  plannedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface Device {
  id: string;
  name: string;
  code: string;
  airportStationId: string;
  status: "offline" | "idle" | "ready" | "executing";
  batteryPercent: number;
  satelliteCount: number;
  lastSeenAt: string | null;
  currentMissionId: string | null;
  followEnabled: boolean;
}

export interface DeviceView extends Device {
  simulatorRunning: boolean;
  latestTelemetry: TelemetryPoint | null;
}

export interface ParkingLot {
  id: string;
  name: string;
  regionId: string;
  address: string;
  latitude: number;
  longitude: number;
  totalSpaces: number;
  availableSpaces: number;
  occupancyRate: number;
  status: "normal" | "busy" | "offline";
  tags: string[];
  lastInspectionAt: string | null;
}

export interface ParkingSpace {
  id: string;
  parkingLotId: string;
  code: string;
  status: "free" | "occupied" | "unknown";
  polygon: RoutePoint[];
  updatedAt: string;
}

export interface AirportStation {
  id: string;
  name: string;
  regionId: string;
  latitude: number;
  longitude: number;
  coverageRadiusMeters: number;
  status: "online" | "maintenance" | "offline";
}

export interface CoverageLink {
  airportStationId: string;
  parkingLotId: string;
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
  type: string;
  message: string;
  status?: Mission["status"];
  timestamp: string;
}

export interface MediaFileItem {
  id: string;
  missionId: string | null;
  deviceId: string;
  type: "photo" | "video";
  name: string;
  url: string;
  createdAt: string;
}

export interface DetectionItem {
  id: string;
  missionId: string | null;
  deviceId: string;
  parkingSpaceId: string | null;
  label: string;
  score: number;
  mediaId: string | null;
  createdAt: string;
}

export interface ParkingOpinion {
  id: string;
  parkingLotId: string;
  parkingLotName: string;
  authorName: string;
  source: string;
  topic: string;
  sentiment: "正向" | "总体较好" | "关注项";
  rating: number;
  content: string;
  imageUrls: string[];
  createdAt: string;
}

export interface DashboardOverview {
  parkingLotCount: number;
  totalSpaces: number;
  availableSpaces: number;
  onlineDevices: number;
  activeMissions: number;
  onlineAirports: number;
  averageOccupancyRate: number;
  inspectionRoundsToday: number;
  alerts: Array<{
    id: string;
    level: "info" | "warning" | "critical";
    title: string;
    message: string;
    timestamp: string;
  }>;
  busyParkingLots: Array<{
    parkingLotId: string;
    name: string;
    occupancyRate: number;
    availableSpaces: number;
  }>;
}

export interface MapLayersResponse {
  regions: Region[];
  parkingLots: ParkingLot[];
  airportStations: AirportStation[];
  devices: Device[];
  missions: Mission[];
  coverageLinks: CoverageLink[];
}

export interface ParkingLotDetailResponse {
  parkingLot: ParkingLot;
  spaces: ParkingSpace[];
  missions: Mission[];
  detections: DetectionItem[];
  opinions: ParkingOpinion[];
}

export interface HeatmapPoint {
  parkingLotId: string;
  name: string;
  latitude: number;
  longitude: number;
  intensity: number;
  occupiedSpaces: number;
}

export interface TurnoverStat {
  parkingLotId: string;
  name: string;
  turnoverRate: number;
  occupancyRate: number;
  inspections: number;
}

export interface H5ParkingCard {
  id: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  availableSpaces: number;
  totalSpaces: number;
  occupancyRate: number;
  tags: string[];
}
