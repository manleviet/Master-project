package org.exquisite.core.engines.query.qc.heuristic.scenario1;

import org.exquisite.core.query.QPartition;
import org.exquisite.core.query.qc.heuristic.HeuristicQC;
import org.exquisite.core.query.qc.heuristic.partitionmeasures.RiskOptimizationMeasure;

import java.math.BigDecimal;

import static org.exquisite.core.TestUtils.getSet;

/**
 * @author wolfi
 */
public class RIOScenario1 extends TestScenario1 {

    private BigDecimal tEnt = new BigDecimal("0.05");
    private BigDecimal tCard = BigDecimal.ZERO;
    private BigDecimal c = new BigDecimal("0.4");

    @Override
    public HeuristicQC<Integer> getHeuristicQC() {
        return new HeuristicQC<>(new RiskOptimizationMeasure(tEnt, tCard, c), getEngine());
    }

    @Override
    protected String getExpectedProbDnx() {
        return "0.46";
    }

    @Override
    protected String getExpectedProbDx() {
        return "0.54";
    }

    @Override
    protected QPartition<Integer> getExpectedQPartition() {
        return new QPartition<>(getSet(D3,D4,D2), getSet(D1,D5,D6), getSet(), getEngine().getCostsEstimator());
    }
}
