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
        transactions.deleteTransactionData();
        var order = inOrder(transactionRepo, invocationRepo);
        order.verify(transactionRepo).findExpiringTransactionData(removeEds ? Constants.SEND_TO_INI : Constants.EDS_WORKFLOW);
        order.verify(transactionRepo).deleteExpiringTransactionData(List.of("workflow-1"));
        order.verify(invocationRepo).deleteIds(List.of("workflow-1"));
    }

    @Test
    void nullSearchResultDoesNotDeleteAnything() {
        when(config.isRemoveEds()).thenReturn(false);
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(null);
        transactions.deleteTransactionData();
        verify(transactionRepo, never()).deleteExpiringTransactionData(any());
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void emptySearchUsesOnlyAnEmptyIdList() {
        when(config.isRemoveEds()).thenReturn(false);
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(List.of());
        transactions.deleteTransactionData();
        verify(transactionRepo).deleteExpiringTransactionData(List.of());
        verify(invocationRepo).deleteIds(List.of());
    }

    @Test
    void transactionDeletionFailureStopsInvocationDeletionAndPreservesCause() {
        when(config.isRemoveEds()).thenReturn(true);
        when(transactionRepo.findExpiringTransactionData(anyString())).thenReturn(List.of());
        var failure = new IllegalStateException("Mongo unavailable");
        when(transactionRepo.deleteExpiringTransactionData(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void configurationFailureIsWrappedBeforeAnyRepositoryAccess() {
        var failure = new IllegalStateException("configuration unavailable");
        when(config.isRemoveEds()).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, transactions::deleteTransactionData).getCause());
        verifyNoInteractions(transactionRepo, invocationRepo);
    }

    @Test
    void deletesDocumentsOlderThanConfiguredCalendarDaysAndTheirInvocations() {
        Date before = DateUtility.addDay(new Date(), -7);
        when(documentRepo.deleteValidatedDocuments(any())).thenReturn(List.of("w1", "w2"));
        documents.deleteValidatedDocuments(7);
        var cutoff = ArgumentCaptor.forClass(Date.class);
        verify(documentRepo).deleteValidatedDocuments(cutoff.capture());
        assertFalse(cutoff.getValue().before(before));
        assertFalse(cutoff.getValue().after(DateUtility.addDay(new Date(), -7)));
        verify(invocationRepo).deleteIds(List.of("w1", "w2"));
    }

    @Test
    void documentFailureStopsAssociatedDeletion() {
        var failure = new IllegalStateException("read failure");
        when(documentRepo.deleteValidatedDocuments(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> documents.deleteValidatedDocuments(1)).getCause());
        verifyNoInteractions(invocationRepo);
    }

    @Test
    void associatedInvocationFailureIsWrapped() {
        when(documentRepo.deleteValidatedDocuments(any())).thenReturn(List.of("w1"));
        var failure = new IllegalStateException("delete failure");
        when(invocationRepo.deleteIds(any())).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> documents.deleteValidatedDocuments(1)).getCause());
    }

    @Test
    void deletesAllConfigurationTypesUsingOneCutoff() {
        items.deleteCFGItems(30);
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
