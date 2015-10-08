/**
 *
 */
package tool;

import jadd.ADD;
import jadd.JADD;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import paramwrapper.ParamWrapper;
import expressionsolver.ExpressionSolver;
import fdtmc.FDTMC;

/**
 * Implements the orchestration of analysis tasks.
 *
 * This is the Façade to the domain model.
 * Its responsibility is establishing **what** needs to be done.
 *
 * @author thiago
 */
public class Analyzer {

    private ADD featureModel;
    private ParametricModelChecker modelChecker;
    private ExpressionSolver expressionSolver;
    private Map<String, ADD> reliabilityCache;
    private JADD jadd;

    /**
     * Creates an Analyzer which will follow the logical rules
     * encoded in the provided feature model file.
     *
     * @param featureModelFile File containing a CNF view of the Feature Model
     *          expressed using Java logical operators.
     * @throws IOException if there is a problem reading the file.
     */
    public Analyzer(File featureModelFile) throws IOException {
        featureModel = parseFeatureModel(featureModelFile);
        modelChecker = new ParamWrapper();
        jadd = new JADD();
        expressionSolver = new ExpressionSolver(jadd);
        reliabilityCache = new HashMap<String, ADD>();
    }

    /**
     * Abstracts UML to RDG transformation.
     *
     * @param umlModels
     * @return
     */
    public RDGNode model(File umlModels) {
        //TODO Implement!
        throw new RuntimeException();
    }

    /**
     * Recursively evaluates the reliability function of an RDG node.
     * A reliability function is a boolean function from the set of features
     * to Real values.
     *
     * @param node RDG node whose reliability is to be evaluated.
     * @return
     */
    public ADD evaluateReliability(RDGNode node) {
        if (isInCache(node)) {
            return getCachedReliability(node);
        }
        String reliabilityExpression = getReliabilityExpression(node);

        Map<String, ADD> childrenReliabilities = new HashMap<String, ADD>();
        for (RDGNode child: node.getChildren()) {
            ADD childReliability = evaluateReliability(child);

            ADD phi = child.getPresenceCondition().times(childReliability);
            childrenReliabilities.put(child.getId(),
                                      phi);
        }
        ADD reliability = expressionSolver.solveExpression(reliabilityExpression,
                                                           childrenReliabilities);

        // After evaluating the expression, constant terms alter the {0,1} nature
        // of the reliability ADD. Thus, we must multiply the result by the
        // {0,1} representation of the feature model in order to retain 0 as the
        // value for invalid configurations.
        return featureModel.times(reliability);
    }

    /**
     * Dumps the computed family reliability function to the output file
     * in the specified path.
     *
     * @param familyReliability Reliability function computed by a call to the
     *          {@link #evaluateReliability(RDGNode)} method.
     * @param outputFile Path to the .dot file to be generated.
     */
    public void generateDotFile(ADD familyReliability, String outputFile) {
        jadd.dumpDot("Family Reliability", familyReliability, outputFile);
    }

    /**
     * Creates a boolean ADD representing a feature model.
     *
     * It is important not to use this ADD with other analyzer, so this
     * method is private.
     *
     * @param featureModelFile File containing a CNF view of the Feature Model
     *          expressed using Java logical operators.
     * @return a boolean ADD (only 0 and 1 as terminals) encoding the Feature
     *          Model rules
     * @throws IOException if there is a problem reading the file.
     */
    private ADD parseFeatureModel(File featureModelFile) throws IOException {
        Path path = featureModelFile.toPath();
        String contents = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        ADD featureModel = expressionSolver.encodeFormula(contents);
        return featureModel;
    }

    /**
     * Computes the reliability expression for the model of a given RDG node.
     *
     * @param node
     * @return an algebraic expression on the variables present in the node's model.
     */
    private String getReliabilityExpression(RDGNode node) {
        FDTMC model = node.getFDTMC();
        return modelChecker.getReliability(model);
    }

    private ADD getCachedReliability(RDGNode node) {
        return reliabilityCache.get(node.getId());
    }

    private boolean isInCache(RDGNode node) {
        return reliabilityCache.containsKey(node.getId());
    }

}