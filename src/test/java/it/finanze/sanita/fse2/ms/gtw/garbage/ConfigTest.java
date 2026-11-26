package it.finanze.sanita.fse2.ms.gtw.garbage;

import static it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.base.ClientRoutes.Config.PROPS_NAME_ITEMS_RETENTION_DAY;
import static it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.base.ClientRoutes.Config.PROPS_NAME_VALD_DOCS_RETENTION_DAY;
import static it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants.Profile.TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles(TEST)
@TestInstance(PER_CLASS)
class ConfigTest extends AbstractConfig {

    private static final List<Pair<String, String>> DEFAULT_PROPS = Arrays.asList(
        Pair.of(PROPS_NAME_VALD_DOCS_RETENTION_DAY, "5"),
        Pair.of(PROPS_NAME_ITEMS_RETENTION_DAY, "5")
    );

    @Test
    void testCacheProps() {
        testCacheProps(DEFAULT_PROPS.get(0), () -> assertEquals(Integer.parseInt(DEFAULT_PROPS.get(0).getValue()), config.getValidatedDocRetentionDay()));
        testCacheProps(DEFAULT_PROPS.get(1), () -> assertEquals(Integer.parseInt(DEFAULT_PROPS.get(1).getValue()), config.getConfigItemsRetentionDay()));
    }

    @Test
    void testRefreshProps() {
        testRefreshProps(DEFAULT_PROPS.get(0), "4", () -> assertEquals(4, config.getValidatedDocRetentionDay()));
        testRefreshProps(DEFAULT_PROPS.get(1), "4", () -> assertEquals(4, config.getConfigItemsRetentionDay()));
    }

    @Test
    void testIntegrityProps() {
        testIntegrityCheck();
    }

    @Override
    public List<Pair<String, String>> defaults() {
        return DEFAULT_PROPS;
    }
}
