package org.jax.mgi.app.targetedalleleload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.ArrayList;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;

/**
 *
 * is a FullCachedLookup storing alleles to count of mutant cell lines
 * @has internal cache of hashset objects indexed by allele symbol
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class AlleleCellLineCount extends FullCachedLookup
{

    // Provides access to the required configuration variables
    
    // Singleton pattern implementation
    private static AlleleCellLineCount _instance;
    public static AlleleCellLineCount getInstance()
    throws MGIException {
        if (_instance == null) {
            _instance = new AlleleCellLineCount();
        }
        return _instance;
    } 

    /**
     * constructor (singleton guarantees that this is only called the 
     * first time an instance of AlleleCellLineCount is created)
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    private AlleleCellLineCount()
    throws MGIException {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        initCache(cache);
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param symbol the allele symbol
     * @return the count of mutant cell lines associated with the allele
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public Integer lookup(String symbol)
    throws DBException, CacheException {
        return (Integer)lookupNullsOk(symbol);
    }

    /**
     * get the query for fully initializing the cache
     * ES cell lines by production center project ID
     * @return the initialization query
     */
    public String getFullInitQuery() {
        String pipeline = null;
        try {
            TargetedAlleleLoadCfg cfg = new TargetedAlleleLoadCfg();
            pipeline = cfg.getPipeline();            
        } catch (MGIException e) {
            return "";
        }

        return "SELECT a.symbol, COUNT(ac._mutantcellline_key) as cnt " +
        "FROM All_allele a, All_allele_cellline ac " +
        "WHERE a._allele_key *= ac._allele_key " +
        "AND a.symbol LIKE '%(" + pipeline + ")%' " +
        "GROUP BY a.symbol " +
        "ORDER BY COUNT(ac._mutantcellline_key) " ;
    }

    /**
     * returns the set of keys from the cache
     * @assumes nothing
     * @effects nothing
     */ 
    public Set getKeySet() {
        return cache.keySet();
    }

    /**
     * Decrement (or set to 0 if it doesn't exist) the count associated
     * with the the allele symbol
     * @assumes nothing
     * @effects the value identified by symbol will be decremented by 1
     * @param allele the allele symbol
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void decrement(String symbol)
    throws MGIException {
        // Increment the count, or if the symbol didn't exist
        // add it and initialize the count to 1
        Integer i = lookup(symbol.toLowerCase());
        i = (i == null) ? new Integer(0) : new Integer(i.intValue() - 1);
        
        if (i.intValue() < 0) {
            throw new MGIException(this.getClass().getName() + ": Trying to decrement a non-existing allele ("+symbol+")");
        }

        // Replace the current value if it exists
        addToCache(symbol.toLowerCase(), i);
    }

    /**
     * Increment (or set to 1 if it doesn't exist) the count associated
     * with the the allele symbol
     * @assumes nothing
     * @effects the value identified by symbol will be incremented by 1
     * @param allele the allele symbol
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void increment(String symbol)
    throws MGIException {
        // Increment the count, or if the symbol didn't exist
        // add it and initialize the count to 1
        Integer i = lookup(symbol.toLowerCase());
        i = (i == null) ? new Integer(1) : new Integer(i.intValue() + 1);

        // Replace the current value if it exists
        addToCache(symbol.toLowerCase(), i);
    }


    /**
     * add a new map to the cache
     * @assumes nothing
     * @effects the value identified by 'projectId' will be added or replaced 
     * @param allele the allele symbol
     * @param count the count of cell lines associated to the allele
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String symbol, Integer count)
    throws DBException, CacheException {
        // Replace the current value if it exists
        cache.put(symbol.toLowerCase(), count);
    }


    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter() {
        return new Interpreter();
    }

    private class Interpreter implements RowDataInterpreter {
        public Interpreter() {}
        public Object interpret(RowReference row)
        throws DBException {
            return new KeyValue(row.getString("symbol"), row.getInt("cnt"));
        }
    }
}

