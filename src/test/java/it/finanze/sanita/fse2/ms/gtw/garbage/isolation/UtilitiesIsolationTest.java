package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.StartupListener;
import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.CollectionNaming;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.DateUtility;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.ProfileUtility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("ISOLATION")
class UtilitiesIsolationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void collectionNamesUseTestPrefixOnlyForTestProfiles(boolean test) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(test ? "TEST" : "ISOLATION");
        var profiles = new ProfileUtility();
        ReflectionTestUtils.setField(profiles, "environment", environment);
        var naming = new CollectionNaming();
        ReflectionTestUtils.setField(naming, "profileUtility", profiles);
        String prefix = test ? "test_" : "";
        assertEquals(List.of(prefix + "transaction_data", prefix + "ini_eds_invocation", prefix + "validated_documents",
                        prefix + "schema", prefix + "schematron", prefix + "transform", prefix + "terminology", prefix + "dictionary"),
                List.of(naming.getTransactionDataCollection(), naming.getIniEdsInvocationCollection(), naming.getValidatedDocuments(),
                        naming.getSchema(), naming.getSchematron(), naming.getTransformCollection(), naming.getTerminologyCollection(), naming.getDictionaryCollection()));
    }

    @Test
    void missingOrEmptyEnvironmentIsNotTestProfile() {
        var utility = new ProfileUtility();
        assertFalse(utility.isTestProfile());
        ReflectionTestUtils.setField(utility, "environment", new MockEnvironment());
        assertFalse(utility.isTestProfile());
    }

    @Test
    void profileDetectionUsesFirstActiveProfileAndCaseInsensitiveSubstring() {
        var environment = new MockEnvironment();
        var utility = new ProfileUtility();
        ReflectionTestUtils.setField(utility, "environment", environment);
        environment.setActiveProfiles("integration-TEST", "dev");
        assertTrue(utility.isTestProfile());
        environment.setActiveProfiles("ISOLATION", "test");
        assertFalse(utility.isTestProfile());
    }

    @Test
    void startupRejectsMissingAndZeroBatchLimitButAcceptsConfiguredLimit() {
        var listener = new StartupListener();
        assertThrows(IllegalArgumentException.class, listener::runAfterStartup);
        ReflectionTestUtils.setField(listener, "queryLimit", 0);
        assertThrows(IllegalArgumentException.class, listener::runAfterStartup);
        ReflectionTestUtils.setField(listener, "queryLimit", 25);
        assertDoesNotThrow(listener::runAfterStartup);
    }

    @Test
    void dateArithmeticCrossesMonthBoundaryWithoutMutatingInput() {
        var calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2024, Calendar.MARCH, 1, 12, 0);
        Date input = calendar.getTime();
        Date result = DateUtility.addDay(input, -1);
        calendar.setTime(result);
        assertEquals(2024, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH));
        assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(12, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(1, input.toInstant().atZone(java.time.ZoneId.systemDefault()).getDayOfMonth());
    }

    @Test
    void hourCutoffUsesRequestedHours() {
        long before = System.currentTimeMillis() - 3 * 3600000L;
        Date cutoff = DateUtility.getDateCondition(3);
        assertTrue(cutoff.getTime() >= before);
        assertTrue(cutoff.getTime() <= System.currentTimeMillis() - 3 * 3600000L);
    }

    @Test
    void invalidDateArgumentsAreWrappedWithOriginalCause() {
        assertInstanceOf(NullPointerException.class,
                assertThrows(BusinessException.class, () -> DateUtility.addDay(null, 1)).getCause());
        assertInstanceOf(NullPointerException.class,
                assertThrows(BusinessException.class, () -> DateUtility.addDay(new Date(), null)).getCause());
    }

    @Test
    void causeOnlyBusinessExceptionRetainsFailure() {
        var cause = new IllegalStateException("failure");
        assertSame(cause, new BusinessException(cause).getCause());
    }
}
