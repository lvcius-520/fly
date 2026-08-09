package com.fly.server;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
class SimulatorService {
    private final DataStoreService store;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, SimState> states = new ConcurrentHashMap<>();

    SimulatorService(DataStoreService store) {
        this.store = store;
    }

    public boolean isRunning(String deviceId) {
        return states.containsKey(deviceId);
    }

    public synchronized void start(String deviceId) {
        if (states.containsKey(deviceId)) {
            return;
        }

        Device device = store.snapshot().devices.stream()
            .filter(item -> item.id.equals(deviceId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("设备不存在"));

        store.mutate(draft -> {
            Device current = draft.devices.stream()
                .filter(item -> item.id.equals(deviceId))
                .findFirst()
                .orElse(null);
            if (current == null) {
                return null;
            }

            current.status = DeviceStatus.executing;
            current.lastSeenAt = Instant.now().toString();

            Mission mission = findActiveMission(draft, deviceId);
            if (mission != null) {
                mission.status = MissionStatus.running;
                mission.updatedAt = Instant.now().toString();
                current.currentMissionId = mission.id;
                draft.events.add(0, createEvent(deviceId, mission.id, "mission_status", "模拟执行已启动", MissionStatus.running));
                trimEvents(draft);
            } else {
                current.currentMissionId = device.currentMissionId;
            }
            return null;
        });

        AtomicInteger waypointIndex = new AtomicInteger(0);
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> tick(deviceId),
            0,
            2,
            TimeUnit.SECONDS
        );
        states.put(deviceId, new SimState(future, waypointIndex));
    }

    public synchronized void stop(String deviceId) {
        SimState state = states.remove(deviceId);
        if (state != null) {
            state.future.cancel(false);
        }

        store.mutate(draft -> {
            Device device = draft.devices.stream()
                .filter(item -> item.id.equals(deviceId))
                .findFirst()
                .orElse(null);
            if (device == null) {
                return null;
            }
            device.status = DeviceStatus.ready;
            device.lastSeenAt = Instant.now().toString();
            draft.events.add(0, createEvent(deviceId, device.currentMissionId, "info", "模拟执行已停止", null));
            trimEvents(draft);
            return null;
        });
    }

    private void tick(String deviceId) {
        SimState simState = states.get(deviceId);
        if (simState == null) {
            return;
        }

        TickOutcome outcome = store.mutate(draft -> {
            Device device = draft.devices.stream()
                .filter(item -> item.id.equals(deviceId))
                .findFirst()
                .orElse(null);
            if (device == null) {
                return new TickOutcome(true);
            }

            Mission mission = findActiveMission(draft, deviceId);
            if (mission == null) {
                device.status = DeviceStatus.ready;
                return new TickOutcome(true);
            }

            RouteTemplate route = draft.routes.stream()
                .filter(item -> item.id.equals(mission.routeTemplateId))
                .findFirst()
                .orElse(null);
            if (route == null || route.waypoints.isEmpty()) {
                mission.status = MissionStatus.failed;
                mission.updatedAt = Instant.now().toString();
                device.status = DeviceStatus.ready;
                draft.events.add(0, createEvent(deviceId, mission.id, "warning", "模拟执行失败：航线不存在", MissionStatus.failed));
                trimEvents(draft);
                return new TickOutcome(true);
            }

            int index = Math.min(simState.waypointIndex.get(), route.waypoints.size() - 1);
            RoutePoint waypoint = route.waypoints.get(index);
            String now = Instant.now().toString();

            TelemetryPoint telemetry = new TelemetryPoint();
            telemetry.id = DataStoreService.nextId();
            telemetry.deviceId = deviceId;
            telemetry.missionId = mission.id;
            telemetry.timestamp = now;
            telemetry.lat = waypoint.lat;
            telemetry.lon = waypoint.lon;
            telemetry.altitudeMeters = waypoint.altitudeMeters;
            telemetry.speedMetersPerSecond = waypoint.speedMetersPerSecond;
            telemetry.verticalSpeedMetersPerSecond = index % 2 == 0 ? 0.3 : -0.2;
            telemetry.batteryPercent = Math.max(18, device.batteryPercent - 1);
            telemetry.satelliteCount = Math.max(10, device.satelliteCount);
            telemetry.flightMode = "GPS_WAYPOINT";

            draft.telemetry.add(0, telemetry);
            trimTelemetry(draft);

            device.batteryPercent = telemetry.batteryPercent;
            device.satelliteCount = telemetry.satelliteCount;
            device.lastSeenAt = telemetry.timestamp;
            device.status = DeviceStatus.executing;
            device.currentMissionId = mission.id;
            mission.status = MissionStatus.running;
            mission.updatedAt = telemetry.timestamp;

            boolean isLast = index >= route.waypoints.size() - 1;
            if (isLast) {
                mission.status = MissionStatus.completed;
                mission.updatedAt = telemetry.timestamp;
                device.status = DeviceStatus.ready;
                draft.events.add(0, createEvent(deviceId, mission.id, "mission_status", "模拟执行完成", MissionStatus.completed));
                trimEvents(draft);
            }

            return new TickOutcome(isLast);
        });

        if (outcome.done) {
            stop(deviceId);
            return;
        }

        simState.waypointIndex.incrementAndGet();
    }

    private Mission findActiveMission(ApiState state, String deviceId) {
        return state.missions.stream()
            .filter(item -> deviceId.equals(item.assignedDeviceId))
            .filter(item -> item.status == MissionStatus.ready
                || item.status == MissionStatus.running
                || item.status == MissionStatus.paused
                || item.status == MissionStatus.pending)
            .findFirst()
            .orElse(null);
    }

    private MissionEvent createEvent(String deviceId, String missionId, String type, String message, MissionStatus status) {
        MissionEvent event = new MissionEvent();
        event.id = DataStoreService.nextId();
        event.deviceId = deviceId;
        event.missionId = missionId;
        event.type = type;
        event.message = message;
        event.status = status;
        event.timestamp = Instant.now().toString();
        return event;
    }

    private void trimTelemetry(ApiState state) {
        if (state.telemetry.size() > 200) {
            state.telemetry = new java.util.ArrayList<>(state.telemetry.subList(0, 200));
        }
    }

    private void trimEvents(ApiState state) {
        if (state.events.size() > 200) {
            state.events = new java.util.ArrayList<>(state.events.subList(0, 200));
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        states.values().forEach(item -> item.future.cancel(true));
        states.clear();
        executor.shutdownNow();
    }

    private record SimState(ScheduledFuture<?> future, AtomicInteger waypointIndex) {
    }

    private record TickOutcome(boolean done) {
    }
}
