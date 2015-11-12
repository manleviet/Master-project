package evaluations;

import org.exquisite.datamodel.ExquisiteEnums.EngineType;
import org.exquisite.datamodel.ExquisiteSession;
import org.exquisite.diagnosis.EngineFactory;
import org.exquisite.diagnosis.IDiagnosisEngine;
import org.exquisite.diagnosis.engines.AbstractHSDagBuilder;
import org.exquisite.diagnosis.engines.AbstractHSDagBuilder.QuickXplainType;
import org.exquisite.diagnosis.models.DiagnosisModel;
import org.exquisite.diagnosis.parallelsearch.SearchStrategies;
import org.exquisite.diagnosis.quickxplain.QuickXPlain;
import org.exquisite.diagnosis.quickxplain.QuickXPlain.SolverType;

import evaluations.configuration.AbstractRunConfiguration;
import evaluations.configuration.AbstractScenario;
import evaluations.configuration.DXCScenario;
import evaluations.configuration.StdRunConfiguration;
import evaluations.configuration.StdRunConfiguration.ExecutionMode;
import evaluations.dxc.synthetic.model.DXCScenarioData;
import evaluations.dxc.synthetic.model.DXCSystem;
import evaluations.dxc.synthetic.model.DXCSystemDescription;
import evaluations.dxc.synthetic.tools.DXCDiagnosisModelGenerator;
import evaluations.dxc.synthetic.tools.DXCTools;

/**
 * Tests of DXC Synthetic Track
 * For documentation of overridden methods, see AbstractEvaluation
 * @author Thomas
 *
 */
public class DXCSyntheticBenchmark extends AbstractEvaluation {

	@Override
	public String getEvaluationName() {
		return "DXCSynthetic";
	}

	@Override
	public String getResultPath() {
		return logFileDirectory;
	}
	
	@Override
	public String getConstraintOrderPath() {
		return inputFileDirectory;
	}

	@Override
	protected boolean shouldShuffleConstraints() {
		return true;
	}
	
	@Override
	public boolean alwaysWriteDiagnoses() {
		return false;
	}
	
	@Override
	public boolean alwaysWriteConflicts() {
		return false;
	}
	
	// ----------------------------------------------------
	// Directories
	static String inputFileDirectory = "experiments/DXCSynthetic/";
	static String logFileDirectory = "logs/DXCSynthetic/";
	// ----------------------------------------------------
	
	// Number of runs
	static int nbInitRuns = 20;
	static int nbTestRuns = 100;
	
	// Standard scenario settings
//	static int searchDepth = -1;
	static int maxDiags = 5;
	
	static StdRunConfiguration[] runConfigurations = new StdRunConfiguration[] {
		new StdRunConfiguration(ExecutionMode.singlethreaded, 1, true),
//		new StdRunConfiguration(ExecutionMode.mergexplain, 1, false),
//		new StdRunConfiguration(ExecutionMode.levelparallel, 4, true),
//		new StdRunConfiguration(ExecutionMode.fullparallel, 4, true),
//		new StdRunConfiguration(ExecutionMode.levelparallel, 8, true),
//		new StdRunConfiguration(ExecutionMode.fullparallel, 8, true),
//		new StdRunConfiguration(ExecutionMode.levelparallel, 10, true),
//		new StdRunConfiguration(ExecutionMode.fullparallel, 10, true),
//		new StdRunConfiguration(ExecutionMode.levelparallel, 12, true),
//		new StdRunConfiguration(ExecutionMode.fullparallel, 12, true),
		new StdRunConfiguration(ExecutionMode.fullparallel, 16, true),
		new StdRunConfiguration(ExecutionMode.fullparallel, 20, true),
//		new StdRunConfiguration(ExecutionMode.continuingmergexplain, 1, false),
//		new StdRunConfiguration(ExecutionMode.parallelmergexplain, 4, false),
//		new StdRunConfiguration(ExecutionMode.inversequickxplain, 1, false),
//		new StdRunConfiguration(ExecutionMode.mxpandinvqxp, 1, false),
//		new StdRunConfiguration(ExecutionMode.fpandmxp, 4, false),
//		new StdRunConfiguration(ExecutionMode.continuingfpandmxp, 4, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 2, false),
//		new StdRunConfiguration(ExecutionMode.hybrid, 2, false),
		
//		new StdRunConfiguration(ExecutionMode.levelparallel, 4, false),
//		new StdRunConfiguration(ExecutionMode.fullparallel, 4, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 1, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 4, false),
//		new StdRunConfiguration(ExecutionMode.hybrid, 4, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 2, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 3, false),
//		new StdRunConfiguration(ExecutionMode.heuristic, 4, false),
//		new StdRunConfiguration(ExecutionMode.prdfs, 1, false),
//		new StdRunConfiguration(ExecutionMode.prdfs, 2, false),
//		new StdRunConfiguration(ExecutionMode.prdfs, 3, false),
//		new StdRunConfiguration(ExecutionMode.prdfs, 4, false),
	};
	
	static DXCScenario[] scenarios = new DXCScenario[] {
		// Settings for finding 1 diagnosis (All engines)
		// maxDiagSize = -1
//		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 1),
//		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 1),
//		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 1),
//		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 1), // Takes too long without search depth limitation
//		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 1), // Takes too long without search depth limitation
//		new DXCScenario("c499.xml", "c499/c499.%03d.scn", 7, 8, -1, 1),
//		new DXCScenario("c499.xml", "c499/c499.%03d.scn", 10, 12, -1, 1),
//		new DXCScenario("c499.xml", "c499/c499.%03d.scn", 14, 19, -1, 1),
//		new DXCScenario("c499.xml", "c499/c499.%03d.scn", 17, 17, -1, 1),
//		
//		
//		// Settings for finding [maxDiags] diagnoses
//		// maxDiagSize = -1
//		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, maxDiags),
//		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, maxDiags),
//		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, maxDiags),
//		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, maxDiags),
//		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, maxDiags),
//		
//
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 5),
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 6),
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 7),
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 8),
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 9),
////		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -1, 10),
////
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 5),
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 6),
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 7),
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 8),
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 9),
////		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -1, 10),
////
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 5),
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 6),
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 7),
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 8),
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 9),
////		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -1, 10),
////
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 5),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 6),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 7),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 8),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 9),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -1, 10),
////
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 5),
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 6),
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 7),
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 8),
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 9),
////		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -1, 10),
//		
//		
//		// Settings for finding all diagnoses (No Heuristic / Hybrid engines!)	
//		// maxDiagSize = 6/5
//		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 7, 6, -1),
//		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 8, 9, 5, -1),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 10, 15, 6, -1),
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 16, 16, 6, -1), // Maybe 6 is too slow
////		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 17, 19, 6, -1),
//		// maxDiagSize = -1
		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19),
		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19),
		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19),
		// maxDiagSize = faultSize
//		new DXCScenario("74182.xml", "74182/74182.%03d.scn", 0, 19, -2, -1),
//		new DXCScenario("74L85.xml", "74L85/74L85.%03d.scn", 0, 19, -2, -1),
		new DXCScenario("74283.xml", "74283/74283.%03d.scn", 0, 19, -2, -1),
		new DXCScenario("74181.xml", "74181/74181.%03d.scn", 0, 19, -2, -1), // Takes too long without search depth limitation
		new DXCScenario("c432.xml", "c432/c432.%03d.scn", 0, 19, -2, -1), // Takes too long without search depth limitation
	};

	@Override
	public IDiagnosisEngine prepareRun(
			AbstractRunConfiguration abstractRunConfiguration,
			AbstractScenario abstractScenario, int subScenario, int iteration) {

		StdRunConfiguration runConfiguration = (StdRunConfiguration)abstractRunConfiguration;
		DXCScenario scenario = (DXCScenario)abstractScenario;
		
		
		AbstractHSDagBuilder.USE_QXTYPE = QuickXplainType.QuickXplain;
		
		// Create the diagnosis model
		DXCSystemDescription sd = DXCTools.readSystemDescription(inputFileDirectory + scenario.inputFileName);
		DXCSystem system = sd.getSystems().get(0);
		DXCScenarioData scn = DXCTools.readScenario(inputFileDirectory + String.format(scenario.ScenarioFile, subScenario), system);
		DiagnosisModel diagModel;
		try {
			diagModel = new DXCDiagnosisModelGenerator().createDiagnosisModel(system, scn.getFaultyState());
		} catch (Exception e) {
			addError(e.getMessage(), abstractRunConfiguration, subScenario, iteration);
			return null;
//			e.printStackTrace();
		}
		// System.out.println(diagModel.getVariables());
		
		if (!DXCTools.checkCorrectState(system, scn)) {
			addError("Scenario is not correct!", abstractRunConfiguration, subScenario, iteration);
			return null;
		}
		
		// Create the engine
		ExquisiteSession sessionData = new ExquisiteSession(null,
				null, new DiagnosisModel(diagModel));
		// Do not try to find a better strategy for the moment
		sessionData.config.searchStrategy = SearchStrategies.Default;
		sessionData.config.maxDiagnoses = scenario.maxDiags;

		// With a value of -2 the maxDiagSize is set to the size of the actual error of the scenario
		if (scenario.searchDepth == -2) {
			sessionData.config.searchDepth = scn.getFaultyComponents().size();
		}
		else {
			sessionData.config.searchDepth = scenario.searchDepth;
		}
	
		EngineType engineType = chooseEngineType(scenario, runConfiguration);
		
		
		IDiagnosisEngine engine = EngineFactory.makeEngine(engineType, sessionData, runConfiguration.threads);
		
		QuickXPlain.ARTIFICIAL_WAIT_TIME = scenario.waitTime;
		if (runConfiguration.choco3) {
			QuickXPlain.SOLVERTYPE = SolverType.Choco3;
		} else {
			QuickXPlain.SOLVERTYPE = SolverType.Choco2;
		}
		
		return engine;
		
	}
	
	public static void main(String[] args) {		
		DXCSyntheticBenchmark dxcSyntheticBenchmark = new DXCSyntheticBenchmark();
		dxcSyntheticBenchmark.runTests(nbInitRuns, nbTestRuns, runConfigurations, scenarios);
	}

}
