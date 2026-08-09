package com.fly.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
class DataStoreService {
    private static final Set<String> LEGACY_REGION_IDS = Set.of("region-north", "region-east", "region-south");
    private static final Set<String> LEGACY_PARKING_LOT_IDS = Set.of("lot-a", "lot-b", "lot-c", "lot-d", "lot-e", "lot-f");
    private static final Set<String> LEGACY_ROUTE_IDS = Set.of("route-east-core", "route-event-south", "route-campus-east", "route-demo-suzhou");

    private final ObjectMapper objectMapper;
    private final Path runtimeDir = Path.of("runtime");
    private final Path runtimeFile = runtimeDir.resolve("mock-db.json");
    private ApiState state = createSeedState();

    DataStoreService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public synchronized void init() throws IOException {
        Files.createDirectories(runtimeDir);
        if (Files.exists(runtimeFile)) {
            state = normalizeState(objectMapper.readValue(runtimeFile.toFile(), ApiState.class));
            persist();
            return;
        }
        state = createSeedState();
        persist();
    }

    public synchronized ApiState snapshot() {
        return deepCopy(state, ApiState.class);
    }

    public synchronized <T> T mutate(Function<ApiState, T> mutator) {
        ApiState draft = normalizeState(snapshot());
        T result = mutator.apply(draft);
        state = normalizeState(draft);
        persist();
        return result;
    }

    private void persist() {
        try {
            Files.createDirectories(runtimeDir);
            objectMapper.writeValue(runtimeFile.toFile(), state);
        } catch (IOException exception) {
            throw new IllegalStateException("写入 mock 数据失败", exception);
        }
    }

    private <T> T deepCopy(Object source, Class<T> targetType) {
        return objectMapper.convertValue(source, targetType);
    }

    private ApiState normalizeState(ApiState source) {
        ApiState seed = createSeedState();
        if (source == null) {
            return seed;
        }

        source.regions = ensureList(source.regions, seed.regions);
        source.routes = ensureList(source.routes, seed.routes);
        source.missions = ensureList(source.missions, seed.missions);
        source.devices = ensureList(source.devices, seed.devices);
        source.airportStations = ensureList(source.airportStations, seed.airportStations);
        source.coverageLinks = ensureList(source.coverageLinks, seed.coverageLinks);
        source.parkingLots = ensureList(source.parkingLots, seed.parkingLots);
        source.parkingSpaces = ensureList(source.parkingSpaces, seed.parkingSpaces);
        source.telemetry = ensureList(source.telemetry, seed.telemetry);
        source.events = ensureList(source.events, seed.events);
        source.mediaFiles = ensureList(source.mediaFiles, seed.mediaFiles);
        source.detections = ensureList(source.detections, seed.detections);
        source.opinions = ensureList(source.opinions, seed.opinions);

        if (shouldReplaceLegacyParkingDataset(source)) {
            source.regions = seed.regions;
            source.airportStations = seed.airportStations;
            source.coverageLinks = seed.coverageLinks;
            source.parkingLots = seed.parkingLots;
            source.parkingSpaces = seed.parkingSpaces;
            source.devices = seed.devices;
            source.missions = seed.missions;
            source.telemetry = seed.telemetry;
            source.events = seed.events;
            source.mediaFiles = seed.mediaFiles;
            source.detections = seed.detections;
            source.opinions = seed.opinions;
            source.routes = mergeRoutes(seed.routes, source.routes);
        }

        if (source.regions.isEmpty()) source.regions = seed.regions;
        if (source.routes.isEmpty()) source.routes = seed.routes;
        if (source.devices.isEmpty()) source.devices = seed.devices;
        if (source.airportStations.isEmpty()) source.airportStations = seed.airportStations;
        if (source.coverageLinks.isEmpty()) source.coverageLinks = seed.coverageLinks;
        if (source.parkingLots.isEmpty()) source.parkingLots = seed.parkingLots;
        if (source.parkingSpaces.isEmpty()) source.parkingSpaces = seed.parkingSpaces;
        if (source.missions.isEmpty()) source.missions = seed.missions;
        if (source.opinions.isEmpty()) source.opinions = seed.opinions;

        for (ParkingOpinion opinion : source.opinions) {
            if (opinion.imageUrls == null) {
                opinion.imageUrls = new ArrayList<>();
            }
        }

        for (Device device : source.devices) {
            if (device.airportStationId == null) {
                device.airportStationId = "airport-east-01";
            }
        }

        for (Mission mission : source.missions) {
            if (mission.parkingLotId == null && !source.parkingLots.isEmpty()) {
                mission.parkingLotId = source.parkingLots.get(0).id;
            }
        }

        refreshParkingAggregates(source);
        return source;
    }

    private <T> List<T> ensureList(List<T> value, List<T> fallback) {
        return value == null ? new ArrayList<>(fallback) : value;
    }

    private boolean shouldReplaceLegacyParkingDataset(ApiState source) {
        boolean hasLegacyRegion = source.regions.stream().anyMatch(region -> region != null && LEGACY_REGION_IDS.contains(region.id));
        boolean hasLegacyParkingLot = source.parkingLots.stream().anyMatch(lot -> lot != null && LEGACY_PARKING_LOT_IDS.contains(lot.id));
        boolean hasOldDistrictName = source.parkingLots.stream()
            .filter(lot -> lot != null && lot.address != null)
            .anyMatch(lot -> lot.address.contains("工业园区") || lot.address.contains("独墅湖"));
        return hasLegacyRegion || hasLegacyParkingLot || hasOldDistrictName;
    }

    private List<RouteTemplate> mergeRoutes(List<RouteTemplate> seedRoutes, List<RouteTemplate> existingRoutes) {
        List<RouteTemplate> merged = new ArrayList<>(seedRoutes);
        for (RouteTemplate route : existingRoutes) {
            if (route == null || route.id == null || LEGACY_ROUTE_IDS.contains(route.id)) {
                continue;
            }

            boolean exists = false;
            for (RouteTemplate item : merged) {
                if (route.id.equals(item.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                merged.add(route);
            }
        }
        return merged;
    }

    private ApiState createSeedState() {
        String now = Instant.now().toString();
        ApiState seed = new ApiState();

        Region urban = createRegion("region-wuzhong-urban", "城南综合服务片区", "WUZHONG_URBAN", "覆盖城南医院、商圈与轨交换乘停车需求");
        Region mudu = createRegion("region-wuzhong-mudu", "木渎文旅片区", "MUDU_TOURISM", "覆盖木渎古镇、灵岩山与换乘停车场");
        Region taihu = createRegion("region-wuzhong-taihu", "太湖度假区片区", "TAIHU_RESORT", "覆盖太湖度假商业与休闲停车场");
        seed.regions.addAll(List.of(urban, mudu, taihu));

        AirportStation airportUrban = createAirport("airport-wuzhong-01", "城南综合服务无人机机场", urban.id, 31.2556, 120.6248, 3800, "online");
        AirportStation airportMudu = createAirport("airport-mudu-01", "木渎文旅无人机机场", mudu.id, 31.2618, 120.5212, 4200, "online");
        AirportStation airportTaihu = createAirport("airport-taihu-01", "太湖度假区无人机机场", taihu.id, 31.2299, 120.4362, 4800, "online");
        seed.airportStations.addAll(List.of(airportUrban, airportMudu, airportTaihu));

        ParkingLot lotHospital = createParkingLot("lot-wz-hospital", "吴中人民医院停车场", urban.id, "苏州市吴中区东吴北路61号", 31.2709, 120.6248, 444, 97, List.of("医院", "刚需停车", "全天开放"), now);
        ParkingLot lotWanda = createParkingLot("lot-wz-wanda", "吴中万达广场停车场", urban.id, "苏州市吴中区石湖西路188号", 31.2558, 120.6127, 500, 136, List.of("商圈", "地铁周边", "综合体"), now);
        ParkingLot lotTianjie = createParkingLot("lot-dw-tianjie", "龙湖苏州东吴天街停车场", urban.id, "苏州市吴中区东吴南路179号", 31.2318, 120.6436, 420, 118, List.of("商业综合体", "夜间高峰"), now);
        ParkingLot lotKaima = createParkingLot("lot-mudu-kaima", "木渎凯马大街停车场", mudu.id, "苏州市吴中区木渎凯马广场南侧", 31.2621, 120.5215, 174, 42, List.of("木渎商圈", "公共停车"), now);
        ParkingLot lotVisitor = createParkingLot("lot-mudu-visitor", "木渎古镇游客中心停车场", mudu.id, "苏州市吴中区山塘街188号", 31.2608, 120.5174, 320, 61, List.of("景区", "游客中心"), now);
        ParkingLot lotP6 = createParkingLot("lot-wangjia-p6", "旺家村P6停车场", mudu.id, "苏州市吴中区木渎站东侧花苑东路附近", 31.2557, 120.5268, 800, 223, List.of("接驳换乘", "大容量"), now);
        ParkingLot lotLingyan = createParkingLot("lot-lingyan", "苏州灵岩山停车场", mudu.id, "苏州市吴中区中山西路39号", 31.2648, 120.5116, 180, 39, List.of("景区", "周末高峰"), now);
        ParkingLot lotTaihuMall = createParkingLot("lot-taihu-mall", "太湖中心Mall停车场", taihu.id, "苏州市吴中区香山路16号540幢", 31.2265, 120.4386, 360, 154, List.of("度假区", "商业休闲"), now);
        seed.parkingLots.addAll(List.of(
            lotHospital,
            lotWanda,
            lotTianjie,
            lotKaima,
            lotVisitor,
            lotP6,
            lotLingyan,
            lotTaihuMall
        ));

        seed.coverageLinks.addAll(List.of(
            new CoverageLink(airportUrban.id, lotHospital.id),
            new CoverageLink(airportUrban.id, lotWanda.id),
            new CoverageLink(airportUrban.id, lotTianjie.id),
            new CoverageLink(airportMudu.id, lotKaima.id),
            new CoverageLink(airportMudu.id, lotVisitor.id),
            new CoverageLink(airportMudu.id, lotP6.id),
            new CoverageLink(airportMudu.id, lotLingyan.id),
            new CoverageLink(airportTaihu.id, lotTaihuMall.id)
        ));

        seed.parkingSpaces.addAll(createParkingSpaces(lotHospital, 30, 6, 5, 0.00005, 0.00004, 22));
        seed.parkingSpaces.addAll(createParkingSpaces(lotWanda, 32, 8, 4, 0.00005, 0.00004, 23));
        seed.parkingSpaces.addAll(createParkingSpaces(lotTianjie, 28, 7, 4, 0.00005, 0.00004, 20));
        seed.parkingSpaces.addAll(createParkingSpaces(lotKaima, 24, 6, 4, 0.00005, 0.00004, 18));
        seed.parkingSpaces.addAll(createParkingSpaces(lotVisitor, 28, 7, 4, 0.00005, 0.00004, 21));
        seed.parkingSpaces.addAll(createParkingSpaces(lotP6, 36, 9, 4, 0.00005, 0.00004, 26));
        seed.parkingSpaces.addAll(createParkingSpaces(lotLingyan, 20, 5, 4, 0.00005, 0.00004, 16));
        seed.parkingSpaces.addAll(createParkingSpaces(lotTaihuMall, 24, 6, 4, 0.00005, 0.00004, 14));

        seed.routes.addAll(List.of(
            createRoute("route-wuzhong-core", "城南综合停车巡检航线", "覆盖吴中人民医院停车场、吴中万达广场停车场与东吴天街停车场", now, List.of(
                new RoutePoint(31.2706, 120.6241, 45, 6),
                new RoutePoint(31.2635, 120.6203, 45, 6),
                new RoutePoint(31.2558, 120.6127, 42, 5),
                new RoutePoint(31.2448, 120.6257, 42, 5),
                new RoutePoint(31.2318, 120.6436, 40, 4)
            )),
            createRoute("route-mudu-tour", "木渎文旅停车巡检航线", "覆盖木渎凯马大街停车场、木渎古镇游客中心停车场、旺家村P6停车场与灵岩山停车场", now, List.of(
                new RoutePoint(31.2558, 120.5264, 45, 6),
                new RoutePoint(31.2595, 120.5229, 42, 5),
                new RoutePoint(31.2621, 120.5215, 40, 5),
                new RoutePoint(31.2608, 120.5174, 38, 4),
                new RoutePoint(31.2648, 120.5116, 38, 4)
            )),
            createRoute("route-taihu-resort", "太湖度假区停车巡检航线", "覆盖太湖中心Mall停车场周边度假商业停车需求", now, List.of(
                new RoutePoint(31.2288, 120.4401, 48, 6),
                new RoutePoint(31.2274, 120.4392, 46, 5),
                new RoutePoint(31.2265, 120.4386, 44, 5),
                new RoutePoint(31.2256, 120.4378, 42, 4)
            ))
        ));

        seed.devices.addAll(List.of(
            createDevice("device-flight-01", "吴中巡检无人机 01", "WZ-FLY-01", airportUrban.id, DeviceStatus.ready, 92, 16, now, "mission-demo-001"),
            createDevice("device-flight-02", "吴中巡检无人机 02", "WZ-FLY-02", airportMudu.id, DeviceStatus.executing, 78, 14, now, "mission-demo-002"),
            createDevice("device-flight-03", "吴中巡检无人机 03", "WZ-FLY-03", airportTaihu.id, DeviceStatus.ready, 86, 15, now, null),
            createDevice("device-flight-04", "吴中巡检无人机 04", "WZ-FLY-04", airportMudu.id, DeviceStatus.offline, 0, 0, null, null)
        ));

        seed.missions.addAll(List.of(
            createMission("mission-demo-001", "城南高峰停车巡检", "关注医院与商圈停车压力变化", "route-wuzhong-core", "device-flight-01", lotHospital.id, MissionStatus.ready, now),
            createMission("mission-demo-002", "木渎文旅保障巡检", "服务木渎古镇、灵岩山节假日停车保障", "route-mudu-tour", "device-flight-02", lotVisitor.id, MissionStatus.running, now),
            createMission("mission-demo-003", "太湖度假区晚间巡检", "关注太湖中心Mall晚间停车周转情况", "route-taihu-resort", null, lotTaihuMall.id, MissionStatus.draft, now)
        ));

        seed.telemetry.addAll(List.of(
            createTelemetry("telemetry-001", "device-flight-02", "mission-demo-002", 31.2598, 120.5198, 42.0, 5.6, -0.2, 78, 14, "GPS_WAYPOINT", now),
            createTelemetry("telemetry-002", "device-flight-01", "mission-demo-001", 31.2635, 120.6203, 40.0, 4.9, 0.1, 92, 16, "STANDBY", now)
        ));

        seed.events.addAll(List.of(
            createEvent("event-001", "mission-demo-002", "device-flight-02", "warning", "木渎古镇游客中心停车场接近满载，建议联动旺家村P6停车场分流", MissionStatus.running, now),
            createEvent("event-002", "mission-demo-001", "device-flight-01", "info", "城南综合服务无人机机场已完成起飞前检查", MissionStatus.ready, now)
        ));

        seed.mediaFiles.addAll(List.of(
            createMedia("media-001", "mission-demo-002", "device-flight-02", MediaType.photo, "lot-mudu-visitor-scan-01.jpg", "https://example.com/media/lot-mudu-visitor-scan-01.jpg", now),
            createMedia("media-002", "mission-demo-001", "device-flight-01", MediaType.photo, "lot-wz-hospital-scan-01.jpg", "https://example.com/media/lot-wz-hospital-scan-01.jpg", now)
        ));

        seed.detections.addAll(List.of(
            createDetection("detect-001", "mission-demo-002", "device-flight-02", "lot-mudu-visitor-space-003", "occupied", 0.96, "media-001", now),
            createDetection("detect-002", "mission-demo-001", "device-flight-01", "lot-wz-hospital-space-018", "free", 0.93, "media-002", now)
        ));

        seed.opinions.addAll(List.of(
            createOpinion("opinion-001", lotHospital, "吴中市民", "市民反馈", "停车场收费", 3.8, "高峰时段排队稍长，但医院门口导视比较清晰，希望空位更新能更及时。", now),
            createOpinion("opinion-002", lotHospital, "就诊家属", "旅客反馈", "步行接驳", 4.2, "从停车位到门诊楼步行路线还算清楚，如果能标出电梯和门诊入口会更方便。", now),
            createOpinion("opinion-003", lotWanda, "商圈用户", "市民反馈", "周围环境", 4.4, "整体环境比较整洁，周边餐饮和商业配套方便，周末晚高峰还是会出现入口拥堵。", now),
            createOpinion("opinion-004", lotWanda, "周末游客", "旅客反馈", "导航准确", 4.1, "导航能到停车场附近，但具体入口提示还可以更明确，第一次来会稍微绕一下。", now),
            createOpinion("opinion-005", lotTianjie, "下班通勤用户", "市民反馈", "夜间导视", 4.0, "夜间灯光不错，但地下与地面入口的导视信息不够连续，回场找车位区有点花时间。", now),
            createOpinion("opinion-006", lotKaima, "木渎居民", "市民反馈", "停车效率", 4.3, "平时找位速度还不错，节假日前后车流会增大，建议高峰时增加临时分流提醒。", now),
            createOpinion("opinion-007", lotVisitor, "古镇游客", "旅客反馈", "景区高峰", 3.7, "节假日游客很多，停车后步行进景区比较方便，但收费和排队信息希望能提前看到。", now),
            createOpinion("opinion-008", lotVisitor, "外地游客", "旅客反馈", "停车场收费", 3.9, "价格还能接受，关键是想提前知道封顶和是否有空位，不然容易到了还得再找其他场。", now),
            createOpinion("opinion-009", lotP6, "换乘用户", "市民反馈", "步行接驳", 4.5, "作为大容量接驳停车场很实用，车位比较充足，如果接驳车信息再直观点会更好。", now),
            createOpinion("opinion-010", lotLingyan, "登山游客", "旅客反馈", "周围环境", 4.6, "停车场周边环境不错，去景区步行距离也能接受，建议增加高峰期剩余车位提示。", now),
            createOpinion("opinion-011", lotTaihuMall, "度假区游客", "旅客反馈", "导航准确", 4.2, "导航整体准确，周边环境舒适，晚间活动多的时候建议把出口分流提示做得更明显。", now),
            createOpinion("opinion-012", lotP6, "周边居民", "市民反馈", "余位更新", 4.4, "推荐页如果能优先把这种大容量停车场排前面，临时找位会轻松很多。", now)
        ));

        refreshParkingAggregates(seed);
        return seed;
    }

    private Region createRegion(String id, String name, String code, String description) {
        Region item = new Region();
        item.id = id;
        item.name = name;
        item.code = code;
        item.description = description;
        return item;
    }

    private AirportStation createAirport(
        String id,
        String name,
        String regionId,
        double latitude,
        double longitude,
        int coverageRadiusMeters,
        String status
    ) {
        AirportStation item = new AirportStation();
        item.id = id;
        item.name = name;
        item.regionId = regionId;
        item.latitude = latitude;
        item.longitude = longitude;
        item.coverageRadiusMeters = coverageRadiusMeters;
        item.status = status;
        return item;
    }

    private ParkingLot createParkingLot(
        String id,
        String name,
        String regionId,
        String address,
        double latitude,
        double longitude,
        int totalSpaces,
        int availableSpaces,
        List<String> tags,
        String lastInspectionAt
    ) {
        ParkingLot item = new ParkingLot();
        item.id = id;
        item.name = name;
        item.regionId = regionId;
        item.address = address;
        item.latitude = latitude;
        item.longitude = longitude;
        item.totalSpaces = totalSpaces;
        item.availableSpaces = availableSpaces;
        item.occupancyRate = totalSpaces == 0 ? 0 : (totalSpaces - availableSpaces) * 100.0 / totalSpaces;
        item.status = item.occupancyRate >= 85 ? "busy" : "normal";
        item.tags = new ArrayList<>(tags);
        item.lastInspectionAt = lastInspectionAt;
        return item;
    }

    private List<ParkingSpace> createParkingSpaces(
        ParkingLot lot,
        int count,
        int columns,
        int rows,
        double width,
        double height,
        int occupiedCount
    ) {
        List<ParkingSpace> spaces = new ArrayList<>();
        int actualCount = Math.min(count, columns * rows);
        for (int index = 0; index < actualCount; index++) {
            int row = index / columns;
            int column = index % columns;
            double baseLat = lot.latitude + row * height;
            double baseLon = lot.longitude + column * width;

            ParkingSpace item = new ParkingSpace();
            item.id = lot.id + "-space-" + String.format("%03d", index + 1);
            item.parkingLotId = lot.id;
            item.code = lot.name.substring(0, 1) + "-" + (index + 1);
            item.status = index < occupiedCount ? "occupied" : "free";
            item.updatedAt = lot.lastInspectionAt;
            item.polygon = List.of(
                new RoutePoint(baseLat, baseLon, 0, 0),
                new RoutePoint(baseLat, baseLon + width, 0, 0),
                new RoutePoint(baseLat + height, baseLon + width, 0, 0),
                new RoutePoint(baseLat + height, baseLon, 0, 0)
            );
            spaces.add(item);
        }
        return spaces;
    }

    private RouteTemplate createRoute(String id, String name, String description, String now, List<RoutePoint> points) {
        RouteTemplate item = new RouteTemplate();
        item.id = id;
        item.name = name;
        item.description = description;
        item.createdAt = now;
        item.updatedAt = now;
        item.waypoints = points;
        return item;
    }

    private Device createDevice(
        String id,
        String name,
        String code,
        String airportStationId,
        DeviceStatus status,
        int batteryPercent,
        int satelliteCount,
        String lastSeenAt,
        String currentMissionId
    ) {
        Device item = new Device();
        item.id = id;
        item.name = name;
        item.code = code;
        item.airportStationId = airportStationId;
        item.status = status;
        item.batteryPercent = batteryPercent;
        item.satelliteCount = satelliteCount;
        item.lastSeenAt = lastSeenAt;
        item.currentMissionId = currentMissionId;
        item.followEnabled = true;
        return item;
    }

    private Mission createMission(
        String id,
        String name,
        String description,
        String routeTemplateId,
        String assignedDeviceId,
        String parkingLotId,
        MissionStatus status,
        String now
    ) {
        Mission item = new Mission();
        item.id = id;
        item.name = name;
        item.description = description;
        item.routeTemplateId = routeTemplateId;
        item.assignedDeviceId = assignedDeviceId;
        item.parkingLotId = parkingLotId;
        item.status = status;
        item.plannedAt = now;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    private TelemetryPoint createTelemetry(
        String id,
        String deviceId,
        String missionId,
        double lat,
        double lon,
        double altitudeMeters,
        double speedMetersPerSecond,
        double verticalSpeedMetersPerSecond,
        int batteryPercent,
        int satelliteCount,
        String flightMode,
        String timestamp
    ) {
        TelemetryPoint item = new TelemetryPoint();
        item.id = id;
        item.deviceId = deviceId;
        item.missionId = missionId;
        item.lat = lat;
        item.lon = lon;
        item.altitudeMeters = altitudeMeters;
        item.speedMetersPerSecond = speedMetersPerSecond;
        item.verticalSpeedMetersPerSecond = verticalSpeedMetersPerSecond;
        item.batteryPercent = batteryPercent;
        item.satelliteCount = satelliteCount;
        item.flightMode = flightMode;
        item.timestamp = timestamp;
        return item;
    }

    private MissionEvent createEvent(
        String id,
        String missionId,
        String deviceId,
        String type,
        String message,
        MissionStatus status,
        String timestamp
    ) {
        MissionEvent item = new MissionEvent();
        item.id = id;
        item.missionId = missionId;
        item.deviceId = deviceId;
        item.type = type;
        item.message = message;
        item.status = status;
        item.timestamp = timestamp;
        return item;
    }

    private MediaFileItem createMedia(
        String id,
        String missionId,
        String deviceId,
        MediaType type,
        String name,
        String url,
        String createdAt
    ) {
        MediaFileItem item = new MediaFileItem();
        item.id = id;
        item.missionId = missionId;
        item.deviceId = deviceId;
        item.type = type;
        item.name = name;
        item.url = url;
        item.createdAt = createdAt;
        return item;
    }

    private DetectionItem createDetection(
        String id,
        String missionId,
        String deviceId,
        String parkingSpaceId,
        String label,
        double score,
        String mediaId,
        String createdAt
    ) {
        DetectionItem item = new DetectionItem();
        item.id = id;
        item.missionId = missionId;
        item.deviceId = deviceId;
        item.parkingSpaceId = parkingSpaceId;
        item.label = label;
        item.score = score;
        item.mediaId = mediaId;
        item.createdAt = createdAt;
        return item;
    }

    private ParkingOpinion createOpinion(
        String id,
        ParkingLot lot,
        String authorName,
        String source,
        String topic,
        double rating,
        String content,
        String createdAt
    ) {
        ParkingOpinion item = new ParkingOpinion();
        item.id = id;
        item.parkingLotId = lot.id;
        item.parkingLotName = lot.name;
        item.authorName = authorName;
        item.source = source;
        item.topic = topic;
        item.rating = rating;
        item.sentiment = rating >= 4.3 ? "正向" : rating >= 4.0 ? "总体较好" : "关注项";
        item.content = content;
        item.imageUrls = new ArrayList<>();
        item.createdAt = createdAt;
        return item;
    }

    private void refreshParkingAggregates(ApiState state) {
        for (ParkingLot lot : state.parkingLots) {
            List<ParkingSpace> spaces = state.parkingSpaces.stream()
                .filter(space -> lot.id.equals(space.parkingLotId))
                .toList();
            if (!spaces.isEmpty()) {
                int sampleTotal = spaces.size();
                int sampleFree = (int) spaces.stream().filter(space -> "free".equals(space.status)).count();
                if (lot.totalSpaces <= 0) {
                    lot.totalSpaces = sampleTotal;
                }
                if (lot.availableSpaces < 0 || lot.availableSpaces > lot.totalSpaces) {
                    lot.availableSpaces = Math.min(sampleFree, lot.totalSpaces);
                }
            }
            lot.occupancyRate = lot.totalSpaces == 0 ? 0 : (lot.totalSpaces - lot.availableSpaces) * 100.0 / lot.totalSpaces;
            lot.status = lot.occupancyRate >= 85 ? "busy" : "normal";
            if (lot.lastInspectionAt == null) {
                lot.lastInspectionAt = Instant.now().toString();
            }
        }
    }

    public static String nextId() {
        return UUID.randomUUID().toString();
    }
}
