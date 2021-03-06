package org.exquisite.diagnosis.quickxplain;

import choco.kernel.model.constraints.Constraint;
import choco.kernel.model.variables.Variable;
import org.exquisite.core.ISolver;
import org.exquisite.core.search.QuickXPlain;
import org.exquisite.datamodel.ExcelExquisiteSession;
import org.exquisite.datamodel.DiagnosisModel;
import org.exquisite.diagnosis.engines.common.SharedCollection;
import org.exquisite.diagnosis.models.ConflictCheckingResult;
import org.exquisite.diagnosis.models.ConstraintsDiagnosisModel;
import org.exquisite.diagnosis.models.Example;
import org.exquisite.diagnosis.quickxplain.choco3.choco2tochoco3.Choco2ToChoco3Solver;
import org.exquisite.diagnosis.quickxplain.ontologies.OntologySolver;
import org.exquisite.diagnosis.quickxplain.parallelqx.FormulaListener;
import org.exquisite.tools.Debug;
import org.exquisite.tools.Utilities;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.exquisite.core.measurements.MeasurementManager.*;

/**
 * A method that implements the QuickExplain algorithm for Choco constraints
 *
 * @author Dietmar
 */
public class ConstraintsQuickXPlain<Formula> extends QuickXPlain<Formula> {

    // a termporary switch to test things
    // public static boolean CHOCO3 = false;
    public static SolverType SOLVERTYPE = SolverType.Choco2;
    public static boolean CONTINUE_AFTER_FIRST_CONFLICT = false;
    public static int maxThreadPoolSize = 4;
    public static Object ContinuingSync = new Object();
    public static int runningThreads = 0;
    public static Object runningThreadsSync = new Object();
    public static int reuseCount = 0;
    // Some artificial wait time in ms to simulate longer solve tasks: Should by
    // -1
    public static int ARTIFICIAL_WAIT_TIME = -1;
    // Only for a test
    public static boolean PRINT_SOLUTION_TIME = false;
    private static ExecutorService threadPool = Executors.newFixedThreadPool(maxThreadPoolSize);
    public boolean finished = false;
    public boolean cancelled = false;
    // A handle to the DAG Builder
    //public IDiagnosisEngine<Formula> diagnosisEngine = null;
    /**
     * The model used during nodeLabel search.
     */
    public ConstraintsDiagnosisModel<Formula> currentDiagnosisModel;
    // For parallel access..
    public FormulaListener constraintListener;
    /**
     * Remember the current example being explored
     */
    public Example currentExample;
    /**
     * Session data, contains appXML, graph and complete Diagnosis model.
     */
    protected DiagnosisModel<Formula> sessionData;
    private Thread currentThread = null;

    /**
     * set currentDiagnosisModel as a copy of SessionData tests.diagnosis model.
     *
     * @param sessionData
     */
    public ConstraintsQuickXPlain(ExcelExquisiteSession sessionData) {
        this.sessionData = sessionData;
        this.currentDiagnosisModel = new ConstraintsQuickXPlain<Formula>(this.sessionData.getDiagnosisModel());
    }

    public static void restartThreadpool() {
        threadPool.shutdownNow();
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {

        }
        if (!threadPool.isTerminated()) {
            System.out.println("QuickXplain threadpool could not be shut down.");
        }
        threadPool = Executors.newFixedThreadPool(maxThreadPoolSize);
    }

    /**
     * Sorts the given constraints by calculating constraint arities and grouping neighboring constraints
     *
     * @param splitPoints      an empty list which is filled by split points
     * @param givenConstraints the given constraints
     * @param inputs           the list of input variables of the problem.
     * @param model            the tests.diagnosis model
     * @return a sorted list
     */
    public static <T extends Constraint> List<T> sortConstraintsByArityAndCalculuateSplittingPoints(
            ConstraintsDiagnosisModel<T> model,
            Set<String>
                    inputs,
            List<T> splitPoints) {
        Map<String, Set<String>> varsOfConstraints = new HashMap<String, Set<String>>();
        Map<String, Integer> constraintArities = new HashMap<String, Integer>();

        // Get a reverse map
        Map<T, String> ctNames = model.getConstraintNames();
        Map<String, T> ctsByName = Utilities.reverse(ctNames);

        Set<String> constraintNames = new HashSet<String>();
        // Formula ct;
        // Iterator<Formula> ct_it = cpmodel.getConstraintIterator();
        // while (ct_it.hasNext()) {
        // ct = ct_it.next();

        for (T ct : model.getPossiblyFaultyStatements()) {
            String name = model.getConstraintName(ct);
            constraintNames.add(name);
            // System.out.println("Formula " + name);
            Set<Variable> allVarNamesOfConstraint = Utilities.getAllVariablesOfConstraint(ct);
            // System.out.println("Got the following variables: " + vars);
            Set<String> relevantVarNamesOfConstraint = new HashSet<String>();
            for (Variable var : allVarNamesOfConstraint) {
                if (!var.getName().equals(name) && !inputs.contains(var.getName())) {
                    relevantVarNamesOfConstraint.add(var.getName());
                }
            }
            varsOfConstraints.put(name, relevantVarNamesOfConstraint);
            // System.out.println("Vars of " + name);
            // System.out.println(relevantVarNamesOfConstraint);
            constraintArities.put(name, relevantVarNamesOfConstraint.size());
        }

        // System.out.println("Arities of constraints: ");
        // Let's sort them according to their arity
        constraintArities = Utilities.sortByValueDescending(constraintArities);
        // System.out.println(constraintArities);
        // Here's the list of constraints to group
        // Allocate them in a list
        List<String> sortedConstraints = new ArrayList<String>();
        // System.out.println("Got " + constraintNames.size() + " constraints");

        // Remember where we created new entries
        while (constraintNames.size() > 0) {
            // get the first of the list
            List<String> constraintKeys = new ArrayList<String>(constraintArities.keySet());
            String first = constraintKeys.get(0);
            // System.out.println("First: " + first);
            // put it in the list
            if (!sortedConstraints.contains(first)) {
                // System.out.println("Adding first");
                sortedConstraints.add(first);
            }

            // remove it from the the map
            constraintArities.remove(first);
            // Remove the name of the list to be worked on
            constraintNames.remove(first);
            // Get the constraints of the first
            Set<String> constraintsOfFirst = varsOfConstraints.get(first);
            // Put them all into the list, remove the names to be worked on and
            // reduce the counter for the rest in the
            // arities map.
            for (String c : constraintsOfFirst) {
                if (!sortedConstraints.contains(c)) {
                    sortedConstraints.add(c);
                }
                constraintNames.remove(c);
                for (String key : constraintArities.keySet()) {
                    Set<String> theVars = varsOfConstraints.get(key);
                    // System.out.println("Have to check vars of first: " + key
                    // + ": "+ theVars);
                    if (theVars.contains(key)) {
                        // System.out.println("Found var, have to reduce the aritiy of "
                        // + key);
                        Integer count = constraintArities.get(key);
                        if (count != null && count > 0) {
                            constraintArities.put(key, count - 1);
                        } else {
                            constraintArities.remove(key);
                        }
                    }
                }
            }
            // Remember where we added a new constraint
            // System.out.println("Split point after some elements at " +
            // (sortedConstraints.size()-1));
            String splitname = sortedConstraints.get(sortedConstraints.size() - 1);
            // System.out.println("Formula is " + splitname);
            if (!splitPoints.contains(ctsByName.get(splitname))) {
                splitPoints.add(ctsByName.get(splitname));
            }
            // System.out.println("Current list after iteration: " +
            // sortedConstraints + " (" + sortedConstraints.size() +
            // " elements)");
            constraintArities = Utilities.sortByValueDescending(constraintArities);
            // System.out.println("Current arities: " + constraintArities);
        }
        // System.out.println("Sorted list of size: " + sortedConstraints.size()
        // + "\n" + sortedConstraints);
        // System.out.println("\nSplit points: " +
        // Utilities.printConstraintList(splitPoints, model));

        // Get the list of possibly faulty constraints
        List<T> orderedPossiblyFaultyConstraints = new ArrayList<T>();

        for (String ctName : sortedConstraints) {
            orderedPossiblyFaultyConstraints.add(ctsByName.get(ctName));
        }
        return orderedPossiblyFaultyConstraints;
    }

    /**
     * Checks if the current bitset is part of the known ones
     *
     * @param all
     * @param one
     * @return
     */
    public static boolean checkSetIsSubsetOfKnown(List<BitSet> all, BitSet one) {
        boolean result = false;
        BitSet tmp = null;
        if (one.cardinality() == 0) {
            return false;
        }
        for (BitSet bs : all) {
            tmp = (BitSet) one.clone();
            tmp.and(bs);
            if (tmp.cardinality() == one.cardinality()) {
                return true;
            }
        }

        return result;
    }

    // public static createSolverFromCurrent

    /**
     * Checks if the current bitset a superset of a known
     *
     * @param all
     * @param one
     * @return
     */
    public static boolean checkIsSupersetOfKnown(List<BitSet> all, BitSet one) {
        boolean result = false;
        BitSet tmp = null;
        for (BitSet bs : all) {
            tmp = (BitSet) bs.clone();
            tmp.and(one);

            // System.out.println("tmp:" + tmp.cardinality());
            // System.out.println("one:" + one.cardinality());
            // System.out.println("bs:" + bs.cardinality());

            if (bs.cardinality() > 0 && tmp.cardinality() == bs.cardinality() && one.cardinality() >= bs
                    .cardinality()) {
                return true;
            }

            // bs: x 0 x 0
            // on: x 0 x x
        }

        return result;
    }

    /**
     * Create a bitset from the list with correct indices
     *
     * @param elements
     * @return
     */
    public static BitSet makeBitSet(List allElements, List subset) {
        BitSet result = new BitSet(allElements.size());
        int done = 0;
        int i = 0;
        for (Object elem : allElements) {
            if (subset.contains(elem)) {
                result.set(i);
                done++;
            }
            // There cannot be more
            if (done >= subset.size()) {
                return result;
            }
            i++;
        }

        return result;
    }

    /**
     * Sets the tests.diagnosis model.
     *
     * @param model
     */
    public void setDiagnosisModel(org.exquisite.core.model.DiagnosisModel model) {
        this.currentDiagnosisModel = model;
    }

    /**
     * Check consistency of constraints that constitute the test case.
     *
     * @return
     * @throws DomainSizeException
     */
    public boolean checkCorrectStatements() throws DomainSizeException {
        List<Formula> correctStatements = new ArrayList<Formula>(this.currentDiagnosisModel.getCorrectStatements());
        try {
            return isConsistent(correctStatements);
        } catch (DomainSizeException e) {
            throw e;
        }
    }

    /**
     * A method that determines if a given set of constraints is consistent
     *
     * @param constraints the set of constraints to be checked
     * @return true, if there is no nodeLabel, false otherwise
     * @throws DomainSizeException
     */
    public boolean isConsistent(List<Formula> constraints) throws DomainSizeException {

        // Simulate some more computation time
        start(TIMER_SOLVER);
        //long start = System.nanoTime();
        if (ARTIFICIAL_WAIT_TIME > 0) {
            while (getTimer(TIMER_SOLVER).getElapsedTime() / 1000000 < ARTIFICIAL_WAIT_TIME) {
                // do nothing..
            }
        }

        ISolver solver = createSolver();

        solver.createModel(this, constraints);

        // Call solve()
        try {
            // / Try to reuse things first

            // Otherwise, solve

            // System.out.println("Start solve..");
            boolean isFeasible = false;

            isFeasible = solver.isFeasible();

            //long time = System.nanoTime() - start;
            stop(TIMER_SOLVER);
            //if (diagnosisEngine != null) {
            //    diagnosisEngine.incrementSolverTime(time);
            //}
            // if (PRINT_SOLUTION_TIME) {
            // System.out.println("Solve needed " + time + " ms.");
            // }
            incrementCounter(COUNTER_CSP_SOLUTIONS);
//            if (this.diagnosisEngine != null && isFeasible) {
//                this.diagnosisEngine.incrementCSPSolutionCount();
//            }
            // System.out.println("QX solution: " + isFeasible);

            return isFeasible;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception here, " + e.getMessage());
            return false;
        }
        // return result;
    }

    public boolean isEntailed(List<Formula> constraints, Set<Formula> entailments) {
        // Simulate some more computation time
        start(TIMER_SOLVER);
        if (ARTIFICIAL_WAIT_TIME > 0) {
            while (getTimer(TIMER_SOLVER).getElapsedTime() / 1000000 < ARTIFICIAL_WAIT_TIME) {
                // do nothing..
            }
        }

        ISolver solver = createSolver();

        solver.createModel(this, constraints);

        // Call solve()
        try {
            // / Try to reuse things first

            // Otherwise, solve

            // System.out.println("Start solve..");
            boolean isFeasible = false;

            isFeasible = solver.isEntailed(entailments);

            //long time = System.nanoTime() - start;
            stop(TIMER_SOLVER);
//            if (diagnosisEngine != null) {
//                diagnosisEngine.incrementSolverTime(time);
//            }
            // if (PRINT_SOLUTION_TIME) {
            // System.out.println("Solve needed " + time + " ms.");
            // }
            incrementCounter(COUNTER_CSP_SOLUTIONS);
//            if (this.diagnosisEngine != null && isFeasible) {
//                this.diagnosisEngine.incrementCSPSolutionCount();
//            }
            // System.out.println("QX solution: " + isFeasible);

            return isFeasible;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception here, " + e.getMessage());
            return false;
        }
        // return result;
    }

    public Set<Formula> calculateEntailments(List<Formula> constraints) {
        ISolver solver = createSolver();

        solver.createModel(this, constraints);

        Set<Formula> entailments = solver.calculateEntailments();

        return entailments;
    }

    private ISolver createSolver() {
        ISolver solver = null;
        // -------------------------------------------------------------------
        switch (SOLVERTYPE) {
            case Choco2:
                solver = new Choco2Solver();
                break;
            case Choco3:
                solver = new Choco2ToChoco3Solver();
                break;
            case OWLAPI:
                solver = new OntologySolver();
                break;
        }
        return solver;
    }

    /**
     * A method that returns a unique code for a set of conflicts
     *
     * @param conflict the list of conflicts
     * @return a code
     */
    private String createStringRepOfConflict(List<Formula> conflict) {
        // System.out.println("Creating a nodeLabel code: ");
        StringBuffer result = new StringBuffer();
        List<String> ct = new ArrayList<String>();
        for (Formula c : conflict) {
            ct.add("" + c.hashCode());
        }
        Collections.sort(ct);
        for (String name : ct) {
            result.append(name);
        }
        return result.toString();
    }

    /**
     * Checks if the set of correct and potentially faulty statements is consistent
     *
     * @return true if consistent, otherwise return false
     * @throws DomainSizeException
     */
    public boolean checkConsistency() throws DomainSizeException {
        List<Formula> allStatements = new ArrayList<Formula>(this.currentDiagnosisModel.getCorrectStatements());
        allStatements.addAll(new ArrayList<Formula>(this.currentDiagnosisModel.getPossiblyFaultyStatements()));

        try {
            return isConsistent(allStatements);
        } catch (DomainSizeException e) {
            throw e;
        }
    }

    /**
     * The main quickxplain method. It uses the background from the constraint model is called otherwise only checkConsistency is called.
     *
     * @return a nodeLabel or an empty set if no nodeLabel was found.
     * @throws DomainSizeException
     */
    public List<Formula> findConflict() throws DomainSizeException {
        List<Formula> result = new ArrayList<Formula>();

        try {
            if (checkConsistency() == true) {
                Debug.msg("checkConsistency = true.");
                return result;
            } else {
                Debug.msg("checkConsistency = false.");
                if (this.currentDiagnosisModel.getPossiblyFaultyStatements().size() == 0) {
                    Debug.msg("No constraints to be analyzed");
                    return result;
                }

                // // DJ Remember the constraints
                // this.allConstraints.clear();
                // this.allConstraints.addAll(this.currentDiagnosisModel.getCorrectStatements());
                // this.allConstraints.addAll(this.currentDiagnosisModel.getPossiblyFaultyStatements());
                //
                // System.out.println("all constraints: " + this.allConstraints.size());

                result = qxplain(this.currentDiagnosisModel.getCorrectStatements(),
                        this.currentDiagnosisModel.getCorrectStatements(),
                        this.currentDiagnosisModel.getPossiblyFaultyStatements(), 0);
            }
            Debug.msg("Returning result of find nodeLabel of size: " + result.size());
            return result;
        } catch (DomainSizeException e) {
            throw e;
        }
    }

    /**
     * A variant of the API which returns multiple conflicts at a time In the default implementation, only one nodeLabel is returned.
     *
     * @return A list of conflicts. Only one in the case of the single-threaded version
     */
    public List<List<Formula>> findConflicts() throws DomainSizeException {
        List<List<Formula>> result = new ArrayList<List<Formula>>();

        List<Formula> conflict = findConflict();
        if (conflict.size() > 0) {
            result.add(new ArrayList<Formula>(conflict));
        }
        // Release this guy
        if (this.constraintListener != null) {
            // System.out.println("Done with QX - release the listener");
            this.constraintListener.release();
        }
        incrementCounter(COUNTER_SEARCH_CONFLICTS);
//        if (diagnosisEngine != null) {
//            diagnosisEngine.incrementSearchesForConflicts();
//        }

        return result;

    }

    public void findConflictsParallel(final ConflictCheckingResult<Formula> result,
                                      final SharedCollection<List<Formula>>
                                              knownConflicts)
            throws DomainSizeException {

        List<Formula> conflict = findConflict();
        if (conflict.size() > 0) {
            knownConflicts.addItemListNoDups(conflict);
            result.addConflict(conflict);
        }

        // Release this guy
        if (this.constraintListener != null) {
            // System.out.println("Done with QX - release the listener");
            this.constraintListener.release();
        }
        incrementCounter(COUNTER_SEARCH_CONFLICTS);

//        if (diagnosisEngine != null) {
//            diagnosisEngine.incrementSearchesForConflicts();
//        }

        synchronized (ConstraintsQuickXPlain.ContinuingSync) {
            finished = true;
            ConstraintsQuickXPlain.ContinuingSync.notifyAll();
        }
    }

    /**
     * Computes a nodeLabel for a given set of examples
     *
     * @param examples            the set of examples to check (all at the beginning)
     * @param constraintsToIgnore (any constraints to ignore)
     * @param createConflicts     (should conflicts be created or just consistency be tested)
     * @return a nodeLabel checking result object containing the list of found conflicts or null, if the current thread was interrupted
     * @throws DomainSizeException
     */
    public ConflictCheckingResult<Formula> checkExamples(List<Example<Formula>> examples,
                                                         List<Formula> constraintsToIgnore,
                                                         boolean createConflicts)
            throws DomainSizeException {

        // System.out.println("Checking examples.");
        // System.out.println("qx examples: " + examples);
        // System.out.println("ignore: " + constraintsToIgnore);
        // System.out.println("create conflicts: " + createConflicts);

        // long start = System.currentTimeMillis();
        incrementCounter(COUNTER_QXP_CALLS);
//        if (diagnosisEngine != null) {
//            diagnosisEngine.incrementQXPCalls();
//        }
        // Debug.msg("    Checking " + examples.size() +
        // " examples ignoring the following " +
        // Utilities.printConstraintList(constraintsToIgnore,
        // this.sessionData.getDiagnosisModel()));
        ConflictCheckingResult result = new ConflictCheckingResult();
        // Remember one detected nodeLabel
        List<Formula> detectedConflict = new ArrayList<Formula>();
        // System.out.println(examples.size());
        // go through all the positive examples
        for (Example<Formula> example : examples) {
            // clean up stuff
            // this.dagBuilder.resetCachedConflicts();
            this.currentExample = example;

            // Check if the thread HSDAG builder is running in has been
            // interrupted, eg if
            // tests.diagnosis calculation has been cancelled by the user.
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            // Make a copy of the tests.diagnosis model
            // add the example to the set of correct constraints
            // and remove constraints to ignore and any
            // constraints that are irrelevant (i.e. independent).
            ConstraintsDiagnosisModel<Formula> copiedModel = new ConstraintsDiagnosisModel<Formula>(
                    (ConstraintsDiagnosisModel<Formula>) sessionData.getDiagnosisModel());

            for (Formula constraint : example.constraints) {
                copiedModel.addCorrectFormula(constraint, example.constraintNames.get(constraint));

                // TS: We dont need this anymore
                // OLDCHOCO3
                // if (CHOCO3) {
                // // copy the formula info
                // FormulaInfo formulaInfo = example.choco3FormulaInfos.get(constraint);
                // if (formulaInfo == null) {
                // System.err.println("FormulaInfo is null");
                // }
                // copiedModel.getFormulaInfoOfConstraints().put(constraint, formulaInfo);
                // }
            }
            copiedModel.removeConstraintsToIgnore(new ArrayList<Formula>(example.irrelevantConstraints.values()));
            copiedModel.removeConstraintsToIgnore(constraintsToIgnore);

            // set qx with the new model copy.
            this.currentDiagnosisModel = copiedModel;

            // do the nodeLabel search for this particular positive example...
            try {
                // Debug.msg("createConflicts = " + createConflsicts);
                if (createConflicts) {

                    // if a nodeLabel has already been detected, then only check
                    // for consistency.
                    boolean alreadyOneConflictFound = detectedConflict.size() > 0;
                    // Debug.msg("alreadyOneConflictFound = " +
                    // alreadyOneConflictFound);
                    if (!alreadyOneConflictFound) {
                        // Try to find a nodeLabel through the example
                        Debug.msg("qx.findConflict() start call...");

                        // long start = System.currentTimeMillis();
                        List<List<Formula>> conflicts = this.findConflicts();
                        // List<Formula> nodeLabel = this.findConflict();
                        // long end = System.currentTimeMillis();
                        // if (ConstraintsQuickXPlain.PRINT_SOLUTION_TIME
                        // && nodeLabel.size() > 0) {
                        // System.out.println("Conflict detection time: "
                        // + (end - start) + ", size of nodeLabel: "
                        // + nodeLabel.size());
                        //
                        // }

                        // Debug.msg("qx.findConflict() end of call...");
                        // We got one
                        // Debug.msg("nodeLabel.size returned from qx = " +
                        // nodeLabel.size());
                        if (conflicts.size() > 0) {

                            for (List<Formula> conflict : conflicts) {
                                if (conflict.size() > 0) {
                                    // Remember it
                                    detectedConflict = conflict;
                                    Debug.msg("    Found conflicts: " + Utilities
                                            .printConstraintList(conflict, copiedModel) + "\n");
                                    List<Formula> conflictSet = new ArrayList<Formula>();
                                    // System.out.println("Adding a new nodeLabel " + nodeLabel);
                                    conflictSet.addAll(conflict);
                                    result.addConflict(conflictSet);
                                    // Remember the failed example
                                    if (!result.failedExamples.contains(example)) {
                                        result.failedExamples.add(example);
                                    }

                                }
                            }

                        } else { // Debugging only
                            // Debug.msg("    No nodeLabel found for example\n");
                        }

                    } else {
                        // Otherwise (we already have one nodeLabel, just check
                        // if the example go through
                        // Debug.msg("   Checking consistency of other example (we already have a nodeLabel...)");
                        boolean consistent = this.checkConsistency();
                        if (!consistent) {
                            // If we can't find a solution, we mark the example
                            // as failed without
                            // looking for a further nodeLabel.
                            result.failedExamples.add(example);
                        }
                    }

                } //
                else { // only check the consistency of the example
                    // Debug.msg("Checking example (at last level) - Only checking consistency");
                    boolean consistent = this.checkConsistency();
                    if (!consistent) {
                        // If we can't find a solution, we mark the example as
                        // failed without
                        // looking for a further nodeLabel.
                        result.failedExamples.add(example);
                    }
                }
            }
            // Catch and re-throw the exception
            catch (DomainSizeException e) {
                System.err.println("Problem with the domain size: " + e.getMessage());
                throw e;
            }
        }
        // System.out.println("done with example check, nb conflicts: " + result.conflicts.size());

        // long end = System.currentTimeMillis();
        // System.out.println("QXP took " + (end - start));
        return result;
    }

    public void checkExamplesParallel(final List<Example<Formula>> examples, final List<Formula> constraintsToIgnore,
                                      final boolean createConflicts,
                                      final ConflictCheckingResult result,
                                      final SharedCollection<List<Formula>> knownConflicts) {
        finished = false;
        currentThread = new Thread() {
            public void run() {
                synchronized (runningThreadsSync) {
                    runningThreads++;
                }

                try {

                    // System.out.println("qx examples: " + examples);
                    // System.out.println("ignore: " + constraintsToIgnore);
                    // System.out.println("create conflicts: " + createConflicts);

                    // long start = System.currentTimeMillis();
                    incrementCounter(COUNTER_QXP_CALLS);
//                    if (diagnosisEngine != null) {
//                        diagnosisEngine.incrementQXPCalls();
//                    }
                    // Debug.msg("    Checking " + examples.size() +
                    // " examples ignoring the following " +
                    // Utilities.printConstraintList(constraintsToIgnore,
                    // this.sessionData.getDiagnosisModel()));
                    // ConflictCheckingResult result = new ConflictCheckingResult();
                    // Remember one detected nodeLabel
                    List<Formula> detectedConflict = new ArrayList<Formula>();
                    // System.out.println(examples.size());
                    // go through all the positive examples
                    for (Example<Formula> example : examples) {
                        // clean up stuff
                        // this.dagBuilder.resetCachedConflicts();
                        currentExample = example;

                        // Check if the thread HSDAG builder is running in has been
                        // interrupted, eg if
                        // tests.diagnosis calculation has been cancelled by the user.
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }

                        // Make a copy of the tests.diagnosis model
                        // add the example to the set of correct constraints
                        // and remove constraints to ignore and any
                        // constraints that are irrelevant (i.e. independent).
                        ConstraintsDiagnosisModel<Formula> copiedModel = new ConstraintsDiagnosisModel<Formula>(
                                (ConstraintsDiagnosisModel<Formula>) sessionData.getDiagnosisModel());

                        for (Formula constraint : example.constraints) {
                            copiedModel.addCorrectFormula(constraint, example.constraintNames.get(constraint));

                            // TS: We dont need this anymore
                            // OLDCHOCO3
                            // if (CHOCO3) {
                            // // copy the formula info
                            // FormulaInfo formulaInfo = example.choco3FormulaInfos.get(constraint);
                            // if (formulaInfo == null) {
                            // System.err.println("FormulaInfo is null");
                            // }
                            // copiedModel.getFormulaInfoOfConstraints().put(constraint, formulaInfo);
                            // }
                        }
                        copiedModel.removeConstraintsToIgnore(
                                new ArrayList<Formula>(example.irrelevantConstraints.values()));
                        copiedModel.removeConstraintsToIgnore(constraintsToIgnore);

                        // set qx with the new model copy.
                        currentDiagnosisModel = copiedModel;

                        // do the nodeLabel search for this particular positive example...
                        try {
                            // Debug.msg("createConflicts = " + createConflsicts);
                            if (createConflicts) {

                                // if a nodeLabel has already been detected, then only check
                                // for consistency.
                                boolean alreadyOneConflictFound = result.conflictFound();
                                // Debug.msg("alreadyOneConflictFound = " +
                                // alreadyOneConflictFound);
                                if (!alreadyOneConflictFound) {
                                    // Try to find a nodeLabel through the example
                                    Debug.msg("qx.findConflict() start call...");

                                    // long start = System.currentTimeMillis();
                                    findConflictsParallel(result, knownConflicts);
                                    // List<Formula> nodeLabel = this.findConflict();
                                    // long end = System.currentTimeMillis();
                                    // if (ConstraintsQuickXPlain.PRINT_SOLUTION_TIME
                                    // && nodeLabel.size() > 0) {
                                    // System.out.println("Conflict detection time: "
                                    // + (end - start) + ", size of nodeLabel: "
                                    // + nodeLabel.size());
                                    //
                                    // }

                                    // Debug.msg("qx.findConflict() end of call...");
                                    // We got one
                                    // Debug.msg("nodeLabel.size returned from qx = " +
                                    // nodeLabel.size());
                                    /*
                                     * if (conflicts.size() > 0) {
									 *
									 * for (List<Formula> nodeLabel : conflicts) { if (nodeLabel.size() > 0) { // Remember it detectedConflict =
									 * nodeLabel; Debug.msg("    Found conflicts: " + Utilities.printConstraintList(nodeLabel, copiedModel) + "\n");
									 * List<Formula> conflictSet = new ArrayList<Formula>(); // System.out.println("Adding a new nodeLabel " +
									 * nodeLabel); conflictSet.addAll(nodeLabel); result.addConflict(conflictSet); // Remember the failed example if
									 * (!result.failedExamples.contains(example)) { result.failedExamples.add(example); }
									 *
									 * } }
									 *
									 *
									 * } else { // Debugging only // Debug.msg("    No nodeLabel found for example\n"); }
									 */

                                }/*
                                 * else { // Otherwise (we already have one nodeLabel, just check // if the example go through //
								 * Debug.msg("   Checking consistency of other example (we already have a nodeLabel...)"); boolean consistent =
								 * checkConsistency(); if (!consistent) { // If we can't find a solution, we mark the example // as failed without //
								 * looking for a further nodeLabel. result.failedExamples.add(example); } }
								 */

                            } //
                            else { // only check the consistency of the example
                                // Debug.msg("Checking example (at last level) - Only checking consistency");
                                boolean consistent = checkConsistency();
                                if (!consistent) {
                                    // If we can't find a solution, we mark the example as
                                    // failed without
                                    // looking for a further nodeLabel.
                                    result.failedExamples.add(example);
                                }
                            }
                        }
                        // Catch and re-throw the exception
                        catch (DomainSizeException e) {
                            System.err.println("Problem with the domain size: " + e.getMessage());
                            return;
                        }
                    }
                    // System.out.println("done with example check, nb conflicts: " + result.conflicts.size());

                    // long end = System.currentTimeMillis();
                    // System.out.println("QXP took " + (end - start));

                } finally {
                    synchronized (ConstraintsQuickXPlain.ContinuingSync) {
                        finished = true;

                        // If no nodeLabel was found, parent thread has to be awoken
                        if (!createConflicts || !result.conflictFound()) {
                            // Debug.syncMsg("MXP: No result found. Returning.");
                            synchronized (ConstraintsQuickXPlain.ContinuingSync) {
                                ConstraintsQuickXPlain.ContinuingSync.notifyAll();
                            }
                        }

                        // ConstraintsQuickXPlain.ContinuingSync.notifyAll();
                    }
                    synchronized (runningThreadsSync) {
                        runningThreads--;
                    }
                }
            }
        };

        threadPool.execute(currentThread);
    }

    public void cancel() {
        if (currentThread != null) {
            // Debug.syncMsg("Found nodeLabel to reuse. Stopping MergeXplain.");
            finished = true;
            cancelled = true;
            currentThread.interrupt();
        }
    }

    /**
     * The method determines a split position for a given list's length. The strategy to split given some split points is set in the class
     *
     * @param sp the list
     * @return the element in the middle (split position)
     */
    public int split(List<Formula> constraints) {
        // System.out.println("-- Split called: " +
        // Utilities.printConstraintList(constraints, model));

        // of there are only two elements, use the half of the elements
        if (constraints.size() == 2) {
            return 1;
        }
        return Math.round(constraints.size() / 2);
    }

    /**
     * The recursive quickxplain method
     *
     * @param background
     * @param constraints
     * @return a minimal nodeLabel
     * <p>
     * TODO: use preferences
     * @throws DomainSizeException
     */
    List<Formula> qxplain(List<Formula> background, List<Formula> delta, List<Formula> constraints, int level)
            throws DomainSizeException {

        boolean debug = Debug.QX_DEBUGGING;

        String indent = "";
        for (int i = 0; i < level; i++) {
            indent += "  ";
        }
        if (debug) {
            Debug.msg(indent + "Called at level:" + level);
            Debug.msg(indent + "bg:    " + Utilities.printConstraintList(background, this.currentDiagnosisModel));
            Debug.msg(indent + "delta: " + Utilities.printConstraintList(delta, this.currentDiagnosisModel));
            Debug.msg(indent + "cts:   " + Utilities.printConstraintList(constraints, this.currentDiagnosisModel));
        }

        // line 4
        boolean backgroundIsConsistent;
        try {
            backgroundIsConsistent = isConsistent(background);

            if (debug) {
                Debug.msg(indent + "Background consistency: " + backgroundIsConsistent);
            }

            if (delta.size() != 0 && !backgroundIsConsistent) {
                // System.err.println("FATAL in ConstraintsQuickXPlain: empty list in line 4: The background is inconsistent.");
                // Debug.msg("delta.size() = " + delta.size() +
                // "backgroundIsConsistent = " + backgroundIsConsistent);
                // System.exit(1);
                // TODO: Something wrong here. Was dead code.
                if (debug) {
                    Debug.msg(indent + "Delta and inconsistent background");
                }
                return new ArrayList<Formula>();
            }
            // line 5
            if (constraints.size() == 1) {
                // DJ: anyone listening?
                if (this.constraintListener != null && !this.constraintListener
                        .hasConstraints() && !this.constraintListener.isReleased()) {
                    this.constraintListener.setFoundConstraint(constraints.get(0));
                    // System.out.println("Ok, the found constraint is: " + constraints.get(0));
                } else {
                    // System.out.println("---> No listener here today?");
                }
                if (debug) {
                    Debug.msg(indent + "Last constraint.. ");
                }
                return new ArrayList<Formula>(constraints);
            }
            int split = this.split(constraints);

            List<Formula> c1 = new ArrayList<Formula>(constraints.subList(0, split));
            List<Formula> c2 = new ArrayList<Formula>(constraints.subList(split, constraints.size()));
            if (debug) {
                Debug.msg(indent + "c1: " + Utilities.printConstraintList(c1, this.currentDiagnosisModel));
                Debug.msg(indent + "c2: " + Utilities.printConstraintList(c2, this.currentDiagnosisModel));
            }

            // create the new background and add all of c1
            List<Formula> b1 = new ArrayList<Formula>(background);
            b1.addAll(c1);

            List<Formula> delta2 = qxplain(b1, new ArrayList<Formula>(c1), new ArrayList<Formula>(c2), level + 1);
            if (debug) {
                Debug.msg(indent + "d2: " + Utilities.printConstraintList(delta2, this.currentDiagnosisModel));
            }

            // create lists for second phase
            List<Formula> b2 = new ArrayList<Formula>(background);
            b2.addAll(delta2);

            List<Formula> delta1 = qxplain(b2, delta2, c1, level + 1);

            List<Formula> result = new ArrayList<Formula>(delta1);
            result.addAll(delta2);
            if (debug) {
                Debug.msg(indent + "result: " + Utilities.printConstraintList(result, this.currentDiagnosisModel));
            }
            return result;
        } catch (DomainSizeException e) {
            throw e;
        }
    }

    /**
     * Get the session info
     *
     * @return
     */
    public DiagnosisModel getSessionData() {
        return sessionData;
    }

    public enum SolverType {
        Choco2, Choco3, OWLAPI
    }

}