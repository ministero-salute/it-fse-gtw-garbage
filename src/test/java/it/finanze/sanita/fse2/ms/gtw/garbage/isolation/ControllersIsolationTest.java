package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.opentelemetry.api.trace.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.controller.impl.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.config.RetentionCFG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Tag("ISOLATION")
@ActiveProfiles("ISOLATION")
@SpringJUnitConfig({SchedulerCTL.class, LivenessCheckCTL.class, DataRetentionScheduler.class,
        ValidatedDocumentRetentionScheduler.class, CFGItemsRetentionScheduler.class})
class ControllersIsolationTest {
    @Autowired SchedulerCTL controller;
    @Autowired LivenessCheckCTL health;
    @Autowired DataRetentionScheduler transactionScheduler;
    @Autowired ValidatedDocumentRetentionScheduler documentScheduler;
    @Autowired CFGItemsRetentionScheduler itemScheduler;
    @MockitoBean IDataRetentionSRV transactions;
    @MockitoBean IValidatedDocumentRetentionSRV documents;
    @MockitoBean ICfgItemsRetentionRepoSRV items;
    @MockitoBean IConfigSRV config;
    @MockitoBean Tracer tracer;
    @MockitoBean RetentionCFG retentionCfg;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        when(retentionCfg.getQueryLimit()).thenReturn(25);
        mvc = MockMvcBuilders.standaloneSetup(controller, health).build();
    }

    @Test
    void transactionEndpointExecutesRealSchedulerAndReturnsTrace() throws Exception {
        var builder = mock(SpanBuilder.class);
        var context = SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());
        when(tracer.spanBuilder(anyString())).thenReturn(builder);
        when(builder.startSpan()).thenReturn(Span.wrap(context));
        mvc.perform(post("/run-scheduler")).andExpect(status().isOk())
                .andExpect(jsonPath("spanID").value(context.getSpanId()))
                .andExpect(jsonPath("traceID").value(context.getTraceId()));
        verify(transactions).deleteTransactionData();
    }

    @Test
    void documentEndpointUsesConfiguredRetention() throws Exception {
        when(config.getValidatedDocRetentionDay()).thenReturn(7);
        mvc.perform(post("/run-scheduler/validatedDocuments")).andExpect(status().isOk())
                .andExpect(jsonPath("spanID").doesNotExist());
        verify(documents).deleteValidatedDocuments(7);
    }

    @Test
    void configurationEndpointUsesConfiguredRetention() throws Exception {
        when(config.getConfigItemsRetentionDay()).thenReturn(30);
        mvc.perform(post("/run-scheduler/cfgItems")).andExpect(status().isOk());
        verify(items).deleteCFGItems(30);
    }

    @Test
    void schedulerFailuresAreContainedAndDoNotBreakManualEndpoints() throws Exception {
        doThrow(new IllegalStateException("transaction failure")).when(transactions).deleteTransactionData();
        doThrow(new IllegalStateException("document failure")).when(documents).deleteValidatedDocuments(anyInt());
        doThrow(new IllegalStateException("config failure")).when(items).deleteCFGItems(anyInt());
        mvc.perform(post("/run-scheduler")).andExpect(status().isOk());
        mvc.perform(post("/run-scheduler/validatedDocuments")).andExpect(status().isOk());
        mvc.perform(post("/run-scheduler/cfgItems")).andExpect(status().isOk());
        verify(transactions).deleteTransactionData();
        verify(documents).deleteValidatedDocuments(0);
        verify(items).deleteCFGItems(0);
    }

    @Test
    void configurationReadFailurePreventsRetentionCalls() throws Exception {
        when(config.getValidatedDocRetentionDay()).thenThrow(new IllegalStateException("offline"));
        when(config.getConfigItemsRetentionDay()).thenThrow(new IllegalStateException("offline"));
        mvc.perform(post("/run-scheduler/validatedDocuments")).andExpect(status().isOk());
        mvc.perform(post("/run-scheduler/cfgItems")).andExpect(status().isOk());
        verifyNoInteractions(documents, items);
    }

    @Test
    void livenessIsUpWithoutExternalDependencies() throws Exception {
        mvc.perform(get("/status")).andExpect(status().isOk()).andExpect(jsonPath("status").value("UP"));
        assertEquals("UP", health.health().getStatus().getCode());
        verifyNoInteractions(transactions, documents, items, config);
    }

    @Test
    void schedulerEndpointRejectsWrongMethod() throws Exception {
        mvc.perform(get("/run-scheduler")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(transactions);
    }

    @Test
    void allSchedulersDrainMultipleBatchesIncludingPartialBatchesUntilEmpty() {
        when(config.getValidatedDocRetentionDay()).thenReturn(7);
        when(config.getConfigItemsRetentionDay()).thenReturn(30);
        when(transactions.deleteTransactionData()).thenReturn(50L, 2L, 0L);
        when(documents.deleteValidatedDocuments(7)).thenReturn(25L, 3L, 0L);
        when(items.deleteCFGItems(30)).thenReturn(125L, 1L, 0L);

        transactionScheduler.action();
        documentScheduler.action();
        itemScheduler.action();

        verify(transactions, times(3)).deleteTransactionData();
        verify(documents, times(3)).deleteValidatedDocuments(7);
        verify(items, times(3)).deleteCFGItems(30);
        verify(config).getValidatedDocRetentionDay();
        verify(config).getConfigItemsRetentionDay();
    }

    @Test
    void allSchedulersStopOnBatchFailureAndRetryOnNextRun() {
        when(transactions.deleteTransactionData()).thenReturn(5L)
                .thenThrow(new IllegalStateException("transaction failure")).thenReturn(0L);
        when(documents.deleteValidatedDocuments(0)).thenReturn(5L)
                .thenThrow(new IllegalStateException("document failure")).thenReturn(0L);
        when(items.deleteCFGItems(0)).thenReturn(5L)
                .thenThrow(new IllegalStateException("configuration failure")).thenReturn(0L);

        assertDoesNotThrow(transactionScheduler::run);
        assertDoesNotThrow(documentScheduler::run);
        assertDoesNotThrow(itemScheduler::run);
        verify(transactions, times(2)).deleteTransactionData();
        verify(documents, times(2)).deleteValidatedDocuments(0);
        verify(items, times(2)).deleteCFGItems(0);

        transactionScheduler.run();
        documentScheduler.run();
        itemScheduler.run();
        verify(transactions, times(3)).deleteTransactionData();
        verify(documents, times(3)).deleteValidatedDocuments(0);
        verify(items, times(3)).deleteCFGItems(0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -1})
    void invalidChunkSizeDoesNotRunUnboundedDeletes(int chunkSize) {
        when(retentionCfg.getQueryLimit()).thenReturn(chunkSize);

        assertDoesNotThrow(transactionScheduler::run);
        assertDoesNotThrow(documentScheduler::run);
        assertDoesNotThrow(itemScheduler::run);

        verifyNoInteractions(transactions, documents, items);
    }
}
