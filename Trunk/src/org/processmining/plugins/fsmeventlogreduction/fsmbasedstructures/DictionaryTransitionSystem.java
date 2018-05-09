package org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;

import java.util.*;

public class DictionaryTransitionSystem extends TransitionSystemImpl {
    private State root;
    private List<String> traceNames = new LinkedList<>();
    private Map<State, Map<Object, Transition>> outEdgesByIdentifiers = new LinkedHashMap<>();
    private Map<State, Integer> distByToState = new LinkedHashMap<>();
    private Map<State, Integer> traceByFinalState = new LinkedHashMap<>();
    private String suffixLinkIdentifier = "# SUFFIX_LINK #";
    private String finalSuffixLinkIdentifier = "# FINAL_SUFFIX_LINK #";

    public DictionaryTransitionSystem(String label, XLog dict, String keyName) {
        super(label);
        this.addState("");
        root = this.getNode("");
        for (int i = 0; i < dict.size(); ++i) {
            StringBuilder prefix = new StringBuilder();
            traceNames.add(dict.get(i).getAttributes().get(keyName).toString());
            XTrace trace = dict.get(i);
            for (int j = 0; j < trace.size(); ++j) {
                String fromPrefix = prefix.toString();
                prefix.append(' ');
                prefix.append(trace.get(j).getAttributes().get(keyName));
                String toPrefix = prefix.toString();
                this.addState(toPrefix);
                State newState = this.getNode(toPrefix);
                distByToState.put(newState, j+1);
                if (j == trace.size() - 1)
                {
                    newState.setAccepting(true);
                    traceByFinalState.put(newState, i);
                }

                State fromState = this.getNode(fromPrefix);
                Object transitionIdentifier = trace.get(j).getAttributes().get(keyName).toString();
                addTransitionInDictionary(fromState, newState, transitionIdentifier);
            }
        }

        buildSuffixLinks();
    }

    private void buildSuffixLinks() {
        addTransitionInDictionary(root, root, suffixLinkIdentifier);
        Queue<Transition> processSuffixLinks = new LinkedList<>();
        addTransitionsToProcess(root, processSuffixLinks);
        while (!processSuffixLinks.isEmpty()) {
            Transition nextTransition = processSuffixLinks.remove();
            buildSuffixLinkInternal(nextTransition);
            addTransitionsToProcess(nextTransition.getTarget(), processSuffixLinks);
        }
    }

    private void buildSuffixLinkInternal(Transition currentTransition) {
        State fromState = currentTransition.getTarget();
        Object transitionIdentifier = currentTransition.getIdentifier();
        State targetState = outEdgesByIdentifiers.get(currentTransition.getSource()).
                get(suffixLinkIdentifier).getTarget();
        while (targetState != root && (!outEdgesByIdentifiers.get(targetState).containsKey(transitionIdentifier) ||
                outEdgesByIdentifiers.get(targetState).get(transitionIdentifier).getTarget() == fromState)) {
            targetState = outEdgesByIdentifiers.get(targetState).
                    get(suffixLinkIdentifier).getTarget();
        }

        if ( outEdgesByIdentifiers.get(targetState).containsKey(transitionIdentifier) &&
                outEdgesByIdentifiers.get(targetState).get(transitionIdentifier).getTarget() != fromState)
            targetState = outEdgesByIdentifiers.get(targetState).
                    get(transitionIdentifier).getTarget();

        addTransitionInDictionary(fromState, targetState, suffixLinkIdentifier);
        buildFinalSuffixLinkInternal(fromState, targetState);
    }

    private void buildFinalSuffixLinkInternal(State fromState, State suffixState) {
        while (suffixState != root && !suffixState.isAccepting())
            suffixState = outEdgesByIdentifiers.get(suffixState).get(suffixLinkIdentifier).getTarget();

        if (suffixState != root)
            addTransitionInDictionary(fromState, suffixState, finalSuffixLinkIdentifier);
    }

    private void addTransitionsToProcess(State state, Queue<Transition> processSuffixLinks) {
        if (this.getOutEdges(state) == null)
            return;

        Iterator<Transition> outEdgesIterator = this.getOutEdges(state).iterator();
        while (outEdgesIterator.hasNext()) {
            Transition nextTransition = outEdgesIterator.next();
            if (!nextTransition.getIdentifier().equals(suffixLinkIdentifier) &&
                    !nextTransition.getIdentifier().equals(finalSuffixLinkIdentifier))
                processSuffixLinks.add(nextTransition);
        }
    }

    private void addTransitionInDictionary(State fromState, State toState, Object identifier) {
        if (!outEdgesByIdentifiers.containsKey(fromState))
            outEdgesByIdentifiers.put(fromState, new LinkedHashMap<>());

        try {
            this.addTransition(fromState.getIdentifier(), toState.getIdentifier(), identifier);
        }
        catch (Exception ex) {}

        outEdgesByIdentifiers.get(fromState).put(identifier,
                new Transition(fromState, toState, identifier));
    }

    public Map<State, Map<Object, Transition>> getOutEdgesByIdentifiers() {
        return outEdgesByIdentifiers;
    }

    public String getFinalSuffixLinkIdentifier() {
        return finalSuffixLinkIdentifier;
    }

    public String getSuffixLinkIdentifier() {
        return suffixLinkIdentifier;
    }

    public Integer getDistByToState(State state) {
        return distByToState.get(state);
    }

    public String getTraceName(int i) {
        return traceNames.get(i);
    }

    public Integer getTraceByFinalState(State state) {
        return traceByFinalState.get(state);
    }
}
