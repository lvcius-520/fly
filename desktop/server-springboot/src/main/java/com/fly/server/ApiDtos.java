package com.fly.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

record LoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    @Pattern(regexp = "ADMIN|OPERATOR") String loginAs
) {
}

record RegisterRequest(
    @NotBlank @Size(min = 3, max = 32) String username,
    @NotBlank @Size(min = 6, max = 50) String password
) {
}

record LoginResponse(
    String token,
    AuthUserSummary user
) {
}

record AuthUserSummary(
    String id,
    String username,
    String displayName,
    String role,
    boolean enabled,
    String lastLoginAt
) {
}

record CreateManagementUserRequest(
    @NotBlank @Size(min = 3, max = 32) String username,
    @NotBlank @Size(min = 2, max = 50) String displayName,
    @NotBlank @Size(min = 6, max = 50) String password,
    @NotBlank @Pattern(regexp = "ADMIN|OPERATOR") String role,
    @NotNull Boolean enabled
) {
}

record UpdateManagementUserRequest(
    @NotBlank @Size(min = 2, max = 50) String displayName,
    @Size(min = 6, max = 50) String password,
    @NotBlank @Pattern(regexp = "ADMIN|OPERATOR") String role,
    @NotNull Boolean enabled
) {
}

record RoutePointRequest(
    @NotNull Double lat,
    @NotNull Double lon,
    @NotNull Double altitudeMeters,
    @NotNull Double speedMetersPerSecond
) {
}

record RouteCreateRequest(
    @NotBlank String name,
    String description,
    @NotNull @Size(min = 2) List<@Valid RoutePointRequest> waypoints
) {
}

record MissionCreateRequest(
    @NotBlank String name,
    String description,
    @NotBlank String routeTemplateId,
    String assignedDeviceId,
    @NotBlank String plannedAt
) {
}

record MissionStatusUpdateRequest(
    @NotNull MissionStatus status
) {
}

record MobileSyncRequest(
    @NotBlank String deviceId
) {
}

record MobileTelemetryRequest(
    @NotBlank String deviceId,
    String missionId,
    @NotNull Double lat,
    @NotNull Double lon,
    @NotNull Double altitudeMeters,
    @NotNull Double speedMetersPerSecond,
    @NotNull Double verticalSpeedMetersPerSecond,
    @NotNull Integer batteryPercent,
    @NotNull Integer satelliteCount,
    @NotBlank String flightMode
) {
}

record MobileEventRequest(
    @NotBlank String deviceId,
    String missionId,
    @NotBlank String type,
    @NotBlank String message,
    MissionStatus status
) {
}

record MobileMediaRequest(
    String missionId,
    @NotBlank String deviceId,
    @NotNull MediaType type,
    @NotBlank String name,
    @NotBlank String url
) {
}

record MobileDetectionRequest(
    String missionId,
    @NotBlank String deviceId,
    @NotBlank String label,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double score,
    String mediaId
) {
}

record ParkingOpinionCreateRequest(
    @Size(max = 24) String authorName,
    @NotBlank @Size(min = 2, max = 24) String topic,
    @NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double rating,
    @NotBlank @Size(min = 4, max = 240) String content,
    @Size(max = 3) List<@NotBlank @Size(max = 2_500_000) String> imageUrls
) {
}
