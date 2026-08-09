package com.fly.server;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api")
class ApiController {
    private final AuthService authService;
    private final DataStoreService store;
    private final SimulatorService simulator;

    ApiController(AuthService authService, DataStoreService store, SimulatorService simulator) {
        this.authService = authService;
        this.store = store;
        this.simulator = simulator;
    }

    @GetMapping("/health")
    public Object health() {
        return java.util.Map.of("ok", true, "service", "fly-desktop-springboot");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/auth/me")
    public AuthUserSummary currentUser(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        return authService.currentUser(authorizationHeader);
    }

    @PostMapping("/auth/logout")
    public Object logout(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return java.util.Map.of("ok", true);
    }

    @GetMapping("/admin/users")
    public List<AuthUserSummary> listUsers(
        @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.listUsers(authorizationHeader);
    }

    @PostMapping("/admin/users")
    public ResponseEntity<AuthUserSummary> createUser(
        @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody CreateManagementUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(authorizationHeader, request));
    }

    @PatchMapping("/admin/users/{id}")
    public AuthUserSummary updateUser(
        @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
        @PathVariable String id,
        @Valid @RequestBody UpdateManagementUserRequest request
    ) {
        return authService.updateUser(authorizationHeader, id, request);
    }

    @GetMapping("/dashboard/overview")
    public DashboardOverview dashboardOverview() {
        ApiState state = store.snapshot();
        DashboardOverview overview = new DashboardOverview();
        overview.deviceCount = state.devices.size();
        overview.onlineDeviceCount = (int) state.devices.stream()
            .filter(item -> item.status != DeviceStatus.offline)
            .count();
        overview.missionCount = state.missions.size();
        overview.activeMissionCount = (int) state.missions.stream()
            .filter(item -> item.status == MissionStatus.ready
                || item.status == MissionStatus.running
                || item.status == MissionStatus.paused)
            .count();
        overview.mediaCount = state.mediaFiles.size();
        overview.detectionCount = state.detections.size();
        return overview;
    }

    @GetMapping("/ops/overview")
    public OpsOverviewResponse opsOverview() {
        ApiState state = store.snapshot();
        OpsOverviewResponse response = new OpsOverviewResponse();
        response.parkingLotCount = state.parkingLots.size();
        response.totalSpaces = state.parkingLots.stream().mapToInt(item -> item.totalSpaces).sum();
        response.availableSpaces = state.parkingLots.stream().mapToInt(item -> item.availableSpaces).sum();
        response.onlineDevices = (int) state.devices.stream().filter(item -> item.status != DeviceStatus.offline).count();
        response.activeMissions = (int) state.missions.stream()
            .filter(item -> item.status == MissionStatus.ready
                || item.status == MissionStatus.running
                || item.status == MissionStatus.paused)
            .count();
        response.onlineAirports = (int) state.airportStations.stream().filter(item -> "online".equals(item.status)).count();
        response.averageOccupancyRate = state.parkingLots.stream().mapToDouble(item -> item.occupancyRate).average().orElse(0);
        response.inspectionRoundsToday = 15;

        response.alerts.addAll(buildOpsAlerts(state));
        response.busyParkingLots.addAll(state.parkingLots.stream()
            .sorted(Comparator.comparingDouble((ParkingLot item) -> item.occupancyRate).reversed())
            .limit(4)
            .map(item -> {
                BusyParkingLot busy = new BusyParkingLot();
                busy.parkingLotId = item.id;
                busy.name = item.name;
                busy.occupancyRate = item.occupancyRate;
                busy.availableSpaces = item.availableSpaces;
                return busy;
            })
            .toList());
        return response;
    }

    @GetMapping("/ops/map-layers")
    public MapLayersResponse mapLayers() {
        ApiState state = store.snapshot();
        MapLayersResponse response = new MapLayersResponse();
        response.regions = state.regions;
        response.parkingLots = state.parkingLots;
        response.airportStations = state.airportStations;
        response.devices = state.devices;
        response.missions = state.missions;
        response.coverageLinks = state.coverageLinks;
        return response;
    }

    @GetMapping("/routes")
    public List<RouteTemplate> routes() {
        return store.snapshot().routes;
    }

    @PostMapping("/routes")
    public ResponseEntity<RouteTemplate> createRoute(@Valid @RequestBody RouteCreateRequest request) {
        RouteTemplate route = store.mutate(draft -> {
            String now = Instant.now().toString();
            RouteTemplate item = new RouteTemplate();
            item.id = DataStoreService.nextId();
            item.name = request.name();
            item.description = request.description() == null ? "" : request.description();
            item.createdAt = now;
            item.updatedAt = now;
            item.waypoints = request.waypoints().stream()
                .map(point -> new RoutePoint(point.lat(), point.lon(), point.altitudeMeters(), point.speedMetersPerSecond()))
                .toList();
            draft.routes.add(0, item);
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(route);
    }

    @GetMapping("/missions")
    public List<Mission> missions() {
        return store.snapshot().missions;
    }

    @PostMapping("/missions")
    public ResponseEntity<Mission> createMission(@Valid @RequestBody MissionCreateRequest request) {
        Mission mission = store.mutate(draft -> {
            if (draft.routes.stream().noneMatch(item -> item.id.equals(request.routeTemplateId()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "航线模板不存在");
            }

            String now = Instant.now().toString();
            Mission item = new Mission();
            item.id = DataStoreService.nextId();
            item.name = request.name();
            item.description = request.description() == null ? "" : request.description();
            item.routeTemplateId = request.routeTemplateId();
            item.assignedDeviceId = blankToNull(request.assignedDeviceId());
            item.parkingLotId = findParkingLotIdForRoute(draft, request.routeTemplateId());
            item.status = item.assignedDeviceId != null ? MissionStatus.ready : MissionStatus.draft;
            item.plannedAt = request.plannedAt();
            item.createdAt = now;
            item.updatedAt = now;
            draft.missions.add(0, item);

            if (item.assignedDeviceId != null) {
                Device device = draft.devices.stream()
                    .filter(entry -> item.assignedDeviceId.equals(entry.id))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定设备不存在"));
                device.currentMissionId = item.id;
                device.status = DeviceStatus.ready;
                device.lastSeenAt = now;
            }
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(mission);
    }

    @PatchMapping("/missions/{id}/status")
    public Mission updateMissionStatus(@PathVariable String id, @Valid @RequestBody MissionStatusUpdateRequest request) {
        Mission mission = store.mutate(draft -> {
            Mission item = draft.missions.stream()
                .filter(entry -> entry.id.equals(id))
                .findFirst()
                .orElse(null);
            if (item == null) {
                return null;
            }
            item.status = request.status();
            item.updatedAt = Instant.now().toString();
            return item;
        });

        if (mission == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return mission;
    }

    @GetMapping("/parking-lots")
    public List<ParkingLot> parkingLots(
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) String status
    ) {
        return store.snapshot().parkingLots.stream()
            .filter(item -> regionId == null || regionId.isBlank() || regionId.equals(item.regionId))
            .filter(item -> status == null || status.isBlank() || status.equals(item.status))
            .toList();
    }

    @GetMapping("/parking-lots/{id}")
    public ParkingLotDetailResponse parkingLotDetail(@PathVariable String id) {
        ApiState state = store.snapshot();
        ParkingLot parkingLot = state.parkingLots.stream()
            .filter(item -> item.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "停车场不存在"));

        ParkingLotDetailResponse response = new ParkingLotDetailResponse();
        response.parkingLot = parkingLot;
        response.spaces = state.parkingSpaces.stream()
            .filter(item -> id.equals(item.parkingLotId))
            .toList();
        response.missions = state.missions.stream()
            .filter(item -> id.equals(item.parkingLotId))
            .toList();
        response.detections = state.detections.stream()
            .filter(item -> response.spaces.stream().anyMatch(space -> space.id.equals(item.parkingSpaceId)))
            .toList();
        response.opinions = state.opinions.stream()
            .filter(item -> id.equals(item.parkingLotId))
            .sorted(Comparator.comparing((ParkingOpinion item) -> item.createdAt).reversed())
            .toList();
        return response;
    }

    @GetMapping("/parking-spaces")
    public List<ParkingSpace> parkingSpaces(@RequestParam(required = false) String parkingLotId) {
        return store.snapshot().parkingSpaces.stream()
            .filter(item -> parkingLotId == null || parkingLotId.isBlank() || parkingLotId.equals(item.parkingLotId))
            .toList();
    }

    @GetMapping("/airports")
    public List<AirportStation> airports() {
        return store.snapshot().airportStations;
    }

    @GetMapping("/devices")
    public List<DeviceView> devices() {
        ApiState state = store.snapshot();
        List<DeviceView> result = new ArrayList<>();
        for (Device device : state.devices) {
            DeviceView view = new DeviceView();
            view.id = device.id;
            view.name = device.name;
            view.code = device.code;
            view.airportStationId = device.airportStationId;
            view.status = device.status;
            view.batteryPercent = device.batteryPercent;
            view.satelliteCount = device.satelliteCount;
            view.lastSeenAt = device.lastSeenAt;
            view.currentMissionId = device.currentMissionId;
            view.followEnabled = device.followEnabled;
            view.simulatorRunning = simulator.isRunning(device.id);
            view.latestTelemetry = state.telemetry.stream()
                .filter(item -> item.deviceId.equals(device.id))
                .findFirst()
                .orElse(null);
            result.add(view);
        }
        return result;
    }

    @PostMapping("/devices/{id}/simulator/start")
    public Object startSimulator(@PathVariable String id) {
        simulator.start(id);
        return java.util.Map.of("ok", true);
    }

    @PostMapping("/devices/{id}/simulator/stop")
    public Object stopSimulator(@PathVariable String id) {
        simulator.stop(id);
        return java.util.Map.of("ok", true);
    }

    @GetMapping("/telemetry")
    public List<TelemetryPoint> telemetry(
        @RequestParam(defaultValue = "40") int limit,
        @RequestParam(required = false) String deviceId
    ) {
        return store.snapshot().telemetry.stream()
            .filter(item -> deviceId == null || deviceId.isBlank() || deviceId.equals(item.deviceId))
            .limit(Math.max(limit, 0))
            .toList();
    }

    @GetMapping("/events")
    public List<MissionEvent> events(
        @RequestParam(defaultValue = "60") int limit,
        @RequestParam(required = false) String missionId
    ) {
        return store.snapshot().events.stream()
            .filter(item -> missionId == null || missionId.isBlank() || missionId.equals(item.missionId))
            .limit(Math.max(limit, 0))
            .toList();
    }

    @GetMapping("/media")
    public List<MediaFileItem> media() {
        return store.snapshot().mediaFiles;
    }

    @GetMapping("/detections")
    public List<DetectionItem> detections() {
        return store.snapshot().detections;
    }

    @GetMapping("/analysis/heatmap")
    public List<HeatmapPoint> analysisHeatmap() {
        return store.snapshot().parkingLots.stream()
            .map(item -> {
                HeatmapPoint point = new HeatmapPoint();
                point.parkingLotId = item.id;
                point.name = item.name;
                point.latitude = item.latitude;
                point.longitude = item.longitude;
                point.intensity = item.occupancyRate;
                point.occupiedSpaces = item.totalSpaces - item.availableSpaces;
                return point;
            })
            .sorted(Comparator.comparingDouble((HeatmapPoint item) -> item.intensity).reversed())
            .toList();
    }

    @GetMapping("/analysis/turnover")
    public List<TurnoverStat> analysisTurnover() {
        ApiState state = store.snapshot();
        return state.parkingLots.stream()
            .map(item -> {
                TurnoverStat stat = new TurnoverStat();
                stat.parkingLotId = item.id;
                stat.name = item.name;
                stat.occupancyRate = item.occupancyRate;
                stat.turnoverRate = Math.min(4.8, 1.2 + item.occupancyRate / 28.0);
                stat.inspections = (int) state.missions.stream().filter(mission -> item.id.equals(mission.parkingLotId)).count() + 9;
                return stat;
            })
            .sorted(Comparator.comparingDouble((TurnoverStat item) -> item.turnoverRate).reversed())
            .toList();
    }

    @GetMapping("/analysis/opinions")
    public List<ParkingOpinion> analysisOpinions(
        @RequestParam(required = false) String parkingLotId,
        @RequestParam(defaultValue = "80") int limit
    ) {
        return store.snapshot().opinions.stream()
            .filter(item -> parkingLotId == null || parkingLotId.isBlank() || parkingLotId.equals(item.parkingLotId))
            .sorted(Comparator.comparing((ParkingOpinion item) -> item.createdAt).reversed())
            .limit(Math.max(limit, 0))
            .toList();
    }

    @PostMapping("/h5/parking-lots/{id}/opinions")
    public ResponseEntity<ParkingOpinion> createParkingOpinion(
        @PathVariable String id,
        @Valid @RequestBody ParkingOpinionCreateRequest request,
        @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        ParkingOpinion opinion = store.mutate(draft -> {
            ParkingLot parkingLot = draft.parkingLots.stream()
                .filter(item -> item.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "停车场不存在"));

            String now = Instant.now().toString();
            String authorName = request.authorName() == null || request.authorName().isBlank()
                ? resolveOpinionAuthor(authorizationHeader)
                : request.authorName().trim();

            ParkingOpinion item = new ParkingOpinion();
            item.id = DataStoreService.nextId();
            item.parkingLotId = parkingLot.id;
            item.parkingLotName = parkingLot.name;
            item.authorName = authorName;
            item.source = "用户提交";
            item.topic = request.topic().trim();
            item.rating = request.rating();
            item.sentiment = resolveSentimentByRating(request.rating());
            item.content = request.content().trim();
            item.imageUrls = request.imageUrls() == null
                ? new ArrayList<>()
                : request.imageUrls().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(3)
                    .toList();
            item.createdAt = now;
            draft.opinions.add(0, item);
            trimOpinions(draft);
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(opinion);
    }

    @GetMapping("/h5/nearby-parking")
    public List<H5ParkingCard> nearbyParking(
        @RequestParam(defaultValue = "31.2992") double lat,
        @RequestParam(defaultValue = "120.6313") double lon,
        @RequestParam(defaultValue = "8") double radiusKm
    ) {
        return store.snapshot().parkingLots.stream()
            .map(item -> toH5Card(item, lat, lon))
            .filter(item -> item.distanceKm <= radiusKm)
            .sorted(Comparator.comparingDouble((H5ParkingCard item) -> item.distanceKm)
                .thenComparing((H5ParkingCard item) -> -item.availableSpaces))
            .toList();
    }

    @GetMapping("/h5/parking-lots/{id}")
    public ParkingLotDetailResponse h5ParkingLot(@PathVariable String id) {
        return parkingLotDetail(id);
    }

    @GetMapping("/h5/recommendations")
    public List<H5ParkingCard> h5Recommendations() {
        return store.snapshot().parkingLots.stream()
            .sorted(Comparator.comparingInt((ParkingLot item) -> item.availableSpaces).reversed())
            .limit(4)
            .map(item -> toH5Card(item, item.latitude, item.longitude))
            .toList();
    }

    @GetMapping("/mobile/contract")
    public MobileContractResponse mobileContract() {
        return new MobileContractResponse(
            new RoutePointContract("Double", "Double", "Float", "Float"),
            List.of(
                "POST /api/mobile/sync",
                "POST /api/mobile/telemetry",
                "POST /api/mobile/events",
                "POST /api/mobile/media",
                "POST /api/mobile/detections"
            ),
            List.of(
                "RoutePoint 字段已与 Android 端 MainActivity.kt 保持一致",
                "当前服务默认支持本地模拟飞行，无需真机即可联调任务与状态流"
            )
        );
    }

    @PostMapping("/mobile/sync")
    public MobileSyncResponse mobileSync(@Valid @RequestBody MobileSyncRequest request) {
        ApiState state = store.snapshot();
        Device device = state.devices.stream()
            .filter(item -> item.id.equals(request.deviceId()))
            .findFirst()
            .orElse(null);

        Mission mission = state.missions.stream()
            .filter(item -> request.deviceId().equals(item.assignedDeviceId))
            .filter(item -> item.status == MissionStatus.ready
                || item.status == MissionStatus.pending
                || item.status == MissionStatus.running
                || item.status == MissionStatus.paused)
            .findFirst()
            .orElse(null);

        RouteTemplate routeTemplate = mission == null
            ? null
            : state.routes.stream()
                .filter(item -> item.id.equals(mission.routeTemplateId))
                .findFirst()
                .orElse(null);

        return new MobileSyncResponse(device, mission, routeTemplate);
    }

    @PostMapping("/mobile/telemetry")
    public ResponseEntity<TelemetryPoint> mobileTelemetry(@Valid @RequestBody MobileTelemetryRequest request) {
        TelemetryPoint telemetry = store.mutate(draft -> {
            TelemetryPoint item = new TelemetryPoint();
            item.id = DataStoreService.nextId();
            item.timestamp = Instant.now().toString();
            item.deviceId = request.deviceId();
            item.missionId = blankToNull(request.missionId());
            item.lat = request.lat();
            item.lon = request.lon();
            item.altitudeMeters = request.altitudeMeters();
            item.speedMetersPerSecond = request.speedMetersPerSecond();
            item.verticalSpeedMetersPerSecond = request.verticalSpeedMetersPerSecond();
            item.batteryPercent = request.batteryPercent();
            item.satelliteCount = request.satelliteCount();
            item.flightMode = request.flightMode();

            draft.telemetry.add(0, item);
            trimTelemetry(draft);

            Device device = draft.devices.stream()
                .filter(entry -> entry.id.equals(request.deviceId()))
                .findFirst()
                .orElse(null);
            if (device != null) {
                device.lastSeenAt = item.timestamp;
                device.batteryPercent = request.batteryPercent();
                device.satelliteCount = request.satelliteCount();
                device.status = item.missionId != null ? DeviceStatus.executing : DeviceStatus.ready;
                device.currentMissionId = item.missionId;
            }
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(telemetry);
    }

    @PostMapping("/mobile/events")
    public ResponseEntity<MissionEvent> mobileEvent(@Valid @RequestBody MobileEventRequest request) {
        MissionEvent event = store.mutate(draft -> {
            MissionEvent item = new MissionEvent();
            item.id = DataStoreService.nextId();
            item.timestamp = Instant.now().toString();
            item.deviceId = request.deviceId();
            item.missionId = blankToNull(request.missionId());
            item.type = request.type();
            item.message = request.message();
            item.status = request.status();
            draft.events.add(0, item);
            trimEvents(draft);

            if (item.missionId != null && request.status() != null) {
                Mission mission = draft.missions.stream()
                    .filter(entry -> entry.id.equals(item.missionId))
                    .findFirst()
                    .orElse(null);
                if (mission != null) {
                    mission.status = request.status();
                    mission.updatedAt = item.timestamp;
                }
            }
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PostMapping("/mobile/media")
    public ResponseEntity<MediaFileItem> mobileMedia(@Valid @RequestBody MobileMediaRequest request) {
        MediaFileItem media = store.mutate(draft -> {
            MediaFileItem item = new MediaFileItem();
            item.id = DataStoreService.nextId();
            item.createdAt = Instant.now().toString();
            item.missionId = blankToNull(request.missionId());
            item.deviceId = request.deviceId();
            item.type = request.type();
            item.name = request.name();
            item.url = request.url();
            draft.mediaFiles.add(0, item);
            trimMedia(draft);
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(media);
    }

    @PostMapping("/mobile/detections")
    public ResponseEntity<DetectionItem> mobileDetection(@Valid @RequestBody MobileDetectionRequest request) {
        DetectionItem detection = store.mutate(draft -> {
            DetectionItem item = new DetectionItem();
            item.id = DataStoreService.nextId();
            item.createdAt = Instant.now().toString();
            item.missionId = blankToNull(request.missionId());
            item.deviceId = request.deviceId();
            item.label = request.label();
            item.score = request.score();
            item.mediaId = blankToNull(request.mediaId());
            draft.detections.add(0, item);
            trimDetections(draft);
            return item;
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(detection);
    }

    private List<OpsAlert> buildOpsAlerts(ApiState state) {
        List<OpsAlert> alerts = new ArrayList<>();

        state.parkingLots.stream()
            .filter(item -> item.occupancyRate >= 85)
            .limit(2)
            .forEach(item -> alerts.add(createAlert("warning", item.name + " 进入高占用", "当前占用率 " + formatRate(item.occupancyRate) + "%")));

        state.devices.stream()
            .filter(item -> item.status != DeviceStatus.offline && item.batteryPercent > 0 && item.batteryPercent <= 25)
            .limit(1)
            .forEach(item -> alerts.add(createAlert("critical", item.name + " 电量偏低", "剩余电量 " + item.batteryPercent + "%，建议尽快返航")));

        state.airportStations.stream()
            .filter(item -> !"online".equals(item.status))
            .limit(1)
            .forEach(item -> alerts.add(createAlert("info", item.name + " 非在线状态", "当前机场状态为 " + item.status)));

        if (alerts.isEmpty()) {
            alerts.add(createAlert("info", "当前系统运行稳定", "所有片区与机场状态正常"));
        }
        return alerts;
    }

    private OpsAlert createAlert(String level, String title, String message) {
        OpsAlert alert = new OpsAlert();
        alert.id = DataStoreService.nextId();
        alert.level = level;
        alert.title = title;
        alert.message = message;
        alert.timestamp = Instant.now().toString();
        return alert;
    }

    private H5ParkingCard toH5Card(ParkingLot item, double lat, double lon) {
        H5ParkingCard card = new H5ParkingCard();
        card.id = item.id;
        card.name = item.name;
        card.address = item.address;
        card.latitude = item.latitude;
        card.longitude = item.longitude;
        card.availableSpaces = item.availableSpaces;
        card.totalSpaces = item.totalSpaces;
        card.occupancyRate = item.occupancyRate;
        card.tags = item.tags;
        card.distanceKm = calculateDistanceKm(lat, lon, item.latitude, item.longitude);
        return card;
    }

    private String findParkingLotIdForRoute(ApiState state, String routeTemplateId) {
        String routeName = state.routes.stream()
            .filter(item -> item.id.equals(routeTemplateId))
            .map(item -> item.name.toLowerCase(Locale.ROOT))
            .findFirst()
            .orElse("");
        return state.parkingLots.stream()
            .filter(item -> routeName.contains(item.id.replace("lot-", "")) || routeName.contains(item.name.substring(0, 2).toLowerCase(Locale.ROOT)))
            .map(item -> item.id)
            .findFirst()
            .orElse(state.parkingLots.isEmpty() ? null : state.parkingLots.get(0).id);
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusKm * c * 100.0) / 100.0;
    }

    private double formatRate(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String resolveOpinionAuthor(String authorizationHeader) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            AuthUserSummary currentUser = authService.currentUser(authorizationHeader);
            if (currentUser != null) {
                if (currentUser.displayName() != null && !currentUser.displayName().isBlank()) {
                    return currentUser.displayName();
                }
                if (currentUser.username() != null && !currentUser.username().isBlank()) {
                    return currentUser.username();
                }
            }
        }
        return "普通用户";
    }

    private String resolveSentimentByRating(double rating) {
        if (rating >= 4.3) {
            return "正向";
        }
        if (rating >= 4.0) {
            return "总体较好";
        }
        return "关注项";
    }

    private void trimTelemetry(ApiState state) {
        if (state.telemetry.size() > 200) {
            state.telemetry = new ArrayList<>(state.telemetry.subList(0, 200));
        }
    }

    private void trimEvents(ApiState state) {
        if (state.events.size() > 200) {
            state.events = new ArrayList<>(state.events.subList(0, 200));
        }
    }

    private void trimMedia(ApiState state) {
        if (state.mediaFiles.size() > 200) {
            state.mediaFiles = new ArrayList<>(state.mediaFiles.subList(0, 200));
        }
    }

    private void trimDetections(ApiState state) {
        if (state.detections.size() > 200) {
            state.detections = new ArrayList<>(state.detections.subList(0, 200));
        }
    }

    private void trimOpinions(ApiState state) {
        if (state.opinions.size() > 300) {
            state.opinions = new ArrayList<>(state.opinions.subList(0, 300));
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
