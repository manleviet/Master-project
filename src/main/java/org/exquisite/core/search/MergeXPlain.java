package org.exquisite.core.search;

import org.exquisite.core.DiagnosisException;
import org.exquisite.core.ISolver;

import java.util.*;

import static org.exquisite.core.perfmeasures.PerfMeasurementManager.COUNTER_QXP_CALLS;
import static org.exquisite.core.perfmeasures.PerfMeasurementManager.incrementCounter;

/**
 * MergeXPlain algorithm that searches for multiple conflicts at once
 */
public class MergeXPlain<F> extends QuickXPlain<F> {

    public MergeXPlain(ISolver<F> solver) {
        super(solver);

    }

    @Override
    public Set<Set<F>> findConflicts(Collection<F> formulas) throws DiagnosisException {
        incrementCounter(COUNTER_QXP_CALLS);
        if (checkInput(formulas))
            return Collections.emptySet();

        ArrayList<F> c = new ArrayList<>(formulas);
        return mergeXPlain(c);
    }

    private Set<Set<F>> mergeXPlain(List<F> c) {
        if (solver.isConsistent(c))
            return new HashSet<>();
        if (c.size() == 1) {
            HashSet<Set<F>> css = new HashSet<>();
            css.add(new HashSet<>(c));
            return css;
        }
        int k = split(c);
        List<F> c1 = c.subList(0, k);
        List<F> c2 = c.subList(k, c.size());

        Set<Set<F>> g = mergeXPlain(c1);
        g.addAll(mergeXPlain(c2));

        while (!solver.isConsistent(c)) {
            List<F> b = new ArrayList<>(c2.size() + c1.size());
            b.addAll(c2);
            List<F> x = quickXPlain(b, c2, c1);
            Set<F> cs = new HashSet<>(2 * x.size());
            cs.addAll(x);
            cs.addAll(quickXPlain(x, x, c2));
            // cannot use remove here since concurrent modification is not allowed
            // nulls are not added to a solver if it inherits from AbstractSolver
            if (!x.isEmpty())
                c1.set(c1.indexOf(x.get(0)), null);
            else
                c2.set(c2.indexOf(cs.iterator().next()), null);
            g.add(cs);
        }
        return g;
    }
}
