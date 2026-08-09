<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { apiFetch } from "../composables/useApi";
import type { RoutePoint, RouteTemplate } from "../types";
import { formatTime } from "../utils/parking";

type PlanningMode = "polyline" | "polygon";
type DispatchModule = "route-planning" | "route-management";
type LngLat = { lng: number; lat: number };
type LocalPoint = { x: number; y: number };

type AMapMouseTool = {
  close: (clear?: boolean) => void;
  polyline: (options: Record<string, unknown>) => void;
  polygon: (options: Record<string, unknown>) => void;
  on: (eventName: string, handler: (event: { obj: any }) => void) => void;
};

type AMapOverlayEditor = {
  open: () => void;
  close: () => void;
};

type AMapNamespace = {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => any;
  Polyline: new (options: Record<string, unknown>) => any;
  Marker: new (options: Record<string, unknown>) => any;
  TileLayer: {
    new (options?: Record<string, unknown>): any;
    Satellite: new (options?: Record<string, unknown>) => any;
    RoadNet: new (options?: Record<string, unknown>) => any;
  };
  MouseTool: new (map: any) => AMapMouseTool;
  PolylineEditor: new (map: any, overlay: any) => AMapOverlayEditor;
  PolygonEditor: new (map: any, overlay: any) => AMapOverlayEditor;
  Scale: new () => any;
  ToolBar: new (options?: Record<string, unknown>) => any;
  LngLat: new (lng: number, lat: number) => any;
};

declare global {
  interface Window {
    AMap?: AMapNamespace;
    _flyAmapLoader?: Promise<AMapNamespace>;
  }
}

const sidebarCollapsed = ref(false);
const mapHost = ref<HTMLElement | null>(null);
const mapReady = ref(false);
const mapError = ref("");
const saving = ref(false);
const loadingRoutes = ref(false);
const activeMode = ref<PlanningMode>("polyline");
const activeModule = ref<DispatchModule>("route-planning");
const plannerPanelCollapsed = ref(false);
const mapViewMode = ref<"vector" | "satellite">("vector");
const routeTemplates = ref<RouteTemplate[]>([]);
const selectedTemplateId = ref("");
const drawSummary = ref("尚未绘制航线");
const generatedWaypoints = ref<RoutePoint[]>([]);
const polygonVertices = ref<LngLat[]>([]);
const saveMessage = ref("");
const saveError = ref("");

const form = reactive({
  name: "苏州停车场巡检航线",
  description: "用于移动端下发的高德底图航线规划",
  altitudeMeters: 45,
  speedMetersPerSecond: 6,
  lineSpacingMeters: 40
});

const modules = [
  { id: "route-planning" as DispatchModule, short: "绘", title: "航线规划" },
  { id: "route-management" as DispatchModule, short: "管", title: "航线管理" }
];

let map: any = null;
let mouseTool: AMapMouseTool | null = null;
let currentOverlay: any = null;
let currentEditor: AMapOverlayEditor | null = null;
let generatedRouteOverlay: any = null;
let waypointMarkers: any[] = [];
let vectorLayer: any = null;
let satelliteLayer: any = null;
let roadNetLayer: any = null;

const amapWebKey = (import.meta.env.VITE_AMAP_WEB_KEY as string | undefined)?.trim() ?? "";

const selectedTemplate = computed(
  () => routeTemplates.value.find((item) => item.id === selectedTemplateId.value) ?? null
);

const planningStats = computed(() => [
  { label: "规划模式", value: activeMode.value === "polyline" ? "线状规划" : "面状规划" },
  { label: "生成航点", value: `${generatedWaypoints.value.length} 个` },
  { label: "巡检高度", value: `${form.altitudeMeters} m` },
  { label: "规划速度", value: `${form.speedMetersPerSecond} m/s` },
  { label: "航点间距", value: `${form.lineSpacingMeters} m` }
]);

const routeManagementStats = computed(() => [
  { label: "模板总数", value: `${routeTemplates.value.length} 条` },
  { label: "当前选中", value: selectedTemplate.value?.name ?? "未选择" }
]);

async function loadRouteTemplates() {
  loadingRoutes.value = true;
  try {
    routeTemplates.value = await apiFetch<RouteTemplate[]>("/api/routes");
    if (!selectedTemplateId.value && routeTemplates.value[0]) {
      selectedTemplateId.value = routeTemplates.value[0].id;
    }
  } catch (exception) {
    saveError.value = exception instanceof Error ? exception.message : "加载航线模板失败";
  } finally {
    loadingRoutes.value = false;
  }
}

function simplifyPoint(point: LngLat): RoutePoint {
  return {
    lat: Number(point.lat.toFixed(6)),
    lon: Number(point.lng.toFixed(6)),
    altitudeMeters: form.altitudeMeters,
    speedMetersPerSecond: form.speedMetersPerSecond
  };
}

function toLocalPoint(point: LngLat, origin: LngLat): LocalPoint {
  return {
    x: (point.lng - origin.lng) * 111320 * Math.cos((origin.lat * Math.PI) / 180),
    y: (point.lat - origin.lat) * 111320
  };
}

function toLngLat(point: LocalPoint, origin: LngLat): LngLat {
  return {
    lng: origin.lng + point.x / (111320 * Math.cos((origin.lat * Math.PI) / 180)),
    lat: origin.lat + point.y / 111320
  };
}

function rotatePoint(point: LocalPoint, angleRad: number): LocalPoint {
  const cos = Math.cos(angleRad);
  const sin = Math.sin(angleRad);
  return {
    x: point.x * cos - point.y * sin,
    y: point.x * sin + point.y * cos
  };
}

function distanceBetween(a: LocalPoint, b: LocalPoint) {
  return Math.hypot(b.x - a.x, b.y - a.y);
}

function densifySegment(start: LocalPoint, end: LocalPoint, spacingMeters: number) {
  const distance = distanceBetween(start, end);
  if (distance <= spacingMeters) return [start, end];

  const points: LocalPoint[] = [start];
  const steps = Math.floor(distance / spacingMeters);
  for (let index = 1; index < steps; index += 1) {
    const t = (index * spacingMeters) / distance;
    points.push({
      x: start.x + (end.x - start.x) * t,
      y: start.y + (end.y - start.y) * t
    });
  }
  points.push(end);
  return points;
}

function buildPolylineWaypoints(path: LngLat[]) {
  if (path.length < 2) return [];

  const origin = path[0];
  const localPath = path.map((item) => toLocalPoint(item, origin));
  const densified: LocalPoint[] = [];

  for (let index = 0; index < localPath.length - 1; index += 1) {
    const segment = densifySegment(localPath[index], localPath[index + 1], Math.max(form.lineSpacingMeters, 10));
    segment.forEach((point, segmentIndex) => {
      if (index > 0 && segmentIndex === 0) return;
      densified.push(point);
    });
  }

  return densified.map((point) => simplifyPoint(toLngLat(point, origin)));
}

function buildPolygonSweepWaypoints(vertices: LngLat[]) {
  if (vertices.length < 3) return [];

  const origin = vertices.reduce(
    (acc, item) => ({ lng: acc.lng + item.lng / vertices.length, lat: acc.lat + item.lat / vertices.length }),
    { lng: 0, lat: 0 }
  );
  const localVertices = vertices.map((item) => toLocalPoint(item, origin));

  let dominantAngle = 0;
  let maxEdgeLength = 0;
  for (let index = 0; index < localVertices.length; index += 1) {
    const start = localVertices[index];
    const end = localVertices[(index + 1) % localVertices.length];
    const edgeLength = distanceBetween(start, end);
    if (edgeLength > maxEdgeLength) {
      maxEdgeLength = edgeLength;
      dominantAngle = Math.atan2(end.y - start.y, end.x - start.x);
    }
  }

  const rotated = localVertices.map((item) => rotatePoint(item, -dominantAngle));
  const ys = rotated.map((item) => item.y);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const spacing = Math.max(form.lineSpacingMeters, 12);

  const routePoints: LocalPoint[] = [];
  let reverse = false;

  for (let y = minY; y <= maxY; y += spacing) {
    const intersections: number[] = [];

    for (let index = 0; index < rotated.length; index += 1) {
      const current = rotated[index];
      const next = rotated[(index + 1) % rotated.length];

      const minEdgeY = Math.min(current.y, next.y);
      const maxEdgeY = Math.max(current.y, next.y);
      if (y < minEdgeY || y >= maxEdgeY || current.y === next.y) continue;

      const t = (y - current.y) / (next.y - current.y);
      intersections.push(current.x + (next.x - current.x) * t);
    }

    intersections.sort((a, b) => a - b);

    const rowSegments: LocalPoint[] = [];
    for (let index = 0; index < intersections.length; index += 2) {
      const startX = intersections[index];
      const endX = intersections[index + 1];
      if (startX === undefined || endX === undefined) continue;

      const segment = reverse
        ? [{ x: endX, y }, { x: startX, y }]
        : [{ x: startX, y }, { x: endX, y }];

      segment.forEach((point, segmentIndex) => {
        if (rowSegments.length > 0 && segmentIndex === 0) return;
        rowSegments.push(point);
      });
    }

    if (rowSegments.length > 0) {
      if (routePoints.length > 0) {
        routePoints.push(rowSegments[0]);
      }
      routePoints.push(...rowSegments);
      reverse = !reverse;
    }
  }

  const normalizedPoints = routePoints.map((point) => rotatePoint(point, dominantAngle));
  return normalizedPoints.map((point) => simplifyPoint(toLngLat(point, origin)));
}

function extractLngLat(item: any): LngLat {
  if (typeof item?.getLng === "function" && typeof item?.getLat === "function") {
    return { lng: item.getLng(), lat: item.getLat() };
  }
  return { lng: item.lng, lat: item.lat };
}

function extractPathFromOverlay(overlay: any): LngLat[] {
  const rawPath = typeof overlay?.getPath === "function" ? overlay.getPath() : [];
  return rawPath.map(extractLngLat);
}

function extractPolygonVertices(overlay: any): LngLat[] {
  const rawPath = typeof overlay?.getPath === "function" ? overlay.getPath() : [];
  const ring = Array.isArray(rawPath[0]) ? rawPath[0] : rawPath;
  return ring.map(extractLngLat);
}

function updatePlanningResultFromOverlay(mode: PlanningMode, overlay: any) {
  if (!overlay) return;

  if (mode === "polyline") {
    const path = extractPathFromOverlay(overlay);
    polygonVertices.value = [];
    generatedWaypoints.value = buildPolylineWaypoints(path);
    drawSummary.value = `线状规划完成，已提取 ${generatedWaypoints.value.length} 个航点`;
  } else {
    const vertices = extractPolygonVertices(overlay);
    polygonVertices.value = vertices;
    generatedWaypoints.value = buildPolygonSweepWaypoints(vertices);
    drawSummary.value = `面状规划完成，已根据区域自动生成 ${generatedWaypoints.value.length} 个巡检航点`;
  }
}

function clearOverlay() {
  currentEditor?.close();
  currentEditor = null;
  if (currentOverlay && typeof currentOverlay.setMap === "function") {
    currentOverlay.setMap(null);
  }
  if (generatedRouteOverlay && typeof generatedRouteOverlay.setMap === "function") {
    generatedRouteOverlay.setMap(null);
  }
  waypointMarkers.forEach((marker) => {
    if (marker && typeof marker.setMap === "function") marker.setMap(null);
  });
  generatedRouteOverlay = null;
  waypointMarkers = [];
  currentOverlay = null;
  generatedWaypoints.value = [];
  polygonVertices.value = [];
  drawSummary.value = "尚未绘制航线";
}

function fitOverlayView() {
  if (!map) return;
  const overlays = [currentOverlay, generatedRouteOverlay].filter(Boolean);
  if (overlays.length === 0) return;
  if (typeof map.setFitView === "function") {
    map.setFitView(overlays, false, [80, 140, 80, 80]);
  }
}

function openEditorForCurrentOverlay(mode: PlanningMode) {
  if (!window.AMap || !map || !currentOverlay) return;
  currentEditor?.close();
  currentEditor =
    mode === "polyline"
      ? new window.AMap.PolylineEditor(map, currentOverlay)
      : new window.AMap.PolygonEditor(map, currentOverlay);
  currentEditor.open();
}

function renderGeneratedRoute(waypoints: RoutePoint[]) {
  if (!window.AMap || !map) return;

  if (generatedRouteOverlay && typeof generatedRouteOverlay.setMap === "function") {
    generatedRouteOverlay.setMap(null);
  }
  waypointMarkers.forEach((marker) => {
    if (marker && typeof marker.setMap === "function") marker.setMap(null);
  });
  waypointMarkers = [];

  if (waypoints.length < 2) return;

  const path = waypoints.map((item) => new window.AMap!.LngLat(item.lon, item.lat));
  generatedRouteOverlay = new window.AMap.Polyline({
    map,
    path,
    strokeColor: "#ff7a45",
    strokeWeight: 4,
    strokeOpacity: 0.98,
    lineJoin: "round",
    lineCap: "round",
    zIndex: 80
  });

  waypointMarkers = waypoints.map(
    (item, index) =>
      new window.AMap!.Marker({
        map,
        position: [item.lon, item.lat],
        offset: [-12, -12],
        content: `<div style="width:24px;height:24px;border-radius:999px;background:#ff7a45;color:#fff;font-size:12px;font-weight:700;display:flex;align-items:center;justify-content:center;box-shadow:0 6px 14px rgba(255,122,69,.32);">${index + 1}</div>`
      })
  );
}

function setMapViewMode(mode: "vector" | "satellite") {
  mapViewMode.value = mode;
  if (!map) return;

  const layers =
    mode === "satellite"
      ? [satelliteLayer, roadNetLayer].filter(Boolean)
      : [vectorLayer].filter(Boolean);

  if (layers.length > 0 && typeof map.setLayers === "function") {
    map.setLayers(layers);
  }
}

function startDrawing(mode: PlanningMode) {
  if (!mouseTool || !map) return;
  activeMode.value = mode;
  sidebarCollapsed.value = true;
  plannerPanelCollapsed.value = false;
  clearOverlay();
  mouseTool.close(true);

  if (mode === "polyline") {
    mouseTool.polyline({
      strokeColor: "#2f6fd3",
      strokeWeight: 5,
      strokeOpacity: 0.95,
      showDir: true
    });
  } else {
    mouseTool.polygon({
      strokeColor: "#2f6fd3",
      strokeWeight: 3,
      strokeOpacity: 0.95,
      fillColor: "#5da1ff",
      fillOpacity: 0.22
    });
  }
}

async function saveRouteTemplate() {
  saveMessage.value = "";
  saveError.value = "";

  const name = form.name.trim();
  const description = form.description.trim();

  if (name.length < 2) {
    saveError.value = "航线名称至少需要 2 个字符";
    return;
  }

  if (generatedWaypoints.value.length < 2) {
    saveError.value = "请先在线状或面状规划中绘制有效航线";
    return;
  }

  saving.value = true;
  try {
    const created = await apiFetch<RouteTemplate>("/api/routes", {
      method: "POST",
      body: JSON.stringify({
        name,
        description,
        waypoints: generatedWaypoints.value
      })
    });
    saveMessage.value = `航线已保存，可直接用于移动端下发：${created.name}`;
    await loadRouteTemplates();
    selectedTemplateId.value = created.id;
  } catch (exception) {
    saveError.value = exception instanceof Error ? exception.message : "保存航线失败";
  } finally {
    saving.value = false;
  }
}

async function ensureAmapLoaded() {
  if (window.AMap) return window.AMap;
  if (window._flyAmapLoader) return window._flyAmapLoader;
  if (!amapWebKey) throw new Error("未配置 VITE_AMAP_WEB_KEY，暂时无法加载高德底图");

  window._flyAmapLoader = new Promise<AMapNamespace>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(
      amapWebKey
    )}&plugin=AMap.MouseTool,AMap.PolylineEditor,AMap.PolygonEditor,AMap.Scale,AMap.ToolBar`;
    script.async = true;
    script.onload = () => {
      if (window.AMap) resolve(window.AMap);
      else reject(new Error("高德地图加载完成，但 AMap 对象不可用"));
    };
    script.onerror = () => reject(new Error("高德地图脚本加载失败"));
    document.head.appendChild(script);
  });

  return window._flyAmapLoader;
}

async function initMap() {
  if (!mapHost.value) return;
  try {
    const AMap = await ensureAmapLoaded();
    map = new AMap.Map(mapHost.value, {
      resizeEnable: true,
      zoom: 11.6,
      center: [120.6196, 31.299],
      mapStyle: "amap://styles/normal"
    });
    vectorLayer = new AMap.TileLayer();
    satelliteLayer = new AMap.TileLayer.Satellite();
    roadNetLayer = new AMap.TileLayer.RoadNet();
    setMapViewMode(mapViewMode.value);
    map.addControl(new AMap.Scale());
    map.addControl(new AMap.ToolBar({ position: { top: "16px", left: "16px" } }));

    mouseTool = new AMap.MouseTool(map);
    mouseTool.on("draw", (event: { obj: any }) => {
      currentOverlay = event.obj;
      updatePlanningResultFromOverlay(activeMode.value, currentOverlay);
      renderGeneratedRoute(generatedWaypoints.value);
      openEditorForCurrentOverlay(activeMode.value);
      fitOverlayView();
      mouseTool?.close();
    });

    mapReady.value = true;
  } catch (exception) {
    mapError.value = exception instanceof Error ? exception.message : "地图初始化失败";
  }
}

async function previewSelectedTemplate() {
  if (!selectedTemplate.value) return;
  activeModule.value = "route-planning";
  activeMode.value = "polyline";
  generatedWaypoints.value = selectedTemplate.value.waypoints;
  drawSummary.value = `已载入模板 ${selectedTemplate.value.name}，包含 ${selectedTemplate.value.waypoints.length} 个航点`;
  await nextTick();
  if (!map && mapHost.value) {
    await initMap();
  }
  if (typeof map?.resize === "function") {
    map.resize();
  }
  renderGeneratedRoute(generatedWaypoints.value);
  fitOverlayView();
}

onMounted(async () => {
  await Promise.all([loadRouteTemplates(), initMap()]);
});

watch(activeModule, async (module) => {
  if (module !== "route-planning") return;
  await nextTick();
  if (!map && mapHost.value) {
    await initMap();
  }
  if (typeof map?.resize === "function") {
    map.resize();
  }
  if (generatedWaypoints.value.length > 1) {
    renderGeneratedRoute(generatedWaypoints.value);
    fitOverlayView();
  }
});

onBeforeUnmount(() => {
  currentEditor?.close();
  mouseTool?.close(true);
  if (map && typeof map.destroy === "function") {
    map.destroy();
  }
});
</script>

<template>
  <div class="dispatch-layout" :class="{ 'dispatch-layout--collapsed': sidebarCollapsed }">
    <aside class="dispatch-sidebar" :class="{ 'dispatch-sidebar--collapsed': sidebarCollapsed }">
      <div class="dispatch-sidebar__actions">
        <button
          class="dispatch-sidebar__toggle"
          :class="{ 'dispatch-sidebar__toggle--collapsed': sidebarCollapsed }"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path
              d="M11.78 4.47a.75.75 0 0 1 0 1.06L7.31 10l4.47 4.47a.75.75 0 1 1-1.06 1.06l-5-5a.75.75 0 0 1 0-1.06l5-5a.75.75 0 0 1 1.06 0Z"
              fill="currentColor"
            />
          </svg>
        </button>
      </div>

      <nav class="dispatch-nav">
        <a
          v-for="item in modules"
          :key="item.id"
          href="#"
          class="dispatch-nav__link"
          :class="{ 'dispatch-nav__link--active': activeModule === item.id }"
          :title="item.title"
          @click.prevent="activeModule = item.id"
        >
          <span class="dispatch-nav__badge">{{ item.short }}</span>
          <span v-if="!sidebarCollapsed" class="dispatch-nav__text">{{ item.title }}</span>
        </a>
      </nav>
    </aside>

    <section v-show="activeModule === 'route-planning'" class="dispatch-map-shell" id="route-planning">
      <div ref="mapHost" class="dispatch-map"></div>

      <div v-if="mapError" class="dispatch-map__state dispatch-map__state--error">
        <strong>高德地图暂未就绪</strong>
        <p>{{ mapError }}</p>
        <p>在前端环境变量中配置 `VITE_AMAP_WEB_KEY` 后刷新页面即可使用底图与绘制能力。</p>
      </div>

      <div v-else-if="!mapReady" class="dispatch-map__state">
        <strong>正在加载高德底图...</strong>
      </div>

      <button
        class="map-layer-toggle"
        :class="{ 'map-layer-toggle--satellite': mapViewMode === 'satellite' }"
        :aria-label="mapViewMode === 'satellite' ? '切换到普通地图' : '切换到卫星影像'"
        :title="mapViewMode === 'satellite' ? '当前：卫星影像，点击切换普通地图' : '当前：普通地图，点击切换卫星影像'"
        @click="setMapViewMode(mapViewMode === 'satellite' ? 'vector' : 'satellite')"
      >
        <span class="map-layer-toggle__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path
              d="M12 3.4 4.6 7.1a1 1 0 0 0-.56.9v8a1 1 0 0 0 .56.9L12 20.6a1 1 0 0 0 .9 0l7.4-3.7a1 1 0 0 0 .56-.9V8a1 1 0 0 0-.56-.9L12.9 3.4a1 1 0 0 0-.9 0ZM12 5.44l5.81 2.9L12 11.25 6.19 8.34 12 5.44Zm-6 4.44 5 2.5v5.52l-5-2.5V9.88Zm7 8.02v-5.52l5-2.5v5.52l-5 2.5Z"
              fill="currentColor"
            />
          </svg>
        </span>
        <span class="map-layer-toggle__text">{{ mapViewMode === "satellite" ? "影像" : "图层" }}</span>
      </button>

      <div class="planner-panel" :class="{ 'planner-panel--collapsed': plannerPanelCollapsed }">
        <div class="planner-panel__head">
          <div class="planner-panel__block">
            <strong>航线规划</strong>
            <p>{{ drawSummary }}</p>
          </div>
          <button
            class="planner-panel__toggle"
            :aria-label="plannerPanelCollapsed ? '展开规划面板' : '缩小规划面板'"
            @click="plannerPanelCollapsed = !plannerPanelCollapsed"
          >
            {{ plannerPanelCollapsed ? "展开" : "缩小" }}
          </button>
        </div>

        <template v-if="!plannerPanelCollapsed">
          <div class="planner-panel__mode-switch">
            <button
              class="planner-chip"
              :class="{ active: activeMode === 'polyline' }"
              @click="startDrawing('polyline')"
            >
              线状规划
            </button>
            <button
              class="planner-chip"
              :class="{ active: activeMode === 'polygon' }"
              @click="startDrawing('polygon')"
            >
              面状规划
            </button>
          </div>

          <div class="planner-panel__form">
            <label class="planner-field">
              航线名称
              <input v-model="form.name" placeholder="请输入航线名称" />
            </label>
            <label class="planner-field">
              说明
              <textarea v-model="form.description" rows="2" placeholder="用于移动端下发的巡检说明" />
            </label>
            <div class="planner-inline-fields">
              <label class="planner-field">
                高度
                <input v-model.number="form.altitudeMeters" type="number" min="10" step="1" />
              </label>
              <label class="planner-field">
                速度
                <input v-model.number="form.speedMetersPerSecond" type="number" min="1" step="1" />
              </label>
              <label class="planner-field">
                线距
                <input v-model.number="form.lineSpacingMeters" type="number" min="10" step="5" />
              </label>
            </div>
          </div>

          <div class="planner-stats">
            <article v-for="item in planningStats" :key="item.label" class="planner-stat">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>

          <div class="planner-panel__actions">
            <button class="secondary-button secondary-button--ghost" @click="clearOverlay">清空绘制</button>
            <button :disabled="saving" @click="saveRouteTemplate">{{ saving ? "保存中..." : "保存并下发" }}</button>
          </div>

          <p v-if="saveMessage" class="planner-message planner-message--success">{{ saveMessage }}</p>
          <p v-if="saveError" class="planner-message planner-message--error">{{ saveError }}</p>
        </template>
      </div>
    </section>

    <section v-show="activeModule === 'route-management'" class="route-management-shell" id="route-management">
      <div class="route-management__header">
        <div>
          <strong>航线管理</strong>
          <p>统一查看已经绘制的航线模板，并可载入到地图继续规划或下发到移动端。</p>
        </div>
        <button class="secondary-button secondary-button--ghost" :disabled="loadingRoutes" @click="loadRouteTemplates">
          {{ loadingRoutes ? "刷新中..." : "刷新列表" }}
        </button>
      </div>

      <div class="route-management__stats">
        <article v-for="item in routeManagementStats" :key="item.label" class="planner-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <div class="route-management__list">
        <article
          v-for="item in routeTemplates"
          :key="item.id"
          class="route-management__item"
          :class="{ active: selectedTemplateId === item.id }"
        >
          <div>
            <strong>{{ item.name }}</strong>
            <p>{{ item.description || "暂无说明" }}</p>
            <small>更新时间：{{ formatTime(item.updatedAt) }}</small>
          </div>
          <div class="route-management__item-meta">
            <span>{{ item.waypoints.length }} 个航点</span>
            <button
              class="secondary-button secondary-button--ghost"
              @click="selectedTemplateId = item.id; previewSelectedTemplate()"
            >
              载入地图
            </button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dispatch-layout,
.dispatch-nav {
  display: grid;
  gap: 18px;
}

.dispatch-layout {
  grid-template-columns: 220px 1fr;
  align-items: stretch;
  min-height: calc(100vh - 184px);
  transition: grid-template-columns 0.2s ease;
  padding-top: 20px;
  border-top: 1px solid rgba(214, 225, 238, 0.98);
}

.dispatch-layout--collapsed {
  grid-template-columns: 88px 1fr;
}

.dispatch-sidebar {
  position: sticky;
  top: 24px;
  display: grid;
  gap: 12px;
  align-self: start;
}

.dispatch-sidebar__actions {
  display: flex;
  justify-content: flex-end;
}

.dispatch-sidebar__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border-radius: 999px;
  background: #ffffff;
  color: #2f6fd3;
  border: 1px solid rgba(216, 227, 239, 0.96);
  box-shadow: 0 10px 22px rgba(55, 94, 138, 0.08);
}

.dispatch-sidebar__toggle svg {
  width: 18px;
  height: 18px;
  transition: transform 0.18s ease;
}

.dispatch-sidebar__toggle--collapsed svg {
  transform: rotate(180deg);
}

.dispatch-nav__link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding-left: 10px;
  border-left: 3px solid #dbe7f3;
  color: #43678d;
  text-decoration: none;
  background: rgba(234, 243, 255, 0.3);
}

.dispatch-nav__link--active {
  color: #1f5ea0;
  border-left-color: #2f6fd3;
}

.dispatch-nav__badge {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #edf5ff;
  color: #2f6fd3;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.dispatch-sidebar--collapsed .dispatch-sidebar__actions {
  justify-content: center;
}

.dispatch-sidebar--collapsed .dispatch-nav__link {
  justify-content: center;
  padding-left: 0;
}

.dispatch-map-shell {
  position: relative;
  min-height: calc(100vh - 204px);
  border-radius: 30px;
  overflow: hidden;
  border: 1px solid rgba(207, 220, 236, 0.95);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
  background: #dfeaf7;
}

.dispatch-map {
  position: absolute;
  inset: 0;
}

.dispatch-map__state {
  position: absolute;
  inset: auto auto 24px 24px;
  z-index: 3;
  max-width: 420px;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(214, 225, 238, 0.95);
  backdrop-filter: blur(10px);
}

.dispatch-map__state--error {
  background: rgba(255, 247, 244, 0.95);
}

.dispatch-map__state p {
  margin: 8px 0 0;
  color: #6a839f;
  line-height: 1.7;
}

.map-layer-toggle {
  position: absolute;
  z-index: 3;
  right: 20px;
  bottom: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 64px;
  height: 76px;
  padding: 10px 8px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(214, 225, 238, 0.96);
  color: #315d92;
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 28px rgba(55, 94, 138, 0.16);
}

.map-layer-toggle--satellite {
  background: rgba(36, 88, 63, 0.92);
  border-color: rgba(107, 173, 137, 0.9);
  color: #f7fffa;
}

.map-layer-toggle__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
}

.map-layer-toggle__icon svg {
  width: 22px;
  height: 22px;
}

.map-layer-toggle__text {
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.planner-panel {
  position: absolute;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(214, 225, 238, 0.95);
  backdrop-filter: blur(14px);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.12);
}

.planner-panel {
  top: 20px;
  right: 20px;
  width: min(420px, calc(100% - 40px));
  max-height: calc(100vh - 210px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.planner-panel--collapsed {
  width: min(220px, calc(100% - 40px));
  max-height: 120px;
  overflow: hidden;
}

.planner-panel__head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.planner-panel__block p,
.planner-message,
.template-item p,
.template-item small {
  margin: 0;
  color: #6a839f;
}

.planner-panel__toggle {
  min-width: 64px;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(47, 111, 211, 0.08);
  color: #2f6fd3;
  border: 1px solid rgba(47, 111, 211, 0.18);
  box-shadow: none;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.planner-panel__mode-switch,
.planner-inline-fields,
.planner-panel__actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.planner-panel__mode-switch {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 2px 0 6px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 255, 255, 0.92));
}

.planner-chip {
  flex: 1;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f4f8fd;
  color: #466a91;
  border: 1px solid rgba(216, 227, 239, 0.96);
  box-shadow: none;
}

.planner-chip.active {
  background: linear-gradient(135deg, #2f6fd3, #4491ff);
  color: #ffffff;
}

.planner-panel__form,
.planner-field,
.planner-stats {
  display: grid;
  gap: 10px;
}

.planner-field {
  color: #4d6b8c;
}

.planner-inline-fields {
  align-items: end;
}

.planner-inline-fields .planner-field {
  flex: 1;
}

.planner-stats {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.planner-stat {
  min-height: 76px;
  padding: 10px 12px;
  border-radius: 16px;
  background: #f6f9fd;
}

.planner-stat span {
  display: block;
  color: #6a839f;
  font-size: 12px;
}

.planner-stat strong {
  display: block;
  margin-top: 6px;
  color: #183452;
}

.secondary-button--ghost {
  background: #ffffff;
  color: #315d92;
  border: 1px solid #d8e3ef;
  box-shadow: none;
}

.planner-panel__actions {
  position: sticky;
  bottom: -14px;
  z-index: 1;
  margin-top: auto;
  padding: 10px 0 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.98) 28%);
}

.planner-panel__actions button {
  flex: 1;
}

.planner-message--success {
  color: #228b62;
}

.planner-message--error {
  color: #d26a3e;
}

.route-management-shell,
.route-management__stats,
.route-management__list {
  display: grid;
  gap: 18px;
}

.route-management-shell {
  align-content: start;
}

.route-management__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
  padding: 24px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.98));
  border: 1px solid rgba(207, 220, 236, 0.95);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.route-management__header p,
.route-management__item p,
.route-management__item small {
  margin: 6px 0 0;
  color: #6a839f;
}

.route-management__stats {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.route-management__item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(214, 225, 238, 0.95);
  background: #f8fbff;
}

.route-management__item.active {
  background: #eaf3ff;
}

.route-management__item-meta {
  display: grid;
  justify-items: end;
  gap: 10px;
}

.route-management__item-meta span {
  color: #2f6fd3;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .dispatch-layout,
  .dispatch-layout--collapsed {
    grid-template-columns: 1fr;
  }

  .dispatch-sidebar {
    position: relative;
    top: 0;
  }

  .dispatch-sidebar--collapsed .dispatch-nav__link {
    justify-content: flex-start;
    padding-left: 10px;
  }

  .planner-inline-fields,
  .planner-panel__actions,
  .route-management__header {
    flex-direction: column;
    align-items: stretch;
  }

  .planner-panel {
    position: relative;
    inset: auto;
    width: auto;
    margin: 16px;
    max-height: none;
    overflow: visible;
  }

  .map-layer-toggle {
    right: 16px;
    bottom: 16px;
  }

  .route-management__stats {
    grid-template-columns: 1fr;
  }

  .route-management__item {
    flex-direction: column;
  }

  .route-management__item-meta {
    justify-items: start;
  }
}

@media (max-height: 860px) {
  .planner-panel {
    max-height: calc(100vh - 160px);
  }

  .planner-stats {
    grid-template-columns: 1fr;
  }
}
</style>
