package org.processmining.plugins;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;

import java.util.*;

public class FSMTransitionSystem extends TransitionSystemImpl {
    private List<String> traceNames;
    private Map<State, Set<Integer>> traceByState = new LinkedHashMap<>();

    public FSMTransitionSystem(String label, XLog log, String keyName) {
        super(label);
        this.addState("");
        traceByState.put(this.getNode(""), new LinkedHashSet<>());
        traceNames = new ArrayList<>();
        for (int i = 0; i < log.size(); ++i) {
            StringBuilder prefix = new StringBuilder();
            XTrace trace = log.get(i);
            traceNames.add(trace.getAttributes().get(keyName).toString());
            for (int j = 0; j < trace.size(); ++j) {
                String fromPrefix = prefix.toString();
                prefix.append(' ');
                prefix.append(trace.get(j).getAttributes().get(keyName));
                String toPrefix = prefix.toString();
                this.addState(toPrefix);
                State newState = this.getNode(toPrefix);
                if (!traceByState.containsKey(newState))
                    traceByState.put(newState, new LinkedHashSet<>());

                traceByState.get(newState).add(i);
                if (j == trace.size()-1)
                    newState.setAccepting(true);

                try{
                    this.addTransition(fromPrefix, toPrefix, trace.get(j).getAttributes().get(keyName).toString());
                }
                catch (Exception ex) {}
            }
        }
    }

    public synchronized void minizeFSM() {
        List<List<State>> statesByGroups = new LinkedList<>();
        statesByGroups.add(new LinkedList<>());
        statesByGroups.add(new LinkedList<>());
        Iterator<State> stateIterator = this.getNodes().iterator();
        while (stateIterator.hasNext()) {
            State state = stateIterator.next();
            if (state.isAccepting())
                statesByGroups.get(0).add(state);
            else
                statesByGroups.get(1).add(state);
        }

        statesByGroups = splitOnGroups(statesByGroups);
        for (int i = 0; i < statesByGroups.size(); ++i)
            unionGroupToState(statesByGroups.get(i));
    }

    private synchronized void unionGroupToState(List<State> groupOfStates) {
        Object unitedState = formUnitedState(groupOfStates);
        boolean res = this.addState(unitedState);
        State newState = this.getNode(unitedState);
        if (!traceByState.containsKey(newState))
            traceByState.put(newState, new LinkedHashSet<>());

        for (int i = 0; i < groupOfStates.size(); ++i) {
            Iterator<Transition> edges = getOutEdges(groupOfStates.get(i)).iterator();
            while (edges.hasNext()) {
                Transition outEdge = edges.next();
                try {
                    this.addTransition(unitedState, outEdge.getTarget().getIdentifier(), outEdge.getIdentifier());
                }
                catch (Exception ex) {}

                this.removeTransition(outEdge.getSource().getIdentifier(),
                        outEdge.getTarget().getIdentifier(), outEdge.getIdentifier());
            }

            edges = getInEdges(groupOfStates.get(i)).iterator();
            while (edges.hasNext()) {
                Transition inEdge = edges.next();
                try {
                    this.addTransition(inEdge.getSource().getIdentifier(), unitedState, inEdge.getIdentifier());
                }
                catch (Exception ex) {}

                this.removeTransition(inEdge.getSource().getIdentifier(),
                        inEdge.getTarget().getIdentifier(), inEdge.getIdentifier());
            }

            if (!this.getNode(unitedState).isAccepting() && groupOfStates.get(i).isAccepting())
                this.getNode(unitedState).setAccepting(true);

            for (Integer traceIdentifier: traceByState.get(groupOfStates.get(i)))
                traceByState.get(newState).add(traceIdentifier);

            traceByState.remove(groupOfStates.get(i));
            this.removeState(groupOfStates.get(i));
        }
    }

    private synchronized Object formUnitedState(List<State> groupOfStates) {
        StringBuilder formState = new StringBuilder();
        for (int i = 0; i < groupOfStates.size(); ++i) {
            formState.append(groupOfStates.get(i));
            formState.append(',');
        }

        return formState.toString();
    }

    private synchronized List<List<State>> splitOnGroups(List<List<State>> statesByGroups) {
        Map<State, Integer> groupNumbers = new LinkedHashMap<>();
        for (int i = 0; i < statesByGroups.size(); ++i)
            for (int j = 0; j < statesByGroups.get(i).size(); ++j)
                groupNumbers.put(statesByGroups.get(i).get(j), i);

        for (int i = 0; i < statesByGroups.size(); ++i) {
            List<State> groupOfStates = statesByGroups.get(i);
            if (groupOfStates.size() == 0)
                continue;

            List<State> newGroup = new LinkedList<>();
            Map<Object, Transition> outEdges1ByIdentifiers = new LinkedHashMap<>();
            Iterator<Transition> outEdges1Iterator = getOutEdges(groupOfStates.get(0)).iterator();
            while (outEdges1Iterator.hasNext()) {
                Transition outEdge = outEdges1Iterator.next();
                outEdges1ByIdentifiers.put(outEdge.getIdentifier(), outEdge);
            }

            for (int j = 1; j < groupOfStates.size(); ++j)
                if (checkOutEdges(outEdges1ByIdentifiers, getOutEdges(groupOfStates.get(j)), groupNumbers))
                    newGroup.add(groupOfStates.remove(j--));

            newGroup.add(groupOfStates.remove(0));
            if (groupOfStates.size() == 0)
                statesByGroups.set(i, newGroup);
            else {
                --i;
                int groupNumber = statesByGroups.size();
                for (int j = 0; j < newGroup.size(); ++j)
                    groupNumbers.put(newGroup.get(j), groupNumber);

                statesByGroups.add(newGroup);
            }
        }

        return statesByGroups;
    }

    private synchronized boolean checkOutEdges(Map<Object, Transition> edgesByIdentifiers, Collection<Transition> outEdges2,
                                  Map<State, Integer> groupNumbers) {
        if (edgesByIdentifiers.size() != outEdges2.size())
            return false;

        Iterator<Transition> outEdges2Iterator = outEdges2.iterator();
        while (outEdges2Iterator.hasNext()) {
            Transition outEdge2 = outEdges2Iterator.next();
            if (!edgesByIdentifiers.containsKey(outEdge2.getIdentifier()))
                return false;

            Transition outEdge1 = edgesByIdentifiers.get(outEdge2.getIdentifier());

            if (!groupNumbers.get(outEdge1.getTarget()).equals(groupNumbers.get(outEdge2.getTarget())))
                return false;
        }

        return true;
    }
}
