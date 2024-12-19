
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
package it.finanze.sanita.fse2.ms.gtw.garbage.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants;
import it.finanze.sanita.fse2.ms.gtw.garbage.utility.ProfileUtility;

@Configuration
public class CollectionNaming {

	@Autowired
	private ProfileUtility profileUtility;

	@Bean("transactionEventsBean")
	public String getTransactionDataCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.TRANSACTION_DATA;
		}
		return Constants.Collections.TRANSACTION_DATA;
	}

	@Bean("iniEdsInvocationBean")
	public String getIniEdsInvocationCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.INI_EDS_INVOCATION;
		}
		return Constants.Collections.INI_EDS_INVOCATION;
	}

	@Bean("validatedDocumentsBean")
	public String getValidatedDocuments() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.VALIDATED_DOCUMENTS;
		}
		return Constants.Collections.VALIDATED_DOCUMENTS;
	}

	@Bean("schemaBean")
	public String getSchema() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.SCHEMA;	
		}
		return Constants.Collections.SCHEMA;
		
	}

	@Bean("schematronBean")
	public String getSchematron() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.SCHEMATRON;	
		}
		return Constants.Collections.SCHEMATRON;
	}

	@Bean("transformBean")
    public String getTransformCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.FHIR_TRANSFORM;	
		}
		return Constants.Collections.FHIR_TRANSFORM;
    }
	
	@Bean("terminologyBean")
    public String getTerminologyCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.TERMINOLOGY;	
		}
		return Constants.Collections.TERMINOLOGY;
    }
	
	@Bean("dictionaryBean")
    public String getDictionaryCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.Collections.DICTIONARY;	
		}
		return Constants.Collections.DICTIONARY;
    }
	
	
}
