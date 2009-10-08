package org.jax.mgi.app.targetedalleleload;

import java.util.HashMap;
import java.util.HashSet;

import org.jax.mgi.shr.ioutils.RecordFormatException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dla.log.DLALoggingException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.dbs.mgd.lookup.TranslationException;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.exception.MGIException;

/**
 * @is An abstract object that knows how to create KOMP Clone objects from 
 * an input file.  Each input file must have a class that overrides this
 * method and provides specific handling for that file.
 * @has
 *   <UL>
 *   <LI> KOMP Clone object.
 *   </UL>
 * @does
 *   <UL>
 *   <LI> Parses a KnockoutAlleleInput record into a KnockoutAllele object
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

abstract class KnockoutAlleleProcessor
{
    public abstract KnockoutAllele process(KnockoutAlleleInput in)
    throws RecordFormatException,ConfigException,KeyNotFoundException,
    DBException,CacheException,TranslationException,MGIException;
    
    public abstract void addToProjectCache(String projectId, HashMap alleleMap)
    throws DBException, CacheException;
    
    public abstract void addToMarkerCache(String symbol, HashSet alleles)
    throws DBException, CacheException;
}