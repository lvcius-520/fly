package com.fly.server;

import jakarta.annotation.PostConstruct;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureDefaultAdmin() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM management_user WHERE username = ?",
            Integer.class,
            "admin"
        );
        if (count != null && count > 0) {
            return;
        }

        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO management_user (
                id, username, display_name, password_hash, role, enabled, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID().toString(),
            "admin",
            "系统管理员",
            passwordEncoder.encode("admin123"),
            "ADMIN",
            true,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    public LoginResponse login(LoginRequest request) {
        ManagementUser user = findByUsername(request.username());
        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "账号或密码错误");
        }
        if (request.loginAs() != null && !request.loginAs().equals(user.role())) {
            throw new ResponseStatusException(UNAUTHORIZED, "当前账号不属于所选登录入口");
        }
        return issueLogin(user);
    }

    public LoginResponse register(RegisterRequest request) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM management_user WHERE username = ?",
            Integer.class,
            request.username()
        );
        if (count != null && count > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名已存在");
        }

        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO management_user (
                id, username, display_name, password_hash, role, enabled, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            request.username(),
            request.username(),
            passwordEncoder.encode(request.password()),
            "OPERATOR",
            true,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return issueLogin(findById(id));
    }

    private LoginResponse issueLogin(ManagementUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(12, ChronoUnit.HOURS);

        jdbcTemplate.update(
            """
            UPDATE management_user
            SET session_token = ?, session_expires_at = ?, last_login_at = ?, updated_at = ?
            WHERE id = ?
            """,
            token,
            Timestamp.from(expiresAt),
            Timestamp.from(now),
            Timestamp.from(now),
            user.id()
        );

        ManagementUser refreshed = requireUser("Bearer " + token);
        return new LoginResponse(token, toSummary(refreshed));
    }

    public AuthUserSummary currentUser(String authorizationHeader) {
        return toSummary(requireUser(authorizationHeader));
    }

    public void logout(String authorizationHeader) {
        ManagementUser user = requireUser(authorizationHeader);
        jdbcTemplate.update(
            """
            UPDATE management_user
            SET session_token = NULL, session_expires_at = NULL, updated_at = ?
            WHERE id = ?
            """,
            Timestamp.from(Instant.now()),
            user.id()
        );
    }

    public List<AuthUserSummary> listUsers(String authorizationHeader) {
        requireAdmin(authorizationHeader);
        return jdbcTemplate.query(
            """
            SELECT id, username, display_name, role, enabled, last_login_at
            FROM management_user
            ORDER BY created_at ASC
            """,
            (resultSet, rowNum) -> new AuthUserSummary(
                resultSet.getString("id"),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("role"),
                resultSet.getBoolean("enabled"),
                toIsoString(resultSet.getTimestamp("last_login_at"))
            )
        );
    }

    public AuthUserSummary createUser(String authorizationHeader, CreateManagementUserRequest request) {
        requireAdmin(authorizationHeader);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM management_user WHERE username = ?",
            Integer.class,
            request.username()
        );
        if (count != null && count > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名已存在");
        }

        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO management_user (
                id, username, display_name, password_hash, role, enabled, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            request.username(),
            request.displayName(),
            passwordEncoder.encode(request.password()),
            request.role(),
            request.enabled(),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return getUserSummary(id);
    }

    public AuthUserSummary updateUser(String authorizationHeader, String id, UpdateManagementUserRequest request) {
        ManagementUser actor = requireAdmin(authorizationHeader);
        ManagementUser target = findById(id);
        if (target == null) {
            throw new ResponseStatusException(NOT_FOUND, "用户不存在");
        }
        if (!actor.id().equals(target.id()) && "ADMIN".equals(target.role()) && !"ADMIN".equals(request.role())) {
            // allow admin role changes, but keep explicit message path for clarity if needed later
        }

        Instant now = Instant.now();
        String passwordHash = (request.password() == null || request.password().isBlank())
            ? target.passwordHash()
            : passwordEncoder.encode(request.password());

        jdbcTemplate.update(
            """
            UPDATE management_user
            SET display_name = ?, password_hash = ?, role = ?, enabled = ?, updated_at = ?
            WHERE id = ?
            """,
            request.displayName(),
            passwordHash,
            request.role(),
            request.enabled(),
            Timestamp.from(now),
            id
        );
        return getUserSummary(id);
    }

    public ManagementUser requireAdmin(String authorizationHeader) {
        ManagementUser user = requireUser(authorizationHeader);
        if (!"ADMIN".equals(user.role())) {
            throw new ResponseStatusException(FORBIDDEN, "仅管理员可执行该操作");
        }
        return user;
    }

    public ManagementUser requireUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        }

        ManagementUser user = jdbcTemplate.query(
            """
            SELECT id, username, display_name, password_hash, role, enabled, session_token, session_expires_at, last_login_at
            FROM management_user
            WHERE session_token = ?
            """,
            rs -> rs.next() ? mapUser(rs) : null,
            token
        );

        if (user == null || !user.enabled()) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }

        if (user.sessionExpiresAt() == null || user.sessionExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已过期");
        }
        return user;
    }

    private AuthUserSummary getUserSummary(String id) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT id, username, display_name, role, enabled, last_login_at
                FROM management_user
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new AuthUserSummary(
                    resultSet.getString("id"),
                    resultSet.getString("username"),
                    resultSet.getString("display_name"),
                    resultSet.getString("role"),
                    resultSet.getBoolean("enabled"),
                    toIsoString(resultSet.getTimestamp("last_login_at"))
                ),
                id
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(NOT_FOUND, "用户不存在");
        }
    }

    private ManagementUser findByUsername(String username) {
        return jdbcTemplate.query(
            """
            SELECT id, username, display_name, password_hash, role, enabled, session_token, session_expires_at, last_login_at
            FROM management_user
            WHERE username = ?
            """,
            rs -> rs.next() ? mapUser(rs) : null,
            username
        );
    }

    private ManagementUser findById(String id) {
        return jdbcTemplate.query(
            """
            SELECT id, username, display_name, password_hash, role, enabled, session_token, session_expires_at, last_login_at
            FROM management_user
            WHERE id = ?
            """,
            rs -> rs.next() ? mapUser(rs) : null,
            id
        );
    }

    private ManagementUser mapUser(ResultSet resultSet) throws SQLException {
        return new ManagementUser(
            resultSet.getString("id"),
            resultSet.getString("username"),
            resultSet.getString("display_name"),
            resultSet.getString("password_hash"),
            resultSet.getString("role"),
            resultSet.getBoolean("enabled"),
            resultSet.getString("session_token"),
            toInstant(resultSet.getTimestamp("session_expires_at")),
            toInstant(resultSet.getTimestamp("last_login_at"))
        );
    }

    private AuthUserSummary toSummary(ManagementUser user) {
        return new AuthUserSummary(
            user.id(),
            user.username(),
            user.displayName(),
            user.role(),
            user.enabled(),
            user.lastLoginAt() == null ? null : user.lastLoginAt().toString()
        );
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        return authorizationHeader.startsWith("Bearer ")
            ? authorizationHeader.substring("Bearer ".length()).trim()
            : authorizationHeader.trim();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String toIsoString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}

record ManagementUser(
    String id,
    String username,
    String displayName,
    String passwordHash,
    String role,
    boolean enabled,
    String sessionToken,
    Instant sessionExpiresAt,
    Instant lastLoginAt
) {
}
