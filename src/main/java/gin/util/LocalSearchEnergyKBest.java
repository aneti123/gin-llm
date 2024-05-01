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
public class LocalSearchEnergyKBest extends GP {
    // Percentage of population size to be selected during tournament selection
    private static final double TOURNAMENT_PERCENTAGE = 0.2;
    // Probability of adding an edit during uniform crossover
    private static final double MUTATE_PROBABILITY = 0.5;

    public LocalSearchEnergyKBest(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        LocalSearchEnergyKBest sampler = new LocalSearchEnergyKBest(args);
        sampler.sampleMethods();
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
        if (results.getCleanCompile() && results.allTestsSuccessful()) {
            return results.totalEnergyUsage();
        }
        return Double.MAX_VALUE;
    }

    // Calculate fitness threshold, for selection to the next generation
    protected boolean fitnessThreshold(UnitTestResultSet results, double orig) {
        return results.allTestsSuccessful();
    }

    /*============== Implementation of abstract methods  ==============*/

    /*====== Search ======*/

    // Simple GP search (based on Simple)
    protected void search(TargetMethod method, Patch origPatch) {

        Logger.info("Running k-best local search.");

        String className = method.getClassName();
        String methodName = method.toString();
        List<UnitTest> tests = method.getGinTests();

        int repeats = 5;
        int k = 5;
        UnitTestResultSet results = null;
        double orig = 0;

        for (int r = 0; r < repeats; r++) {
            results = initFitness(className, tests, origPatch);
            orig += fitness(results);
        }
        orig /= repeats;

        super.writePatch(-1, 0, results, methodName, orig, 0);

        // Keep best
        double best = orig;
        Patch bestPatch = origPatch;
        ArrayList<Tuple> bestNeighbours = new ArrayList<>();

        int numAdds = 0;
        for (int i = 1; i < indNumber; i++) {
            Logger.info(String.format("Step number %d\n", i));
            // Add a mutation
            Patch patch = mutate(bestPatch);

            double newFitness = 0;
            for (int j = 0; j < repeats; j++) {
                results = testPatch(className, tests, patch, null);
                if (!fitnessThreshold(results, orig)) {
                    break;
                }
                newFitness += fitness(results);
            }

            if (fitnessThreshold(results, orig) && (compareFitness(newFitness, best) > 0)) {
                bestNeighbours.add(new Tuple(patch, newFitness));
            }

            if (!fitnessThreshold(results, orig)) {
                newFitness = Double.MAX_VALUE;
            } else {
                newFitness /= repeats;
            }

            if (bestNeighbours.size() == k) {
                for (Tuple tuple : bestNeighbours) {
                    if (tuple.fitness() > best) {
                        best = tuple.fitness();
                        bestPatch = tuple.patch();
                    }
                    numAdds++;
                }
                bestNeighbours.clear();
            }

            super.writePatch(numAdds,i,results, methodName, newFitness, compareFitness(newFitness, orig));
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

