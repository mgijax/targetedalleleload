package org.jax.mgi.app.targetedalleleload;

/**
 * a plain old java object for storing strain information
 * @has strain attributes
 * @does nothing
 * @company Jackson Laboratory
 * @author jmason
 *
 */

public class Strain
{
    private String name = null;
    private int key = 0;

    /**
     * constructor
     * @param name the name of the strain
     * @param key the databse key of object
     */
    public Strain(String name, int key)
    {
        this.name = name;
        this.key = key;
    }

    /**
     * get the symbol of strain
     * @return the symbol of strain
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * get the database key of the strain
     * @return the database key
     */
    public int getKey()
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
        if (!(o instanceof Strain))
            return false;
        Strain s = (Strain)o;
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
        return "<Strain: "+ this.name + " (key: " +
            this.getKey() + ")>";
    }

}

