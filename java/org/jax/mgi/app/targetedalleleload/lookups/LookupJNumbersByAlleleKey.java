package org.jax.mgi.app.targetedalleleload.lookups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.LazyCachedLookup;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;



/**
 * @is a FullCachedLookup for caching jnumbers by allele key
 * @has a RowDataCacheStrategy of type FULL_CACHE used for creating the 
 * 		cache and performing the cache lookup
 * @does provides a lookup method to return an array of jnumber strings
 *       given an allele key
 * @company The Jackson Laboratory
 * @author jmason
 * @version 1.0
 */
public class LookupJNumbersByAlleleKey 
extends LazyCachedLookup 
{

	// provide a static cache so that all instances share one cache
	private static Map cache = new HashMap();

	// indicate if the cache has been initialized
	private static boolean hasBeenInitialized = false;

	/**
	 * constructor
	 * 
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 * @throws DBException
	 *             thrown if there is an error accessing the db
	 * @throws ConfigException
	 *             thrown if there is an error accessing the 
	 *             configuration file
	 */
	public LookupJNumbersByAlleleKey()
	throws CacheException, DBException, ConfigException 
	{
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

		// since cache is static make sure it is not reinitialized
		if (!hasBeenInitialized) {
			initCache(cache);
		}
		hasBeenInitialized = true;
	}

	/**
	 * look up the jnumbers given an allele key
	 * 
	 * @param key
	 *            the allele key to lookup
	 * @return an array of strings representing the set of J numbers
	 *         associated to this allele
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 */
	public String[] lookup(Integer key)
	throws CacheException, DBException, ConfigException 
	{
		return (String[]) super.lookupNullsOk(key);
	}

	/**
	 * add a new allele to the cache
	 * 
	 * @assumes nothing
	 * @effects the key/value identified by 'alleleKey' will be 
	 *          added or replaced
	 * @param alleleKey
	 *            the key of an existing allele
	 * @param jnumbers
	 *            the array of jnumber strings
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(Integer alleleKey, String[] jnumbers)
	throws DBException, CacheException 
	{
		// Replace the current value if it exists
		cache.put(alleleKey, jnumbers);
	}


	/**
	 * get the query for partial initializing the cache mouse KnockoutAlleles by
	 * name
	 * 
	 * @return the initialization query
	 */
	public String getPartialInitQuery() 
	{
			return null;
	}

	/**
     * get the query to use when adding new entries to the cache
     * 
     * @param addObject the lookup identifier which triggers the cache add
     * 
     * @return the query string to add an allele to the cache based
     * 			on the given cellline
     */
    public String getAddQuery(Object addObject)
    {
    	Integer key = (Integer)addObject;

		return "SELECT jnumid, mra._object_key '_allele_key' " +
			"FROM MGI_Reference_Assoc mra, BIB_citation_cache bcc " +
			"WHERE mra._refs_key = bcc._refs_key " +
			"AND mra._mgitype_key = 11 " +
			"AND mra._object_key = " + key + " " +
			"ORDER BY mra._object_key " ;
	}

	/**
	 * return the RowDataInterpreter for creating KeyValue objects from 
	 * the query results
	 * 
	 * @return the RowDataInterpreter for this query
	 */
	public RowDataInterpreter getRowDataInterpreter()
	{
		class Interpreter 
		implements MultiRowInterpreter 
		{
			public Object interpret(RowReference ref) 
			throws DBException 
			{
				return new RowData(ref);
			}

			public Object interpretKey(RowReference row) 
			throws DBException 
			{
				return row.getString("_allele_key");
			}

			public Object interpretRows(Vector v) 
			{

				// All rows return the same values for the key
				RowData rd = (RowData) v.get(0);

				List jnumbers = new ArrayList();

				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();

					// add the jnumber to the list
					jnumbers.add(rd.jnumber);
				}
				
				return new KeyValue(
					rd._allele_key, 
					(String[]) jnumbers.toArray(new String[0])
					);
			}
		}
		return new Interpreter();
	}

	/**
	 * Simple data object representing a row of data from the query
	 */
	class RowData 
	{
		protected Integer _allele_key;
		protected String jnumber;

		public String toString() 
		{
			return "alleleKey: " + _allele_key + 
				"\njnumber: " + jnumber;
		}

		public RowData(RowReference row) 
		throws DBException 
		{
			_allele_key = row.getInt("_allele_key");
			jnumber = row.getString("jnumid");
		}
	}
}

