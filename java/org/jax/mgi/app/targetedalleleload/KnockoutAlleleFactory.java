package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;

import org.jax.mgi.shr.ioutils.RecordDataInterpreter;
import org.jax.mgi.shr.exception.MGIException;

abstract class KnockoutAlleleFactory
{
    public static KnockoutAlleleFactory getFactory()
    throws MGIException
    {
        // Get the configuration object
        TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();
        
        String provider = cfg.getProvider();
        if (provider.equals("Velocigene")) 
        {
            // Regeneron
            return new RegeneronFactory();
        }
/*
        else if (provider.equals("CSD"))
        {
            // CSD
            return new CSDFactory();
        }
*/
    return null;
    }
    
    public abstract RecordDataInterpreter getInterpreter();
    public abstract KnockoutAlleleProcessor getProcessor()
    throws MGIException;

} 