package org.exquisite.core.solver;

import org.exquisite.core.DiagnosisException;
import org.exquisite.core.model.DiagnosisModel;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by kostya on 17-Mar-16.
 */
public class ExquisiteOWLReasoner extends AbstractSolver<OWLLogicalAxiom> {


    private final OWLReasoner reasoner;
    private InferenceType[] interenceTypes = new InferenceType[]{InferenceType.CLASS_HIERARCHY,
            InferenceType.CLASS_ASSERTIONS, InferenceType.DISJOINT_CLASSES, InferenceType.DIFFERENT_INDIVIDUALS,
            InferenceType.SAME_INDIVIDUAL};
    private HashSet<InferredAxiomGenerator<? extends OWLLogicalAxiom>> axiomGenerators = new HashSet<>();

    /**
     * Constructor of the reasoner which also generates a simple diagnosis model from the given ontology.
     *
     * @param ontology        for which a diagnosis model must be generated
     * @param reasonerFactory of a reasoner expressive enough to reason about consistency of the ontology
     * @throws OWLOntologyCreationException
     * @throws DiagnosisException
     */
    public ExquisiteOWLReasoner(OWLOntology ontology, OWLReasonerFactory reasonerFactory)
            throws OWLOntologyCreationException, DiagnosisException {
        super(generateDiagnosisModel(ontology, reasonerFactory));
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLOntology debugOntology = manager.createOntology(
                IRI.create("http://ainf.aau.at/debug_ontology#" + System.nanoTime()));

        this.reasoner = reasonerFactory.createReasoner(debugOntology);
    }

    /**
     * Default constructor of the reasoner
     *
     * @param dm              a diagnosis model
     * @param manager         ontology manager
     * @param reasonerFactory of a reasoner expressive enough to reason about consistency of the ontology
     * @throws OWLOntologyCreationException
     * @throws DiagnosisException
     */
    public ExquisiteOWLReasoner(DiagnosisModel<OWLLogicalAxiom> dm,
                                OWLOntologyManager manager, OWLReasonerFactory reasonerFactory)
            throws OWLOntologyCreationException, DiagnosisException {
        super(dm);
        OWLOntology debugOntology = manager.createOntology(
                IRI.create("http://ainf.aau.at/debug_ontology#" + System.nanoTime()));

        this.reasoner = reasonerFactory.createReasoner(debugOntology);
    }

    /**
     * Creates a default diagnosis model which possibly faulty statements comprise:
     * 1) all logical axioms, if the ontology is inconsistent, and
     * 2) only *star* modules of unsatisfiable classes it the ontology is consistent, but its terminology is incoherent.
     * Moreover, in case of an incoherent ontology, for each unsatisfiable class the correct statements of the model
     * comprise an assertion axiom, thus reducing the incoherency to inconsistency. The latter is usually easier for a
     * reasoner to compute.
     *
     * @param ontology        for which a diagnosis model must be generated
     * @param reasonerFactory of a reasoner expressive enough to reason about consistency of the ontology
     * @return a diagnosis model
     * @throws OWLOntologyCreationException
     * @throws DiagnosisException
     */
    public static DiagnosisModel<OWLLogicalAxiom> generateDiagnosisModel(OWLOntology ontology,
                                                                         OWLReasonerFactory reasonerFactory)
            throws OWLOntologyCreationException, DiagnosisException {
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        Set<OWLLogicalAxiom> possiblyFaulty = new HashSet<>(ontology.getLogicalAxiomCount());
        DiagnosisModel<OWLLogicalAxiom> dm = new DiagnosisModel<>();

        // in case the ontology is consistent we assume that the user wants to debug the incoherency.
        if (reasoner.isConsistent()) {
            reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
            Set<OWLClass> classes = reasoner.getBottomClassNode().getEntities();
            if (classes.size() > 1) {
                SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(manager,
                        ontology, ModuleType.STAR);

                Set<OWLEntity> entities = classes.stream()
                        .map(o -> (OWLEntity) o).collect(Collectors.toSet());

                possiblyFaulty = extractor.extract(entities).stream().map(o -> (OWLLogicalAxiom) o).
                        collect(Collectors.toSet());

                // instantiate unsat classes thus reducing the incoherency to inconsistency
                for (OWLClass cl : classes) {
                    OWLDataFactory df = manager.getOWLDataFactory();
                    OWLIndividual ind = df.getOWLAnonymousIndividual();
                    dm.getCorrectStatements().add(df.getOWLClassAssertionAxiom(cl, ind));
                }
            }
        } else
            possiblyFaulty.addAll(ontology.getLogicalAxioms());

        possiblyFaulty.removeAll(dm.getCorrectStatements());
        dm.setPossiblyFaultyStatements(possiblyFaulty);
        return dm;
    }

    /**
     * Sets types of entailements that must be computed by {@link #calculateEntailments()}
     *
     * @param infType entailment type
     */
    public void setEntailmentTypes(InferenceType... infType) {
        this.interenceTypes = infType;
        HashSet<InferredAxiomGenerator<? extends OWLLogicalAxiom>> types = new HashSet<>(12);

        for (InferenceType type : infType) {
            switch (type) {
                case DISJOINT_CLASSES:
                    types.add(new InferredDisjointClassesAxiomGenerator());
                    break;
                case CLASS_HIERARCHY:
                    types.add(new InferredSubClassAxiomGenerator());
                    types.add(new InferredEquivalentClassAxiomGenerator());
                    break;
                case OBJECT_PROPERTY_HIERARCHY:
                    types.add(new InferredSubObjectPropertyAxiomGenerator());
                    types.add(new InferredInverseObjectPropertiesAxiomGenerator());
                    types.add(new InferredObjectPropertyCharacteristicAxiomGenerator());
                    types.add(new InferredEquivalentObjectPropertyAxiomGenerator());
                    break;
                case DATA_PROPERTY_HIERARCHY:
                    types.add(new InferredDataPropertyCharacteristicAxiomGenerator());
                    types.add(new InferredEquivalentDataPropertiesAxiomGenerator());
                    types.add(new InferredSubDataPropertyAxiomGenerator());
                    break;
                case CLASS_ASSERTIONS:
                    types.add(new InferredClassAssertionAxiomGenerator());
                    break;
                case OBJECT_PROPERTY_ASSERTIONS:
                    types.add(new InferredPropertyAssertionGenerator());
                    break;
                case DATA_PROPERTY_ASSERTIONS:
                    types.add(new InferredPropertyAssertionGenerator());
                    break;
                case SAME_INDIVIDUAL: // same case as different individual
                case DIFFERENT_INDIVIDUALS:
                    types.add(new InferredPropertyAssertionGenerator());
                    types.add(new InferredClassAssertionAxiomGenerator());
                    break;
                default:
                    throw new RuntimeException("Unknown inference type!");
            }
        }
        this.axiomGenerators = types;
    }

    @Override
    /**
     * Uses underlying OWL reasoner to compute a set of entailments of formulas stored in the reasoner. The types of
     * computed entailments must be defined prior to calling this method using {@link #setEntailmentTypes(InferenceType...)}
     */
    protected Set<OWLLogicalAxiom> calculateEntailments() {

        this.reasoner.precomputeInferences(this.interenceTypes);
        OWLOntology ontology = this.reasoner.getRootOntology();

        return this.axiomGenerators.stream().flatMap(inferredAxiomGenerator ->
                inferredAxiomGenerator.createAxioms(ontology.getOWLOntologyManager().getOWLDataFactory(), reasoner).stream()).
                map(o -> (OWLLogicalAxiom) o).collect(Collectors.toSet());
    }

    @Override
    protected boolean isEntailed(Collection<OWLLogicalAxiom> entailments) {
        return this.reasoner.isEntailed(new HashSet<OWLAxiom>(entailments));
    }

    @Override
    protected OWLLogicalAxiom negate(OWLLogicalAxiom example) {
        throw new NotImplementedException();
    }

    @Override
    protected boolean supportsNegation() {
        return false;
    }

    @Override
    protected void sync(Set<OWLLogicalAxiom> addFormulas, Set<OWLLogicalAxiom> removeFormulas) {
        OWLOntology ontology = this.reasoner.getRootOntology();
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        manager.removeAxioms(ontology, removeFormulas);
        manager.addAxioms(ontology, addFormulas);
        this.reasoner.flush();
    }

    @Override
    protected boolean isConsistent() {
        return this.reasoner.isConsistent();
    }
}