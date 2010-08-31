package org.jax.mgi.app.targetedalleleload;

import java.util.HashMap;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;

/**
 * @is a FullCachedLookup for caching cell line strain keys by their cell line
 *     key
 * @has a RowDataCacheStrategy of type FULL_CACHE used for creating the cache
 *      and performing the cache lookup
 * @does provides a lookup method to return a cell line strain key
 *       (ALL_CellLine._Strain_key) given a cell line key
 * @company The Jackson Laboratory
 * @author sc
 * @version 1.0
 */
public class CellLineStrainKeyLookupByCellLineKey extends FullCachedLookup {
	// provide a static cache so that all instances share one cache
	private static HashMap cache = new HashMap();

	// indicator of whether or not the cache has been initialized
	private static boolean hasBeenInitialized = false;

	/**
	 * constructor
	 * 
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 * @throws DBException
	 *             thrown if there is an error accessing the db
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration file
	 */
	public CellLineStrainKeyLookupByCellLineKey() throws CacheException,
			DBException, ConfigException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
		// since cache is static make sure you do not reinit
		if (!hasBeenInitialized) {
			initCache(cache);
		}
		hasBeenInitialized = true;
	}

	/**
	 * look up the cell line strain key given a cell line database key
	 * 
	 * @param key
	 *            the key to lookup
	 * @return the cell line strain key
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 */
	public Integer lookup(Integer key) throws CacheException, DBException,
			ConfigException {
		return (Integer) super.lookupNullsOk(key);
	}

	/**
	 * get the full initialization query which is called by the CacheStrategy
	 * class when performing cache initialization
	 * 
	 * @assumes nothing
	 * @effects nothing
	 * @return the full initialization query
	 */
	public String getFullInitQuery() {
		return "SELECT _CellLine_key, _Strain_key FROM ALL_CellLine";
	}

	/**
	 * get the RowDataInterpreter which is required by the CacheStrategy to read
	 * the results of a database query.
	 * 
	 * @assumes nothing
	 * @effects nothing
	 * @return the partial initialization query
	 */
	public RowDataInterpreter getRowDataInterpreter() {
		class Interpreter implements RowDataInterpreter {
			public Object interpret(RowReference row) throws DBException {
				return new KeyValue(row.getInt(1), row.getInt(2));
			}
		}
		return new Interpreter();
	}
}
