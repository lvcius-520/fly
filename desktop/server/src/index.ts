import { randomUUID } from "node:crypto";
import express from "express";
import cors from "cors";
import { createServer } from "node:http";
import { Server as SocketIOServer } from "socket.io";
import { z } from "zod";
import type {
  DashboardOverview,
  Device,
  LoginPayload,
  MediaFileItem,
  Mission,
  MissionEvent,
  MobileDetectionPayload,
  MobileMediaPayload,
  MobileMissionEventPayload,
  MobileSyncPayload,
  MobileTelemetryPayload,
  RouteTemplate
} from "@fly/shared";
import { DataStore } from "./store.js";
import { FlightSimulator } from "./simulator.js";

const app = express();
const httpServer = createServer(app);
const io = new SocketIOServer(httpServer, {
  cors: {
    origin: "*"
  }
});

const store = new DataStore();
const simulator = new FlightSimulator(store, (event, payload) => io.emit(event, payload));

app.use(cors());
app.use(express.json({ limit: "2mb" }));

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1)
}) satisfies z.ZodType<LoginPayload>;

const routeSchema = z.object({
  name: z.string().min(1),
  description: z.string().default(""),
  waypoints: z.array(z.object({
    lat: z.number(),
    lon: z.number(),
    altitudeMeters: z.number(),
    speedMetersPerSecond: z.number()
  })).min(2)
});

const missionSchema = z.object({
  name: z.string().min(1),
  description: z.string().default(""),
  routeTemplateId: z.string().min(1),
  assignedDeviceId: z.string().nullable(),
  plannedAt: z.string().min(1)
});

const missionStatusSchema = z.object({
  status: z.enum(["draft", "pending", "ready", "running", "paused", "completed", "failed"])
});

const mobileSyncSchema = z.object({
  deviceId: z.string().min(1)
}) satisfies z.ZodType<MobileSyncPayload>;

const mobileTelemetrySchema = z.object({
  deviceId: z.string().min(1),
  missionId: z.string().nullable(),
  lat: z.number(),
  lon: z.number(),
  altitudeMeters: z.number(),
  speedMetersPerSecond: z.number(),
  verticalSpeedMetersPerSecond: z.number(),
  batteryPercent: z.number(),
  satelliteCount: z.number(),
  flightMode: z.string().min(1)
}) satisfies z.ZodType<MobileTelemetryPayload>;

const mobileEventSchema = z.object({
  deviceId: z.string().min(1),
  missionId: z.string().nullable(),
  type: z.enum(["mission_status", "camera", "warning", "info"]),
  message: z.string().min(1),
  status: z.enum(["draft", "pending", "ready", "running", "paused", "completed", "failed"]).optional()
}) satisfies z.ZodType<MobileMissionEventPayload>;

const mobileMediaSchema = z.object({
  missionId: z.string().nullable(),
  deviceId: z.string().min(1),
  type: z.enum(["photo", "video"]),
  name: z.string().min(1),
  url: z.string().min(1)
}) satisfies z.ZodType<MobileMediaPayload>;

const mobileDetectionSchema = z.object({
  missionId: z.string().nullable(),
  deviceId: z.string().min(1),
  label: z.string().min(1),
  score: z.number().min(0).max(1),
  mediaId: z.string().nullable()
}) satisfies z.ZodType<MobileDetectionPayload>;

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, service: "fly-desktop-server" });
});

app.post("/api/auth/login", (req, res) => {
  const payload = loginSchema.parse(req.body);
  res.json({
    token: `mock-token-${payload.username}`,
    username: payload.username,
    displayName: payload.username === "admin" ? "FLY 管理员" : payload.username
  });
});

app.get("/api/dashboard/overview", (_req, res) => {
  const state = store.snapshot();
  const overview: DashboardOverview = {
    deviceCount: state.devices.length,
    onlineDeviceCount: state.devices.filter((item) => item.status !== "offline").length,
    missionCount: state.missions.length,
    activeMissionCount: state.missions.filter((item) => ["ready", "running", "paused"].includes(item.status)).length,
    mediaCount: state.mediaFiles.length,
    detectionCount: state.detections.length
  };
  res.json(overview);
});

app.get("/api/routes", (_req, res) => {
  res.json(store.snapshot().routes);
});

app.post("/api/routes", async (req, res) => {
  const payload = routeSchema.parse(req.body);
  const route = await store.mutate((draft) => {
    const now = new Date().toISOString();
    const item: RouteTemplate = {
      id: randomUUID(),
      name: payload.name,
      description: payload.description,
      waypoints: payload.waypoints,
      createdAt: now,
      updatedAt: now
    };
    draft.routes.unshift(item);
    return item;
  });
  io.emit("route:update", route);
  res.status(201).json(route);
});

app.get("/api/missions", (_req, res) => {
  res.json(store.snapshot().missions);
});

app.post("/api/missions", async (req, res) => {
  const payload = missionSchema.parse(req.body);
  const mission = await store.mutate((draft) => {
    const now = new Date().toISOString();
    const item: Mission = {
      id: randomUUID(),
      name: payload.name,
      description: payload.description,
      routeTemplateId: payload.routeTemplateId,
      assignedDeviceId: payload.assignedDeviceId,
      status: payload.assignedDeviceId ? "ready" : "draft",
      plannedAt: payload.plannedAt,
      createdAt: now,
      updatedAt: now
    };
    draft.missions.unshift(item);

    if (payload.assignedDeviceId) {
      const device = draft.devices.find((entry) => entry.id === payload.assignedDeviceId);
      if (device) {
        device.currentMissionId = item.id;
        device.status = "ready";
        device.lastSeenAt = now;
      }
    }
    return item;
  });
  io.emit("mission:update", mission);
  res.status(201).json(mission);
});

app.patch("/api/missions/:id/status", async (req, res) => {
  const payload = missionStatusSchema.parse(req.body);
  const mission = await store.mutate((draft) => {
    const item = draft.missions.find((entry) => entry.id === req.params.id);
    if (!item) return null;
    item.status = payload.status;
    item.updatedAt = new Date().toISOString();
    return item;
  });

  if (!mission) {
    res.status(404).json({ message: "任务不存在" });
    return;
  }

  io.emit("mission:update", mission);
  res.json(mission);
});

app.get("/api/devices", (_req, res) => {
  const state = store.snapshot();
  const payload = state.devices.map((device) => ({
    ...device,
    simulatorRunning: simulator.isRunning(device.id),
    latestTelemetry: state.telemetry.find((item) => item.deviceId === device.id) ?? null
  }));
  res.json(payload);
});

app.post("/api/devices/:id/simulator/start", async (req, res) => {
  const device = store.snapshot().devices.find((item) => item.id === req.params.id);
  if (!device) {
    res.status(404).json({ message: "设备不存在" });
    return;
  }
  await simulator.start(device.id);
  res.json({ ok: true });
});

app.post("/api/devices/:id/simulator/stop", async (req, res) => {
  await simulator.stop(req.params.id);
  res.json({ ok: true });
});

app.get("/api/telemetry", (req, res) => {
  const limit = Number(req.query.limit ?? 40);
  const deviceId = typeof req.query.deviceId === "string" ? req.query.deviceId : undefined;
  const items = store.snapshot().telemetry
    .filter((item) => !deviceId || item.deviceId === deviceId)
    .slice(0, limit);
  res.json(items);
});

app.get("/api/events", (req, res) => {
  const limit = Number(req.query.limit ?? 60);
  const missionId = typeof req.query.missionId === "string" ? req.query.missionId : undefined;
  const items = store.snapshot().events
    .filter((item) => !missionId || item.missionId === missionId)
    .slice(0, limit);
  res.json(items);
});

app.get("/api/media", (_req, res) => {
  res.json(store.snapshot().mediaFiles);
});

app.get("/api/detections", (_req, res) => {
  res.json(store.snapshot().detections);
});

app.get("/api/mobile/contract", (_req, res) => {
  res.json({
    routePoint: {
      lat: "Double",
      lon: "Double",
      altitudeMeters: "Float",
      speedMetersPerSecond: "Float"
    },
    endpoints: [
      "POST /api/mobile/sync",
      "POST /api/mobile/telemetry",
      "POST /api/mobile/events",
      "POST /api/mobile/media",
      "POST /api/mobile/detections"
    ],
    notes: [
      "RoutePoint 字段已与 Android 端 MainActivity.kt 保持一致",
      "当前服务默认支持本地模拟飞行，无需真机即可联调任务与状态流"
    ]
  });
});

app.post("/api/mobile/sync", (req, res) => {
  const payload = mobileSyncSchema.parse(req.body);
  const state = store.snapshot();
  const device = state.devices.find((item) => item.id === payload.deviceId) ?? null;
  const pendingMission = state.missions.find((item) =>
    item.assignedDeviceId === payload.deviceId &&
    ["ready", "pending", "running", "paused"].includes(item.status)
  ) ?? null;
  const routeTemplate = pendingMission
    ? state.routes.find((item) => item.id === pendingMission.routeTemplateId) ?? null
    : null;

  res.json({ device, pendingMission, routeTemplate });
});

app.post("/api/mobile/telemetry", async (req, res) => {
  const payload = mobileTelemetrySchema.parse(req.body);
  const telemetry = await store.mutate((draft) => {
    const item = {
      id: randomUUID(),
      timestamp: new Date().toISOString(),
      ...payload
    };

    draft.telemetry.unshift(item);
    draft.telemetry = draft.telemetry.slice(0, 200);

    const device = draft.devices.find((entry) => entry.id === payload.deviceId);
    if (device) {
      device.lastSeenAt = item.timestamp;
      device.batteryPercent = payload.batteryPercent;
      device.satelliteCount = payload.satelliteCount;
      device.status = payload.missionId ? "executing" : "ready";
      device.currentMissionId = payload.missionId;
    }

    return item;
  });

  io.emit("telemetry:update", telemetry);
  res.status(201).json(telemetry);
});

app.post("/api/mobile/events", async (req, res) => {
  const payload = mobileEventSchema.parse(req.body);
  const event = await store.mutate((draft) => {
    const item: MissionEvent = {
      id: randomUUID(),
      timestamp: new Date().toISOString(),
      ...payload
    };
    draft.events.unshift(item);
    draft.events = draft.events.slice(0, 200);

    if (payload.missionId && payload.status) {
      const mission = draft.missions.find((entry) => entry.id === payload.missionId);
      if (mission) {
        mission.status = payload.status;
        mission.updatedAt = item.timestamp;
      }
    }
    return item;
  });
  io.emit("event:new", event);
  res.status(201).json(event);
});

app.post("/api/mobile/media", async (req, res) => {
  const payload = mobileMediaSchema.parse(req.body);
  const media = await store.mutate((draft) => {
    const item: MediaFileItem = {
      id: randomUUID(),
      createdAt: new Date().toISOString(),
      ...payload
    };
    draft.mediaFiles.unshift(item);
    draft.mediaFiles = draft.mediaFiles.slice(0, 200);
    return item;
  });
  io.emit("media:update", media);
  res.status(201).json(media);
});

app.post("/api/mobile/detections", async (req, res) => {
  const payload = mobileDetectionSchema.parse(req.body);
  const detection = await store.mutate((draft) => {
    const item = {
      id: randomUUID(),
      createdAt: new Date().toISOString(),
      ...payload
    };
    draft.detections.unshift(item);
    draft.detections = draft.detections.slice(0, 200);
    return item;
  });
  io.emit("detection:update", detection);
  res.status(201).json(detection);
});

app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  if (error instanceof z.ZodError) {
    res.status(400).json({ message: "请求参数不合法", issues: error.issues });
    return;
  }
  console.error(error);
  res.status(500).json({ message: "服务内部错误" });
});

io.on("connection", (socket) => {
  socket.emit("state:bootstrap", store.snapshot());
});

const port = Number(process.env.PORT ?? 8787);

async function main() {
  await store.init();
  httpServer.listen(port, () => {
    console.log(`FLY desktop server listening on http://localhost:${port}`);
  });
}

void main();
