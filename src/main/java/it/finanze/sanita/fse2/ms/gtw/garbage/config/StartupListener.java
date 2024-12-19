
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 *
 */
@Component
public class StartupListener {

	@Value("${retention.transactions-query.limit}")
	private Integer queryLimit;

	@EventListener(ApplicationReadyEvent.class)
	public void runAfterStartup() {
		ensureProperties();
	}

	private void ensureProperties() {
		if (this.queryLimit == null || this.queryLimit == 0) {
			throw new IllegalArgumentException("Number of records limit to use to execute retention must be set");
		}
	}
	
}
