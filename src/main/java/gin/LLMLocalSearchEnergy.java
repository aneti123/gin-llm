package gin;

import java.util.*;

import gin.edit.Edit;
import gin.edit.llm.LLMReplaceStatementNew;
import org.pmw.tinylog.Logger;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import gin.edit.llm.LLMReplaceStatement;
import gin.edit.llm.PromptTemplate;
import gin.edit.llm.PromptTemplate.PromptTag;
import gin.test.UnitTest;
import gin.test.UnitTestResult;
import gin.test.UnitTestResultSet;
import gin.util.LocalSearchSimple;
import gin.util.StringFitnessStack;

import javax.xml.transform.Source;

/**
 * Based on LocalSearchSimple. Takes a source filename and a method signature, optimises it for runtime (energy).
 * Assumes the existence of accompanying Test Class.
 * The class must be in the top level package, if classPath not provided.
 */
public class LLMLocalSearchEnergy extends LocalSearchSimple {

    // command-line argument for prompt template file
    @Argument(alias = "tpt1", description = "Prompt Template 1 Filename for LLM edits")
    protected String llmPromptTemplate1 = null;

    @Argument(alias = "tpt2", description = "Prompt Template 2 Filename for LLM edits")
    protected String llmPromptTemplate2 = null;

    private final StringFitnessStack<Double> stringFitnessStack = new StringFitnessStack<>();
    Edit currLLMEdit;

    // constructor for class
    public LLMLocalSearchEnergy(String[] args) {
        super(args);
        Args.parseOrExit(this, args);
        this.editType = "gin.edit.llm.LLMReplaceStatement"; // can specify this as terminal argument, but we're only doing LLM edits for now...
        printAdditionalArguments();
    }

    public static void main(String[] args) {
        LLMLocalSearchEnergy sampler = new LLMLocalSearchEnergy(args);
        sampler.sampleMethods(); // ultimately this will call search() via the GP class
    }

    private void printAdditionalArguments() {
        Logger.info("Template1: " + llmPromptTemplate1);
        Logger.info("Template2: " + llmPromptTemplate2);
    }

    // Simple GP search (based on Simple)
    protected void search(TargetMethod method, Patch origPatch) {

        Logger.info("Running best-first local search.");

        String className = method.getClassName();
        String methodName = method.toString();

        List<UnitTest> tests = method.getGinTests();

        PromptTemplate regularTemplate = null;
        PromptTemplate errorTemplate = null;
        if (llmPromptTemplate1 != null) {
            regularTemplate = PromptTemplate.fromFile(llmPromptTemplate1);
        }
        if (llmPromptTemplate2 != null) {
            errorTemplate = PromptTemplate.fromFile(llmPromptTemplate2);
        }

        UnitTestResultSet results = initFitness(className, tests, origPatch);
        double origEnergy = results.totalEnergyUsage();
        this.stringFitnessStack.setOriginalFitness(origEnergy);
        super.writePatch(-1, 0, results, methodName, origEnergy, 0);

        Patch bestPatch = origPatch;
        double bestEnergy = origEnergy;

        String lastErrorPatch = null;
        String lastError = null;

        for (int step = 1; step < indNumber; step++) {
            Logger.info(String.format("Step number %d\n", step));

            Patch neighbour;

            Map<PromptTemplate.PromptTag, String> metadata = new HashMap<>();
            metadata.put(PromptTag.CONTEXT, stringFitnessStack.getStackAsString());
            if (lastError != null) {
                metadata.put(PromptTag.PREVIOUS, lastErrorPatch);
                metadata.put(PromptTag.ERROR, lastError);

                neighbour = mutate(bestPatch, errorTemplate, metadata);
            } else {
                neighbour = mutate(bestPatch, regularTemplate, metadata);
            }

            UnitTestResultSet testResultSet = testPatch(className, tests, neighbour, metadata);
            String currentEditString = this.currLLMEdit.getReplacement();

            String msg;
            double improvement = 0;
            lastErrorPatch = lastError = null;

            if (!testResultSet.getValidPatch()) {
                msg = "Patch invalid";
            } else if (!testResultSet.getCleanCompile()) {
                msg = "Failed to compile";

                lastErrorPatch = testResultSet.getPatchedCode();
                lastError = "Failed to compile."
                        + "Error was:\n"
                        + testResultSet.getCompileError();
            } else if (!testResultSet.allTestsSuccessful()) {
                msg = ("Failed to pass all tests");

                lastErrorPatch = testResultSet.getPatchedCode();
                lastError = "";
                for (UnitTestResult testResult : testResultSet.getResults()) {
                    lastError += testResult.getExceptionMessage() + "\n";
                }
            } else if (testResultSet.totalEnergyUsage() >= bestEnergy) {
                msg = "Energy: " + testResultSet.totalEnergyUsage() + "ns";
            } else {
                bestPatch = neighbour;
                bestEnergy = testResultSet.totalEnergyUsage();
                improvement = origEnergy - bestEnergy;
                msg = "New best time: " + bestEnergy + "(ns)";
            }

            if (testResultSet.getValidPatch()) {
                this.stringFitnessStack.addToStack(currentEditString, testResultSet.totalEnergyUsage());
            }

            Logger.info(String.format("Step: %d, Patch: %s, %s ", step, neighbour, msg));
            super.writePatch(step, step, testResultSet, methodName, improvement, improvement);
        }
    }

    protected UnitTestResultSet initFitness(String className, List<UnitTest> tests, Patch origPatch) {
        return testPatch(className, tests, origPatch, null);
    }

    // Calculate fitness
    protected double fitness(UnitTestResultSet results) {
        double fitness = Double.MAX_VALUE;
        if (results.getCleanCompile() && results.allTestsSuccessful()) {
            return results.totalEnergyUsage();
        }
        return fitness;
    }

    // Calculate fitness threshold, for selection to the next generation
    protected boolean fitnessThreshold(UnitTestResultSet results, double orig) {
        return results.allTestsSuccessful();
    }

    // Compare two fitness values, newFitness better if result > 0
    protected double compareFitness(double newFitness, double oldFitness) {
        return oldFitness - newFitness;
    }

    protected Patch mutate(Patch oldPatch, PromptTemplate template, Map<PromptTag, String> metadata) {
        Patch patch = oldPatch.clone();

        if (patch.size() > 0 && super.mutationRng.nextFloat() > 0.5) {
            patch.remove(super.mutationRng.nextInt(patch.size()));
        } else {
            SourceFile updatedSourceFile = patch.getUpdatedSourceFile();
            this.currLLMEdit =  new LLMReplaceStatementNew(updatedSourceFile, mutationRng, template, metadata);
            patch.add(this.currLLMEdit);
        }

        return patch;
    }

}
