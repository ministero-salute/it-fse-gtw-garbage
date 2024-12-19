
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
package it.finanze.sanita.fse2.ms.gtw.garbage.config.mongo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;

import javax.validation.constraints.NotNull;

@Configuration
public class MongoDatabaseCFG {

	@Autowired
	private MongoPropertiesDataCFG mongoData;

	@Autowired
	private MongoPropertiesTransactionsCFG mongoTransactions;

	@Autowired
	private MongoPropertiesValidatedDocumentCFG mongoFse;

	@Autowired
	private ApplicationContext appContext;

	final List<Converter<?, ?>> conversions = new ArrayList<>();

	@Bean
	@Primary
	@Qualifier("mongo-factory-data")
	public MongoDatabaseFactory mongoDatabaseFactoryData() {
		ConnectionString connectionString = new ConnectionString(mongoData.getUri());
		MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
				.applyConnectionString(connectionString)
				.build();
		return new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoClientSettings), mongoData.getSchemaName());
	}

	@Bean
	@Qualifier("mongo-factory-transaction")
	public MongoDatabaseFactory mongoDatabaseFactoryTransactions() {
		ConnectionString connectionString = new ConnectionString(mongoTransactions.getUri());
		MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
				.applyConnectionString(connectionString)
				.build();
		return new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoClientSettings), mongoData.getSchemaName());
	}

	@Bean
	@Qualifier("mongo-factory-valdoc")
	public MongoDatabaseFactory mongoDatabaseFactoryFse() {
		ConnectionString connectionString = new ConnectionString(mongoFse.getUri());
		MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
				.applyConnectionString(connectionString)
				.build();
		return new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoClientSettings), mongoData.getSchemaName());
	}

	@Bean
	@Qualifier("mongo-factory-rules")
	public MongoDatabaseFactory mongoDatabaseFactoryRules() {
		ConnectionString connectionString = new ConnectionString(mongoFse.getUri());
		MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
				.applyConnectionString(connectionString)
				.build();
		return new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoClientSettings), mongoData.getSchemaName());
	}

	@Bean
	@Primary
	@Qualifier("mongo-template-data")
	public MongoTemplate mongoTemplateData() {
		final MongoDatabaseFactory factory = mongoDatabaseFactoryData();
		return getMongoTemplate(factory);
	}


	@Bean
	@Qualifier("mongo-template-transaction")
	public MongoTemplate mongoTemplateTransactions() {
		final MongoDatabaseFactory factory = mongoDatabaseFactoryTransactions();
		return getMongoTemplate(factory);
	}

	@Bean
	@Qualifier("mongo-template-valdoc")
	public MongoTemplate mongoTemplateFse() {
		final MongoDatabaseFactory factory = mongoDatabaseFactoryFse();
		return getMongoTemplate(factory);
	}

	@Bean
	@Qualifier("mongo-template-rules")
	public MongoTemplate mongoTemplateRules() {
		final MongoDatabaseFactory factory = mongoDatabaseFactoryRules();
		return getMongoTemplate(factory);
	}

	@NotNull
	private MongoTemplate getMongoTemplate(MongoDatabaseFactory factory) {
		final MongoMappingContext mongoMappingContext = new MongoMappingContext();
		mongoMappingContext.setApplicationContext(appContext);

		MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory),
				mongoMappingContext);
		converter.setTypeMapper(new DefaultMongoTypeMapper(null));
		return new MongoTemplate(factory, converter);
	}

	@Bean
	public LockProvider lockProvider(MongoTemplate template) {
		return new MongoLockProvider(template.getDb());
	}

}
