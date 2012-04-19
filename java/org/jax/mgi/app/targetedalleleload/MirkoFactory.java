package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.exception.MGIException;

public class MirkoFactory extends KnockoutAlleleFactory {

	public KnockoutAlleleInterpreter getInterpreter() throws MGIException {
		return new SangerInterpreter();
	}

	public KnockoutAlleleProcessor getProcessor() throws MGIException {
		return new MirkoProcessor();
	}
}
