package it.finanze.sanita.fse2.ms.gtw.garbage.controller.impl;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import brave.Tracer;
import it.finanze.sanita.fse2.ms.gtw.garbage.dto.LogTraceInfoDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author vincenzoingenito
 *
 *	Abstract controller.
 */
@Slf4j
public abstract class AbstractCTL implements Serializable {

	/**
	 * Serial version uid.
	 */
	private static final long serialVersionUID = -3077780100650268134L;
	
	@Autowired
	private transient Tracer tracer;
 

	protected LogTraceInfoDTO getLogTraceInfo() {
		LogTraceInfoDTO out = new LogTraceInfoDTO(null, null);
		if (tracer.currentSpan() != null) {
			out = new LogTraceInfoDTO(
					tracer.currentSpan().context().spanIdString(), 
					tracer.currentSpan().context().traceIdString());
		}
		return out;
	}
  
}
