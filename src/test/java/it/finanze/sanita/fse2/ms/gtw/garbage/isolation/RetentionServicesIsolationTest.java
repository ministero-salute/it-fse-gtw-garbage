package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants;
import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.IConfigSRV;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.impl.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.DateUtility;

@Tag("ISOLATION")
@ActiveProfiles("ISOLATION")
@SpringJUnitConfig({DataRetentionSRV.class, ValidatedDocumentRetentionSRV.class, CfgItemsRetentionSRV.class})
class RetentionServicesIsolationTest {
    @Autowired DataRetentionSRV transactions;
    @Autowired ValidatedDocumentRetentionSRV documents;
    @Autowired CfgItemsRetentionSRV items;
    @MockitoBean ITransactionsRepo transactionRepo;
    @MockitoBean IIniEdsInvocationRepo invocationRepo;
    @MockitoBean IValidatedDocumentRepo documentRepo;
    @MockitoBean ICfgItemsRetentionRepo itemRepo;
    @MockitoBean IConfigSRV config;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletesMatchingTransactionsBeforeAssociatedInvocations(boolean removeEds) {
        when(config.isRemoveEds()).thenReturn(removeEds);
        TransactionEventsETY event = new TransactionEventsETY();
        event.setWorkflowInstanceId("workflow-1");
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(List.of(event));
        when(transactionRepo.deleteExpiringTransactionData(List.of("workflow-1"))).thenReturn(3);
        when(invocationRepo.deleteMarked()).thenReturn(2);
        assertEquals(5L, transactions.deleteTransactionData());
        var order = inOrder(transactionRepo, invocationRepo);
        order.verify(transactionRepo).findExpiringTransactionData(removeEds ? Constants.SEND_TO_INI : Constants.UAR_FINAL_STATUS);
        order.verify(invocationRepo).findWorkflowInstanceIdsMarkedForDeletion();
        order.verify(invocationRepo).markIdsForDeletion(List.of("workflow-1"));
        order.verify(transactionRepo).deleteExpiringTransactionData(List.of("workflow-1"));
        order.verify(invocationRepo).deleteMarked();
    }

    @Test
    void nullSearchResultStillCleansUpMarkedInvocations() {
        when(config.isRemoveEds()).thenReturn(false);
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(null);
        assertEquals(0L, transactions.deleteTransactionData());
        verify(transactionRepo, never()).deleteExpiringTransactionData(any());
        verify(invocationRepo).findWorkflowInstanceIdsMarkedForDeletion();
        verify(invocationRepo).deleteMarked();
        verifyNoMoreInteractions(invocationRepo);
    }

    @Test
    void emptySearchStillCleansUpMarkedInvocations() {
        when(config.isRemoveEds()).thenReturn(false);
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(List.of());
        assertEquals(0L, transactions.deleteTransactionData());
        verify(transactionRepo, never()).deleteExpiringTransactionData(any());
        verify(invocationRepo).findWorkflowInstanceIdsMarkedForDeletion();
        verify(invocationRepo).deleteMarked();
        verifyNoMoreInteractions(invocationRepo);
    }

    @Test
    void transactionDeletionFailureStopsInvocationDeletionAndPreservesCause() {
        when(config.isRemoveEds()).thenReturn(true);
        when(invocationRepo.findWorkflowInstanceIdsMarkedForDeletion()).thenReturn(List.of("workflow-1"));
        var failure = new IllegalStateException("Mongo unavailable");
        when(transactionRepo.deleteExpiringTransactionData(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());
        verify(invocationRepo).markIdsForDeletion(List.of("workflow-1"));
        verify(invocationRepo, never()).deleteMarked();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void retriesMarkedWorkflowsEvenWithoutExpiringTransactions(List<TransactionEventsETY> events) {
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(events);
        when(invocationRepo.findWorkflowInstanceIdsMarkedForDeletion()).thenReturn(List.of("pending"));

        transactions.deleteTransactionData();

        var order = inOrder(transactionRepo, invocationRepo);
        order.verify(invocationRepo).markIdsForDeletion(List.of("pending"));
        order.verify(transactionRepo).deleteExpiringTransactionData(List.of("pending"));
        order.verify(invocationRepo).deleteMarked();
    }

    @Test
    void mergesPendingAndNewWorkflowIdsWithoutDuplicates() {
        var event = new TransactionEventsETY();
        event.setWorkflowInstanceId("new");
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(List.of(event, event));
        when(invocationRepo.findWorkflowInstanceIdsMarkedForDeletion()).thenReturn(List.of("pending", "new"));

        transactions.deleteTransactionData();

        verify(invocationRepo).markIdsForDeletion(List.of("pending", "new"));
        verify(transactionRepo).deleteExpiringTransactionData(List.of("pending", "new"));
        verify(invocationRepo).deleteMarked();
    }

    @Test
    void markingFailurePreventsBothDeletes() {
        when(invocationRepo.findWorkflowInstanceIdsMarkedForDeletion()).thenReturn(List.of("pending"));
        var failure = new IllegalStateException("mark failure");
        doThrow(failure).when(invocationRepo).markIdsForDeletion(any());

        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());

        verify(transactionRepo, never()).deleteExpiringTransactionData(any());
        verify(invocationRepo, never()).deleteMarked();
    }

    @Test
    void failedInvocationCleanupIsRetriedOnNextRun() {
        when(invocationRepo.findWorkflowInstanceIdsMarkedForDeletion()).thenReturn(List.of("pending"));
        var failure = new IllegalStateException("cleanup failure");
        when(invocationRepo.deleteMarked()).thenThrow(failure).thenReturn(1);

        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());
        transactions.deleteTransactionData();

        var order = inOrder(transactionRepo, invocationRepo);
        for (int attempt = 0; attempt < 2; attempt++) {
            order.verify(invocationRepo).markIdsForDeletion(List.of("pending"));
            order.verify(transactionRepo).deleteExpiringTransactionData(List.of("pending"));
            order.verify(invocationRepo).deleteMarked();
        }
    }

    @Test
    void configurationFailureIsWrappedBeforeAnyRepositoryAccess() {
        var failure = new IllegalStateException("configuration unavailable");
        when(config.isRemoveEds()).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());
        verifyNoInteractions(transactionRepo, invocationRepo);
    }

    @Test
    void deletesDocumentsOlderThanConfiguredCalendarDaysWithoutTouchingInvocations() {
        Date before = DateUtility.addDay(new Date(), -7);
        when(documentRepo.deleteValidatedDocuments(any())).thenReturn(List.of("w1", "w2"));
        assertEquals(2L, documents.deleteValidatedDocuments(7));
        var cutoff = ArgumentCaptor.forClass(Date.class);
        verify(documentRepo).deleteValidatedDocuments(cutoff.capture());
        assertFalse(cutoff.getValue().before(before));
        assertFalse(cutoff.getValue().after(DateUtility.addDay(new Date(), -7)));
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void documentFailureStopsAssociatedDeletion() {
        var failure = new IllegalStateException("read failure");
        when(documentRepo.deleteValidatedDocuments(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> documents.deleteValidatedDocuments(1)).getCause());
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void noExpiredDocumentsDoesNotTouchInvocations() {
        when(documentRepo.deleteValidatedDocuments(any())).thenReturn(List.of());

        assertEquals(0L, documents.deleteValidatedDocuments(1));

        verify(documentRepo).deleteValidatedDocuments(any());
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void deletesAllConfigurationTypesUsingOneCutoff() {
        when(itemRepo.deleteCfgItems(any(), any())).thenReturn(2);
        when(itemRepo.deleteTerminology(any())).thenReturn(3);
        assertEquals(11L, items.deleteCFGItems(30));
        var cutoff = ArgumentCaptor.forClass(Date.class);
        verify(itemRepo).deleteCfgItems(cutoff.capture(), eq(SchemaETY.class));
        Date date = cutoff.getValue();
        assertTrue(Math.abs(date.getTime() - DateUtility.addDay(new Date(), -30).getTime()) < 5000);
        verify(itemRepo).deleteCfgItems(date, SchematronETY.class);
        verify(itemRepo).deleteCfgItems(date, TransformETY.class);
        verify(itemRepo).deleteCfgItems(date, TerminologyETY.class);
        verify(itemRepo).deleteTerminology(date);
    }

    @Test
    void configurationDeletionFailureStopsFurtherCollections() {
        var failure = new IllegalStateException("delete failure");
        when(itemRepo.deleteCfgItems(any(), eq(SchemaETY.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> items.deleteCFGItems(30)).getCause());
        verify(itemRepo, never()).deleteTerminology(any());
        verify(itemRepo, never()).deleteCfgItems(any(), eq(SchematronETY.class));
    }
}
