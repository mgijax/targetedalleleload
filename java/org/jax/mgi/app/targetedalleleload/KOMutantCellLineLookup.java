package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.MGITypeConstants;
import org.jax.mgi.dbs.mgd.loads.Alo.MutantCellLine;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.InterpretException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dla.log.DLALoggingException;

/**
 * @is a FullCachedLookup for caching MutantCellLine objects
 * by their cell line IDs
 * @has a RowDataCacheStrategy of type FULL_CACHE used for creating the
 * cache and performing the cache lookup
 * @does provides a lookup method to return a MutantCellLine object given a mutant
 * cell line ID
 * @company The Jackson Laboratory
 * @author jmason
 */

public class KOMutantCellLineLookup 
extends FullCachedLookup
{
    // provide a static cache so that all instances share one cache
//    private static HashMap cache = new HashMap();

    // indicator of whether or not the cache has been initialized
//    private static boolean hasBeenInitialized = false;

    // Which provider of cells is restricted by the logicalDB
    private Integer cellLineLogicalDB = null;
  
    /**
    * constructor
    * @throws CacheException thrown if there is an error with the cache
    * @throws DBException thrown if there is an error accessing the db
    * @throws ConfigException thrown if there is an error accessing the
    * configuration file
    */
    public KOMutantCellLineLookup(Integer cellLineLogicalDB)
    throws CacheException, DBException, ConfigException
    {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

        this.cellLineLogicalDB = cellLineLogicalDB;

        // since cache is static make sure you do not reinit
//        if (!hasBeenInitialized) {
            initCache(cache);
//        }
//        hasBeenInitialized = true;

    }

    /**
    * lookup the MutantCellLine given a mutant cell line ID and creator
    * @param cellLineID mclID
    * @return MutantCellLine object for cellLineID
    * @throws CacheException thrown if there is an error accessing the cache
    * @throws ConfigException thrown if there is an error accessing the
    * configuration
    * @throws CacheException thrown if there is an error accessing the cache
    * @throws DBException thrown if there is an error accessing the
    * database
    * @throws ConfigException thrown if there is an error accessing the
    * configuration file
    * @throws KeyNotFoundException thrown if the key is not found
    */
    public MutantCellLine lookup(String cellLineID)
    throws CacheException, DBException, ConfigException, KeyNotFoundException
    {
        return (MutantCellLine)super.lookupNullsOk(cellLineID);
    }

    /**
    * get the full initialization query which is called by the CacheStrategy
    * class when performing cache initialization. This query restricts
    * celllines to the logical databases for KOMP-Regeneron, KOMP-CSD,
    * EUCOMM, and NorCOMM (108,109,137,142)
    * @return the full initialization query
    */
    public String getFullInitQuery() {
        return new String("SELECT a.accID, a._logicalDB_key, ldb.name as ldbName, " +
            "c._CellLine_key, c.cellLine, c._CellLine_Type_key, " +
            "v.term as cellLineType, c._Strain_key, s.strain, " +
            "c._Derivation_key, c.isMutant, c.creation_date, "+
            "c.modification_date, " +
            "c._CreatedBy_key, c._ModifiedBy_key " +
            "FROM ACC_Accession a, ALL_CellLine_View c, "+
            "VOC_Term v, PRB_Strain s, ACC_LogicalDB ldb " +
            "WHERE a._MGIType_key = " + MGITypeConstants.CELLLINE + " " +
            "AND a._LogicalDB_key = ldb._LogicalDB_key " +
            "AND a._Object_key = c._CellLine_key " +
            "AND c.isMutant = 1 " +
            "AND c._CellLine_Type_key = v._Term_key " +
            "AND c._Strain_key = s._Strain_key " +
            "AND c._Derivation_key != null " +
            "AND a._LogicalDB_Key in (108,109,137,142)");
    }
  
    /**
     * add a new entry to the cache
     * @assumes nothing
     * @effects the value identified by key 'accId' will be added or replaced 
     * @param accId the accId
     * @param mcl The mutant cell line to add to the cache
     * @throws DBException thrown if there is an error with the database
     * @throws CacheException thrown if there is an error with the cache
     */
    protected void addToCache(String accId, MutantCellLine mcl)
    throws DBException, CacheException
    {
        // Replace the current value if it exists
        super.cache.put(accId.toLowerCase(), mcl);
    }
  
  
    /**
    * get the RowDataInterpreter which is required by the CacheStrategy to
    * read the results of a database query.
    * @return the partial initialization query
    */
    public RowDataInterpreter getRowDataInterpreter()
    {
        class Interpreter 
        implements RowDataInterpreter
        {
            public Object interpret(RowReference row)
            throws DBException, InterpretException 
            {
                String accID = row.getString(1);
                Integer ldbKey = row.getInt(2);
                MutantCellLine mcl = null;
                try 
                {
                    mcl = new MutantCellLine();
                } 
                catch (DLALoggingException e) 
                {
                    throw new InterpretException (
                        "KOMutantCellLineLookup " + e.getMessage());
                }
                mcl.setAccID(accID);
                mcl.setLdbKey(ldbKey);
                mcl.setLdbName(row.getString(3));
                mcl.setMCLKey(row.getInt(4));
                mcl.setCellLine(row.getString(5));
                mcl.setCellLineTypeKey(row.getInt(6));
                mcl.setCellLineType(row.getString(7));
                mcl.setStrainKey(row.getInt(8));
                mcl.setStrain(row.getString(9));
                mcl.setDerivationKey(row.getInt(10));
                mcl.setIsMutant(row.getBoolean(11));
                mcl.setCreationDate(row.getTimestamp(12));
                mcl.setModificationDate(row.getTimestamp(13));
                mcl.setCreatedByKey(row.getInt(14));
                mcl.setModifiedByKey(row.getInt(15));
                return new KeyValue(row.getString("cellLine").toLowerCase(), mcl);
            }
        }
        return new Interpreter();
    }
}
