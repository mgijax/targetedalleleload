package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.shr.dla.log.DLALoggingException;

public class MutantCellLine 
extends org.jax.mgi.dbs.mgd.loads.Alo.MutantCellLine
{

	public MutantCellLine() throws DLALoggingException {
		super();
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Cellline: ")
			.append(this.getAccID())
			.append("(key: ")
			.append(this.getMCLKey())
			.append(")\n");
		
		return sb.toString();		
	}

}
