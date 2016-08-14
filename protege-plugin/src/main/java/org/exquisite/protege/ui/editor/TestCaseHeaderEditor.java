package org.exquisite.protege.ui.editor;

import org.exquisite.protege.model.EditorKitHook;
import org.exquisite.protege.model.error.AbstractErrorHandler;
import org.exquisite.protege.model.OntologyDiagnosisSearcher;
import org.exquisite.protege.ui.list.TestcaseListHeader;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Set;

public class TestCaseHeaderEditor extends AbstractEditor {

    private org.slf4j.Logger logger = LoggerFactory.getLogger(TestCaseHeaderEditor.class.getName());

    protected String getEditorTitle() {
        return "Add " + header.getEditorTitleSuffix();
    }

    private TestcaseListHeader header;

    public TestCaseHeaderEditor(TestcaseListHeader header, OWLEditorKit editorKit, EditorKitHook editorKitHook) {
        super(editorKit, editorKitHook);
        this.header = header;
    }

    protected void handleEditorConfirmed(Set<OWLLogicalAxiom> testcase) {
        OntologyDiagnosisSearcher diagnosisSearcher = getEditorKitHook().getActiveOntologyDiagnosisSearcher();

        diagnosisSearcher.doAddTestcase(testcase,header.getType(),new AbstractErrorHandler() {
            @Override
            public void errorHappened(OntologyDiagnosisSearcher.ErrorStatus error, Exception ex) {
                showMessageDialog(null, "This testcase is not compatible with already specified testcases and was " +
                        "therefore nod added. To resolve this problem you can try to delete testcase which are conflicting. ",
                        "Inconsistent Theory Exception", JOptionPane.ERROR_MESSAGE, null);
            }

        });
        logger.debug("OK");
    }


}
