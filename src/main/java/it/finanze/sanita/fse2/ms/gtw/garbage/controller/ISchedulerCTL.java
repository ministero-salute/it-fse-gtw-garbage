package it.finanze.sanita.fse2.ms.gtw.garbage.controller;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.finanze.sanita.fse2.ms.gtw.garbage.dto.DeletedSchedulerResDTO;

public interface ISchedulerCTL extends Serializable {

	@PostMapping("/run-scheduler")
	@Operation(summary = "Run scheduler", description = "Run scheduler.")
	@ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = DeletedSchedulerResDTO.class)))
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Garbage startato", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = void.class))),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)) })
	DeletedSchedulerResDTO runScheduler(HttpServletRequest request);

}
