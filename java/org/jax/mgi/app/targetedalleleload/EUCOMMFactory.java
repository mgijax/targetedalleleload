package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dla.log.DLALoggingException;

public class EUCOMMFactory extends KnockoutAlleleFactory
{

    public KnockoutAlleleInterpreter getInterpreter()
    {
        return new EUCOMMInterpreter();
    }

    public KnockoutAlleleProcessor getProcessor()
    throws ConfigException,DLALoggingException,
    DBException,CacheException,TranslationException
    {
        return new EUCOMMProcessor();
    }

}
