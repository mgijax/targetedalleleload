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

    // provide a static cache so that all instances share one cache
    private static HashMap cache = new HashMap();

    // indicator of whether or not the cache has been initialized
    private static boolean hasBeenInitialized = false;


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

        // since cache is static make sure you do not reinit
        if (!hasBeenInitialized)
        {
            initCache(cache);
            hasBeenInitialized = true;
        }

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
        return "SELECT acc.accID 'projectid', " +
            "a._Allele_key 'allelekey', " +
            "c.cellLine 'mutantCellLine', " +
            "aa.symbol 'symbol', " +
            "p._CellLine_key 'parentCellLine_key', " +
            "p.cellLine 'parentCellLine' " +
            "FROM ACC_Accession acc, " +
            "ALL_Allele_CellLine a, " +
            "ALL_CellLine c, " +
            "ALL_Allele aa, " +
            "ALL_CellLine p, " +
            "ALL_CellLine_Derivation d " +
            "WHERE acc._logicaldb_key = "+ logicalDb +" " +
            "AND acc._object_key = a._Allele_key " +
            "AND a._MutantCellLine_key = c._CellLine_key " +
            "AND a._Allele_key = aa._Allele_key " +
            "AND d._ParentCellLine_key = p._CellLine_key " +
            "AND c._Derivation_key = d._Derivation_key " +
            "ORDER BY projectid " ;
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
            mutantCellLine = row.getString("mutantCellLine");

            // do the same transform to the es cell line name that 
            // is done to the input record es cell line name 
            // See CSDAlleleInput.setParentESCellName
            parentCellLine = row.getString("parentCellLine").toUpperCase()
            .replaceAll("\\s+", "")
            .replaceAll("\\(", "")
            .replaceAll("\\)", "")
            .replaceAll("/", "")
            .replaceAll("\\?", "")
            .replaceAll("\\.","");
        }
    }
}

