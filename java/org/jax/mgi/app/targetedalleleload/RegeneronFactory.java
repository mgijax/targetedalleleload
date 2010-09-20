package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.exception.MGIException;

public class RegeneronFactory extends KnockoutAlleleFactory {

	public KnockoutAlleleInterpreter getInterpreter() throws MGIException {
		return new RegeneronInterpreter();
	}

	public KnockoutAlleleProcessor getProcessor() throws MGIException {
		return new RegeneronProcessor();
	}

}