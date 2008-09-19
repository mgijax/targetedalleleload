package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.dbs.mgd.LogicalDBConstants;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

/**
 *
 * is a FullCachedLookup storing strain name associations to strains
 * @has internal cache of strains
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class StrainLookup extends FullCachedLookup {

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public StrainLookup() throws ConfigException, DBException, CacheException {
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
    }

    /**
     * look up an associated strain by a given name.  The strain MUST exist
     * or the constructed allele would be invalid so lookupNullOk is not
     * allowed.
     * @param name the strain name
     * @return the associated strain
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public Strain lookup(String name)
    throws DBException, CacheException, KeyNotFoundException
    {
        return (Strain)super.lookup(name);
    }

    /**
     * get the query for fully initializing the cache
     * mouse strains by name
     * @return the initialization query
     */
    public String getFullInitQuery() {
        return "select strainKey=_Strain_key, name=strain from PRB_Strain";
    }

    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter() {
        return new Interpreter();
    }

    private class Interpreter implements RowDataInterpreter
    {
        public Object interpret(RowReference row)
        throws DBException
        {
            Strain strain = new Strain(row.getString("name"), 
                row.getInt("strainKey").intValue());
            return new KeyValue(row.getString("name"), strain);
        }
    }

}

