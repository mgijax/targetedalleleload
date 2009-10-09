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

public class AlleleLookupByProjectId extends FullCachedLookup
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
    public AlleleLookupByProjectId(Integer logicalDb)
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
    public HashMap lookup(String projectID)
    throws DBException, CacheException
    {
        return (HashMap)super.lookupNullsOk(projectID);
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param projectID the project ID
     * @return the associated set of ES cell line names
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public HashMap lookupExisting(String projectID)
    throws DBException, CacheException, KeyNotFoundException
    {
        return (HashMap)super.lookup(projectID);
    }

    /**
     * get the query for fully initializing the cache
     * ES cell lines by production center project ID
     * @return the initialization query
     */
    public String getFullInitQuery()
    {
        return "SELECT acc.accID 'projectid', acv._allele_key 'allelekey', " +
        "acv.symbol 'symbol', acv.cellLine 'mutantCellLine', " +
        "parentCellLine_key, parentCellLine " +
        "FROM ACC_Accession acc,  ALL_Allele_CellLine_View acv " +
        "WHERE acc._logicaldb_key = " + this.logicalDb.toString() + " " +
        "AND acc._object_key = acv._Allele_key " +
        "ORDER BY projectid";
    }

    /**
     * add a new map to the cache
     * @assumes nothing
     * @effects the value identified by 'projectId' will be added or replaced 
     * @param projectId the project ID
     * @param alleleMap map of all allele symbol to details for this project
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String projectId, HashMap alleleMap)
    throws DBException, CacheException
    {
        // Replace the current value if it exists
        super.cache.put(projectId.toLowerCase(), alleleMap);
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
                return row.getString("projectid");
            }

            public Object interpretRows(Vector v)
            {
                RowData rd = (RowData)v.get(0);
                String projectId = rd.projectId;
                HashMap alleles = new HashMap();
                for (Iterator it = v.iterator(); it.hasNext();)
                {
                    rd = (RowData)it.next();
                    
                    // Did we already add this allele to the set?
                    HashMap currentAllele = (HashMap)alleles.get(rd.symbol);
                    
                    if (currentAllele == null)
                    {
                        // Create the allele with all the data from this row
                        HashMap allele = new HashMap();
                        allele.put("projectid", rd.projectId);
                        allele.put("key", rd.key);
                        allele.put("symbol", rd.symbol);
                        Vector mcls = new Vector();
                        mcls.add(rd.mutantCellLine);
                        allele.put("mutantCellLines", mcls);
                        allele.put("parentCellLine", rd.parentCellLine);

                        // add the new allele to the map
                        alleles.put(rd.symbol, allele);
                    } else {
                        // grab the existing allele, and add the MCL to it
                        Vector mcls = (Vector)currentAllele.get("mutantCellLines");
                        mcls.add(rd.mutantCellLine);
                        currentAllele.put("mutantCellLines", mcls);

                        // replace the new allele, with the new updated one
                        alleles.put(rd.symbol, currentAllele);
                    }
                }
                return new KeyValue(projectId, alleles);
            }
        }

        return new Interpreter();
    }

    /**
     * Simple data object representing a row of data from the query
     */
    class RowData
    {
        protected String projectId;
        protected Integer key;
        protected String symbol;
        protected String mutantCellLine;
        protected String parentCellLine;
        public RowData (RowReference row) throws DBException
        {
            projectId = row.getString("projectid");
            symbol = row.getString("symbol");
            key = row.getInt("allelekey");
            parentCellLine = row.getString("parentCellLine");
            mutantCellLine = row.getString("mutantCellLine");
        }
    }
}

