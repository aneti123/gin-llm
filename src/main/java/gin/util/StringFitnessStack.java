package gin.util;

import java.util.Stack;

/**
 * This class is used to store the previous mutated programs and their corresponding fitnesses. Each API call to the LLM
 * then uses getsStackAsString to give context on changes that the LLM has made and what those changes have lead to.
 * We opt to do this over giving the whole string of messages as it is more concise, and uses less tokens.
 */
public class StringFitnessStack<T> {
    private final Stack<String> stack = new Stack<>();
    private T originalFitness;

    //
    public void addToStack(String replacement, T fitness) {
        String str = "***The following edit: \n" + replacement + "\nhad an execution time of: " + fitness + "ns***\n";
        this.stack.push(str);
        if (stack.size() > 5) {
            stack.pop();
        }
    }

    public void setOriginalFitness(T fitness) {
        this.originalFitness = fitness;
    }

    public String getStackAsString() {
        if (stack.isEmpty()) {
            return "";
        }

        String contextString = "The following Java blocks are implementations that you have suggested previously, and their runtime in nanoseconds (ns). The original program had a fitness of " + this.originalFitness + " and we want to do better.\n";
        for (String str : this.stack) {
            contextString = contextString.concat(str);
        }
        return contextString;
    }
}