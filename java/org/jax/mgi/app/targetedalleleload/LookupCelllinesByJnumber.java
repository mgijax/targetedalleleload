package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.dbutils.ResultsNavigator;
import org.jax.mgi.shr.dbutils.RowDataInterpreter;
import org.jax.mgi.shr.dbutils.RowDataIterator;
import org.jax.mgi.shr.dbutils.RowReference;
import org.jax.mgi.shr.dbutils.SQLDataManager;
import org.jax.mgi.shr.exception.MGIException;

/**
 * 
 * is a FullCachedLookup storing jnumber to celllines
 * 
 * @has internal iterator of Strings of celllines
 * @does provides a lookup for accessing the cache
 * @company Jackson Laboratory
 * @author jmason
 * 
 */

public class LookupCelllinesByJnumber 
{
	// A results navigator to step through the cell lines
	private ResultsNavigator rn;

    // An iterator to step through the cell lines
    //
    private RowDataIterator it;


	/**
	 * constructor
	 * 
	 * @throws MGIException
	 *             thrown if there is an error accessing the configuration
	 *             thrown if there is an error accessing the database
	 *             thrown if there is an error accessing the cache
	 *             thrown if there is an error initializing the logger
	 */
	public LookupCelllinesByJnumber(SQLDataManager sqlMgr, String jnum) 
	throws MGIException 
	{
        // Create a ResultsNavigator to get the records that were just loaded
        // into the WRK_CloneAssociation table.
        //
        String sql = "SELECT cellline " +
        	"FROM ALL_Allele_Cellline aac, All_Cellline ac, " +
        	"MGI_Reference_Assoc mra, BIB_citation_cache bcc " +
        	"WHERE mra._refs_key = bcc._refs_key " +
        	"AND mra._mgitype_key = 11 " +
        	"AND mra._object_key = aac._allele_key " +
    		"AND aac._mutantcellline_key = ac._cellline_key " +
    		"AND bcc.jnumid = 'J:157064' " ;

        rn = sqlMgr.executeQuery(sql);

        // Create a MultiRowIterator that uses a Interpreter to build and
        // return RADARClone objects from the ResultsNavigator.
        //
        it = new RowDataIterator(rn, new Interpreter());

	}

    /**
     * Checks to see if there is another RADARClone to be processed.
     * @assumes Nothing
     * @effects Nothing
     * @param None
     * @return True if there is another RADARClone, otherwise false.
     * @throws Nothing
     */
    public boolean hasNext ()
    {
        return it.hasNext();
    }

    /**
     * Gets the next RADARClone to be processed.
     * @assumes Nothing
     * @effects Nothing
     * @param None
     * @return The next RADARClone object.
     * @throws DBException
     */
    public String next ()
        throws DBException
    {
        return (String)it.next();
    }

	public class Interpreter 
	implements RowDataInterpreter 
	{
		public Object interpret(RowReference row)
		throws DBException
		{
            RowData rd = new RowData(row);
            return rd;
		}
	}

	/**
	 * Simple data object representing a row of data from the query
	 */
	class RowData {
		protected String cellline;

		public RowData(RowReference row) 
		throws DBException 
		{
			cellline = row.getString("cellline");
		}
	}
}
