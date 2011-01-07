package org.jax.mgi.app.targetedalleleload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyNotFoundException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.MultiRowInterpreter;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.dla.log.DLALoggingException;

/**
 * 
 * is a FullCachedLookup storing alleles to project id
 * 
 * @has internal cache of hashset objects indexed by project id
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class AlleleLookupByMarker extends FullCachedLookup {

	private static AlleleLookupByMarker _instance;

	public static AlleleLookupByMarker getInstance() throws ConfigException,
			DBException, CacheException {
		if (_instance == null) {
			_instance = new AlleleLookupByMarker();
		}
		return _instance;
	}

	// provide a static cache so that all instances share one cache
	private static HashMap cache = new HashMap();

	// indicator of whether or not the cache has been initialized
	private static boolean hasBeenInitialized = false;

	private TargetedAlleleLoadCfg cfg = null;

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
	public AlleleLookupByMarker() throws ConfigException, DBException,
			CacheException {
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));

		// since cache is static make sure you do not reinit
		if (!hasBeenInitialized) {
			try {
				cfg = new TargetedAlleleLoadCfg();
			} catch (DLALoggingException e) {
				System.out
						.println("KnockoutAlleleLookup DLALoggingException exception");
			}
			initCache(cache);
			hasBeenInitialized = true;
		}

	}

	/**
	 * look up associated alleles by marker symbol
	 * 
	 * @param symbol
	 *            the marker symbol
	 * @return the associated set of allele symbols
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public HashSet lookup(String symbol) throws DBException, CacheException {
		return (HashSet) super.lookupNullsOk(symbol);
	}

	/**
	 * look up associated alleles by marker symbol
	 * 
	 * @param symbol
	 *            the marker symbol
	 * @return the associated set of allele symbols
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public HashSet lookupExisting(String symbol) throws DBException,
			CacheException, KeyNotFoundException {
		return (HashSet) super.lookup(symbol);
	}

	/**
	 * get the query for fully initializing the cache
	 * 
	 * @return the initialization query
	 */
	public String getFullInitQuery() {
		String provider = null;
		try {
			provider = cfg.getProvider();
		} catch (ConfigException e) {
			System.out.println("Config Exception retrieving JNUMBER");
		}

		return "SELECT av._allele_key 'alleleKey', av.markerSymbol 'markerSymbol' "
				+ "FROM ALL_Allele_View av "
				+ "WHERE av.symbol like '%<tm%"
				+ provider
				+ ">'"
				+ "ORDER BY markerSymbol";
	}

	/**
	 * add a new map to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'symbol' will be added or replaced
	 * @param symbol
	 *            the marker symbol
	 * @param alleles
	 *            set of all allele symbols
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(String symbol, Set alleles) throws DBException,
			CacheException {
		// Replace the current value if it exists
		super.cache.put(symbol.toLowerCase(), alleles);
	}

	/**
	 * return the RowDataInterpreter for creating KeyValue objects from the
	 * query results
	 * 
	 * @return the RowDataInterpreter for this query
	 */
	public RowDataInterpreter getRowDataInterpreter() {
		class Interpreter implements MultiRowInterpreter {
			public Object interpret(RowReference ref) throws DBException {
				return new RowData(ref);
			}

			public Object interpretKey(RowReference row) throws DBException {
				return row.getString("markerSymbol");
			}

			public Object interpretRows(Vector v) {
				RowData rd = (RowData) v.get(0);
				String markerSymbol = rd.markerSymbol;
				Set alleles = new HashSet();
				for (Iterator it = v.iterator(); it.hasNext();) {
					rd = (RowData) it.next();

					alleles.add(rd.alleleKey);
				}
				return new KeyValue(markerSymbol, alleles);
			}
		}

		return new Interpreter();
	}

	/**
	 * Simple data object representing a row of data from the query
	 */
	class RowData {
		protected String markerSymbol;
		protected Integer alleleKey;

		public RowData(RowReference row) throws DBException {
			markerSymbol = row.getString("markerSymbol");
			alleleKey = row.getInt("alleleKey");
		}
	}
}
