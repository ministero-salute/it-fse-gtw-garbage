
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
package it.finanze.sanita.fse2.ms.gtw.garbage.config;

/**
 *
 */
public final class Constants {
	
	public static final String SEND_TO_INI = "SEND_TO_INI";
	public static final String EDS_WORKFLOW = "EDS_WORKFLOW";

	
	public static final class Collections {

			public static final String TRANSACTION_DATA = "transaction_data";

			public static final String INI_EDS_INVOCATION = "ini_eds_invocation";

			public static final String VALIDATED_DOCUMENTS = "validated_documents";

			public static final String SCHEMA = "schema";

			public static final String SCHEMATRON = "schematron";
			
			public static final String FHIR_TRANSFORM = "transform";
			
			public static final String TERMINOLOGY = "terminology";
			
			public static final String DICTIONARY = "dictionary";

		private Collections() {

		}
	}


	public static final class ConfigItems {

		public static final String SUCCESS_TRANSACTION_RETENTION_HOURS = "SUCCESS";

		private ConfigItems() {
		}
	}

	public static final class Profile {

		public static final String TEST = "test";

		public static final String TEST_PREFIX = "test_";

		public static final String DEV = "dev";

		/**
		 * Constructor.
		 */
		private Profile() {
			// This method is intentionally left blank.
		}

	}

	/**
	 * Constants.
	 */
	private Constants() {

	}

}
