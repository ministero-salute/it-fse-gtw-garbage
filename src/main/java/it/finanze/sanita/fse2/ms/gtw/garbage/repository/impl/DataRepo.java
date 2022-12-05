/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package it.finanze.sanita.fse2.ms.gtw.garbage.repository.impl;

import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.IDataRepo;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.IniEdsInvocationETY;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Repository
public class DataRepo implements IDataRepo {


	@Autowired
	@Qualifier("mongo-template-data")
	private MongoTemplate mongoTemplate;


	@Override
	public int deleteIds(final List<String> workflowInstanceId) {
		Long output = null;

		try { 
			Document query = new Document();
		  
			query.append("workflow_instance_id", new Document("$in", workflowInstanceId));
			output = mongoTemplate.getCollection(mongoTemplate.getCollectionName(IniEdsInvocationETY.class)).deleteMany(query).getDeletedCount();
			
		} catch (Exception e) {
			log.error("Errore nel tentativo di eliminare la lista di ids nella collection 'ini_eds_invocation': " , e);
			throw new BusinessException("Errore nel tentativo di eliminare la lista di ids nella collection 'ini_eds_invocation': " , e);
		}
		
		return output.intValue();
	}

}
