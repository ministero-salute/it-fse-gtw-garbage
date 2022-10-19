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
			return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.TRANSACTION_DATA;
		}
		return Constants.ComponentScan.Collections.TRANSACTION_DATA;
	}

	@Bean("iniEdsInvocationBean")
	public String getIniEdsInvocationCollection() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.INI_EDS_INVOCATION;
		}
		return Constants.ComponentScan.Collections.INI_EDS_INVOCATION;
	}

	@Bean("validatedDocumentsBean")
	public String getValidatedDocuments() {
		if (profileUtility.isTestProfile()) {
			return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.VALIDATED_DOCUMENTS;
		}
		return Constants.ComponentScan.Collections.VALIDATED_DOCUMENTS;
	}

	@Bean("schemaBean")
	public String getSchema() {
		return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.SCHEMA;
	}

	@Bean("schematronBean")
	public String getSchematron() {
		return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.SCHEMATRON;
	}

	@Bean("structuresBean")
    public String getStructuresCollection() {
        return Constants.Profile.TEST_PREFIX + Constants.ComponentScan.Collections.FHIR_TRANSFORM;
    }
}
