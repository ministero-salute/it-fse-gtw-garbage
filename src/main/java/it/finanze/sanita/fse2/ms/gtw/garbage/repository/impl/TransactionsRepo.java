
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
package it.finanze.sanita.fse2.ms.gtw.garbage.repository.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.mongodb.client.result.DeleteResult;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.RetentionCFG;
import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.ITransactionsRepo;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.TransactionEventsETY;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Repository
public class TransactionsRepo implements ITransactionsRepo {

	@Autowired
	@Qualifier("mongo-template-transaction")
	private MongoTemplate mongoTemplate;

    @Autowired
	private RetentionCFG retentionCfg;

	
	@Override
	public List<TransactionEventsETY> findExpiringTransactionData(final String eventType) {
		List<TransactionEventsETY> output = null;
		try {
			Query query = new Query();
			query.fields().include("workflow_instance_id");
			
			Criteria criteria = new Criteria();
			criteria.orOperator(
					Criteria.where("eventType").is(eventType).and("eventStatus").is("SUCCESS"), 
					Criteria.where("eventStatus").is("BLOCKING_ERROR"),
					Criteria.where("eventStatus").is("BLOCKING_ERROR_MAX_RETRY")
					);
			criteria.and("expiring_date").lt(new Date());
			query.addCriteria(criteria);
			query.limit(retentionCfg.getQueryLimit());
			output = mongoTemplate.find(query, TransactionEventsETY.class);
		} catch (Exception ex) {
			log.error("Error while perform find expiring transaction data" , ex);
			throw new BusinessException("Error while perform find expiring transaction data" , ex);
		}

		return output;
	}
	
	@Override
	public Integer deleteExpiringTransactionData(final List<String> wii) {
		Integer output = 0;
		try {
			Query query = new Query();
			query.addCriteria(Criteria.where("workflow_instance_id").in(wii));
			DeleteResult dResult = mongoTemplate.remove(query, TransactionEventsETY.class);
			output = (int)dResult.getDeletedCount();
		} catch (Exception ex) {
			log.error("Error while perform deleteExpiringTransactionData" , ex);
			throw new BusinessException("Error while perform deleteExpiringTransactionData" , ex);
		}

		return output;
	}

}
