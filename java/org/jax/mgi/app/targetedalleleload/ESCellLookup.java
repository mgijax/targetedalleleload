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
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyNotFoundException;

/**
 *
 * is a FullCachedLookup storing es cell line name associations to database key
 * @has internal cache of es cell objects
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class ESCellLookup extends FullCachedLookup {

	private StrainLookup strainLookup = null;

    /**
     * constructor
     * @throws ConfigException thrown if there is an error accessing the
     * configuration
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * cache
     */
    public ESCellLookup(StrainLookup strainLookup) 
	throws ConfigException, DBException, CacheException
	{
        super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

        this.strainLookup = strainLookup;

    }

    /**
     * look up an associated ESCell by a given name
     * @param name the ESCell name
     * @return the associated ESCell
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public ESCell lookup(String name) 
	throws DBException, CacheException
	{
        return (ESCell)super.lookupNullsOk(name);
    }

    /**
     * look up an associated ESCell by a given name.
     * @param name the ESCell name
     * @return the associated ESCell
     * @throws DBException thrown if there is an error accessing the database
     * @throws CacheException thrown if there is an error accessing the
     * configuration
     */
    public ESCell lookupExisting(String name) 
	throws DBException, CacheException, KeyNotFoundException
	{
        return (ESCell)super.lookup(name);
    }

    /**
     * get the query for fully initializing the cache
     * mouse ESCells by name
     * @return the initialization query
     */
    public String getFullInitQuery() {
        return "SELECT strainKey=a._Strain_key, celllineKey=a._CellLine_Key, " +
                "name=a.cellLine, a.isMutant, a.provider, strainName=s.strain " +
                "FROM ALL_CellLine a, PRB_Strain s " + 
                "WHERE a._Strain_key = s._strain_key";
    }

    /**
     * get the RowDataInterpreter for interpreting initialization query
     * @return the RowDataInterpreter
     */
    public RowDataInterpreter getRowDataInterpreter() {
        return new Interpreter(this.strainLookup);
    }

    private class Interpreter implements RowDataInterpreter {
        private StrainLookup strainLookup = null;
        public Interpreter(StrainLookup strainLookup)
        {
            this.strainLookup = strainLookup;
        }
        public Object interpret(RowReference row)
        throws DBException
        {
            ESCell escell = null;
            try
            {
                escell = new ESCell(row.getInt("celllineKey").intValue(), 
                this.strainLookup.lookup(row.getString("strainName")),
                row.getString("name"),
                row.getString("provider"),
                row.getBoolean("isMutant").booleanValue()
                );
            }
            catch (CacheException e)
            {
                return null;                
            }
            catch (KeyNotFoundException e)
            {
                return null;
            }
            return new KeyValue(row.getString("name"), escell);
        }
    }

}

