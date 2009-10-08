package org.jax.mgi.app.targetedalleleload;

/**
 * a plain old java object for storing marker information
 * @has marker attributes (key, symbol, chromosome, primary MGI ID)
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class Marker
{
    private String accid = null;
    private String chromosome = null;
    private String symbol = null;
    private Integer key = null;

    /**
     * constructor
     * @param accid the accession id of the marker
     * @param key the databse key of object
     */
    public Marker(String accid, String symbol, String chromosome, Integer key)
    {
        this.accid = accid;
        this.chromosome = chromosome;
        this.symbol = symbol;
        this.key = key;
    }

    /**
     * get the symbol of marker
     * @return the symbol of marker
     */
    public String getSymbol()
    {
        return this.symbol;
    }

    /**
     * get the chromosome of marker
     * @return the chromosome of marker
     */
    public String getChromosome()
    {
        return this.chromosome;
    }

    /**
     * get the accession id of marker
     * @return the accession id of marker
     */
    public String getAccid()
    {
        return this.accid;
    }

    /**
     * get the database key of the marker
     * @return the database key
     */
    public Integer getKey()
    {
        return this.key;
    }

    /**
     * override of equals method from Object class
     * @param o the object to compare to
     * @return true if the two objects are equal, false otherwise
     */
    public boolean equals(Object o)
    {
        if (!(o instanceof Marker))
            return false;
        Marker m = (Marker)o;
        if (this.key == m.getKey())
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
        return (this.getKey()).hashCode();
    }

    /**
     * override of toString method from Object class
     * @return the string representation of this instance
     */
    public String toString()
    {
        return "<Marker: "+ this.symbol + ", " +
            this.getAccid() + "(key: " +
            this.getKey() + ")>";
    }

}

