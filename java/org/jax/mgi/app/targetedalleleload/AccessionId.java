package org.jax.mgi.app.targetedalleleload;

import java.util.Vector;
import java.lang.Integer;

import org.jax.mgi.shr.dbutils.dao.SQLStream;

import org.jax.mgi.shr.config.ConfigException;
import org.jax.mgi.shr.dbutils.DBException;

import org.jax.mgi.dbs.mgd.AccessionLib;

import org.jax.mgi.dbs.mgd.dao.ACC_AccessionState;
import org.jax.mgi.dbs.mgd.dao.ACC_AccessionDAO;

/**
 * a plain old java object for storing and inserting an AccessionId
 * @has AccessionId attributes
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class AccessionId
{

    private int key = 0;
    private String accessionId = null;
    private Integer logicalDb = null;
    private Integer objectKey = null;
    private Integer mgiTypeKey = null;
    private Boolean privateVal = null;
    private Boolean preferred = null;


    /**
     * constructor
     * @param logicalDb the logical database of the accession ID
     * @param objectKey the object key that this accession ID to which 
     *                  this ID is associated
     * @param mgiTypeKey the mgi type of the accession ID
     * @param privateVal if tge accession ID is private
     * @param preferred if the accession ID is the preferred one
     */
    public AccessionId(int logicalDb, int objectKey, 
        int mgiTypeKey, Boolean privateVal, Boolean preferred)
    {
        this.accessionId = null;
        this.logicalDb = new Integer(logicalDb);
        this.objectKey = new Integer(objectKey);
        this.mgiTypeKey = new Integer(mgiTypeKey);
        this.privateVal = privateVal;
        this.preferred = preferred;
    }

    /**
     * constructor
     * @param accID the accession id
     * @param logicalDb the logical database of the accession ID
     * @param objectKey the object key that this accession ID to which 
     *                  this ID is associated
     * @param mgiTypeKey the mgi type of the accession ID
     * @param privateVal if tge accession ID is private
     * @param preferred if the accession ID is the preferred one
     */
    public AccessionId(String accID, int logicalDb, int objectKey, 
        int mgiTypeKey, Boolean privateVal, Boolean preferred)
    {
        this.accessionId = accID;
        this.logicalDb = new Integer(logicalDb);
        this.objectKey = new Integer(objectKey);
        this.mgiTypeKey = new Integer(mgiTypeKey);
        this.privateVal = privateVal;
        this.preferred = preferred;
    }

    /**
     * get the symbol of AccessionId
     * @return the symbol of AccessionId
     */
    public String getAccessionId()
    {
        return this.accessionId;
    }

    /**
     * get the database key of the AccessionId
     * @return the database key
     */
    public int getKey()
    {
        return this.key;
    }

    /**
     * Insert this AccessionId object into the stream to create the required
     * bcp records.
     * @assumes If the caller didn't supply an Accession ID, then the default
     *          action is to create and MGI ID
     * @effects Nothing
     * @param stream The bcp stream to write the bcp records to.
     * @return Nothing
     * @throws ConfigException
     * @throws DBException
     */
    public void insert (SQLStream stream)
    throws ConfigException,DBException
    {
        // Create the Accession entry
        //
        ACC_AccessionState accState = null;

        if(this.accessionId == null) {

            // If the caller did not specify an Accession ID, then the 
            // default action of this class is triggered.  Insert an MGI ID 
            // as the accession ID

            // Get an ACC_AccessionState object that contains a new MGI ID
            accState = AccessionLib.getNextAccState();

            Vector vParts = AccessionLib.splitAccID(accState.getAccID());
            accState.setPrefixPart((String)vParts.get(0));
            accState.setNumericPart((Integer)vParts.get(1));
            
        } else {

            // The user supplied an Accession ID, populate all the appropriate
            // appropriate fields

            // Get an empty ACC_AccessionState object, we'll fill in all 
            // the details below
            accState = new ACC_AccessionState();
            
            Vector vParts = AccessionLib.splitAccID(this.accessionId);
            accState.setPrefixPart((String)vParts.get(0));
            accState.setNumericPart((Integer)vParts.get(1));
            accState.setAccID(this.accessionId);
        }

        accState.setLogicalDBKey(this.logicalDb);
        accState.setObjectKey(this.objectKey);
        accState.setMGITypeKey(this.mgiTypeKey);
        accState.setPrivateVal(this.privateVal);
        accState.setPreferred(this.preferred);
        
        ACC_AccessionDAO accDAO = new ACC_AccessionDAO(accState);
        stream.insert(accDAO);
        
        // Store the key that gets gerenated when this object is instered
        // into the database
        this.key = accDAO.getKey().getKey().intValue();
    }


    /**
     * override of equals method from Object class
     * @param o the object to compare to
     * @return true if the two objects are equal, false otherwise
     */
    public boolean equals(Object o)
    {
        if (!(o instanceof AccessionId))
            return false;
        AccessionId s = (AccessionId)o;
        if (this.accessionId == s.getAccessionId())
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
        return "<AccessionID: "+ this.accessionId + " (key: " +
            this.getKey() + ")>";
    }

}

