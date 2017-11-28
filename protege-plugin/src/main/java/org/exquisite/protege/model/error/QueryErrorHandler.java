package org.exquisite.protege.model.error;

import static org.exquisite.protege.Debugger.ErrorStatus;

import javax.swing.*;

public class QueryErrorHandler extends SearchErrorHandler {

    @Override
    public void errorHappened(ErrorStatus error, Exception ex) {

        switch (error) {
            case NO_QUERY:
                showErrorDialog(null, "There is no possible query", "No Query", JOptionPane.INFORMATION_MESSAGE, ex);
                break;
            case NO_CONFLICT_EXCEPTION:
                // we have not found new diagnoses but perhaps there are still diagnoses to process so this is no prob
                break;
            case ONLY_ONE_DIAG:
                showErrorDialog(null, "There is only one diagnosis so we can not discriminate  ", "Only One Diagnosis", JOptionPane.INFORMATION_MESSAGE, ex);
                break;
            case RECURRING_QUERY_ERROR:
                showErrorDialog(null, "The next query has an already answered axiom.", "Recurring Query Error", JOptionPane.INFORMATION_MESSAGE, ex);
            default:
                super.errorHappened(error, ex);
                break;
        }
    }

}
