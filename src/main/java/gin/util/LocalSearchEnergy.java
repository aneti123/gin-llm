package gin.util;

import gin.Patch;
import gin.test.UnitTest;
import gin.test.UnitTestResultSet;

import java.util.List;

public class LocalSearchEnergy extends LocalSearchSimple {
    public LocalSearchEnergy(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        LocalSearchEnergy sampler = new LocalSearchEnergy(args);
        sampler.sampleMethods();
    }

    protected double compareFitness(double newFitness, double oldFitness) {
        return oldFitness - newFitness;
    }

    // this method is used to get the initial fitness of the program
    protected UnitTestResultSet initFitness(String className, List<UnitTest> tests, Patch origPatch) {
        return testPatch(className, tests, origPatch, null);
    }

    protected double fitness(UnitTestResultSet results) {
        if (results.getCleanCompile() && results.allTestsSuccessful()) {
            return results.totalEnergyUsage();
        }
        return Double.MAX_VALUE;
    }

    protected boolean fitnessThreshold(UnitTestResultSet results, double orig) {
        return results.allTestsSuccessful();
    }
}
