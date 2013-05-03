package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.exception.MGIException;

public class KompCsdFactory extends KnockoutAlleleFactory {

	public KnockoutAlleleInterpreter getInterpreter() throws MGIException {
		return new KompCsdInterpreter();
	}

	public KnockoutAlleleProcessor getProcessor() throws MGIException {
		return new KompCsdProcessor();
	}

}
