package it.finanze.sanita.fse2.ms.gtw.garbage.isolation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;

import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.RetentionCFG;
import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.impl.*;

@Tag("ISOLATION")
@ActiveProfiles("ISOLATION")
@TestPropertySource(properties = "retention.transactions-query.limit=25")
@SpringJUnitConfig({TransactionsRepo.class, ValidatedDocumentRepo.class, IniEdsInvocationRepo.class,
        CfgItemsRetentionRepo.class, RetentionCFG.class})
class RepositoriesIsolationTest {
    @Autowired TransactionsRepo transactions;
    @Autowired ValidatedDocumentRepo documents;
    @Autowired IniEdsInvocationRepo invocations;
    @Autowired CfgItemsRetentionRepo items;
    @MockitoBean MongoTemplate mongo;

    @Test
    void transactionSearchRestrictsStatusesExpiryProjectionAndBatchSize() {
        var event = new TransactionEventsETY();
        when(mongo.find(any(Query.class), eq(TransactionEventsETY.class))).thenReturn(List.of(event));
        Date before = new Date();
        assertEquals(List.of(event), transactions.findExpiringTransactionData("SEND_TO_INI"));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(TransactionEventsETY.class));
        assertEquals(25, query.getValue().getLimit());
        assertEquals(new Document("workflow_instance_id", 1), query.getValue().getFieldsObject());
        Document criteria = query.getValue().getQueryObject();
        assertEquals(List.of(new Document("eventType", "SEND_TO_INI").append("eventStatus", "SUCCESS"),
                new Document("eventStatus", "BLOCKING_ERROR"),
                new Document("eventStatus", "BLOCKING_ERROR_MAX_RETRY")), criteria.get("$or"));
        Date cutoff = criteria.get("expiring_date", Document.class).getDate("$lt");
        assertFalse(cutoff.before(before));
        assertFalse(cutoff.after(new Date()));
    }

    @Test
    void transactionDeletionIsRestrictedToRequestedWorkflowIds() {
        when(mongo.remove(any(Query.class), eq(TransactionEventsETY.class))).thenReturn(DeleteResult.acknowledged(2));
        assertEquals(2, transactions.deleteExpiringTransactionData(List.of("w1", "w2")));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(TransactionEventsETY.class));
        assertEquals(new Document("workflow_instance_id", new Document("$in", List.of("w1", "w2"))), query.getValue().getQueryObject());
    }

    @Test
    void transactionSearchFailureRetainsCause() {
        var failure = new IllegalStateException("query failure");
        when(mongo.find(any(Query.class), eq(TransactionEventsETY.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> transactions.findExpiringTransactionData("SEND_TO_INI")).getCause());
    }

    @Test
    void transactionDeleteFailureRetainsCause() {
        var failure = new IllegalStateException("delete failure");
        when(mongo.remove(any(Query.class), eq(TransactionEventsETY.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> transactions.deleteExpiringTransactionData(List.of("w1"))).getCause());
    }

    @Test
    void documentDeletionProjectsWorkflowIdsAndEnforcesCutoffAndLimit() {
        var document = new ValidatedDocumentsETY();
        document.setWorkflowInstanceId("w1");
        when(mongo.findAllAndRemove(any(Query.class), eq(ValidatedDocumentsETY.class))).thenReturn(List.of(document));
        Date cutoff = new Date(123456789L);
        assertEquals(List.of("w1"), documents.deleteValidatedDocuments(cutoff));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAllAndRemove(query.capture(), eq(ValidatedDocumentsETY.class));
        assertEquals(new Document("insertion_date", new Document("$lt", cutoff)), query.getValue().getQueryObject());
        assertEquals(new Document("w_id", 1), query.getValue().getFieldsObject());
        assertEquals(25, query.getValue().getLimit());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void noDocumentsReturnsEmptyIds(List<ValidatedDocumentsETY> result) {
        when(mongo.findAllAndRemove(any(Query.class), eq(ValidatedDocumentsETY.class))).thenReturn(result);
        assertEquals(List.of(), documents.deleteValidatedDocuments(new Date()));
    }

    @Test
    void documentDeleteFailureRetainsCause() {
        var failure = new IllegalStateException("delete failure");
        when(mongo.findAllAndRemove(any(Query.class), eq(ValidatedDocumentsETY.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> documents.deleteValidatedDocuments(new Date())).getCause());
    }

    @Test
    void invocationDeletionIsRestrictedToWorkflowIds() {
        when(mongo.remove(any(Query.class), eq(IniEdsInvocationETY.class))).thenReturn(DeleteResult.acknowledged(3));
        assertEquals(3, invocations.deleteIds(List.of("w1")));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(IniEdsInvocationETY.class));
        assertEquals(new Document("workflow_instance_id", new Document("$in", List.of("w1"))), query.getValue().getQueryObject());
    }

    @Test
    void invocationFailurePropagates() {
        var failure = new IllegalStateException("delete failure");
        when(mongo.remove(any(Query.class), eq(IniEdsInvocationETY.class))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> invocations.deleteIds(List.of())));
    }

    @Test
    void invocationMarkingPersistsFlagOnlyForRequestedWorkflows() {
        invocations.markIdsForDeletion(List.of("w1", "w2"));

        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(IniEdsInvocationETY.class));
        assertEquals(new Document("workflow_instance_id", new Document("$in", List.of("w1", "w2"))),
                query.getValue().getQueryObject());
        assertEquals(new Document("$set", new Document("to_delete", true)), update.getValue().getUpdateObject());
    }

    @Test
    void findsDistinctWorkflowIdsWithDeletionMarker() {
        when(mongo.findDistinct(any(Query.class), eq("workflow_instance_id"), eq(IniEdsInvocationETY.class), eq(String.class)))
                .thenReturn(List.of("w1"));

        assertEquals(List.of("w1"), invocations.findWorkflowInstanceIdsMarkedForDeletion());

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findDistinct(query.capture(), eq("workflow_instance_id"), eq(IniEdsInvocationETY.class), eq(String.class));
        assertEquals(new Document("to_delete", true), query.getValue().getQueryObject());
    }

    @Test
    void invocationCleanupOnlyRequiresDeletionMarker() {
        when(mongo.remove(any(Query.class), eq(IniEdsInvocationETY.class))).thenReturn(DeleteResult.acknowledged(2));

        assertEquals(2, invocations.deleteMarked());

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(IniEdsInvocationETY.class));
        assertEquals(new Document("to_delete", true),
                query.getValue().getQueryObject());
    }

    @Test
    void configurationDeletionRequiresDeletedFlagAndLastUpdateCutoff() {
        when(mongo.remove(any(Query.class), eq(SchemaETY.class))).thenReturn(DeleteResult.acknowledged(4));
        Date cutoff = new Date(123456789L);
        assertEquals(4, items.deleteCfgItems(cutoff, SchemaETY.class));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(SchemaETY.class));
        assertEquals(new Document("deleted", true).append("last_update_date", new Document("$lt", cutoff)), query.getValue().getQueryObject());
    }

    @Test
    void terminologyDeletionUsesCreationDateAndDictionaryCollection() {
        when(mongo.remove(any(Query.class), eq(DictionaryETY.class))).thenReturn(DeleteResult.acknowledged(5));
        Date cutoff = new Date(123456789L);
        assertEquals(5, items.deleteTerminology(cutoff));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(DictionaryETY.class));
        assertEquals(new Document("deleted", true).append("creation_date", new Document("$lt", cutoff)), query.getValue().getQueryObject());
    }

    @Test
    void configurationDeleteFailuresRetainCause() {
        var failure = new IllegalStateException("delete failure");
        when(mongo.remove(any(Query.class), any(Class.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BusinessException.class, () -> items.deleteCfgItems(new Date(), SchemaETY.class)).getCause());
        assertSame(failure, assertThrows(BusinessException.class, () -> items.deleteTerminology(new Date())).getCause());
    }
}
