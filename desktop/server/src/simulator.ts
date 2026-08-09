import { randomUUID } from "node:crypto";
import type { ApiState, MissionEvent, MissionStatus, TelemetryPoint } from "@fly/shared";
import type { DataStore } from "./store.js";

type BroadcastFn = (event: string, payload: unknown) => void;

interface SimState {
  timer: NodeJS.Timeout;
  waypointIndex: number;
}

export class FlightSimulator {
  private readonly states = new Map<string, SimState>();

  constructor(
    private readonly store: DataStore,
    private readonly broadcast: BroadcastFn
  ) {}

  isRunning(deviceId: string): boolean {
    return this.states.has(deviceId);
  }

  async start(deviceId: string): Promise<void> {
    if (this.states.has(deviceId)) return;

    await this.store.mutate((draft) => {
      const device = draft.devices.find((item) => item.id === deviceId);
      if (!device) return;
      device.status = "executing";
      device.lastSeenAt = new Date().toISOString();

      const mission = findActiveMission(draft, deviceId);
      if (mission) {
        mission.status = "running";
        mission.updatedAt = new Date().toISOString();
        device.currentMissionId = mission.id;
        draft.events.unshift(createEvent(deviceId, mission.id, "mission_status", "模拟执行已启动", "running"));
      }
    });

    const timer = setInterval(() => {
      void this.tick(deviceId);
    }, 2000);

    this.states.set(deviceId, { timer, waypointIndex: 0 });
  }

  async stop(deviceId: string): Promise<void> {
    const sim = this.states.get(deviceId);
    if (sim) {
      clearInterval(sim.timer);
      this.states.delete(deviceId);
    }

    await this.store.mutate((draft) => {
      const device = draft.devices.find((item) => item.id === deviceId);
      if (!device) return;
      device.status = "ready";
      device.lastSeenAt = new Date().toISOString();
      draft.events.unshift(createEvent(deviceId, device.currentMissionId, "info", "模拟执行已停止"));
    });

    this.broadcast("simulator:stopped", { deviceId });
  }

  private async tick(deviceId: string): Promise<void> {
    const sim = this.states.get(deviceId);
    if (!sim) return;

    const outcome = await this.store.mutate((draft) => {
      const device = draft.devices.find((item) => item.id === deviceId);
      if (!device) {
        return { done: true, telemetry: null as TelemetryPoint | null, missionStatus: null as MissionStatus | null };
      }

      const mission = findActiveMission(draft, deviceId);
      if (!mission) {
        device.status = "ready";
        return { done: true, telemetry: null as TelemetryPoint | null, missionStatus: null as MissionStatus | null };
      }

      const route = draft.routes.find((item) => item.id === mission.routeTemplateId);
      if (!route || route.waypoints.length === 0) {
        mission.status = "failed";
        mission.updatedAt = new Date().toISOString();
        device.status = "ready";
        draft.events.unshift(createEvent(deviceId, mission.id, "warning", "模拟执行失败：航线不存在", "failed"));
        return { done: true, telemetry: null as TelemetryPoint | null, missionStatus: "failed" as MissionStatus };
      }

      const waypoint = route.waypoints[Math.min(sim.waypointIndex, route.waypoints.length - 1)];
      const telemetry: TelemetryPoint = {
        id: randomUUID(),
        deviceId,
        missionId: mission.id,
        timestamp: new Date().toISOString(),
        lat: waypoint.lat,
        lon: waypoint.lon,
        altitudeMeters: waypoint.altitudeMeters,
        speedMetersPerSecond: waypoint.speedMetersPerSecond,
        verticalSpeedMetersPerSecond: sim.waypointIndex % 2 === 0 ? 0.3 : -0.2,
        batteryPercent: Math.max(18, device.batteryPercent - 1),
        satelliteCount: Math.max(10, device.satelliteCount),
        flightMode: "GPS_WAYPOINT"
      };

      draft.telemetry.unshift(telemetry);
      draft.telemetry = draft.telemetry.slice(0, 200);
      device.batteryPercent = telemetry.batteryPercent;
      device.satelliteCount = telemetry.satelliteCount;
      device.lastSeenAt = telemetry.timestamp;
      device.status = "executing";
      device.currentMissionId = mission.id;
      mission.status = "running";
      mission.updatedAt = telemetry.timestamp;

      const isLast = sim.waypointIndex >= route.waypoints.length - 1;
      if (isLast) {
        mission.status = "completed";
        mission.updatedAt = telemetry.timestamp;
        device.status = "ready";
        draft.events.unshift(createEvent(deviceId, mission.id, "mission_status", "模拟执行完成", "completed"));
      }

      return {
        done: isLast,
        telemetry,
        missionStatus: isLast ? ("completed" as MissionStatus) : ("running" as MissionStatus)
      };
    });

    if (outcome.telemetry) {
      this.broadcast("telemetry:update", outcome.telemetry);
    }
    if (outcome.missionStatus) {
      this.broadcast("mission:update", { deviceId, status: outcome.missionStatus });
    }
    if (outcome.done) {
      await this.stop(deviceId);
      return;
    }

    sim.waypointIndex += 1;
  }
}

function findActiveMission(state: ApiState, deviceId: string) {
  return state.missions.find((item) =>
    item.assignedDeviceId === deviceId &&
    ["ready", "running", "paused", "pending"].includes(item.status)
  );
}

function createEvent(
  deviceId: string,
  missionId: string | null,
  type: MissionEvent["type"],
  message: string,
  status?: MissionStatus
): MissionEvent {
  return {
    id: randomUUID(),
    deviceId,
    missionId,
    type,
    message,
    status,
    timestamp: new Date().toISOString()
  };
}
