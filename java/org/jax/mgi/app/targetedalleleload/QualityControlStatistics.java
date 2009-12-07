package org.jax.mgi.app.targetedalleleload;

import java.lang.Integer;
import java.util.TreeMap;

/**
 * @is An object that maintains a static record of quality control statistics
 * that gets updated throughout the load process
 * @has
 *   nothing
 * @does
 *   <UL>
 *   <LI> Maintains a list of 
 *   <LI>
 *   </UL>
 * @company The Jackson Laboratory
 * @author jmason
 */

class QualityControlStatistics
{

    static protected TreeMap stats = new TreeMap();

    /**
    * Record an entry in the Quality Control statistic tracking object
     * @param levelName the statistic level to which this QC entry belongs
     * @param qcParameter a string describing the statistic being tracked
     * @param qty override the count for this statistic
     */
    protected void record(String levelName, String qcParameter, int qty)
    {
        TreeMap statLevel = null;
        if (stats.containsKey(levelName))
        {
            statLevel = (TreeMap)stats.get(levelName);
        }
        else
        {
            // First time seeing this level
            statLevel = new TreeMap();
        }
        
        // This replaced the current value!
        statLevel.put(qcParameter, new Integer(qty));
        stats.put(levelName, statLevel);
    }

    /**
     * Record an entry in the Quality Control statistic tracking object
     * @param levelName the statistic level to which this QC entry belongs
     * @param qcParameter a string describing the statistic being tracked
     */
    protected void record(String levelName, String qcParameter)
    {
        if (stats.containsKey(levelName))
        {
            TreeMap statLevel = (TreeMap)stats.get(levelName);
            if (statLevel.containsKey(qcParameter))
            {
                Integer oldLevel = (Integer)((TreeMap)stats.get(levelName)).get(qcParameter);
                Integer newLevel = new Integer(oldLevel.intValue() + 1);
                statLevel.put(qcParameter, newLevel);
                
            }
            else
            {
                // First time seeing this qc parameter
                statLevel.put(qcParameter, new Integer(1));
            }
            stats.put(levelName, statLevel);
        }
        else
        {
            // First time seeing this level
            TreeMap statLevel = new TreeMap();
            statLevel.put(qcParameter, new Integer(1));
            stats.put(levelName, statLevel);
        }
    }

    /**
     * Record an entry in the Quality Control statistic tracking object
     * @param levelName the statistic level to which this QC entry belongs
     * @param qcParameter a string describing the statistic being tracked
     * @return a map of maps containing the QC statistics
     */
    public TreeMap getStatistics()
    {
        return stats;
    }

}