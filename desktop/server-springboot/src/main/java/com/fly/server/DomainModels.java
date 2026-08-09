package com.fly.server;

import java.util.ArrayList;
import java.util.List;

enum MissionStatus {
    draft,
    pending,
    ready,
    running,
    paused,
    completed,
    failed
}

enum DeviceStatus {
    offline,
    idle,
    ready,
    executing
}

enum MediaType {
    photo,
    video
}

class RoutePoint {
    public double lat;
    public double lon;
    public double altitudeMeters;
    public double speedMetersPerSecond;

    public RoutePoint() {
    }

    public RoutePoint(double lat, double lon, double altitudeMeters, double speedMetersPerSecond) {
        this.lat = lat;
        this.lon = lon;
        this.altitudeMeters = altitudeMeters;
        this.speedMetersPerSecond = speedMetersPerSecond;
    }
}

class RouteTemplate {
    public String id;
    public String name;
    public String description;
    public List<RoutePoint> waypoints = new ArrayList<>();
    public String createdAt;
    public String updatedAt;
}

class Mission {
    public String id;
    public String name;
    public String description;
    public String routeTemplateId;
    public String assignedDeviceId;
    public String parkingLotId;
    public MissionStatus status;
    public String plannedAt;
    public String createdAt;
    public String updatedAt;
}

class Device {
    public String id;
    public String name;
    public String code;
    public String airportStationId;
    public DeviceStatus status;
    public int batteryPercent;
    public int satelliteCount;
    public String lastSeenAt;
    public String currentMissionId;
    public boolean followEnabled;
}

class TelemetryPoint {
    public String id;
    public String deviceId;
    public String missionId;
    public String timestamp;
    public double lat;
    public double lon;
    public double altitudeMeters;
    public double speedMetersPerSecond;
    public double verticalSpeedMetersPerSecond;
    public int batteryPercent;
    public int satelliteCount;
    public String flightMode;
}

class MissionEvent {
    public String id;
    public String missionId;
    public String deviceId;
    public String type;
    public String message;
    public MissionStatus status;
    public String timestamp;
}

class MediaFileItem {
    public String id;
    public String missionId;
    public String deviceId;
    public MediaType type;
    public String name;
    public String url;
    public String createdAt;
}

class DetectionItem {
    public String id;
    public String missionId;
    public String deviceId;
    public String parkingSpaceId;
    public String label;
    public double score;
    public String mediaId;
    public String createdAt;
}

class ParkingOpinion {
    public String id;
    public String parkingLotId;
    public String parkingLotName;
    public String authorName;
    public String source;
    public String topic;
    public String sentiment;
    public double rating;
    public String content;
    public List<String> imageUrls = new ArrayList<>();
    public String createdAt;
}

class DashboardOverview {
    public int deviceCount;
    public int onlineDeviceCount;
    public int missionCount;
    public int activeMissionCount;
    public int mediaCount;
    public int detectionCount;
}

class Region {
    public String id;
    public String name;
    public String code;
    public String description;
}

class ParkingLot {
    public String id;
    public String name;
    public String regionId;
    public String address;
    public double latitude;
    public double longitude;
    public int totalSpaces;
    public int availableSpaces;
    public double occupancyRate;
    public String status;
    public List<String> tags = new ArrayList<>();
    public String lastInspectionAt;
}

class ParkingSpace {
    public String id;
    public String parkingLotId;
    public String code;
    public String status;
    public List<RoutePoint> polygon = new ArrayList<>();
    public String updatedAt;
}

class AirportStation {
    public String id;
    public String name;
    public String regionId;
    public double latitude;
    public double longitude;
    public int coverageRadiusMeters;
    public String status;
}

class CoverageLink {
    public String airportStationId;
    public String parkingLotId;

    public CoverageLink() {
    }

    public CoverageLink(String airportStationId, String parkingLotId) {
        this.airportStationId = airportStationId;
        this.parkingLotId = parkingLotId;
    }
}

class OpsAlert {
    public String id;
    public String level;
    public String title;
    public String message;
    public String timestamp;
}

class BusyParkingLot {
    public String parkingLotId;
    public String name;
    public double occupancyRate;
    public int availableSpaces;
}

class OpsOverviewResponse {
    public int parkingLotCount;
    public int totalSpaces;
    public int availableSpaces;
    public int onlineDevices;
    public int activeMissions;
    public int onlineAirports;
    public double averageOccupancyRate;
    public int inspectionRoundsToday;
    public List<OpsAlert> alerts = new ArrayList<>();
    public List<BusyParkingLot> busyParkingLots = new ArrayList<>();
}

class MapLayersResponse {
    public List<Region> regions = new ArrayList<>();
    public List<ParkingLot> parkingLots = new ArrayList<>();
    public List<AirportStation> airportStations = new ArrayList<>();
    public List<Device> devices = new ArrayList<>();
    public List<Mission> missions = new ArrayList<>();
    public List<CoverageLink> coverageLinks = new ArrayList<>();
}

class ParkingLotDetailResponse {
    public ParkingLot parkingLot;
    public List<ParkingSpace> spaces = new ArrayList<>();
    public List<Mission> missions = new ArrayList<>();
    public List<DetectionItem> detections = new ArrayList<>();
    public List<ParkingOpinion> opinions = new ArrayList<>();
}

class HeatmapPoint {
    public String parkingLotId;
    public String name;
    public double latitude;
    public double longitude;
    public double intensity;
    public int occupiedSpaces;
}

class TurnoverStat {
    public String parkingLotId;
    public String name;
    public double turnoverRate;
    public double occupancyRate;
    public int inspections;
}

class H5ParkingCard {
    public String id;
    public String name;
    public String address;
    public double latitude;
    public double longitude;
    public double distanceKm;
    public int availableSpaces;
    public int totalSpaces;
    public double occupancyRate;
    public List<String> tags = new ArrayList<>();
}

class DeviceView extends Device {
    public boolean simulatorRunning;
    public TelemetryPoint latestTelemetry;
}

class MobileSyncResponse {
    public Device device;
    public Mission pendingMission;
    public RouteTemplate routeTemplate;

    public MobileSyncResponse(Device device, Mission pendingMission, RouteTemplate routeTemplate) {
        this.device = device;
        this.pendingMission = pendingMission;
        this.routeTemplate = routeTemplate;
    }
}

class MobileContractResponse {
    public RoutePointContract routePoint;
    public List<String> endpoints;
    public List<String> notes;

    public MobileContractResponse(RoutePointContract routePoint, List<String> endpoints, List<String> notes) {
        this.routePoint = routePoint;
        this.endpoints = endpoints;
        this.notes = notes;
    }
}

class RoutePointContract {
    public String lat;
    public String lon;
    public String altitudeMeters;
    public String speedMetersPerSecond;

    public RoutePointContract(String lat, String lon, String altitudeMeters, String speedMetersPerSecond) {
        this.lat = lat;
        this.lon = lon;
        this.altitudeMeters = altitudeMeters;
        this.speedMetersPerSecond = speedMetersPerSecond;
    }
}

class ApiState {
    public List<Region> regions = new ArrayList<>();
    public List<RouteTemplate> routes = new ArrayList<>();
    public List<Mission> missions = new ArrayList<>();
    public List<Device> devices = new ArrayList<>();
    public List<AirportStation> airportStations = new ArrayList<>();
    public List<CoverageLink> coverageLinks = new ArrayList<>();
    public List<ParkingLot> parkingLots = new ArrayList<>();
    public List<ParkingSpace> parkingSpaces = new ArrayList<>();
    public List<TelemetryPoint> telemetry = new ArrayList<>();
    public List<MissionEvent> events = new ArrayList<>();
    public List<MediaFileItem> mediaFiles = new ArrayList<>();
    public List<DetectionItem> detections = new ArrayList<>();
    public List<ParkingOpinion> opinions = new ArrayList<>();
}
