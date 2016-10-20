package org.exquisite.protege;

import org.exquisite.core.DiagnosisException;
import org.exquisite.core.DiagnosisRuntimeException;
import org.exquisite.core.costestimators.CardinalityCostEstimator;
import org.exquisite.core.costestimators.ICostsEstimator;
import org.exquisite.core.costestimators.OWLAxiomKeywordCostsEstimator;
import org.exquisite.core.costestimators.SimpleCostsEstimator;
import org.exquisite.core.engines.AbstractDiagnosisEngine;
import org.exquisite.core.engines.IDiagnosisEngine;
import org.exquisite.core.model.Diagnosis;
import org.exquisite.core.model.DiagnosisModel;
import org.exquisite.core.query.Answer;
import org.exquisite.core.query.Query;
import org.exquisite.core.query.querycomputation.IQueryComputation;
import org.exquisite.core.query.querycomputation.heuristic.HeuristicConfiguration;
import org.exquisite.core.query.querycomputation.heuristic.HeuristicQueryComputation;
import org.exquisite.core.query.querycomputation.heuristic.partitionmeasures.*;
import org.exquisite.core.query.querycomputation.heuristic.sortcriteria.MinMaxFormulaWeights;
import org.exquisite.core.query.querycomputation.heuristic.sortcriteria.MinQueryCardinality;
import org.exquisite.core.query.querycomputation.heuristic.sortcriteria.MinSumFormulaWeights;
import org.exquisite.protege.model.DebuggingSession;
import org.exquisite.protege.model.listener.OntologyChangeListener;
import org.exquisite.protege.model.TestcasesModel;
import org.exquisite.protege.model.preferences.DiagnosisEngineFactory;
import org.exquisite.protege.model.preferences.DebuggerConfiguration;
import org.exquisite.protege.model.error.AbstractErrorHandler;
import org.exquisite.protege.model.error.QueryErrorHandler;
import org.exquisite.protege.model.event.EventType;
import org.exquisite.protege.model.event.OntologyDebuggerChangeEvent;
import org.exquisite.protege.model.exception.DiagnosisModelCreationException;
import org.exquisite.protege.model.state.PagingState;
import org.exquisite.protege.ui.dialog.DebuggingDialog;
import org.exquisite.protege.ui.list.AxiomListItem;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.inference.OWLReasonerManager;
import org.protege.editor.owl.model.inference.ReasonerStatus;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntax;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.reasoner.ReasonerInternalException;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.exquisite.protege.Debugger.ErrorStatus.NO_ERROR;
import static org.exquisite.protege.Debugger.ErrorStatus.SOLVER_EXCEPTION;

public class Debugger {

    private org.slf4j.Logger logger = LoggerFactory.getLogger(Debugger.class.getCanonicalName());

    public enum TestcaseType {ORIGINAL_ENTAILED_TC, ORIGINAL_NON_ENTAILED_TC, ACQUIRED_ENTAILED_TC, ACQUIRED_NON_ENTAILED_TC}

    public enum ErrorStatus {NO_CONFLICT_EXCEPTION, SOLVER_EXCEPTION, INCONSISTENT_THEORY_EXCEPTION,
        NO_QUERY, ONLY_ONE_DIAG, NO_ERROR, UNKNOWN_RM, UNKNOWN_SORTCRITERION}

    public enum QuerySearchStatus { IDLE, SEARCH_DIAG, GENERATING_QUERY, MINIMZE_QUERY, ASKING_QUERY }

    /**
     * When calling doStopSession() use this flag to inform the user, why the debugging session has stopped.
     */
    public enum SessionStopReason { PREFERENCES_CHANGED, INVOKED_BY_USER, CONSISTENT_ONTOLOGY,
        ERROR_OCCURRED, DEBUGGER_RESET, REASONER_CHANGED, ONTOLOGY_RELOADED, ONTOLOGY_CHANGED, SESSION_RESTARTED,
        DEBUGGING_ONTOLOGY_SELECTED
    };

    private DebuggingSession debuggingSession;

    private QuerySearchStatus querySearchStatus = QuerySearchStatus.IDLE;

    private DiagnosisEngineFactory diagnosisEngineFactory;

    private ErrorStatus errorStatus = NO_ERROR;

    private Set<ChangeListener> changeListeners = new LinkedHashSet<>();

    private Set<Diagnosis<OWLLogicalAxiom>> diagnoses = new HashSet<>();

    private Set<Diagnosis<OWLLogicalAxiom>> previousDiagnoses = new HashSet<>();

    private Set<Set<OWLLogicalAxiom>> conflicts = new HashSet<>();

    private Answer<OWLLogicalAxiom> answer = new Answer<>();

    private Answer<OWLLogicalAxiom> previousAnswer = new Answer<>();

    private List<Answer<OWLLogicalAxiom>> queryHistory = new LinkedList<>();

    private IQueryComputation<OWLLogicalAxiom> qc = null;

    private final OWLModelManager modelManager;

    private final OWLReasonerManager reasonerManager;

    private Double cautiousParameter, previousCautiousParameter;

    private TestcasesModel testcases;

    private DiagnosisModel<OWLLogicalAxiom> diagnosisModel;

    // state information for the paging mode of the InputOntologyView
    private PagingState pagingState;

    /**
     * Singleton instance of an listener to ontology changes. Registered in the EditorKitHook.
     */
    private OntologyChangeListener ontologyChangeListener;

    public Debugger(OWLEditorKit editorKit) {
        modelManager = editorKit.getModelManager();
        reasonerManager = modelManager.getOWLReasonerManager();
        diagnosisEngineFactory = new DiagnosisEngineFactory(this, modelManager.getActiveOntology(), reasonerManager);
        debuggingSession = new DebuggingSession();
        this.testcases = new TestcasesModel(this);
        this.diagnosisModel = new DiagnosisModel<>();
        this.pagingState = new PagingState();
    }

    public void createNewDiagnosisModel() throws DiagnosisModelCreationException {
        this.diagnosisModel = diagnosisEngineFactory.createDiagnosisModel();
    }

    public PagingState getPagingState() {
        return pagingState;
    }

    public DiagnosisModel<OWLLogicalAxiom> getDiagnosisModel() {
        return diagnosisModel;
    }

    public DiagnosisEngineFactory getDiagnosisEngineFactory() {
        return diagnosisEngineFactory;
    }

    public Query<OWLLogicalAxiom> getActualQuery() {
        return answer.query;
    }

    public Set<Diagnosis<OWLLogicalAxiom>> getDiagnoses() {
        return diagnoses;
    }

    public Set<Set<OWLLogicalAxiom>> getConflicts() {
        return conflicts;
    }

    private ErrorStatus getErrorStatus() {
        return errorStatus;
    }

    public List<Answer<OWLLogicalAxiom>> getQueryHistory() {
        return queryHistory;
    }

    public QuerySearchStatus getQuerySearchStatus() {
        return querySearchStatus;
    }

    public boolean isMarkedEntailed(OWLLogicalAxiom axiom) {
        return answer.positive.contains(axiom);
    }

    public boolean isMarkedNonEntailed(OWLLogicalAxiom axiom) {
        return answer.negative.contains(axiom);
    }

    public int sizeOfEntailedAndNonEntailedAxioms() {
        return answer.positive.size() + answer.negative.size();
    }

    public boolean isSessionRunning() {
        return debuggingSession.getState() == DebuggingSession.State.STARTED;
    }

    public boolean isSessionStopped() {
        return debuggingSession.getState() == DebuggingSession.State.STOPPED;
    }

    public TestcasesModel getTestcases() {
        return this.testcases;
    }

    void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners(OntologyDebuggerChangeEvent e) {
        for (ChangeListener listener : changeListeners)
            listener.stateChanged(e);
    }

    OntologyChangeListener getOntologyChangeListener() {
        if (this.ontologyChangeListener == null)
            this.ontologyChangeListener = new OntologyChangeListener(this);
        return this.ontologyChangeListener;
    }

    /**
     * Starts a new debugging session. This step initiates the search for a diagnosis and the presentation of a
     * query.
     *
     * @param errorHandler An error handler.
     */
    public void doStartDebugging(QueryErrorHandler errorHandler) {
        if (!isSessionRunning()) {
            if (reasonerManager.getReasonerStatus() == ReasonerStatus.NO_REASONER_FACTORY_CHOSEN) {
                DebuggingDialog.showNoReasonerSelectedMessage();
                return;
            }

            logger.info("------------------------ Starting new Debugging Session ------------------------");

            diagnosisEngineFactory.reset();                 // create new engine
            debuggingSession.startSession();                // start session
            notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.SESSION_STATE_CHANGED));
            doCalculateDiagnosesAndGetQuery(errorHandler);  // calculate diagnoses and compute query

            switch (diagnoses.size()) {
                case 0:
                    doStopDebugging(SessionStopReason.CONSISTENT_ONTOLOGY);
                    if (diagnosisEngineFactory.getSearchConfiguration().reduceIncoherency)
                        DebuggingDialog.showCoherentOntologyMessage(getDiagnosisEngineFactory().getOntology());
                    else
                        DebuggingDialog.showConsistentOntologyMessage(getDiagnosisEngineFactory().getOntology());
                    break;
                case 1:
                    notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.DIAGNOSIS_FOUND));
                    DebuggingDialog.showDiagnosisFoundMessage(diagnoses, getDiagnosisEngineFactory().getOntology());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Stops the current running debugging session and restarts with another one.
     *
     * @param errorHandler An error handler.
     */
    public void doRestartDebugging(QueryErrorHandler errorHandler) {
        doStopDebugging(SessionStopReason.SESSION_RESTARTED);
        doStartDebugging(errorHandler);
    }

    /**
     * Stop diagnosis session -> reset engine, diagnoses, conflicts, queries and history.
     */
    public void doStopDebugging(SessionStopReason reason) {
        if (isSessionRunning()) {
            logger.info("-------------------------- Stopping Debugging Session --------------------------");

            try {
                diagnosisEngineFactory.dispose();
            } catch (RuntimeException rex) {
                logger.error("A runtime exception occurred while disposing diagnosis engine", rex);
            }

            this.diagnoses.clear();                                     // reset diagnoses, conflicts
            this.conflicts.clear();

            this.previousDiagnoses.clear();

            resetQuery();                                               // reset queries
            resetQueryHistory();                                        // reset history
            testcases.reset();
            debuggingSession.stopSession();                             // stop session

            this.cautiousParameter = null;
            this.previousCautiousParameter = null;


            // we need to create a new diagnosis model when ...
            switch (reason) {
                case PREFERENCES_CHANGED: // ... switching between coherency/consistency and consistency-only check
                case ONTOLOGY_RELOADED:   // ... the ontology has been reloaded
                case REASONER_CHANGED:    // ... the reasoner has been changed
                    try {
                        createNewDiagnosisModel();
                    } catch (DiagnosisModelCreationException e) {
                        logger.error("An error occurred during creation of a new diagnosis model for " +
                                DebuggingDialog.getOntologyName(getDiagnosisEngineFactory().getOntology()), e);
                    }
                    break;

            }

            notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.SESSION_STATE_CHANGED));

            // notify the user why session has stopped
            String reasonMsg = null;
            switch (reason) {
                case ERROR_OCCURRED:
                    reasonMsg = "an unexpected error occured!";
                    break;
                case ONTOLOGY_RELOADED:
                    reasonMsg = "the ontology " + DebuggingDialog.getOntologyName(diagnosisEngineFactory.getOntology()) + " has been reloaded.";
                    break;
                case ONTOLOGY_CHANGED:
                    reasonMsg = "the ontology " + DebuggingDialog.getOntologyName(diagnosisEngineFactory.getOntology()) + " has been modified!";
                    break;
                case PREFERENCES_CHANGED:
                    reasonMsg = "the preferences have been modified!";
                    break;
                case REASONER_CHANGED:
                    reasonMsg = "the reasoner has been changed!";
                    break;
                case DEBUGGING_ONTOLOGY_SELECTED:
                    reasonMsg = "an anonymous debugging ontology has been selected!";
                    break;
                case INVOKED_BY_USER:     // no message necessary
                case DEBUGGER_RESET:      // no message necessary
                case CONSISTENT_ONTOLOGY: // no message necessary
                case SESSION_RESTARTED:   // no message necessary
                    break;
                default:
                    reasonMsg = reason.toString();
                    logger.warn("unknown reason: " + reason);
            }

            if (reasonMsg != null)
                DebuggingDialog.showDebuggingSessionStoppedMessage(diagnosisEngineFactory.getOntology(), reasonMsg);
        }
    }

    /**
     * Reset debugger ->  reset test cases + doStopDebugging()
     */
    public void doResetDebugger() {
        doStopDebugging(SessionStopReason.DEBUGGER_RESET);
    }

    /**
     * Reset the diagnoses engine and doFullReset().
     */
    void doReload() {
        doStopDebugging(SessionStopReason.ONTOLOGY_RELOADED);
    }

    private void resetQuery() {
        this.previousAnswer = this.answer;
        this.answer = new Answer<>();

        this.querySearchStatus = QuerySearchStatus.IDLE;
        if (this.qc!=null) qc.reset();
    }

    private void resetQueryHistory() {
        this.queryHistory.clear();
    }

    /**
     * Moves a set of correct axioms to the set of possibly faulty axioms in the diagnosis model.
     *
     * @param selectedCorrectAxioms The selected, yet correct, axioms that shall become possibly faulty.
     */
    public void moveToPossiblyFaultyAxioms(List<AxiomListItem> selectedCorrectAxioms) {
        logger.debug("moving " + selectedCorrectAxioms + " from background to possiblyFaultyAxioms");
        List<OWLLogicalAxiom> axioms = selectedCorrectAxioms.stream().map(AxiomListItem::getAxiom).collect(Collectors.toList());
        diagnosisModel.getCorrectFormulas().removeAll(axioms);
        diagnosisModel.getPossiblyFaultyFormulas().addAll(axioms);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.INPUT_ONTOLOGY_CHANGED));
    }

    /**
     * Moves a list of possibly faulty axioms to the set of correct axioms in the diagnosis model.
     *
     * @param selectedPossiblyFaultyAxioms The selected, yet possibly faulty, axioms that shall become correct axioms.
     */
    public void moveToToCorrectAxioms(List<AxiomListItem> selectedPossiblyFaultyAxioms) {
        logger.debug("moving " + selectedPossiblyFaultyAxioms + " from possiblyFaultyAxioms to correctAxioms");
        List<OWLLogicalAxiom> axioms = selectedPossiblyFaultyAxioms.stream().map(AxiomListItem::getAxiom).collect(Collectors.toList());
        diagnosisModel.getPossiblyFaultyFormulas().removeAll(axioms);
        diagnosisModel.getCorrectFormulas().addAll(axioms);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.INPUT_ONTOLOGY_CHANGED));
    }

    public void doRemoveTestcase(Set<OWLLogicalAxiom> axioms, TestcaseType type) {
        this.testcases.removeTestcase(axioms, type);

        // We also have to synchronize the query history (if the user removed the test case from the acquired test cases)
        if (axioms.size() == 1 && (type == TestcaseType.ACQUIRED_ENTAILED_TC || type == TestcaseType.ACQUIRED_NON_ENTAILED_TC)) {
            // It is possible that the user used the remove button in the acquired test cases view
            // Now search for the answer in the history
            Answer<OWLLogicalAxiom> answer = null;
            Iterator<Answer<OWLLogicalAxiom>> it = this.queryHistory.iterator();
            while (it.hasNext() && answer == null) {
                Answer<OWLLogicalAxiom> anAnswer = it.next();
                switch (type) {
                    case ACQUIRED_ENTAILED_TC:
                        if (anAnswer.positive.removeAll(axioms)) // note that only one axiom is removed
                            answer = anAnswer;
                        break;
                    case ACQUIRED_NON_ENTAILED_TC:
                        if (anAnswer.negative.removeAll(axioms)) // note that only one axiom is removed
                            answer = anAnswer;
                        break;
                    default:
                        throw new DiagnosisRuntimeException("Unexpected test case type used to clean up history: " + type);
                }
            }

            // we found an answer in the history we have to remove from history if the answer is empty
            if (answer != null && answer.positive.isEmpty() && answer.negative.isEmpty()) {
                this.queryHistory.remove(answer);
                logger.debug("Removed from history: " + answer);
            }

        }

        if (isSessionRunning())
            doCalculateDiagnosesAndGetQuery(new QueryErrorHandler());
    }

    public void doAddTestcase(Set<OWLLogicalAxiom> axioms, TestcaseType type, AbstractErrorHandler errorHandler) {
        this.testcases.addTestcase(axioms, type);
        if(!getErrorStatus().equals(NO_ERROR))
            errorHandler.errorHappened(getErrorStatus());
    }

    /**
     * Check if the set of new acquired test cases is empty. This check is called by the ResetDebuggerAction.
     *
     * @return <code>true</code> if there are no acquired test cases yet, otherwise <code>false</code>.
     */
    public boolean areTestcasesEmpty() {
        return testcases.areTestcasesEmpty();
    }

    public void updateConfig(DebuggerConfiguration newConfiguration) {
        getDiagnosisEngineFactory().updateConfig(newConfiguration);
    }

    public void doAddAxiomsMarkedEntailed(OWLLogicalAxiom axiom) {
        this.answer.positive.add(axiom);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.QUERY_ANSWER_EVENT));
    }

    public void doAddAxiomsMarkedNonEntailed(OWLLogicalAxiom axiom) {
        this.answer.negative.add(axiom);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.QUERY_ANSWER_EVENT));
    }

    public void doRemoveAxiomsMarkedEntailed(OWLLogicalAxiom axiom) {
        this.answer.positive.remove(axiom);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.QUERY_ANSWER_EVENT));
    }

    public void doRemoveAxiomsMarkedNonEntailed(OWLLogicalAxiom axiom) {
        this.answer.negative.remove(axiom);
        notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.QUERY_ANSWER_EVENT));
    }

    /**
     * Commit the response from the expert, calculate the new diagnoses and get the new queries.
     *
     * @param errorHandler An error handler.
     */
    public void doCommitAndGetNewQuery(QueryErrorHandler errorHandler) {
        this.previousDiagnoses = new HashSet<>(this.diagnoses);
        doCommitQuery();
        boolean noErrorOccur = doCalculateDiagnoses(errorHandler);
        if (noErrorOccur) {
            switch (diagnoses.size()) {
                case 0:
                    doStopDebugging(SessionStopReason.CONSISTENT_ONTOLOGY);
                    if (diagnosisEngineFactory.getSearchConfiguration().reduceIncoherency)
                        DebuggingDialog.showCoherentOntologyMessage(getDiagnosisEngineFactory().getOntology());
                    else
                        DebuggingDialog.showConsistentOntologyMessage(getDiagnosisEngineFactory().getOntology());
                    break;
                case 1:
                    notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.DIAGNOSIS_FOUND));
                    DebuggingDialog.showDiagnosisFoundMessage(diagnoses, getDiagnosisEngineFactory().getOntology());
                    break;
                default:
                    doGetQuery(errorHandler);
                    break;
            }
        } else {
            doStopDebugging(SessionStopReason.ERROR_OCCURRED);
        }
    }

    public void doGetAlternativeQuery() {
        JOptionPane.showMessageDialog(null, "The function is not implemented yet", "Not Implemented", JOptionPane.INFORMATION_MESSAGE);
    }

    private void doCommitQuery() {
        if (!this.answer.positive.isEmpty()) {
            doAddTestcase(this.answer.positive, TestcaseType.ACQUIRED_ENTAILED_TC, new QueryErrorHandler());
        }
        if (!this.answer.negative.isEmpty()) {
            doAddTestcase(this.answer.negative, TestcaseType.ACQUIRED_NON_ENTAILED_TC, new QueryErrorHandler());
        }

        this.queryHistory.add(this.answer);

        resetQuery();
    }

    /**
     * Calculate the diagnoses.
     *
     * @param errorHandler An error handler
     * @return <code>true</code> if no error occurred, otherwise <code>false</code>.
     */
    private boolean doCalculateDiagnoses(AbstractErrorHandler errorHandler) {
        final IDiagnosisEngine<OWLLogicalAxiom> diagnosisEngine = diagnosisEngineFactory.getDiagnosisEngine();

        this.diagnoses.clear();
        this.conflicts.clear();

        diagnosisEngine.resetEngine();

        // set the cost estimator
        if (diagnosisEngine instanceof AbstractDiagnosisEngine) {
            final AbstractDiagnosisEngine abstractDiagnosisEngine = ((AbstractDiagnosisEngine) diagnosisEngine);
            final ICostsEstimator currentCostsEstimator = abstractDiagnosisEngine.getCostsEstimator();
            switch (diagnosisEngineFactory.getSearchConfiguration().costEstimator) {
                case EQUAL:
                    if (! (currentCostsEstimator instanceof SimpleCostsEstimator))
                        abstractDiagnosisEngine.setCostsEstimator(new SimpleCostsEstimator());
                    break;
                case CARD:
                    if (! (currentCostsEstimator instanceof CardinalityCostEstimator))
                        abstractDiagnosisEngine.setCostsEstimator(new CardinalityCostEstimator());
                    break;
                case SYNTAX:
                    if (! (currentCostsEstimator instanceof OWLAxiomKeywordCostsEstimator))
                        abstractDiagnosisEngine.setCostsEstimator(new OWLAxiomKeywordCostsEstimator(getDiagnosisModel()));
                    break;
                default:
                    logger.warn("Cost estimator " + diagnosisEngineFactory.getSearchConfiguration().costEstimator + " is unknown. Using " + currentCostsEstimator + " as cost estimator.");
            };

        }

        // set the maximum number of diagnoses to be calculated
        final int n = diagnosisEngineFactory.getSearchConfiguration().numOfLeadingDiags;
        diagnosisEngine.setMaxNumberOfDiagnoses(n);
        try {
            logger.debug("Calculating {} diagnoses ...", n);
            diagnoses.addAll(diagnosisEngine.calculateDiagnoses());
            conflicts.addAll(diagnosisEngine.getConflicts());
            logger.debug("Found {} diagnoses.", diagnoses.size());
            logger.debug("Diagnoses: " + diagnoses.toString());
            logger.debug("Diagnoses are based on {} conflicts", conflicts.size());
            logger.debug("Conflicts: " + conflicts.toString());
            return true;
        } catch (DiagnosisException | ReasonerInternalException e) {
            errorHandler.errorHappened(SOLVER_EXCEPTION, e);
            logger.error("Exception occurred while calculating diagnoses.", e);
            diagnoses.clear(); // reset diagnoses and conflicts
            conflicts.clear();
            return false;
        }

    }

    /**
     * First calculate diagnoses and compute queries afterwards.
     *
     * @param errorHandler The error handler.
     */
    private void doCalculateDiagnosesAndGetQuery(QueryErrorHandler errorHandler) {
        boolean noErrorOccurred = doCalculateDiagnoses(errorHandler);
        if (noErrorOccurred) {
            if (diagnoses.size() > 1)
                doGetQuery(errorHandler);
        } else {
            doStopDebugging(SessionStopReason.ERROR_OCCURRED);
        }
    }

    /**
     * The main method to calculate a new query.
     * The calling method has to check that the size of diagnoses is at least 2.
     *
     * @param errorHandler An error handler.
     */
    private void doGetQuery(QueryErrorHandler errorHandler) {

        final IDiagnosisEngine<OWLLogicalAxiom> diagnosisEngine = diagnosisEngineFactory.getDiagnosisEngine();
        final DebuggerConfiguration preference = diagnosisEngineFactory.getSearchConfiguration();
        HeuristicConfiguration<OWLLogicalAxiom> heuristicConfiguration = new HeuristicConfiguration<>((AbstractDiagnosisEngine)diagnosisEngine);

        heuristicConfiguration.setMinQueries(1);
        heuristicConfiguration.setMaxQueries(1);

        heuristicConfiguration.setEnrichQueries(preference.enrichQuery);
        switch (preference.rm) {
            case ENT:
                heuristicConfiguration.setRm(
                        new EntropyBasedMeasure<>(new BigDecimal(String.valueOf(preference.entropyThreshold))));
                break;
            case SPL:
                heuristicConfiguration.setRm(
                        new SplitInHalfMeasure<>(new BigDecimal(String.valueOf(preference.entropyThreshold))));
                break;
            case RIO:

                heuristicConfiguration.setRm(
                        new RiskOptimizationMeasure<>(new BigDecimal(String.valueOf(preference.entropyThreshold)),
                        new BigDecimal(String.valueOf(preference.cardinalityThreshold)),
                        new BigDecimal(String.valueOf(updateCautiousParameter(preference.cautiousParameter)))));
                break;
            case KL:
                heuristicConfiguration.setRm(
                        new KLMeasure(new BigDecimal(String.valueOf(preference.entropyThreshold))));
                break;
            case EMCb:
                heuristicConfiguration.setRm(new EMCbMeasure());
                break;
            case BME:
                heuristicConfiguration.setRm(
                        new BMEMeasure(new BigDecimal(String.valueOf(preference.cardinalityThreshold))));
                break;
            default:
                errorHandler.errorHappened(ErrorStatus.UNKNOWN_RM);
        }

        switch (preference.sortCriterion) {
            case MINCARD:
                heuristicConfiguration.setSortCriterion(new MinQueryCardinality<>());
                break;
            case MINSUM:
                heuristicConfiguration.setSortCriterion(new MinSumFormulaWeights<>(new HashMap<>())); // TODO find a method to automatically calculate formula weights
                break;
            case MINMAX:
                heuristicConfiguration.setSortCriterion(new MinMaxFormulaWeights<>(new HashMap<>())); // TODO find a method to automatically calculate formula weights
                break;
            default:
                errorHandler.errorHappened(ErrorStatus.UNKNOWN_SORTCRITERION);
        }

        qc = new HeuristicQueryComputation<>(heuristicConfiguration);

        try {
            qc.initialize(diagnoses);

            if ( qc.hasNext()) {
                this.answer.query = qc.next();
                logger.debug("query configuration: " + qc);
            } else {
                errorHandler.errorHappened(ErrorStatus.NO_QUERY);
                errorStatus = ErrorStatus.NO_QUERY;
                resetQuery();
            }
            querySearchStatus = QuerySearchStatus.ASKING_QUERY;

        } catch (DiagnosisException e) {
            errorHandler.errorHappened(ErrorStatus.SOLVER_EXCEPTION);
        } finally {
            notifyListeners(new OntologyDebuggerChangeEvent(this, EventType.QUERY_CALCULATED));
        }
    }

    /**
     * Method that learns the cautious parameter for RIO for each new query generation.
     *
     * @param preferenceCautiousParameter the unmodifiable cautious parameter from the preferences.
     */
    private Double updateCautiousParameter(Double preferenceCautiousParameter) {
        if (previousDiagnoses.size() > 0) {
            previousCautiousParameter = cautiousParameter;
            final double epsilon = 0.25;
            final double intervalLength = (Math.floor((double)previousDiagnoses.size() / 2d) - 1d) / (double)previousDiagnoses.size();

            logger.debug("epsilon: " + epsilon);
            logger.debug("intervalLength: " + intervalLength);
            logger.debug("old cautiousParameter: " + previousCautiousParameter);
            logger.debug("previousDiagnoses#: " + previousDiagnoses.size());
            logger.debug("diagnoses#: " + diagnoses.size());

            double eliminationRate = calculateEliminationRate();
            logger.debug("eliminationRate: " + eliminationRate);

            double adjustmentFactor = ((Math.floor((double)previousDiagnoses.size() / 2.0 - epsilon) + 0.5) / (double)previousDiagnoses.size()) - eliminationRate;
            logger.debug("adjustmentFactor: " + adjustmentFactor);

            double adjustedCautiousParameter = previousCautiousParameter + (2 * intervalLength * adjustmentFactor);
            final double minCautiousValue = 1.0 / (double) diagnoses.size();
            final double maxCautiousValue = Math.floor((double)diagnoses.size() / 2.0) / (double)diagnoses.size();

            logger.debug(adjustedCautiousParameter + " in [" + minCautiousValue + "," + maxCautiousValue + "] ?");

            if (adjustedCautiousParameter < minCautiousValue)
                adjustedCautiousParameter = minCautiousValue;
            if (adjustedCautiousParameter > maxCautiousValue)
                adjustedCautiousParameter = maxCautiousValue;

            cautiousParameter = adjustedCautiousParameter;
            logger.debug("NEW cautiousParameter: " + cautiousParameter);

        } else {
            cautiousParameter = preferenceCautiousParameter;
            previousCautiousParameter = null;
        }
        return cautiousParameter;
    }

    private Double calculateEliminationRate() {
        Double eliminationRate;
        if (this.previousAnswer.negative.size() >= 1)
            // this calculates the lower bound of the actual elimination rate
            eliminationRate = (double)this.previousAnswer.query.qPartition.dx.size() / (double)previousDiagnoses.size();
        else if (this.previousAnswer.positive.size() == this.previousAnswer.query.formulas.size())
            // this calculates the lower bound of the actual elimination rate
            eliminationRate = (double)this.previousAnswer.query.qPartition.dnx.size() / (double)previousDiagnoses.size();
        else
            // an approximation of the elimination rate (saves the costs to expensive reasoner calls)
            eliminationRate = ( (double)this.previousAnswer.query.qPartition.dnx.size() / (double)previousDiagnoses.size() ) * this.previousAnswer.positive.size() / this.previousAnswer.query.formulas.size();

        return eliminationRate;
    }

    public void doRemoveQueryHistoryAnswer(Answer<OWLLogicalAxiom> answer) {
        queryHistory.remove(answer);
        // TODO FIX THIS DOUBLE CALCULATION
        doRemoveTestcase(answer.positive, TestcaseType.ACQUIRED_ENTAILED_TC);
        doRemoveTestcase(answer.negative, TestcaseType.ACQUIRED_NON_ENTAILED_TC);
    }

    public void updateProbab(Map<ManchesterOWLSyntax, BigDecimal> map) {
        // TODO
        /*
        CostsEstimator<OWLLogicalAxiom> estimator = getSearchCreator().getSearch().getCostsEstimator();
        ((OWLAxiomKeywordCostsEstimator)estimator).updateKeywordProb(map);
        */
    }

    public void dispose(EditorKitHook editorKitHook) {
        removeChangeListener(editorKitHook);
        this.diagnosisEngineFactory.dispose();
    }

    @Override
    public String toString() {
        //return "OntologyDebugger{" + "engine=" + diagnosisEngineFactory.getDiagnosisEngine() +
        return "OntologyDebugger{" + "ontology=" + diagnosisEngineFactory.getOntology() +
                "reasonerManager=" + diagnosisEngineFactory.getReasonerManager() +
                '}';
    }


}