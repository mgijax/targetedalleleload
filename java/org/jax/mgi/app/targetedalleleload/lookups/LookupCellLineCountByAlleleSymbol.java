package org.jax.mgi.app.targetedalleleload.lookups;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.FullCachedLookup;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.exception.MGIException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;

/**
 * 
 * is a FullCachedLookup storing alleles to count of mutant cell lines
 * 
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class LookupCellLineCountByAlleleSymbol 
extends FullCachedLookup 
{

	// Singleton pattern implementation
	private static LookupCellLineCountByAlleleSymbol _instance;

	public static LookupCellLineCountByAlleleSymbol getInstance() 
	throws MGIException 
	{
		if (_instance == null) {
			_instance = new LookupCellLineCountByAlleleSymbol();
		}
		return _instance;
	}

	/**
	 * constructor (singleton guarantees that this is only called 
	 * the first time an instance of AlleleCellLineCount is created)
	 * 
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	private LookupCellLineCountByAlleleSymbol() 
	throws MGIException 
	{
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
		initCache(cache);
	}

	/**
	 * look up associated ES cell lines by production center project ID
	 * 
	 * @param symbol
	 *            the allele symbol
	 * @return the count of mutant cell lines associated with the allele
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the configuration
	 */
	public Integer lookup(String symbol) 
	throws DBException, CacheException 
	{
		return (Integer) lookupNullsOk(symbol);
	}


	/**
	 * get the query for partial initializing the cache 
	 *  
	 * @return the initialization query
	 */
	public String getFullInitQuery() 
	{
		return "SELECT DISTINCT a.symbol, " +
			"COUNT(ac._mutantcellline_key) as cnt " +
			"FROM All_allele a " +
			    "LEFT OUTER JOIN  All_allele_cellline ac on (a._allele_key = ac._allele_key)," +
			"ACC_Accession acc2 " +
			"WHERE acc2.preferred = 1 " +
			"AND acc2.private = 1 " +
			"AND acc2._Object_key = a._Allele_key " +
			"AND acc2._LogicalDB_key in (125,126,138,143,166) " +
			"AND acc2._MGIType_key=11 " +
			"GROUP BY a.symbol " +
			"ORDER BY COUNT(ac._mutantcellline_key)" ;
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
    	String symbol = (String)addObject;
		return "SELECT a.symbol, COUNT(ac._mutantcellline_key) as cnt " +
				"FROM All_allele a " +
				"LEFT OUTER JOIN All_allele_cellline ac " +
				"on (a._allele_key = ac._allele_key) " +
				"AND a.symbol = '" + symbol + "' " +
				"GROUP BY a.symbol " +
				"ORDER BY COUNT(ac._mutantcellline_key)" ;
	}

	/**
	 * Decrement (or set to 0 if it doesn't exist) the count 
	 * associated with the the allele symbol
	 * 
	 * @assumes nothing
	 * @effects the value identified by symbol will be decremented by 1
	 * @param symbol
	 *            the allele symbol
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	public void decrement(String symbol) 
	throws MGIException 
	{
		// Increment the count, or if the symbol didn't exist
		// add it and initialize the count to 1
		Integer i = lookup(symbol.toLowerCase());
		if (i == null) {
			throw new MGIException(this.getClass().getName() +
				": Trying to decrement a non existing allele (" +
				symbol + ")"
				);
		}

		i = new Integer(i.intValue() - 1);

		if (i.intValue() < 0) {
			throw new MGIException(this.getClass().getName() +
				": Trying to decrement an allele lower than 0 (" + 
				symbol + ")");
		}

		// Replace the current value if it exists
		addToCache(symbol.toLowerCase(), i);
	}

	/**
	 * Increment (or set to 1 if it doesn't exist) the count associated 
	 * with the the allele symbol
	 * 
	 * @assumes nothing
	 * @effects the value identified by symbol will be incremented by 1
	 * @param symbol
	 *            the allele symbol
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	public void increment(String symbol) 
	throws MGIException 
	{
		// Increment the count, or if the symbol didn't exist
		// add it and initialize the count to 1
		Integer i = lookup(symbol.toLowerCase());
		i = (i == null) ? new Integer(1) : new Integer(i.intValue() + 1);

		// Replace the current value if it exists
		addToCache(symbol.toLowerCase(), i);
	}

	/**
	 * add a new map to the cache
	 * 
	 * @assumes nothing
	 * @effects the value identified by 'projectId' will be added or 
	 * 			replaced
	 * @param symbol
	 *            the allele symbol
	 * @param count
	 *            the count of cell lines associated to the allele
	 * @throws DBException
	 *             thrown if there is an error with the database
	 * @throws CacheException
	 *             thrown if there is an error with the cache
	 */
	protected void addToCache(String symbol, Integer count) 
	throws DBException, CacheException 
	{
		// Replace the current value if it exists
		cache.put(symbol.toLowerCase(), count);
	}

	/**
	 * get the RowDataInterpreter for interpreting initialization query
	 * 
	 * @return the RowDataInterpreter
	 */
	public RowDataInterpreter getRowDataInterpreter() 
	{
		return new Interpreter();
	}

	private class Interpreter 
	implements RowDataInterpreter 
	{
		public Interpreter() 
		{
		}

		public Object interpret(RowReference row) 
		throws DBException 
		{
			String symbol = row.getString("symbol");
			Integer count = row.getInt("cnt");
			return new KeyValue(symbol, count);
		}
	}
}
