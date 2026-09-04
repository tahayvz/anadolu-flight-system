package com.anadoluair.flight.integrationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Service bağlamı")
class IntegrationServiceApplicationTests {

    @Test
    @DisplayName("uygulama bağlamı yüklenir")
    void contextLoads() {
    }
}
