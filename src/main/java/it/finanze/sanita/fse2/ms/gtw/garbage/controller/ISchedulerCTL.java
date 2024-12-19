
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
package it.finanze.sanita.fse2.ms.gtw.garbage.controller;

import javax.servlet.http.HttpServletRequest;

import it.finanze.sanita.fse2.ms.gtw.garbage.dto.LogTraceInfoDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

public interface ISchedulerCTL {

	@PostMapping("/run-scheduler")
	@Operation(summary = "Run scheduler", description = "Run scheduler.")
	@ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = LogTraceInfoDTO.class)))
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Garbage startato", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = void.class))),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)) })
	LogTraceInfoDTO runSchedulerDataRetention(HttpServletRequest request);

	@PostMapping("/run-scheduler/validatedDocuments")
	@Operation(summary = "Run scheduler", description = "Run scheduler.")
	@ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = LogTraceInfoDTO.class)))
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Garbage startato", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = void.class))),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)) })
	LogTraceInfoDTO runSchedulerValidatedDocuments(HttpServletRequest request);
	
	@PostMapping("/run-scheduler/cfgItems")
	@Operation(summary = "Run scheduler", description = "Run scheduler.")
	@ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = LogTraceInfoDTO.class)))
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Garbage startato", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = void.class))),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)) })
	LogTraceInfoDTO runSchedulerCfgItems(HttpServletRequest request);
}
