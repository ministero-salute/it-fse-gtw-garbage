
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.mongodb.client.result.DeleteResult;

import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.ICfgItemsRetentionRepo;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.DictionaryETY;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.data.mongodb.core.query.Criteria.*;

@Slf4j
@Repository
public class CfgItemsRetentionRepo implements ICfgItemsRetentionRepo {

	
	@Qualifier("mongo-template-rules")
	@Autowired
	MongoTemplate mongoTemplate;
 
 
	@Override
	public Integer deleteCfgItems(final Date dateToRemove, final Class<?> clazz) {
		Integer cfgItemsDeleted = 0;

		try {
			Query query = new Query();
			query.addCriteria(where("deleted").is(true).and("last_update_date").lt(dateToRemove));

			DeleteResult dRes = mongoTemplate.remove(query, clazz);
			cfgItemsDeleted = (int)dRes.getDeletedCount();
		} catch (Exception ex) {
			log.error("Error while perform delete cfg items" , ex);
			throw new BusinessException("Error while perform delete cfg items" , ex);
		}
		return cfgItemsDeleted;
	}
	
	@Override
	public Integer deleteTerminology(final Date dateToRemove) {
		Integer cfgItemsDeleted = 0;

		try {
			Query query = new Query();
			query.addCriteria(where("deleted").is(true).and("creation_date").lt(dateToRemove));

			DeleteResult dRes = mongoTemplate.remove(query, DictionaryETY.class);
			cfgItemsDeleted = (int)dRes.getDeletedCount();
		} catch (Exception ex) {
			log.error("Error while perform delete terminology" , ex);
			throw new BusinessException("Error while perform delete terminology" , ex);
		}
		return cfgItemsDeleted;
	}

}
