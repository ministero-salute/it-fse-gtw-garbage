
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
package it.finanze.sanita.fse2.ms.gtw.garbage.scheduler;

import it.finanze.sanita.fse2.ms.gtw.garbage.service.IConfigSRV;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.finanze.sanita.fse2.ms.gtw.garbage.service.ICfgItemsRetentionRepoSRV;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Slf4j
@Component
public class CFGItemsRetentionScheduler {

	@Autowired
	private ICfgItemsRetentionRepoSRV retentionSRV;

	@Autowired
	private IConfigSRV configSRV;

	@Scheduled(cron = "${scheduler.rules-retention}")
	@SchedulerLock(name = "invokeRulesRetentionScheduler", lockAtMostFor = "60m")
	public void action() {
		run();
	}

	public void run() {
		log.debug("Rules Retention Scheduler - Retention Scheduler starting");
		try {
			// Eliminazione Transactions in base alle configurazioni recuperate.
			retentionSRV.deleteCFGItems(configSRV.getConfigItemsRetentionDay());
		} catch (Exception e) {
			log.warn("Rules Retention Scheduler - Error while executing data retention", e);
		}
		log.debug("Rules Retention Scheduler - Retention Scheduler finished");
	}
}
