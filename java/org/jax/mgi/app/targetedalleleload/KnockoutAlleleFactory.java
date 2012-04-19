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
		if (provider.equals("Wtsi") || 
			provider.equals("Hmgu") || 
			provider.equals("Mbp")
			) {

			// mirKO uses a different processor than other sanger
			// ESC
			if (cfg.getPrimaryJNumber().equals("J:174268")) {
				return new MirkoFactory();
			}
			return new SangerFactory();
		}
		else if (provider.equals("Vlcg")) {
			return new RegeneronFactory();
		}
		else if (provider.equals("Mfgc") || provider.equals("Cmhd")) {
			return new NorcommFactory();
		}

//		String[] jNumber = cfg.getJNumbers();
//
//		// The reference number uniquely identifies which project
//		// produced the alleles and cell lines we are about to load
//		if (jNumber.equals("J:136110")) {
//			// Regeneron
//			return new RegeneronFactory();
//		} else if (jNumber.equals("J:148605")) {
//			// CSD Wtsi
//			return new SangerFactory();
//		} else if (jNumber.equals("J:157064")) {
//			// CSD Ucd
//			return new SangerFactory();
//		} else if (jNumber.equals("J:155845")) {
//			// EUCOMM Wtsi
//			return new SangerFactory();
//		} else if (jNumber.equals("J:157065")) {
//			// EUCOMM Hmgu
//			return new SangerFactory();
//		} else if (jNumber.equals("J:165963")) {
//			// NorCOMM Cmhd
//			return new NorcommFactory();
//		} else if (jNumber.equals("J:165964")) {
//			// NorCOMM Mfgc
//			return new NorcommFactory();
//		}

		throw new MGIException("Provider not found. Check cfg file.");
	}

	public abstract KnockoutAlleleInterpreter getInterpreter()
	throws MGIException;

	public abstract KnockoutAlleleProcessor getProcessor() 
	throws MGIException;
}