package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.exception.MGIException;

public class NorcommFactory extends KnockoutAlleleFactory {

	public KnockoutAlleleInterpreter getInterpreter() throws MGIException {
		return new NorcommInterpreter();

	}

	public KnockoutAlleleProcessor getProcessor() throws MGIException {
		return new NorcommProcessor();
	}

}
