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

public class DerivationLookupByVectorCreatorParent extends FullCachedLookup
{

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public DerivationLookupByVectorCreatorParent()
    throws ConfigException, DBException, CacheException
    {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param identifier the identifier
     * @return the associated set of ES cell line names
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public Integer lookup(String identifier)
    throws DBException, CacheException
    {
        return (Integer)super.lookupNullsOk(identifier);
    }

    /**
     * look up associated ES cell lines by production center project ID
     * @param identifier the identifier
     * @return the associated set of ES cell line names
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public Integer lookupExisting(String identifier)
    throws DBException, CacheException, KeyNotFoundException
    {
        return (Integer)super.lookup(identifier);
    }

    /**
     * get the query for fully initializing the cache
     * ES cell lines by production center project ID
     * @return the initialization query
     */
    public String getFullInitQuery()
    {
        return "SELECT _Derivation_key, _Vector_key 'vectorkey', " +
        "_ParentCellLine_key 'parentalkey', _Creator_key 'creatorkey' " +
        "FROM ALL_CellLine_Derivation ";
    }


    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter()
    {
        return new Interpreter();
    }

    private class Interpreter implements RowDataInterpreter
    {
        public Interpreter() {}
        public Object interpret(RowReference row)
        throws DBException
        {
            String vectorkey = row.getString("vectorkey");
            String parentkey = row.getString("parentalkey");
            String creatorkey = row.getString("creatorkey");
            String lookupkey = vectorkey + "|" + creatorkey + "|" + parentkey;

            return new KeyValue(lookupkey, row.getInt("_Derivation_key"));
        }
    }

}

