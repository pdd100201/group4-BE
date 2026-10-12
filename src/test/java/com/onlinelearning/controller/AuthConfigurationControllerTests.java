package com.onlinelearning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.seed-demo-users.enabled=false", "app.google.client-id=public-fixture.apps.googleusercontent.com"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthConfigurationControllerTests {
    @Autowired MockMvc mvc;

    @Test
    void anonymousReceivesOnlyPublicGoogleClientId() throws Exception {
        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"googleClientId\":\"public-fixture.apps.googleusercontent.com\"}", true));
    }
}
