CREATE TABLE IF NOT EXISTS management_user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    session_token VARCHAR(120),
    session_expires_at TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_management_user_session_token
    ON management_user (session_token)
    WHERE session_token IS NOT NULL;

CREATE TABLE IF NOT EXISTS region (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS airport_station (
    id VARCHAR(64) PRIMARY KEY,
    region_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    coverage_radius_meters INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_airport_station_region ON airport_station(region_id);

CREATE TABLE IF NOT EXISTS drone_device (
    id VARCHAR(64) PRIMARY KEY,
    airport_station_id VARCHAR(64),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    battery_percent INTEGER NOT NULL DEFAULT 0,
    satellite_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    current_mission_id VARCHAR(64),
    last_seen_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_drone_device_airport_station ON drone_device(airport_station_id);

CREATE TABLE IF NOT EXISTS parking_lot (
    id VARCHAR(64) PRIMARY KEY,
    region_id VARCHAR(64),
    name VARCHAR(120) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    total_spaces INTEGER NOT NULL DEFAULT 0,
    available_spaces INTEGER NOT NULL DEFAULT 0,
    occupancy_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    last_inspection_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parking_lot_region ON parking_lot(region_id);
CREATE INDEX IF NOT EXISTS idx_parking_lot_status ON parking_lot(status);

CREATE TABLE IF NOT EXISTS parking_space (
    id VARCHAR(64) PRIMARY KEY,
    parking_lot_id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    polygon_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parking_space_parking_lot_id ON parking_space(parking_lot_id);

CREATE TABLE IF NOT EXISTS route_template (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    waypoints_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inspection_mission (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    parking_lot_id VARCHAR(64),
    route_template_id VARCHAR(64) NOT NULL,
    drone_device_id VARCHAR(64),
    mission_status VARCHAR(20) NOT NULL,
    planned_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inspection_mission_status ON inspection_mission(mission_status);
CREATE INDEX IF NOT EXISTS idx_inspection_mission_planned_at ON inspection_mission(planned_at);

CREATE TABLE IF NOT EXISTS telemetry_point (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    drone_device_id VARCHAR(64) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    altitude_meters NUMERIC(8, 2) NOT NULL,
    speed_mps NUMERIC(8, 2) NOT NULL,
    vertical_speed_mps NUMERIC(8, 2) NOT NULL,
    battery_percent INTEGER NOT NULL,
    satellite_count INTEGER NOT NULL,
    flight_mode VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_point_device_time ON telemetry_point(drone_device_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS mission_event (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    drone_device_id VARCHAR(64),
    event_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    mission_status VARCHAR(20),
    recorded_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mission_event_mission_time ON mission_event(mission_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS media_file (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    drone_device_id VARCHAR(64) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    file_name VARCHAR(150) NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS detection_result (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    media_id VARCHAR(64),
    drone_device_id VARCHAR(64) NOT NULL,
    parking_space_id VARCHAR(64),
    label VARCHAR(100) NOT NULL,
    score NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_detection_result_mission ON detection_result(mission_id);
