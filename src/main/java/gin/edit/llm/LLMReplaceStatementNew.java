package gin.edit.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pmw.tinylog.Logger;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.Statement;

import gin.SourceFile;
import gin.SourceFileTree;
import gin.edit.Edit;
import gin.edit.llm.PromptTemplate.PromptTag;
import gin.edit.statement.StatementEdit;

public class LLMReplaceStatementNew extends StatementEdit {
    private static final long serialVersionUID = 1112502387236768006L;
    public String destinationFilename;
    public int destinationStatement;

    private PromptTemplate promptTemplate;
    private String replacementString;
    private Statement replacementStatement;
    private String answer;

    /**fairly rubbish approach to having something meaningful for the toString*/
    private String lastReplacement;
    private String lastPrompt;

    /**
     * create a random LLMReplaceStatementNew for the given sourcefile, using the provided RNG
     *
     * all this does is pick a location
     *
     * @param sourceFile to create an edit for
     * @param rng        random number generator, used to choose the target statements
     */
    public LLMReplaceStatementNew(SourceFile sourceFile, Random rng, PromptTemplate promptTemplate, Map<PromptTag, String> tagReplacements) {
        SourceFileTree sf = (SourceFileTree) sourceFile;

        destinationFilename = sourceFile.getRelativePathToWorkingDir();

        // target is in target method only
        // this returns a random block ID (integer) in the target method
        destinationStatement = sf.getRandomBlockID(true, rng);

        this.promptTemplate = promptTemplate;

        lastReplacement = "NOT YET APPLIED";
        lastPrompt = "NOT YET APPLIED";

        // get the node of the destination statement
        Node destination = sf.getNode(destinationStatement);

        LLMQuery llmQuery;

        // assume that our query will always be openAI for now
        llmQuery = new OpenAILLMQuery();

        Logger.info("Seeking replacements for:");
        Logger.info(destination);

        if(tagReplacements == null) {
            tagReplacements = new HashMap<>();
        }
        tagReplacements.put(PromptTag.COUNT, Integer.toString(1));
        tagReplacements.put(PromptTag.DESTINATION, destination.toString());
        tagReplacements.put(PromptTag.PROJECT, LLMConfig.projectName);

        String prompt = this.promptTemplate.replaceTags(tagReplacements);
        lastPrompt = prompt;

        // LLM for ChatGPT
        answer = llmQuery.chatLLM(prompt);
        Pattern pattern = Pattern.compile("```(?:java)(.*?)```", Pattern.DOTALL | Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(answer);

        // now parse the strings return by LLM into JavaParser Statements
        while (matcher.find()) {
            String str = matcher.group(1);

            try {
                Statement stmt;
                stmt = StaticJavaParser.parseBlock(str);

                replacementString = str;
                replacementStatement = stmt;

                break;
            }
            catch (ParseProblemException ignored) {
            }
        }

    }

    public LLMReplaceStatementNew(SourceFile sourceFile, Random rng) {
        this(sourceFile, rng, LLMConfig.getDefaultPromptTemplate(), null);
    }

    public LLMReplaceStatementNew(String destinationFilename, int destinationStatement) {
        this.destinationFilename = destinationFilename;
        this.destinationStatement = destinationStatement;

        this.lastReplacement = "NOT YET APPLIED";
    }

    public static Edit fromString(String description) {
        // TODO - update with lastReplacement
        String[] tokens = description.split("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        String[] destTokens = tokens[1].split(":");
        String destFilename = destTokens[0].replace("\"", "");
        int destination = Integer.parseInt(destTokens[1]);
        return new LLMReplaceStatementNew(destFilename, destination);
    }

    @Override
    public SourceFile apply(SourceFile sourceFile, Object tagReplacements) {

        SourceFileTree sf = (SourceFileTree) sourceFile;
        Node destination = sf.getNode(destinationStatement);

        if (destination == null) {
            return sf;
        }

        List<SourceFile> variantSourceFiles = new ArrayList<>();
        SourceFile variantSourceFile;

        // check if replacementString is null
        if (replacementString == null) {
            Logger.info("No replacements found. Response was:");
        }

        if (replacementString == null) {
            Logger.info("============");
            Logger.info("No replacements found. Response was:");
            Logger.info(answer);
            Logger.info("============");
            this.lastReplacement = "LLM GAVE NO SUGGESTIONS";
        } else {
            this.lastReplacement = replacementString;
            Logger.info("============");
            Logger.info(String.format("The LLM response was: %s", this.lastReplacement));
            Logger.info("============");
        }

        try {
            variantSourceFile = sf.replaceNode(destinationStatement, replacementStatement);
        } catch (ClassCastException e) { // JavaParser sometimes throws this if the statements don't match
            Logger.info("Replace statement failed");
            return null;
        }

        return variantSourceFile;
    }

    @Override
    public String getReplacement() {
        return this.lastReplacement;
    }

    @Override
    public String toString() {
        return this.getClass().getCanonicalName() + " \"" + destinationFilename + "\":" + destinationStatement + "\nPrompt: !!!\n" + lastPrompt +  "\n!!! --> !!!\n" + lastReplacement + "\n!!!";
    }

}
