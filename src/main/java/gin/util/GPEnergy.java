package gin.util;

import gin.Patch;
import gin.test.UnitTest;
import gin.test.UnitTestResultSet;

import java.util.List;

public class GPEnergy extends GPSimple {

    public GPEnergy(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        GPEnergy sampler = new GPEnergy(args);
        sampler.sampleMethods();
    }
    @Override
    protected double compareFitness(double newFitness, double oldFitness) {
        return oldFitness - newFitness;
    }

    @Override
    protected UnitTestResultSet initFitness(String className, List<UnitTest> tests, Patch origPatch) {
        return testPatch(className, tests, origPatch, null);
    }

    @Override
    protected double fitness(UnitTestResultSet results) {
        double fitness = Double.MAX_VALUE;
        if (results.getCleanCompile() && results.allTestsSuccessful()) {
            return results.totalEnergyUsage();
        }
        return fitness;
    }

    @Override
    protected boolean fitnessThreshold(UnitTestResultSet results, double orig) {
        return results.allTestsSuccessful();
    }
}
