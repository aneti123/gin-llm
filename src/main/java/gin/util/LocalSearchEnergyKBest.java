package gin.util;

import gin.Patch;
import gin.edit.Edit;
import gin.test.UnitTest;
import gin.test.UnitTestResultSet;
import org.pmw.tinylog.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

record Tuple(Patch patch, double fitness) {
}

/**
 * @brief k-best Local Search
 *
 */
public abstract class LocalSearchEnergyKBest extends GP {
    // Percentage of population size to be selected during tournament selection
    private static final double TOURNAMENT_PERCENTAGE = 0.2;
    // Probability of adding an edit during uniform crossover
    private static final double MUTATE_PROBABILITY = 0.5;

    public LocalSearchEnergyKBest(String[] args) {
        super(args);
    }

    // Constructor used for testing
    public LocalSearchEnergyKBest(File projectDir, File methodFile) {
        super(projectDir, methodFile);
    }

    protected double compareFitness(double newFitness, double oldFitness) {
        return oldFitness - newFitness;
    }

    // Whatever initialisation needs to be done for fitness calculations
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

    /*============== Implementation of abstract methods  ==============*/

    /*====== Search ======*/

    // Simple GP search (based on Simple)
    protected void search(TargetMethod method, Patch origPatch) {

        Logger.info("Runnning best-first local search.");

        String className = method.getClassName();
        String methodName = method.toString();
        List<UnitTest> tests = method.getGinTests();

        // Anis code starts
        int repeats = 10;
        int k = 5;
        UnitTestResultSet results = null;
        double orig = 0;

        for (int i = 0; i < repeats; i++) {
            results = initFitness(className, tests, origPatch);
            orig += fitness(results);
        }

        orig /= repeats;
        // Anis code ends

        super.writePatch(-1, 0,results, methodName, orig, 0);

        // Keep best
        double best = orig;
        Patch bestPatch = origPatch;
        ArrayList<Tuple> bestNeighbours = new ArrayList<>();


        for (int i = 1; i < indNumber; i++) {

            // Add a mutation
            Patch patch = mutate(bestPatch);

            // Calculate fitness
            // Anis code starts

            double newFitness = 0;
            int j;
            for (j = 0; j < repeats; j++) {
                results = testPatch(className, tests, patch, null);
                newFitness += fitness(results);
                if (!fitnessThreshold(results, orig)) {
                    break;
                }
            }
            newFitness /= (j+1);
            if (fitnessThreshold(results, orig) && (compareFitness(newFitness, best) > 0)) {
                bestNeighbours.add(new Tuple(patch, newFitness));
            }
            // Anis code ends

            super.writePatch(i,i,results, methodName, newFitness, compareFitness(newFitness, orig));

            if (bestNeighbours.size() == k) {
                best = orig;
                bestPatch = origPatch;
                for (Tuple tuple : bestNeighbours) {
                    if (tuple.fitness() > best) {
                        best = tuple.fitness();
                        bestPatch = tuple.patch();
                    }
                }
                bestNeighbours.clear();
            }
        }
    }

    /*====== GP Operators ======*/

    // Adds a random edit of the given type with equal probability among allowed types
    protected Patch mutate(Patch oldPatch) {
        Patch patch = oldPatch.clone();
        patch.addRandomEditOfClasses(super.mutationRng, super.editTypes);
        return patch;
    }

    protected List<Patch> select(Map<Patch, Double> population, Patch origPatch, double origFitness) {

        List<Patch> patches = new ArrayList<>(population.keySet());
        if (patches.size() < super.indNumber) {
            population.put(origPatch, origFitness);
            while (patches.size() < super.indNumber) {
                patches.add(origPatch);
            }
        }
        List<Patch> selectedPatches = new ArrayList<>();

        // Pick half of the population size
        for (int i = 0; i < super.indNumber / 2; i++) {

            Collections.shuffle(patches, super.individualRng);

            // Best patch from x% randomly selected patches picked each time
            Patch bestPatch = patches.get(0);
            double best = population.get(bestPatch);
            for (int j = 1; j < (super.indNumber * TOURNAMENT_PERCENTAGE); j++) {
                Patch patch = patches.get(j);
                double fitness = population.get(patch);

                if (compareFitness(fitness, best) > 0) {
                    bestPatch = patch;
                    best = fitness;
                }
            }

            selectedPatches.add(bestPatch.clone());

        }
        return selectedPatches;
    }

    // Uniform crossover: patch1patch2 and patch2patch1 created, each edit added with x% probability
    protected List<Patch> crossover(List<Patch> patches, Patch origPatch) {

        List<Patch> crossedPatches = new ArrayList<>();

        Collections.shuffle(patches, super.individualRng);
        int half = patches.size() / 2;
        for (int i = 0; i < half; i++) {

            Patch parent1 = patches.get(i);
            Patch parent2 = patches.get(i + half);
            List<Edit> list1 = parent1.getEdits();
            List<Edit> list2 = parent2.getEdits();

            Patch child1 = origPatch.clone();
            Patch child2 = origPatch.clone();

            for (Edit edit : list1) {
                if (super.mutationRng.nextFloat() > MUTATE_PROBABILITY) {
                    child1.add(edit);
                }
            }
            for (Edit edit : list2) {
                if (super.mutationRng.nextFloat() > MUTATE_PROBABILITY) {
                    child1.add(edit);
                }
                if (super.mutationRng.nextFloat() > MUTATE_PROBABILITY) {
                    child2.add(edit);
                }
            }
            for (Edit edit : list1) {
                if (super.mutationRng.nextFloat() > MUTATE_PROBABILITY) {
                    child2.add(edit);
                }
            }

            crossedPatches.add(parent1);
            crossedPatches.add(parent2);
            crossedPatches.add(child1);
            crossedPatches.add(child2);
        }

        return crossedPatches;
    }

}

