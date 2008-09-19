package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.config.TargetedAlleleLoadCfg;
import org.jax.mgi.shr.config.ConfigException;

import org.jax.mgi.shr.dbutils.dao.SQLStream;
import org.jax.mgi.shr.dbutils.DBException;
import org.jax.mgi.shr.cache.CacheException;

import org.jax.mgi.dbs.mgd.dao.ACC_AccessionState;
import org.jax.mgi.dbs.mgd.dao.ACC_AccessionDAO;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineState;
import org.jax.mgi.dbs.mgd.dao.ALL_CellLineDAO;

import java.util.regex.Pattern;

/**
 * a plain old java object for storing ESCell information
 * @has ESCell attributes
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class ESCell
{
    private int key = 0;
    private String name = null;
    private Strain strain = null;
    private String provider = null;
    private boolean isMutant = false;
    private int logicalDB = 0;
    
    // Constants
    private int mgiType = 28;           // ALL_CellLine

    /**
     * constructor
     * @param key the databse key of object
     * @param strain the strain the ES Cell Line was derived from
     * @param name the name of the ES Cell Line
     * @param provider the name of the organization the ES Cell fomes from
     */
    public ESCell(int key, Strain strain, String name, String provider, boolean isMutant)
    {
        this.key = key;
        this.strain = strain;
        this.name = name;
        this.provider = provider;
        this.isMutant = isMutant;
    }

    /**
     * get the symbol of ESCell
     * @return the symbol of ESCell
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * get the database key of the ESCell
     * @return the database key
     */
    public int getKey()
    {
        return this.key;
    }

    /**
     * get the database key of the ESCell
     * @return the database key
     */
    public int getStrainKey()
    {
        return this.strain.getKey();
    }

    /**
     * set the logical DB of ESCell
     */
    public void setLogicalDB(int logicalDB)
    {
        this.logicalDB = logicalDB;
    }

    /**
     * Insert this ESCell object into the stream to create the required
     * bcp records.
     * @assumes Nothing
     * @effects Nothing
     * @param stream The bcp stream to write the bcp records to.
     * @return Nothing
     * @throws ConfigException
     * @throws DBException
     */
    public void insert (SQLStream stream)
    throws ConfigException,DBException
    {
        // Create the mutant ES Cell line in the database
        ALL_CellLineState cState = new ALL_CellLineState();
        cState.setCellLine(this.getName());
        cState.setStrainKey(new Integer(this.getStrainKey()));
        cState.setProvider(this.provider);
        cState.setIsMutant(Boolean.TRUE);
        
        ALL_CellLineDAO cDAO = new ALL_CellLineDAO(cState);
        stream.insert(cDAO);
        this.key = cDAO.getKey().getKey().intValue();

        // Create the Accession entry attaching it to the ESCell
        ACC_AccessionState accState =  new ACC_AccessionState();
        accState.setLogicalDBKey(new Integer(this.logicalDB));
        accState.setObjectKey(new Integer(this.key));
        accState.setMGITypeKey(new Integer(this.mgiType));
        accState.setPrivateVal(Boolean.FALSE);
        accState.setPreferred(Boolean.TRUE);
        accState.setAccID(this.name);
        
        // Remove the last numeric part of the name using the regex
        // \d - digits 
        // * - zero or more 
        // $ - at the end of the string
        String prefix = this.name.replaceAll("\\d*$", "");
        
        // Get the numeric part by striping off the prefix part
        Integer numeric = Integer.valueOf(this.name.replaceAll(prefix, ""));

        accState.setNumericPart(numeric);
        accState.setPrefixPart(prefix);

        ACC_AccessionDAO accDAO = new ACC_AccessionDAO(accState);
        stream.insert(accDAO);

    }


    /**
     * override of equals method from Object class
     * @param o the object to compare to
     * @return true if the two objects are equal, false otherwise
     */
    public boolean equals(Object o)
    {
        if (!(o instanceof ESCell))
            return false;
        ESCell s = (ESCell)o;
        if (this.key == s.getKey())
            return true;
        else
            return false;
    }

    /**
     * override of hashCode method from Object class
     * @return the object hash code
     */
    public int hashCode()
    {
        return (new Integer(this.getKey())).hashCode();
    }

    /**
     * override of toString method from Object class
     * @return the string representation of this instance
     */
    public String toString()
    {
        return "<ESCell: "+ this.name + " (key: " +
            this.getKey() + ")>";
    }

}

