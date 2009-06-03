package org.jax.mgi.app.targetedalleleload;

import java.util.HashSet;

import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;

import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

/**
 *
 * is a FullCachedLookup storing es cell line name associations to project id
 * @has internal cache of hashset objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class CellLineLookup
extends FullCachedLookup
{

    private String esCellLogicalDb;
    private String projectLogicalDb;

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public CellLineLookup(String esCellLogicalDb, String projectLogicalDb) 
	throws ConfigException, DBException, CacheException
	{
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
        this.esCellLogicalDb = esCellLogicalDb;
        this.projectLogicalDb = projectLogicalDb;
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
        return "SELECT acc.accID 'escell', acc2.accID 'projectid'" +
            " FROM all_allele al, acc_accession acc, acc_accession acc2" +
            " WHERE acc._logicaldb_key = " + this.esCellLogicalDb + 
            " AND acc._MGIType_key = 28" +
            " AND acc._object_key = al._MutantESCellLine_key" +
            " AND acc2._object_key = al._allele_key" +
            " AND acc2._MGIType_key = 11" +
            " AND acc2._logicalDB_key = " + this.projectLogicalDb;
    }

    /**
     * add a new es cell to the cache
     * @assumes nothing
     * @effects the hashset identified by key 'projectid' will gain an entry
     * @param projectid the project the ES Cell is associated with
     * @param escell the name of the cell line
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String projectid, String escell)
    throws DBException, CacheException
    {
        HashSet escells = lookup(projectid.toLowerCase());
        if (escells == null)
        {
            escells = new HashSet();
        }
        escells.add(escell);
        super.cache.put(projectid.toLowerCase(), escells);
    }

    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter()
    {
        return new Interpreter();
    }

    private class Interpreter
    implements RowDataInterpreter
    {
        
        public Object interpret(RowReference row)
        throws DBException
        {
            // Get the existing set of es cells associated to the project id
            // for this row
            HashSet escells = null;
            try
            {
                escells = lookup(row.getString("projectid"));
            }
            catch (CacheException e)
            {
                escells = new HashSet();
            }
            

            // If there is no existing set of es cells, create an empty set
            if (escells == null)
            {
                escells = new HashSet();
            }
            
            // Add the current es cell line to the set of
            // cell lines associated with this project
            escells.add(row.getString("escell"));
            
            return new KeyValue(row.getString("projectid"), escells);
        }
    }

}

