package org.jax.mgi.app.targetedalleleload.lookups;

import org.jax.mgi.dbs.SchemaConstants;
import org.jax.mgi.shr.cache.CacheException;
import org.jax.mgi.shr.cache.KeyValue;
import org.jax.mgi.shr.cache.LazyCachedLookup;
import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManagerFactory;
import org.jax.mgi.shr.exception.MGIException;

public class LookupMirkoClusterByCellLine 
extends LazyCachedLookup {

	private static LookupMirkoClusterByCellLine _instance;

	public static LookupMirkoClusterByCellLine getInstance() 
	throws MGIException 
	{
		if (_instance == null) {
			_instance = new LookupMirkoClusterByCellLine();
		}
		return _instance;
	}

	/**
	 * constructor uses the singleton pattern
	 * 
	 * @throws ConfigException
	 *             thrown if there is an error accessing the configuration
	 * @throws DBException
	 *             thrown if there is an error accessing the database
	 * @throws CacheException
	 *             thrown if there is an error accessing the cache
	 */
	private LookupMirkoClusterByCellLine() 
	throws MGIException 
	{
		super(SQLDataManagerFactory.getShared(SchemaConstants.MGD));
	}

	/**
	 * look up if this cellline belongs to a MirKO cluster
	 * 
	 * @param cellline
	 *            the cellline of interest
	 * @return the allele symbol associated to the cellline (or null if
	 * 			not found) 
	 * @throws MGIException
	 *             if something goes wrong
	 */
	public String lookup(String cellline) 
	throws MGIException 
	{
		return (String) super.lookupNullsOk(cellline);
	}

	/**
	 * get the query for initializing the cache
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
    	String cellline = (String)addObject;

    	return "SELECT a.symbol, ac.cellline " +
			"FROM ALL_Allele a " +
			"INNER JOIN ALL_Allele_cellline aac ON (a._allele_key = aac._Allele_key) " +
			"INNER JOIN ALL_Cellline ac ON (aac._mutantcellline_key = ac._cellline_key) " +
			"WHERE a.symbol LIKE 'Mirc%Wtsi>'" +
			"AND ac.cellline = '" + cellline + "'";
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
		public Object interpret(RowReference row) 
		throws DBException 
		{
			return new KeyValue(
				row.getString("cellline"), 
				row.getString("symbol"));
		}
	}

}
