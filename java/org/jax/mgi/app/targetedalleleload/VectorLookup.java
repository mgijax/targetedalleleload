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

/**
 * 
 * is a FullCachedLookup storing es cell line name associations to project id
 * 
 * @has internal cache of set objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class VectorLookup extends FullCachedLookup {

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
	public VectorLookup() throws ConfigException, DBException, CacheException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
	}

	/**
	 * look up associated vector key by vector name
	 * 
	 * @param vectorName
	 *            the name of the vector
	 * @return the associated key for the vector name
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public String lookup(String vectorName) throws DBException, CacheException {
		return (String) super.lookupNullsOk(vectorName);
	}

	/**
	 * look up associated ES cell lines by production center project ID
	 * 
	 * @param vectorName
	 *            the name of the vector
	 * @return the string value of the term key
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public String lookupExisting(String vectorName) throws DBException,
			CacheException, KeyNotFoundException {
		return (String) super.lookup(vectorName);
	}

	/**
	 * get the query for fully initializing the cache ES cell lines by
	 * production center project ID
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() {
		return "SELECT _term_key, term FROM VOC_Term WHERE _Vocab_key = 72";
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

		public Object interpret(RowReference row) throws DBException {
			return new KeyValue(row.getString("term"),
					row.getString("_term_key"));
		}
	}

}
