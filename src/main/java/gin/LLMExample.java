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

/**
 * Based on LocalSearchSimple. Takes a source filename and a method signature, optimises it for runtime (fitness).
 * Assumes the existence of accompanying Test Class.
 * The class must be in the top level package, if classPath not provided.
 */
public class LLMExample extends LocalSearchSimple {

    // command-line argument for prompt template file
    @Argument(alias = "tpt1", description = "Prompt Template 1 Filename for LLM edits")
    protected String llmPromptTemplate1 = null;
    
    @Argument(alias = "tpt2", description = "Prompt Template 2 Filename for LLM edits")
    protected String llmPromptTemplate2 = null;

    private final StringFitnessStack<Long> stringFitnessStack = new StringFitnessStack<>();
    Edit currLLMEdit;

    // constructor for class
    public LLMExample(String[] args) {
        super(args);
        Args.parseOrExit(this, args);
        this.editType = "gin.edit.llm.LLMReplaceStatement"; // can specify this as terminal argument, but we're only doing LLM edits for now...
        printAdditionalArguments();
    }
    
    public static void main(String[] args) {
        LLMExample sampler = new LLMExample(args);
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

        Logger.info(String.format("The class name being tested is %s", className));
        Logger.info(String.format("The method name being tested is %s", className));

        List<UnitTest> tests = method.getGinTests();

        // we have two templates.
        // 1 is much as we did before. It just asks for variants of the target method.
        // 2 adds in an error message we've been given previously
        // see further below as to how this is passed in
        PromptTemplate regularTemplate = null;
        PromptTemplate errorTemplate = null;
        if (llmPromptTemplate1 != null) {
            regularTemplate = PromptTemplate.fromFile(llmPromptTemplate1);
        }
        if (llmPromptTemplate2 != null) {
            errorTemplate = PromptTemplate.fromFile(llmPromptTemplate2);
        }

        // Run original code
        // Results store the initial fitness results of the original method.
        UnitTestResultSet results = initFitness(className, tests, origPatch);
        long origTime = results.totalExecutionTime();
        this.stringFitnessStack.setOriginalFitness(origTime);
        super.writePatch(-1, 0, results, methodName, origTime, 0);

        // Keep best
        // The best patch right now is currently just the original code
        Patch bestPatch = origPatch;
        long bestTime = origTime;

        // the patch which lead to the last error is null, the string message for the last error is also null
        String lastErrorPatch = null;
        String lastError = null;

        for (int step = 1; step < indNumber; step++) {
            Logger.info(String.format("At step %d, the best patch is the following:", step));
            Logger.info(bestPatch.getUpdatedSourceFile().toString());
            // Add a mutation
            // The neighbour patch will contain the best patches so far + the mutation generated by the llm
            Patch neighbour;

            // populate metadata with the error messages so they can feed into the prompt
            Map<PromptTemplate.PromptTag, String> metadata = new HashMap<>();
            metadata.put(PromptTag.CONTEXT, stringFitnessStack.getStackAsString());
            if (lastError != null) {
                // PromptTag.PREVIOUS is the previous patch which lead to the error, as a string.
                // PromptTag.ERROR is the error caused by the last patch, as a string
                metadata.put(PromptTag.PREVIOUS, lastErrorPatch);
                metadata.put(PromptTag.ERROR, lastError);

                neighbour = mutate(bestPatch, errorTemplate, metadata);
            } else {
                // add a new mutation to list of best patches
                neighbour = mutate(bestPatch, regularTemplate, metadata);
            }
            // currently the following assumes only one patched version of the code will
            // be generated by one patch. LLMReplaceStatement can return >1 replacements
            // so at some point we can look at how we deal with those here too. For now
            // we just get the first valid one.

            // testPatch is a method from the super class Sampler, it compiles the target class (className)
            // with the new patch neighbour and tests it against the tests provided.
            // The metadata is simply just passed along all the way LLMReplaceStatement.apply, where it is
            // used to initialise the prompt template
//            metadata.put(PromptTag.CONTEXT, stringFitnessStack.getStackAsString());
            UnitTestResultSet testResultSet = testPatch(className, tests, neighbour, metadata);
            String currentEditString = currLLMEdit.getReplacement();

            String msg;
            double improvement = 0;
            lastErrorPatch = lastError = null;

            // getValidPatch returns a boolean for whether the patch is valid or not
            if (!testResultSet.getValidPatch()) {
                // was the patch valid as in was it in JSON etc.
                msg = "Patch invalid";
            } else if (!testResultSet.getCleanCompile()) {
                msg = "Failed to compile";

                // store error message for next time round
                lastErrorPatch = testResultSet.getPatchedCode();
                lastError = "Failed to compile."
                        + "Error was:\n"
                        + testResultSet.getCompileError();
            } else if (!testResultSet.allTestsSuccessful()) {
                msg = ("Failed to pass all tests");

                // store error message for next time round
                lastErrorPatch = testResultSet.getPatchedCode();
                lastError = "";
                for (UnitTestResult testResult : testResultSet.getResults()) {
                    lastError += testResult.getExceptionMessage() + "\n";
                }
            } else if (testResultSet.totalExecutionTime() >= bestTime) {
                msg = "Time: " + testResultSet.totalExecutionTime() + "ns";
            } else {
                bestPatch = neighbour;
                bestTime = testResultSet.totalExecutionTime();
                improvement = origTime - bestTime;
                msg = "New best time: " + bestTime + "(ns)";
            }

            // if the patch is valid (at least recognised as java code), then add it to the stack to be used as context later
            if (testResultSet.getValidPatch()) {
                this.stringFitnessStack.addToStack(currentEditString, testResultSet.totalExecutionTime());
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
            return (double) (results.totalExecutionTime() / 1000000);
        }
        return fitness;
    }
    // trying super.mutationRng in case that makes a difference in choosing the mutation

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
            currLLMEdit = new LLMReplaceStatementNew(updatedSourceFile, mutationRng, template, metadata);
            patch.add(currLLMEdit);
            // patch.add(new LLMReplaceStatement(patch.getSourceFile(), mutationRng, template));
        }
        
        return patch;
    }

}
