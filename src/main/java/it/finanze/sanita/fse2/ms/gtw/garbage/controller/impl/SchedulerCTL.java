
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
package it.finanze.sanita.fse2.ms.gtw.garbage.controller.impl;

import it.finanze.sanita.fse2.ms.gtw.garbage.controller.ISchedulerCTL;
import it.finanze.sanita.fse2.ms.gtw.garbage.dto.LogTraceInfoDTO;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.CFGItemsRetentionScheduler;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.DataRetentionScheduler;
import it.finanze.sanita.fse2.ms.gtw.garbage.scheduler.ValidatedDocumentRetentionScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;


@RestController
public class SchedulerCTL extends AbstractCTL implements ISchedulerCTL {

	@Autowired
	private DataRetentionScheduler dataRetentionScheduler;
	
	@Autowired
	private ValidatedDocumentRetentionScheduler validatedDocsScheduler;
	
	@Autowired
	private CFGItemsRetentionScheduler cfgItemsScheduler;
	
	@Override
	public LogTraceInfoDTO runSchedulerDataRetention(HttpServletRequest request) {
		dataRetentionScheduler.action();
		return getLogTraceInfo();
	}
	
	@Override
	public LogTraceInfoDTO runSchedulerValidatedDocuments(HttpServletRequest request) {
		validatedDocsScheduler.action();
		return getLogTraceInfo();
	}
	
	@Override
	public LogTraceInfoDTO runSchedulerCfgItems(HttpServletRequest request) {
		cfgItemsScheduler.action();
		return getLogTraceInfo();
	}

	 
}
