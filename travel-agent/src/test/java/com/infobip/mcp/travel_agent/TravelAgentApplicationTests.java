package com.infobip.mcp.travel_agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestBase.BedrockTestConfig.class)
class TravelAgentApplicationTests extends TestBase {

    @Test
    void contextLoads() {
    }

}
