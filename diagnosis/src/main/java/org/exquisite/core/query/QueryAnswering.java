package org.exquisite.core.query;

/**
 * Created by kostya on 04.12.2015.
 */
public interface QueryAnswering<F> {

    Answer<F> getAnswer(Query<F> query);
}