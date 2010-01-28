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
 * is a FullCachedLookup storing projectIDs to marker symbol
 * @has internal cache of hashset objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class ProjectLookupByMarker
extends FullCachedLookup
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
    public ProjectLookupByMarker(Integer logicalDb) 
	throws ConfigException, DBException, CacheException
	{
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        this.logicalDb = logicalDb;
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param projectID the project ID
     * @return the associated set of ES cell line names
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public HashSet lookup(String projectID) 
	throws DBException, CacheException
	{
        return (HashSet)super.lookupNullsOk(projectID);
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param projectID the project ID
     * @return the associated set of ES cell line names
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public HashSet lookupExisting(String projectID) 
	throws DBException, CacheException, KeyNotFoundException
	{
        return (HashSet)super.lookup(projectID);
    }

    /**
     * get the query for fully initializing the cache
     * ES cell lines by production center project ID
     * @return the initialization query
     */
    public String getFullInitQuery()
    {
        return "SELECT distinct m.symbol 'symbol', a1.accID 'projectid' " +
            "FROM acc_accession a1, all_allele allele, MRK_marker m " +
            "WHERE a1._logicaldb_key = " + this.logicalDb.toString() + " " + 
            "AND a1._object_key = allele._allele_key " +
            "AND allele._marker_key = m._marker_key " +
            "ORDER BY symbol";
    }

    /**
     * add a new map to the cache
     * @assumes nothing
     * @effects the value identified by key 'symbol' will be added or replaced 
     * @param symbol marker symbol that the projects are associated with
     * @param projectSet project IDs associated to this marker
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String symbol, HashSet projectSet)
    throws DBException, CacheException
    {
        // Replace the current value if it exists
        super.cache.put(symbol, projectSet);
    }

    /**
     * return the RowDataInterpreter for creating KeyValue objects from the query results
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
                    return row.getString("symbol");
                }

                public Object interpretRows(Vector v)
                {
                    RowData rd = (RowData)v.get(0);
                    String symbol = rd.symbol;
                    HashSet projectSet = new HashSet();
                    for (Iterator it = v.iterator(); it.hasNext();)
                    {
                        rd = (RowData)it.next();
                        projectSet.add(rd.projectid);
                    }
                    return new KeyValue(symbol, projectSet);
                }
            }
            
        return new Interpreter();
    }

    /**
     * Simple data object representing a row of data from the query
     */
    class RowData
    {
        protected String symbol;
        protected String projectid;

        public RowData (RowReference row)
        throws DBException
        {
            symbol = row.getString("symbol");
            projectid = row.getString("projectid");
        }
    }

}

