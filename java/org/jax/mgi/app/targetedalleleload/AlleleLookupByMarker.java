package org.jax.mgi.app.targetedalleleload;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;

import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;

import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

/**
 *
 * is a FullCachedLookup storing alleles to project id
 * @has internal cache of hashset objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class AlleleLookupByMarker extends FullCachedLookup
{

    private Integer logicalDb;

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public AlleleLookupByMarker(Integer logicalDb)
    throws ConfigException, DBException, CacheException
    {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        this.logicalDb = logicalDb;
    }

    /**
     * look up associated alleles by marker symbol
     * @param symbol the marker symbol
     * @return the associated set of allele symbols
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public HashSet lookup(String symbol)
    throws DBException, CacheException
    {
        return (HashSet)super.lookupNullsOk(symbol);
    }

    /**
     * look up associated alleles by marker symbol
     * @param symbol the marker symbol
     * @return the associated set of allele symbols
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public HashSet lookupExisting(String symbol)
    throws DBException, CacheException, KeyNotFoundException
    {
        return (HashSet)super.lookup(symbol);
    }

    /**
     * get the query for fully initializing the cache
     * @return the initialization query
     */
    public String getFullInitQuery()
    {
        return "SELECT av.symbol 'alleleSymbol', av.markerSymbol 'markerSymbol' " +
        "FROM ACC_Accession acc,  ALL_Allele_View av " +
        "WHERE acc._logicaldb_key = " + this.logicalDb.toString() + " " +
        "AND acc._object_key = av._Allele_key " +
        "ORDER BY markerSymbol";
    }

    /**
     * add a new map to the cache
     * @assumes nothing
     * @effects the value identified by 'symbol' will be added or replaced 
     * @param symbol the marker symbol
     * @param alleles set of all allele symbols
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String symbol, HashSet alleles)
    throws DBException, CacheException
    {
        // Replace the current value if it exists
        super.cache.put(symbol.toLowerCase(), alleles);
    }


    /**
     * return the RowDataInterpreter for creating KeyValue objects from the 
     *        query results
     * @return the RowDataInterpreter for this query
     */
    public RowDataInterpreter getRowDataInterpreter()
    {
        class Interpreter implements MultiRowInterpreter
        {
            public Object interpret(RowReference ref)
            throws DBException
            {
                return new RowData(ref);
            }

            public Object interpretKey(RowReference row)
            throws DBException
            {
                return row.getString("markerSymbol");
            }

            public Object interpretRows(Vector v)
            {
                RowData rd = (RowData)v.get(0);
                String markerSymbol = rd.markerSymbol;
                HashSet alleles = new HashSet();
                for (Iterator it = v.iterator(); it.hasNext();)
                {
                    rd = (RowData)it.next();
                    
                    alleles.add(rd.alleleSymbol);
                }
                return new KeyValue(markerSymbol, alleles);
            }
        }

        return new Interpreter();
    }

    /**
     * Simple data object representing a row of data from the query
     */
    class RowData
    {
        protected String markerSymbol;
        protected String alleleSymbol;
        public RowData (RowReference row) throws DBException
        {
            markerSymbol = row.getString("markerSymbol");
            alleleSymbol = row.getString("alleleSymbol");
        }
    }
}

