package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.exception.MGIException;

abstract class KnockoutAlleleFactory {
	public static KnockoutAlleleFactory getFactory() throws MGIException {
		// Get the configuration object
		TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();

		String jNumber = cfg.getJNumber();

		// The reference number uniquely identifies which project
		// produced the alleles and cell lines we are about to load
		if (jNumber.equals("J:136110")) {
			// Regeneron
			return new RegeneronFactory();
		} else if (jNumber.equals("J:148605")) {
			// CSD Wtsi
			return new SangerFactory();
		} else if (jNumber.equals("J:157064")) {
			// CSD Ucd
			return new SangerFactory();
		} else if (jNumber.equals("J:155845")) {
			// EUCOMM Wtsi
			return new SangerFactory();
		} else if (jNumber.equals("J:157065")) {
			// EUCOMM Hmgu
			return new SangerFactory();
		}

		throw new MGIException("Factory not found. Check Jnumber in cfg");
	}

	public abstract KnockoutAlleleInterpreter getInterpreter()
			throws MGIException;

	public abstract KnockoutAlleleProcessor getProcessor() throws MGIException;

}