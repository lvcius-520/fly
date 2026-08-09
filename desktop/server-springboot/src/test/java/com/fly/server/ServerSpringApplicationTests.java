package com.fly.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServerSpringApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opsOverviewShouldReturnParkingMetrics() throws Exception {
        mockMvc.perform(get("/api/ops/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parkingLotCount").isNumber())
            .andExpect(jsonPath("$.totalSpaces").isNumber())
            .andExpect(jsonPath("$.alerts").isArray());
    }

    @Test
    void parkingLotDetailShouldContainSpaces() throws Exception {
        mockMvc.perform(get("/api/parking-lots/lot-wz-hospital"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parkingLot.id").value("lot-wz-hospital"))
            .andExpect(jsonPath("$.spaces").isArray());
    }

    @Test
    void h5NearbyParkingShouldReturnCards() throws Exception {
        mockMvc.perform(get("/api/h5/nearby-parking")
                .param("lat", "31.2558")
                .param("lon", "120.6127")
                .param("radiusKm", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").isString())
            .andExpect(jsonPath("$[0].availableSpaces").isNumber());
    }

    @Test
    void h5ParkingOpinionShouldCreateAndAppearInDetail() throws Exception {
        mockMvc.perform(post("/api/h5/parking-lots/lot-wz-hospital/opinions")
                .contentType("application/json")
                .content("""
                    {
                      "authorName": "测试用户",
                      "topic": "停车场收费",
                      "rating": 4.0,
                      "content": "收费规则整体清楚，希望高峰时段排队提醒能更明显。",
                      "imageUrls": ["data:image/png;base64,ZmFrZS1pbWFnZQ=="]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parkingLotId").value("lot-wz-hospital"))
            .andExpect(jsonPath("$.topic").value("停车场收费"))
            .andExpect(jsonPath("$.imageUrls[0]").value("data:image/png;base64,ZmFrZS1pbWFnZQ=="));

        mockMvc.perform(get("/api/h5/parking-lots/lot-wz-hospital"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opinions").isArray());
    }

    @Test
    void loginShouldReturnAuthUserPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("""
                    {
                      "username": "admin",
                      "password": "admin123",
                      "loginAs": "ADMIN"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.user.username").value("admin"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void registerShouldCreateOperatorAndReturnLoginPayload() throws Exception {
        String username = "ops_test_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("""
                    {
                      "username": "%s",
                      "password": "ops123456"
                    }
                    """.formatted(username)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.user.username").value(username))
            .andExpect(jsonPath("$.user.role").value("OPERATOR"));
    }

    @Test
    void operatorShouldNotLoginFromAdminEntry() throws Exception {
        String username = "role_test_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("""
                    {
                      "username": "%s",
                      "password": "role123456"
                    }
                    """.formatted(username)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("""
                    {
                      "username": "%s",
                      "password": "role123456",
                      "loginAs": "ADMIN"
                    }
                    """.formatted(username)))
            .andExpect(status().isUnauthorized());
    }
}
