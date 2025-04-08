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
package it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity;

import java.util.Date;

import jakarta.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model to save validation events.
 */
@Data
@NoArgsConstructor
@Document(collection = "#{@transactionEventsBean}")
public class TransactionEventsETY {
 
	@Id
	@Size(min = 0, max = 100)
	private String id;
	
	@Field(name = "eventType")
	@Size(min = 0, max = 100)
	private String eventType;

	@Field(name = "eventDate")
	private Date eventDate;  
	
	@Field(name = "eventStatus")
	@Size(min = 0, max = 100)
	private String eventStatus;
	
	@Field(name = "message")
	@Size(min = 0, max = 100)
	private String message;

	@Field(name = "identificativoDocumento")
	@Size(min = 0, max = 100)
	private String identificativoDocumento;

	@Field(name = "subject")
	@Size(min = 0, max = 100)
	private String subject;

	@Field(name = "subjectRole")
	@Size(min = 0, max = 100)
	private String subjectRole;
	
	@Field(name = "tipoAttivita")
	@Size(min = 0, max = 100)
	private String tipoAttivita;
	
	@Field(name = "organizzazione")
	@Size(min = 0, max = 100)
	private String organizzazione;
	
	@Field(name = "workflow_instance_id")
	@Size(min = 0, max = 100)
	private String workflowInstanceId;
	
	@Field(name = "expiring_date")
	private Date expiringDate;  
}
