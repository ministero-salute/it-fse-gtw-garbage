package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.base.ClientRoutes.Config.*;
import static it.finanze.sanita.fse2.ms.gtw.garbage.enums.ConfigItemTypeEnum.*;

import java.util.List;
import java.util.Map;
import it.finanze.sanita.fse2.ms.gtw.garbage.client.IConfigClient;
import it.finanze.sanita.fse2.ms.gtw.garbage.dto.ConfigItemDTO;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.impl.ConfigSRV;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.ProfileUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("ISOLATION")
@ActiveProfiles("ISOLATION")
@SpringJUnitConfig(ConfigurationServiceIsolationTest.Context.class)
class ConfigurationServiceIsolationTest {
    // Create only the dependencies in the context; initialize each real service after stubbing the client.
    @org.springframework.context.annotation.Profile("ISOLATION")
    @org.springframework.context.annotation.Configuration
    static class Context { }
    @MockitoBean IConfigClient client;
    @MockitoBean ProfileUtility profiles;
    @Autowired org.springframework.context.ApplicationContext context;
    ConfigSRV service;

    @BeforeEach
    void setup() {
        service = new ConfigSRV();
        context.getAutowireCapableBeanFactory().autowireBean(service);
        ReflectionTestUtils.setField(service, "refreshRate", 900000L);
    }

    ConfigItemDTO response(Map<String, String> values) {
        var response = new ConfigItemDTO();
        response.setConfigurationItems(List.of(new ConfigItemDTO.ConfigDataItemDTO("props", values)));
        return response;
    }

    void initialize() {
        when(client.getConfigurationItems(GENERIC)).thenReturn(response(Map.of(
                PROPS_NAME_VALD_DOCS_RETENTION_DAY, "7", PROPS_NAME_ITEMS_RETENTION_DAY, "30",
                PROPS_NAME_REMOVE_EDS_ENABLE, "false")));
        when(client.getConfigurationItems(GARBAGE)).thenReturn(response(Map.of(PROPS_NAME_ITEMS_RETENTION_DAY, "60")));
        service.postConstruct();
    }

    @Test
    void specificPropertiesOverrideGenericAndFreshValuesAreCached() {
        initialize();
        assertEquals(7, service.getValidatedDocRetentionDay());
        assertEquals(60, service.getConfigItemsRetentionDay());
        assertFalse(service.isRemoveEds());
        assertEquals(900000, service.getRefreshRate());
        verify(client, never()).getProps(anyString(), anyString(), any());
    }

    @Test
    void stalePropertiesRefreshUsingPreviousValues() {
        initialize();
        ReflectionTestUtils.setField(service, "refreshRate", 0L);
        when(client.getProps(PROPS_NAME_VALD_DOCS_RETENTION_DAY, "7", GARBAGE)).thenReturn("10");
        when(client.getProps(PROPS_NAME_ITEMS_RETENTION_DAY, "60", GARBAGE)).thenReturn("90");
        when(client.getProps(PROPS_NAME_REMOVE_EDS_ENABLE, "false", GARBAGE)).thenReturn("true");
        assertEquals(10, service.getValidatedDocRetentionDay());
        assertEquals(90, service.getConfigItemsRetentionDay());
        assertTrue(service.isRemoveEds());
    }

    @Test
    void testProfileSkipsRemoteInitialization() {
        when(profiles.isTestProfile()).thenReturn(true);
        service.postConstruct();
        verifyNoInteractions(client);
    }

    @Test
    void missingRequiredPropertiesFailInitialization() {
        var empty = new ConfigItemDTO();
        empty.setConfigurationItems(List.of());
        when(client.getConfigurationItems(any())).thenReturn(empty);
        assertTrue(assertThrows(IllegalStateException.class, service::postConstruct).getMessage().contains(PROPS_NAME_VALD_DOCS_RETENTION_DAY));
        when(client.getConfigurationItems(any())).thenReturn(response(Map.of(PROPS_NAME_VALD_DOCS_RETENTION_DAY, "7")));
        assertTrue(assertThrows(IllegalStateException.class, service::postConstruct).getMessage().contains(PROPS_NAME_ITEMS_RETENTION_DAY));
    }

    @Test
    void initializationFailurePropagates() {
        var failure = new IllegalStateException("config offline");
        when(client.getConfigurationItems(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, service::postConstruct));
    }

    @Test
    void invalidRefreshedRetentionIsNotSilentlyAccepted() {
        initialize();
        ReflectionTestUtils.setField(service, "refreshRate", 0L);
        when(client.getProps(PROPS_NAME_ITEMS_RETENTION_DAY, "60", GARBAGE)).thenReturn("invalid");
        assertThrows(NumberFormatException.class, service::getConfigItemsRetentionDay);
    }
}
