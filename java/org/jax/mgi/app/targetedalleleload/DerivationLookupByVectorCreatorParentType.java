package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * is a FullCachedLookup storing alleles to project id
 * 
 * @has internal cache of set objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class DerivationLookupByVectorCreatorParentType extends FullCachedLookup {

	// Singleton pattern implementation
	private static DerivationLookupByVectorCreatorParentType _instance;

	public static DerivationLookupByVectorCreatorParentType getInstance()
			throws MGIException {
		if (_instance == null) {
			_instance = new DerivationLookupByVectorCreatorParentType();
		}
		return _instance;
	}

	/**
	 * constructor
	 * 
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	private DerivationLookupByVectorCreatorParentType() throws ConfigException,
			DBException, CacheException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
		initCache(cache);
	}

	/**
	 * look up associated ES cell lines by production center project ID
	 * 
	 * @param identifier
	 *            the identifier
	 * @return the associated set of ES cell line names
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public Integer lookup(String identifier) throws DBException, CacheException {
		return (Integer) super.lookupNullsOk(identifier);
	}

	/**
	 * look up associated ES cell lines by production center project ID
	 * 
	 * @param identifier
	 *            the identifier
	 * @return the associated set of ES cell line names
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public Integer lookupExisting(String identifier) throws DBException,
			CacheException, KeyNotFoundException {
		return (Integer) super.lookup(identifier);
	}

	/**
	 * add a new map to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'key' will be added or replaced
	 * @param d
	 *            the derivation to add
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(Derivation d) throws MGIException {
		// Replace the current value if it exists
		String value = d.getVectorKey().toString();
		value += "|" + d.getCreatorKey();
		value += "|" + d.getParentCellLineKey();
		value += "|" + d.getDerivationTypeKey();

		super.cache.put(value, d.getDerivationKey());
	}

	/**
	 * get the query for fully initializing the cache ES cell lines by
	 * production center project ID
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() {
		return "SELECT _Derivation_key, _Vector_key 'vectorkey', "
				+ "_ParentCellLine_key 'parentalkey', _Creator_key 'creatorkey', "
				+ "_DerivationType_key 'typekey' "
				+ "FROM ALL_CellLine_Derivation ";
	}

	/**
	 * get the RowDataInterpreter for interpreting initialization query
	 * 
	 * @return the RowDataInterpreter
	 */
	public RowDataInterpreter getRowDataInterpreter() {
		return new Interpreter();
	}

	private class Interpreter implements RowDataInterpreter {
		public Interpreter() {
		}

		public Object interpret(RowReference row) throws DBException {
			String lookupkey = row.getString("vectorkey");
			lookupkey += "|" + row.getString("creatorkey");
			lookupkey += "|" + row.getString("parentalkey");
			lookupkey += "|" + row.getString("typekey");

			return new KeyValue(lookupkey, row.getInt("_Derivation_key"));
		}
	}

}
