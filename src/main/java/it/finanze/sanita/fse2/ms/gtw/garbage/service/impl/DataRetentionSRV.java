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
package it.finanze.sanita.fse2.ms.gtw.garbage.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import it.finanze.sanita.fse2.ms.gtw.garbage.service.IConfigSRV;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants;
import it.finanze.sanita.fse2.ms.gtw.garbage.exceptions.BusinessException;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.IIniEdsInvocationRepo;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.ITransactionsRepo;
import it.finanze.sanita.fse2.ms.gtw.garbage.repository.entity.TransactionEventsETY;
import it.finanze.sanita.fse2.ms.gtw.garbage.service.IDataRetentionSRV;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Service
public class DataRetentionSRV implements IDataRetentionSRV {


	@Autowired
	private IIniEdsInvocationRepo dataRepo;

	@Autowired
	private ITransactionsRepo transactionsRepo;

	@Autowired
	private IConfigSRV configSRV;

	@Override
	public void deleteTransactionData() {

		try {
			String finalStatus = configSRV.isRemoveEds() ? Constants.SEND_TO_INI : Constants.UAR_FINAL_STATUS;
			List<TransactionEventsETY> expiringTransactions = transactionsRepo.findExpiringTransactionData(finalStatus);
			Set<String> workflowIds = new LinkedHashSet<>(dataRepo.findWorkflowInstanceIdsMarkedForDeletion());
			if (expiringTransactions != null) {
				expiringTransactions.stream().map(TransactionEventsETY::getWorkflowInstanceId).forEach(workflowIds::add);
			}

			if (!workflowIds.isEmpty()) {
				List<String> wiiToDelete = new ArrayList<>(workflowIds);
				// Persist the work before deleting transactions so interrupted runs can be retried.
				dataRepo.markIdsForDeletion(wiiToDelete);
				Integer deletedTransaction = transactionsRepo.deleteExpiringTransactionData(wiiToDelete);
				log.info("DELETED transaction_data: {}", deletedTransaction);
			}
			Integer deletedIniEds = dataRepo.deleteMarked();
			log.info("DELETED ini_eds_invocation: {}", deletedIniEds);
		} catch (Exception e) {
			log.error("Errore durante esecuzione Engine Data Retention per il contenuto di 'transaction_data': ", e);
			throw new BusinessException("Errore durante esecuzione Engine Data Retention per il contenuto di 'transaction_data': ", e);
		}

	}
}
