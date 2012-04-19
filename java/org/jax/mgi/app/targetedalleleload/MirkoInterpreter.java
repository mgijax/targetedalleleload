package org.jax.mgi.app.targetedalleleload;

import org.jax.mgi.app.targetedalleleload.lookups.LookupMirkoClusterByCellLine;
import org.jax.mgi.shr.exception.MGIException;

public class MirkoInterpreter extends SangerInterpreter {

	LookupMirkoClusterByCellLine lookupMirkoClusterByCellLine;

	public MirkoInterpreter() throws MGIException {
		super();
		LookupMirkoClusterByCellLine.getInstance();
	}

	public Object interpret(String rec) throws MGIException {
		return super.interpret(rec);
	}

	public boolean isValid(String rec) {
		boolean isValid = super.isValid(rec);
		
		if (isValid) {
			try {
				SangerAlleleInput in = (SangerAlleleInput) super.interpret(rec);

				if(!lookupMirkoClusterByCellLine.lookup(in.getMutantCellLine())) {
					// Looks like this cell line is not already
					// loaded as belonging to a mirKO cluster project
					return true;
				}
				// We have a mirko cluster, don't do anything with these
				return false;
			} catch (MGIException e) {
				return false;
			}
		}

		return false;
	}

}
