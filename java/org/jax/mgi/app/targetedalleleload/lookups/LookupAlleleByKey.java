package org.jax.mgi.app.targetedalleleload.lookups;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jax.mgi.app.targetedalleleload.KnockoutAllele;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dla.log.DLALogger;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * is a FullCachedLookup storing knockout allele mutant ES cell line name
 * associations to knockout allele objects
 * 
 * @has internal cache of knockout alleles
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 * HISTORY
 *  6/1/2015 - removed commented out original class which had been replaced by below
 */

public class LookupAlleleByKey 
{

        private static LookupAlleleByKey _instance;
        private static DLALogger logger;
        private Map cache = new HashMap();


        // No args get instance instantiates it's own 
        // lookupAlleleByCellline if needed
        public static LookupAlleleByKey getInstance() 
        throws MGIException 
        {
        	logger = DLALogger.getInstance();
        	if (_instance == null) {
            	LookupAlleleByCellLine lookupAlleleByCellline = 
            		LookupAlleleByCellLine.getInstance();
        		_instance = new LookupAlleleByKey(lookupAlleleByCellline);
        	}
        	return _instance;
        }


        /**
         * constructor
         * 
         * @throws ConfigException
         * thrown if there is an error accessing the configuration
         * @throws DBException
         * thrown if there is an error accessing the database
         * @throws CacheException
         * thrown if there is an error accessing the cache
         */
        public LookupAlleleByKey(
        	LookupAlleleByCellLine lookupAlleleByCellline) 
        throws MGIException 
        {
    		int numAlleles = 0;

    		Set celllines = lookupAlleleByCellline.getKeySet();
        	Iterator it = celllines.iterator();
        	String cellline;
    		KnockoutAllele ka;

    		while (it.hasNext())
        	{
        		cellline = (String)it.next();
        		ka = lookupAlleleByCellline.lookup(cellline);
        		Integer alleleKey = ka.getKey();
        		cache.put(alleleKey, ka);
        		numAlleles++;
        	}
    		
    		// Guess what.... the orphaned alleles are missing
    		// from that set!
    		LookupOrphanedAlleleByKey lookupOrphanedAlleleByKey = 
    			LookupOrphanedAlleleByKey.getInstance();

    		Set keys = lookupOrphanedAlleleByKey.getKeySet();
    		it = keys.iterator();
    		Integer key;

     		while (it.hasNext())
        	{
     			key = (Integer)it.next();
        		ka = lookupOrphanedAlleleByKey.lookup(key);
        		cache.put(key, ka);
        		numAlleles++;
        	}

     		String m = "Size of alleleByKey cache: ";
    		m += new Integer(numAlleles);
    		logger.logdInfo(m , true);

        }

        /**
         * look up an associated KnockoutAllele by a given name
         * 
         * @param alleleKey
         * the KnockoutAllele key
         * @return the associated KnockoutAllele
         * @throws DBException
         * thrown if there is an error accessing the database
         * @throws CacheException
         * thrown if there is an error accessing the configuration
         */
        public KnockoutAllele lookup(Integer alleleKey) 
        throws DBException,CacheException 
        {
                return (KnockoutAllele) cache.get(alleleKey);
        }

        /**
         * add a new allele to the cache
         * 
         * @assumes nothing
         * @effects the value identified by 'projectId' will be added or 
         * replaced
         * @param alleleKey
         * the key of an existing allele
         * @param koAllele
         * a knockout allele
         * @throws DBException
         * thrown if there is an error with the database
         * @throws CacheException
         * thrown if there is an error with the cache
         */
        public void addToCache(Integer alleleKey, KnockoutAllele koAllele)
        throws DBException, CacheException 
        {
                // Replace the current value if it exists
                cache.put(alleleKey, koAllele);
        }


}

