package org.jax.mgi.app.targetedalleleload;

import junit.framework.*;
import org.jax.mgi.shr.exception.*;

public class TestTargetedAlleleLoad
    extends TestCase
{
    private TargetedAlleleLoad taLoad = null;

    public static void main(String[] args)
    throws Exception
    {
        TargetedAlleleLoad load = new TargetedAlleleLoad();
        load.run();
    }

    public TestTargetedAlleleLoad(String name)
    {
        super(name);
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();
        /**@todo verify the constructors*/
        taLoad = new TargetedAlleleLoad();
    }

    protected void tearDown()
        throws Exception
    {
        taLoad = null;
        super.tearDown();
    }

    public void testRun()
        throws MGIException
    {
        taLoad.load();
    }

}
