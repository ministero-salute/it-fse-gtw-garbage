package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;
import static it.finanze.sanita.fse2.ms.gtw.garbage.enums.ConfigItemTypeEnum.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import it.finanze.sanita.fse2.ms.gtw.garbage.client.impl.ConfigClient;
import it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.ConfigClientRoutes;
import it.finanze.sanita.fse2.ms.gtw.garbage.config.MicroservicesURLCFG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Tag("ISOLATION")
@ActiveProfiles("ISOLATION")
@TestPropertySource(properties = "ms.url.gtw-config=http://config.invalid")
@SpringJUnitConfig({ConfigClient.class, ConfigClientRoutes.class, MicroservicesURLCFG.class, ConfigClientIsolationTest.Context.class})
class ConfigClientIsolationTest {
    @org.springframework.context.annotation.Profile("ISOLATION")
    @Configuration
    static class Context {
        @Bean RestTemplate restTemplate() { return new RestTemplate(); }
    }
    @Autowired ConfigClient client;
    @Autowired ConfigClientRoutes routes;
    @Autowired RestTemplate rest;
    MockRestServiceServer server;

    @BeforeEach
    void setup() { server = MockRestServiceServer.bindTo(rest).build(); }

    @Test
    void readsAndDeserializesConfigurationItems() {
        server.expect(requestTo("http://config.invalid/v1/config-items?type=GARBAGE"))
                .andRespond(withSuccess("{\"traceId\":\"trace\",\"spanId\":\"span\",\"size\":1,\"configurationItems\":[{\"key\":\"GARBAGE\",\"items\":{\"days\":\"7\"}}]}", MediaType.APPLICATION_JSON));
        var result = client.getConfigurationItems(GARBAGE);
        assertEquals("trace", result.getTraceId());
        assertEquals("span", result.getSpanId());
        assertEquals(1, result.getSize());
        assertEquals("GARBAGE", result.getConfigurationItems().getFirst().getKey());
        assertEquals("7", result.getConfigurationItems().getFirst().getItems().get("days"));
        server.verify();
    }

    @Test
    void specificPropertyWins() {
        server.expect(requestTo(routes.status())).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GARBAGE, "days"))).andRespond(withSuccess("7", MediaType.TEXT_PLAIN));
        assertEquals("7", client.getProps("days", "1", GARBAGE));
        server.verify();
    }

    @Test
    void absentSpecificPropertyFallsBackToGeneric() {
        server.expect(requestTo(routes.status())).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GARBAGE, "days"))).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GENERIC, "days"))).andRespond(withSuccess("30", MediaType.TEXT_PLAIN));
        assertEquals("30", client.getProps("days", "1", GARBAGE));
        server.verify();
    }

    @Test
    void missingGenericPropertyReturnsNull() {
        server.expect(requestTo(routes.status())).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GARBAGE, "days"))).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GENERIC, "days"))).andRespond(withSuccess());
        assertNull(client.getProps("days", "1", GARBAGE));
        server.verify();
    }

    @Test
    void networkFailureKeepsPreviousValueWithoutFetchingProperty() {
        server.expect(requestTo(routes.status())).andRespond(withException(new java.io.IOException("offline")));
        assertEquals("7", client.getProps("days", "7", GARBAGE));
        server.verify();
    }

    @Test
    void unchangedPropertyIsReturned() {
        server.expect(requestTo(routes.status())).andRespond(withSuccess());
        server.expect(requestTo(routes.getConfigItem(GARBAGE, "days"))).andRespond(withSuccess("7", MediaType.TEXT_PLAIN));
        assertEquals("7", client.getProps("days", "7", GARBAGE));
        server.verify();
    }

    @Test
    void serverFailureIsNotHiddenAsNetworkUnavailability() {
        server.expect(requestTo(routes.status())).andRespond(withServerError());
        assertThrows(HttpServerErrorException.class, () -> client.getProps("days", "7", GARBAGE));
        server.verify();
    }

    @Test
    void routeIdentifiersRemainStable() {
        assertEquals("[CFG]", routes.identifier());
        assertEquals("cfg", routes.microservice());
    }
}
