package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.exception.MGIException;

abstract class KnockoutAlleleFactory 
{

	public static KnockoutAlleleFactory getFactory() 
	throws MGIException 
	{
		// Get the configuration object
		TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();

		String provider = cfg.getProviderLabcode();

		// The providers require different factories
		if (provider.equals("Wtsi") ) {

			// KOMP CSD wtsi - has two Jnums, this one is uniq, 
			// other is the same as EUCOMM wtsi
			if (cfg.getPrimaryJNumber().indexOf("J:148605") > -1) {
				return new KompCsdFactory();
			}
			// EUCOMM wtsi
			return new SangerFactory();
		}
		else if (provider.equals("Mbp")) {
			return new KompCsdFactory();
		}
		else if (provider.equals("Hmgu")) {
			return new SangerFactory();
		}	

		throw new MGIException("Provider not found. Check cfg file.");
	}

	public abstract KnockoutAlleleInterpreter getInterpreter()
	throws MGIException;

	public abstract KnockoutAlleleProcessor getProcessor() 
	throws MGIException;
}
