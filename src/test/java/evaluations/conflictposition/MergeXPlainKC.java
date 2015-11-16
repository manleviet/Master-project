package evaluations.conflictposition;

import choco.kernel.model.constraints.Constraint;
import org.exquisite.datamodel.ExquisiteSession;
import org.exquisite.diagnosis.IDiagnosisEngine;
import org.exquisite.diagnosis.quickxplain.DomainSizeException;
import org.exquisite.diagnosis.quickxplain.mergexplain.MergeXplain;

import java.util.List;

/**
 * A variant of MXP that uses a set of predefined conflicts
 * @author dietmar
 *
 */
public class MergeXPlainKC extends MergeXplain<Constraint> {

	
	/**
	 * Create this guy
	 * @param sessionData
	 * @param dagbuilder
	 */
	public MergeXPlainKC(ExquisiteSession sessionData,
						 IDiagnosisEngine<Constraint> diagnosisEngine) {
		super(sessionData, diagnosisEngine);
//		System.out.println("Created MXP with known conflicts");
	}
	
	/**
	 * Use the set of known conflicts. A set of constraints is consistent if it
	 * is not a superset of a known conflict
	 */
	@Override
	public boolean isConsistent(List<Constraint> constraints)
			throws DomainSizeException {
		return QXKCTools.isConsistent(constraints, this.diagnosisEngine);
	}


}
