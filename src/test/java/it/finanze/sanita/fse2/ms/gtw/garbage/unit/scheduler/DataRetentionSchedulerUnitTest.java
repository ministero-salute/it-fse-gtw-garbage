
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package it.finanze.sanita.fse2.ms.gtw.garbage.unit.scheduler;

import com.mongodb.MongoException;
import it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants;
import it.finanze.sanita.fse2.ms.gtw.garbage.config.RetentionCFG;
import it.finanze.sanita.fse2.ms.gtw.garbage.dto.ConfigItemDTO;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.*;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.CFGItemsRetentionScheduler;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.DataRetentionScheduler;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.ValidatedDocumentRetentionScheduler;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.IConfigSRV;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.DateUtility;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

import static it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.base.ClientRoutes.Config.PROPS_NAME_ITEMS_RETENTION_DAY;
import static it.finanze.sanita.fse2.ms.gtw.garbage.client.routes.base.ClientRoutes.Config.PROPS_NAME_VALD_DOCS_RETENTION_DAY;
import static it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants.ConfigItems.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 *
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@ActiveProfiles(Constants.Profile.TEST)
@DisplayName("Data Retention Scheduler Unit Test")
class DataRetentionSchedulerUnitTest {

	@Getter
	static final int hoursAfterInsertion = 120;

	@Getter
	static final int daysAfterInsertion = 5;

	@Autowired
	DataRetentionScheduler retentionScheduler;

	@Autowired
	CFGItemsRetentionScheduler cfgItemsRetentionScheduler;
	
	@Autowired
	ValidatedDocumentRetentionScheduler validatedDocumentRetentionScheduler;

	@SpyBean
	@Qualifier("mongo-template-data")
	MongoTemplate dataTemplate;	
	
	@SpyBean
	@Qualifier("mongo-template-transaction")
	MongoTemplate transactionTemplate;

	@SpyBean
	@Qualifier("mongo-template-rules")
	MongoTemplate rulesTemplate;

	@SpyBean
	@Qualifier("mongo-template-valdoc")
	MongoTemplate valdocTemplate;

	@MockBean
	RetentionCFG retentionCFG;

	@SpyBean
	RestTemplate restTemplate;

	@MockBean
	IConfigSRV config;

	@BeforeEach
	void setup() {
		dataTemplate.dropCollection(IniEdsInvocationETY.class);
		dataTemplate.dropCollection(TransactionEventsETY.class);
		rulesTemplate.dropCollection(SchemaETY.class);
		rulesTemplate.dropCollection(SchematronETY.class);
		rulesTemplate.dropCollection(TerminologyETY.class);
		rulesTemplate.dropCollection(DictionaryETY.class);
		rulesTemplate.dropCollection(TransformETY.class);
		valdocTemplate.dropCollection(ValidatedDocumentsETY.class);
	}
	
	@ParameterizedTest
	@DisplayName("Deletion of transactions and data - Same size")
	@ValueSource(ints = {1000, 15000, 30000})
	void runDeleteTransactions(final int size) {
		
		mockConfigurationItems(0, 0, HttpStatus.OK, RetentionCase.SUCCESS);
		when(config.isRemoveEds()).thenReturn(true);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, false, getHoursAfterInsertion());
		
		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(!CollectionUtils.isEmpty(transactions), "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(!CollectionUtils.isEmpty(data), "Data should be inserted before testing the deletion.");
		retentionScheduler.run();

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertTrue(CollectionUtils.isEmpty(transactions));

		data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assertTrue(CollectionUtils.isEmpty(data));
	}

	@ParameterizedTest
	@DisplayName("Deletion of transactions - database error")
	@ValueSource(ints = {50})
	void deleteTransactionsDatabaseError(final int size) {

		mockConfigurationItems(0, 0, HttpStatus.OK, RetentionCase.SUCCESS);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, false, getHoursAfterInsertion());

		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(!CollectionUtils.isEmpty(transactions), "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(!CollectionUtils.isEmpty(data), "Data should be inserted before testing the deletion.");

		doThrow(MongoException.class).when(transactionTemplate).remove(any(Query.class), eq(TransactionEventsETY.class));
		assertDoesNotThrow(() -> retentionScheduler.run());

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertFalse(CollectionUtils.isEmpty(transactions), "No transaction should have been deleted");

		data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assertFalse(CollectionUtils.isEmpty(data), "No data should have been deleted");
	}

	@ParameterizedTest
	@DisplayName("Deletion of transactions and data - database error on data")
	@ValueSource(ints = {50})
	void deleteDataDatabaseError(final int size) {

		mockConfigurationItems(0, 0, HttpStatus.OK, RetentionCase.SUCCESS);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, false, getHoursAfterInsertion());

		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(!CollectionUtils.isEmpty(transactions), "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(!CollectionUtils.isEmpty(data), "Data should be inserted before testing the deletion.");

		doThrow(MongoException.class).when(dataTemplate).remove(any(Query.class), eq(IniEdsInvocationETY.class));
		assertDoesNotThrow(() -> retentionScheduler.run());

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertTrue(CollectionUtils.isEmpty(transactions), "Transactions should have been deleted");

		data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assertFalse(CollectionUtils.isEmpty(data), "No data should have been deleted");
	}

	private void mockConfigurationItems(final Integer success, final Integer error, final HttpStatus status, RetentionCase retentionCase) {
		List<ConfigItemDTO.ConfigDataItemDTO> items = new ArrayList<>();
		Map<String, String> configItems = new HashMap<>();
		switch (retentionCase) {
			case CONFIG_ITEMS:
				configItems.put(PROPS_NAME_ITEMS_RETENTION_DAY, String.valueOf(success));
				break;
			case VAL_DOCS:
				configItems.put(PROPS_NAME_VALD_DOCS_RETENTION_DAY, String.valueOf(success));
				break;
			case SUCCESS:
				configItems.put(SUCCESS_TRANSACTION_RETENTION_HOURS, String.valueOf(success));
				break;
			default:
				configItems.put(PROPS_NAME_ITEMS_RETENTION_DAY, String.valueOf(success));
				configItems.put(PROPS_NAME_VALD_DOCS_RETENTION_DAY, String.valueOf(success));
				configItems.put(SUCCESS_TRANSACTION_RETENTION_HOURS, String.valueOf(success));
				break;
		}
		items.add(new ConfigItemDTO.ConfigDataItemDTO("GARBAGE", configItems));

		ConfigItemDTO configItemDTO = new ConfigItemDTO();
		configItemDTO.setConfigurationItems(items);
		configItemDTO.setSize(items.size());

		if (status.is4xxClientError()) {
			doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST)).when(restTemplate).exchange(
					anyString(),
					eq(HttpMethod.GET),
					any(HttpEntity.class),
					eq(ConfigItemDTO.class));
		} else if (status.is5xxServerError()) {
			doThrow(new ResourceAccessException("")).when(restTemplate).exchange(
					anyString(),
					eq(HttpMethod.GET),
					any(HttpEntity.class),
					eq(ConfigItemDTO.class));
		} else {
			doReturn(new ResponseEntity<>(configItemDTO, HttpStatus.OK)).when(restTemplate).exchange(
					anyString(),
					eq(HttpMethod.GET),
					any(HttpEntity.class),
					eq(ConfigItemDTO.class)
			);
		}
	}

	@Test
	@DisplayName("Error test if config items client not available")
	void errorTest() {
		final int size = 500;
		mockConfigurationItems(getHoursAfterInsertion() + 1, 0, HttpStatus.BAD_REQUEST, RetentionCase.SUCCESS);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, true, getHoursAfterInsertion());

		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(!CollectionUtils.isEmpty(transactions), "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(!CollectionUtils.isEmpty(data), "Data should be inserted before testing the deletion.");
		assertDoesNotThrow(() -> retentionScheduler.run());

		mockConfigurationItems(getHoursAfterInsertion() + 1, 0, HttpStatus.INTERNAL_SERVER_ERROR, RetentionCase.SUCCESS);
		assertDoesNotThrow(() -> retentionScheduler.run());
	}

	@Test
	@DisplayName("Action only over items that passed threshold, no action on others")
	void multipleItems() {
		final int size = 500;
		mockConfigurationItems(getHoursAfterInsertion() + 1, 0, HttpStatus.OK, RetentionCase.SUCCESS);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, false, getHoursAfterInsertion());
		transactionsPreparationItems(size, false, getHoursAfterInsertion() + 2); // Oldest, ones to be deleted
		
		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(transactions.size() == size*2, "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(data.size() == size*2, "Data should be inserted before testing the deletion.");
		retentionScheduler.run();

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertEquals(size, transactions.size(), "Only half of items should have been deleted");
	}

	@Test
	@DisplayName("Action on items items in success or in error with different time")
	void multipleItemStates() {
		final int size = 500;
		mockConfigurationItems(getHoursAfterInsertion() + 1, getHoursAfterInsertion(), HttpStatus.OK, RetentionCase.SUCCESS);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, true, getHoursAfterInsertion() + 2);
		transactionsPreparationItems(size, false, getHoursAfterInsertion() + 1);
		
		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(transactions.size() == size*2, "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(data.size() == size*2, "Data should be inserted before testing the deletion.");
		retentionScheduler.run();

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertEquals(size, transactions.size(), "Only items in BLOCKING state should have been deleted");
	}

	void transactionsPreparationItems(final int size, final boolean isSuccessful, final int hoursAfterInsertion) {
		
		Date oldDate = DateUtility.getDateCondition(hoursAfterInsertion);

		List<IniEdsInvocationETY> data = new ArrayList<>();
		List<TransactionEventsETY> dataList = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			TransactionEventsETY transaction = new TransactionEventsETY();
			final String id = UUID.randomUUID().toString();

			IniEdsInvocationETY item = new IniEdsInvocationETY();
			item.setWorkflowInstanceId(id);
			data.add(item);

			transaction.setWorkflowInstanceId(id);
			transaction.setEventType(Constants.SEND_TO_INI);
			transaction.setEventDate(oldDate);
			transaction.setExpiringDate(oldDate);

			if (isSuccessful) {
				transaction.setEventStatus(SUCCESS_TRANSACTION_RETENTION_HOURS);
			} else {
				transaction.setEventStatus("BLOCKING_ERROR");
			}
			dataList.add(transaction);
		}

		transactionTemplate.insertAll(dataList);
		dataTemplate.insertAll(data);
	}

	void cfgItemsPreparation(final int size, final int daysAfterInsertion) {
		Date oldDate = Date.from(LocalDateTime.of(LocalDate.now().minusDays(daysAfterInsertion+1), LocalTime.MIN).toInstant(ZoneOffset.UTC));

		List<Document> schemas = new ArrayList<>();
		List<Document> schematron = new ArrayList<>();
		List<Document> terminologies = new ArrayList<>();
		List<Document> dictionaries = new ArrayList<>();
		List<Document> transforms = new ArrayList<>();

		for (int i = 0; i < size; i++) {
			Document schemaETY = new Document();
			schemaETY.put("_id", new ObjectId());
			schemaETY.put("last_update_date", oldDate);
			schemaETY.put("deleted", true);

			Document schematronETY = new Document();
			schematronETY.put("_id", new ObjectId());
			schematronETY.put("last_update_date", oldDate);
			schematronETY.put("deleted", true);

			Document terminologyETY = new Document();
			terminologyETY.put("_id", new ObjectId());
			terminologyETY.put("last_update_date", oldDate);
			terminologyETY.put("deleted", true);

			Document dictionaryETY = new Document();
			dictionaryETY.put("_id", new ObjectId());
			dictionaryETY.put("creation_date", oldDate);
			dictionaryETY.put("deleted", true);

			Document transformETY = new Document();
			transformETY.put("_id", new ObjectId());
			transformETY.put("last_update_date", oldDate);
			transformETY.put("deleted", true);

			schemas.add(schemaETY);
			schematron.add(schematronETY);
			terminologies.add(terminologyETY);
			dictionaries.add(dictionaryETY);
			transforms.add(transformETY);
		}

		rulesTemplate.insert(schemas, rulesTemplate.getCollectionName(SchemaETY.class));
		rulesTemplate.insert(schematron, rulesTemplate.getCollectionName(SchematronETY.class));
		rulesTemplate.insert(terminologies, rulesTemplate.getCollectionName(TerminologyETY.class));
		rulesTemplate.insert(dictionaries, rulesTemplate.getCollectionName(DictionaryETY.class));
		rulesTemplate.insert(transforms, rulesTemplate.getCollectionName(TransformETY.class));
	}

	@Test
	@DisplayName("Action only on items that passed threshold")
	void noDeletion() {
		final int size = 500;
		mockConfigurationItems(getHoursAfterInsertion() + 1, 0, HttpStatus.OK, RetentionCase.SUCCESS);
		when(config.isRemoveEds()).thenReturn(true);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, true, getHoursAfterInsertion());

		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(!CollectionUtils.isEmpty(transactions), "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(!CollectionUtils.isEmpty(data), "Data should be inserted before testing the deletion.");
		retentionScheduler.run();

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertEquals(0, transactions.size(), "No item should have been deleted");
	}

	@Test
	@DisplayName("Action on items items in success or in error with different time")
	void deleteOkState() {
		final int size = 500;
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.OK, RetentionCase.SUCCESS);
		when(config.isRemoveEds()).thenReturn(true);
		given(retentionCFG.getQueryLimit()).willReturn(size);

		transactionsPreparationItems(size, true, getHoursAfterInsertion() + 1);
		transactionsPreparationItems(size, false, getHoursAfterInsertion() + 1);

		List<TransactionEventsETY> transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assumeTrue(transactions.size() == size*2, "Transactions should be inserted before testing the deletion");

		List<IniEdsInvocationETY> data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assumeTrue(data.size() == size*2, "Data should be inserted before testing the deletion.");
		retentionScheduler.run();

		transactions = transactionTemplate.findAll(TransactionEventsETY.class);
		assertEquals(size, transactions.size(), "Only items in SUCCESS state should have been deleted");

		transactions.forEach(transaction -> assertEquals("BLOCKING_ERROR", transaction.getEventStatus(), "All remaining items should have be in error"));

		data = dataTemplate.findAll(IniEdsInvocationETY.class);
		assertEquals(size, data.size(), "Only half of data should have been deleted");
	}

	@ParameterizedTest
	@DisplayName("Deletion of configuration items")
	@ValueSource(ints = {10, 50, 100})
	void runDeleteConfigurationItems(final int size) {
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.OK, RetentionCase.CONFIG_ITEMS);
		cfgItemsPreparation(size, getHoursAfterInsertion());

		List<SchemaETY> schemas = rulesTemplate.findAll(SchemaETY.class);
		List<SchematronETY> schematron = rulesTemplate.findAll(SchematronETY.class);
		List<TerminologyETY> terminologies = rulesTemplate.findAll(TerminologyETY.class);
		List<DictionaryETY> dictionaries = rulesTemplate.findAll(DictionaryETY.class);
		List<TransformETY> transforms = rulesTemplate.findAll(TransformETY.class);

		List<SchemaETY> finalSchemas = schemas;
		List<SchematronETY> finalSchematron = schematron;
		List<TerminologyETY> finalTerminologies = terminologies;
		List<DictionaryETY> finalDictionaries = dictionaries;
		List<TransformETY> finalTransforms = transforms;

		assertAll(
				() -> assertFalse(CollectionUtils.isEmpty(finalSchemas), "schemas should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(finalSchematron), "schematron should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(finalTerminologies), "terminologies should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(finalDictionaries), "dictionaries should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(finalTransforms), "transforms should be inserted before testing the deletion")
		);

		when(config.getConfigItemsRetentionDay()).thenReturn(5);

		cfgItemsRetentionScheduler.run();

		schemas = rulesTemplate.findAll(SchemaETY.class);
		schematron = rulesTemplate.findAll(SchematronETY.class);
		terminologies = rulesTemplate.findAll(TerminologyETY.class);
		dictionaries = rulesTemplate.findAll(DictionaryETY.class);
		transforms = rulesTemplate.findAll(TransformETY.class);

		assertTrue(CollectionUtils.isEmpty(schemas));
		assertTrue(CollectionUtils.isEmpty(schematron));
		assertTrue(CollectionUtils.isEmpty(terminologies));
		assertTrue(CollectionUtils.isEmpty(dictionaries));
		assertTrue(CollectionUtils.isEmpty(transforms));
	}

	@ParameterizedTest
	@DisplayName("Deletion of configuration items error for database exception")
	@ValueSource(ints = {10})
	void cfgItemsDeleteDatabaseError(final int size) {
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.OK, RetentionCase.CONFIG_ITEMS);
		cfgItemsPreparation(size, getHoursAfterInsertion());

		List<SchemaETY> schemas = rulesTemplate.findAll(SchemaETY.class);
		List<SchematronETY> schematron = rulesTemplate.findAll(SchematronETY.class);
		List<TerminologyETY> terminologies = rulesTemplate.findAll(TerminologyETY.class);
		List<DictionaryETY> dictionaries = rulesTemplate.findAll(DictionaryETY.class);
		List<TransformETY> transforms = rulesTemplate.findAll(TransformETY.class);

		assertAll(
				() -> assertFalse(CollectionUtils.isEmpty(schemas), "schemas should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(schematron), "schematron should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(terminologies), "terminologies should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(dictionaries), "dictionaries should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(transforms), "transforms should be inserted before testing the deletion")
		);

		doThrow(MongoException.class).when(rulesTemplate).remove(any(Query.class), eq(SchemaETY.class));
		doThrow(MongoException.class).when(rulesTemplate).remove(any(Query.class), eq(SchematronETY.class));
		doThrow(MongoException.class).when(rulesTemplate).remove(any(Query.class), eq(TerminologyETY.class));
		doThrow(MongoException.class).when(rulesTemplate).remove(any(Query.class), eq(TransformETY.class));
		doThrow(MongoException.class).when(rulesTemplate).remove(any(Query.class), eq(DictionaryETY.class));

		assertDoesNotThrow(() -> cfgItemsRetentionScheduler.run());

		assertAll(
				() -> assertFalse(CollectionUtils.isEmpty(schemas), "schemas should be still full because database failed"),
				() -> assertFalse(CollectionUtils.isEmpty(schematron), "schematron should be still full because database failed"),
				() -> assertFalse(CollectionUtils.isEmpty(terminologies), "terminologies should be still full because database failed"),
				() -> assertFalse(CollectionUtils.isEmpty(dictionaries), "dictionaries should be still full because database failed"),
				() -> assertFalse(CollectionUtils.isEmpty(transforms), "transforms should be still full because database failed")
		);
	}

	@Test
	@DisplayName("Error test if config items client throws an HttpClientError")
	void errorConfigItemsClient() {
		int size = 10;
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.BAD_REQUEST, RetentionCase.CONFIG_ITEMS);
		cfgItemsPreparation(size, getHoursAfterInsertion());

		List<SchemaETY> schemas = rulesTemplate.findAll(SchemaETY.class);
		List<SchematronETY> schematron = rulesTemplate.findAll(SchematronETY.class);
		List<TerminologyETY> terminologies = rulesTemplate.findAll(TerminologyETY.class);
		List<DictionaryETY> dictionaries = rulesTemplate.findAll(DictionaryETY.class);
		List<TransformETY> transforms = rulesTemplate.findAll(TransformETY.class);

		assertAll(
				() -> assertFalse(CollectionUtils.isEmpty(schemas), "schemas should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(schematron), "schematron should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(terminologies), "terminologies should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(dictionaries), "dictionaries should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(transforms), "transforms should be inserted before testing the deletion")
		);

		assertDoesNotThrow(() -> cfgItemsRetentionScheduler.run());
	}

	@Test
	@DisplayName("Error test if config items client throws a Generic Exception")
	void errorConfigItemsGenericException() {
		int size = 10;
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.INTERNAL_SERVER_ERROR, RetentionCase.CONFIG_ITEMS);
		cfgItemsPreparation(size, getHoursAfterInsertion());

		List<SchemaETY> schemas = rulesTemplate.findAll(SchemaETY.class);
		List<SchematronETY> schematron = rulesTemplate.findAll(SchematronETY.class);
		List<TerminologyETY> terminologies = rulesTemplate.findAll(TerminologyETY.class);
		List<DictionaryETY> dictionaries = rulesTemplate.findAll(DictionaryETY.class);
		List<TransformETY> transforms = rulesTemplate.findAll(TransformETY.class);

		assertAll(
				() -> assertFalse(CollectionUtils.isEmpty(schemas), "schemas should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(schematron), "schematron should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(terminologies), "terminologies should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(dictionaries), "dictionaries should be inserted before testing the deletion"),
				() -> assertFalse(CollectionUtils.isEmpty(transforms), "transforms should be inserted before testing the deletion")
		);

		assertDoesNotThrow(() -> cfgItemsRetentionScheduler.run());
	}

	@ParameterizedTest
	@DisplayName("Deletion of validated documents")
	@ValueSource(ints = {10, 50, 100})
	void runDeleteValidatedDocuments(final int size) {
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.OK, RetentionCase.VAL_DOCS);
		valdocPreparation(size, getHoursAfterInsertion());

		List<ValidatedDocumentsETY> validatedDocuments = valdocTemplate.findAll(ValidatedDocumentsETY.class);
		List<ValidatedDocumentsETY> finalValDocs = validatedDocuments;

		assertFalse(CollectionUtils.isEmpty(finalValDocs), "valdocs should be inserted before testing the deletion");
		when(config.getValidatedDocRetentionDay()).thenReturn(5);
		validatedDocumentRetentionScheduler.run();
		validatedDocuments = valdocTemplate.findAll(ValidatedDocumentsETY.class);
		assertTrue(CollectionUtils.isEmpty(validatedDocuments));
	}

	@ParameterizedTest
	@DisplayName("Deletion of validated documents")
	@ValueSource(ints = {10})
	void valdocDeleteDatabaseError(final int size) {
		given(retentionCFG.getQueryLimit()).willReturn(size);
		mockConfigurationItems(getHoursAfterInsertion(), getHoursAfterInsertion()* 2, HttpStatus.OK, RetentionCase.VAL_DOCS);
		valdocPreparation(size, getHoursAfterInsertion());

		List<ValidatedDocumentsETY> validatedDocuments = valdocTemplate.findAll(ValidatedDocumentsETY.class);
		List<ValidatedDocumentsETY> finalValDocs = validatedDocuments;

		assertFalse(CollectionUtils.isEmpty(finalValDocs), "valdocs should be inserted before testing the deletion");

		doThrow(MongoException.class).when(valdocTemplate).findAllAndRemove(any(Query.class), eq(ValidatedDocumentsETY.class));

		assertDoesNotThrow(() -> validatedDocumentRetentionScheduler.run());

		validatedDocuments = valdocTemplate.findAll(ValidatedDocumentsETY.class);
		assertFalse(CollectionUtils.isEmpty(validatedDocuments));
	}

	private void valdocPreparation(int size, int daysAfterInsertion) {
		Date oldDate = Date.from(LocalDateTime.of(LocalDate.now().minusDays(daysAfterInsertion+1), LocalTime.MIN).toInstant(ZoneOffset.UTC));

		List<ValidatedDocumentsETY> validatedDocuments = new ArrayList<>();

		for (int i = 0; i < size; i++) {
			ValidatedDocumentsETY validatedDocumentsETY = new ValidatedDocumentsETY();
			validatedDocumentsETY.setId(new ObjectId().toString());
			validatedDocumentsETY.setWorkflowInstanceId(UUID.randomUUID().toString());
			validatedDocumentsETY.setInsertionDate(oldDate);
			validatedDocuments.add(validatedDocumentsETY);
		}

		valdocTemplate.insertAll(validatedDocuments);
	}
}
