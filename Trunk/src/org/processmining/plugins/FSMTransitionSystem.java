package org.processmining.plugins;

import com.sun.corba.se.spi.orbutil.fsm.FSM;
import org.apache.commons.collections.map.LinkedMap;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.directed.*;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.plugins.transitionsystem.miner.TSMinerTransitionSystem;

import java.util.*;

public class FSMTransitionSystem extends TransitionSystemImpl {
    public FSMTransitionSystem(String label, XLog log, String keyName) {
        super(label);
        for (int i = 0; i < log.size(); ++i) {
            StringBuilder prefix = new StringBuilder();
            XTrace trace = log.get(i);
            for (int j = 0; j < trace.size(); ++j) {
                String fromPrefix = prefix.toString();
                prefix.append(' ');
                prefix.append(trace.get(j).getAttributes().get(keyName));
                String toPrefix = prefix.toString();
                this.addState(toPrefix);
                if (j > 0)
                    this.addTransition(fromPrefix, toPrefix, trace.get(j).getAttributes().get(keyName).toString());
            }
        }
    }

    public void minizeFSM() {
        List<List<State>> statesByGroups = new LinkedList<>();
        statesByGroups.add(new LinkedList<>());
        statesByGroups.add(new LinkedList<>());
        Iterator<State> stateIterator = this.getNodes().iterator();
        while (stateIterator.hasNext()) {
            State state = stateIterator.next();
            Collection<Transition> outEdges = getOutEdges(state);
            if (outEdges == null || outEdges.size() == 0)
                statesByGroups.get(0).add(state);
            else
                statesByGroups.get(1).add(state);
        }

        statesByGroups = splitOnGroups(statesByGroups);
        for (int i = 0; i < statesByGroups.size(); ++i)
            unionGroupToState(statesByGroups.get(i));
    }

    private void unionGroupToState(List<State> groupOfStates) {
        Object unitedState = formUnitedState(groupOfStates);
        boolean res = this.addState(unitedState);
        for (int i = 0; i < groupOfStates.size(); ++i) {
            Iterator<Transition> edges = getOutEdges(groupOfStates.get(i)).iterator();
            while (edges.hasNext()) {
                Transition outEdge = edges.next();
                this.removeTransition(outEdge.getSource().getIdentifier(), outEdge.getTarget().getIdentifier(), outEdge.getIdentifier());
                this.addTransition(unitedState, outEdge.getTarget().getIdentifier(), outEdge.getIdentifier());
            }

            edges = getInEdges(groupOfStates.get(i)).iterator();
            while (edges.hasNext()) {
                Transition inEdge = edges.next();
                this.removeTransition(inEdge.getSource().getIdentifier(), inEdge.getTarget().getIdentifier(), inEdge.getIdentifier());
                this.addTransition(inEdge.getSource().getIdentifier(), unitedState, inEdge.getIdentifier());
            }

            this.removeState(groupOfStates.get(i));
        }
    }

    private Object formUnitedState(List<State> groupOfStates) {
        StringBuilder formState = new StringBuilder();
        for (int i = 0; i < groupOfStates.size(); ++i) {
            formState.append(groupOfStates.get(i));
            formState.append(',');
        }

        return formState.toString();
    }

    private List<List<State>> splitOnGroups(List<List<State>> statesByGroups) {
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

    private boolean checkOutEdges(Map<Object, Transition> edgesByIdentifiers, Collection<Transition> outEdges2,
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

    /*private Map<Object, FSMState> states = new LinkedMap();
    private Set<FSMState> nodes = new LinkedHashSet<>();
    private Set<FSMTransition> edges = new LinkedHashSet();
    private Map<Object, Set<FSMTransition>> transitions = new LinkedHashMap();
    private Map<Object, Object> proxyMap;

    public FSMTransitionSystem(String label, XLog log, String keyName) {
        this.getAttributeMap().put("ProM_Vis_attr_label", label);
        this.getAttributeMap().put("ProM_Vis_attr_orientation", 1);
        this.proxyMap = new HashMap();


    }

    @Override
    protected AbstractDirectedGraph<FSMState, FSMTransition> getEmptyClone() {
        return null;
    }

    @Override
    protected Map<? extends DirectedGraphElement, ? extends DirectedGraphElement>
    cloneFrom(DirectedGraph<FSMState, FSMTransition> directedGraph) {
        return null;
    }

    @Override
    public Set<FSMState> getNodes() {
        return nodes;
    }

    @Override
    public Set<FSMTransition> getEdges() {
        return edges;
    }

    @Override
    /**
     * DO NOT USE
     * /
    public void removeEdge(DirectedGraphEdge directedGraphEdge) {

    }

    @Override
    /**
     * DO NOT USE
     * /
    public void removeNode(DirectedGraphNode directedGraphNode) {

    }

    @Override
    public boolean addTransition(Object fromStateId, Object toStateId, Object newId) {
        FSMState source = states.get(fromStateId);
        FSMState target = states.get(toStateId);
        FSMTransition transition = new FSMTransition(source, target, newId);
        if (edges.add(transition)) {
            Set<FSMTransition> set = transitions.get(newId);
            if (set == null) {
                set = new LinkedHashSet();
                transitions.put(newId, set);
            }

            set.add(transition);
            graphElementAdded(transition);
            return true;
        }

        return false;
    }

    @Override
    public Object removeTransition(Object fromStateId, Object toStateId, Object removeId) {
        return null;
    }

    @Override
    public Collection<Object> getTransitions() {
        return this.transitions.keySet();
    }

    @Override
    /**
     * DO NOT USE
     * /
    public Collection<Transition> getEdges(Object o) {
        return null;
    }

    @Override
    public boolean addState(Object newFSMState) {
        FSMState state = (FSMState) newFSMState;

        if (!this.states.containsKey(state.getIdentifier())) {
            state.setGraph(this);
            states.put(state.getIdentifier(), state);
            nodes.add(state);
            graphElementAdded(state);
            return true;
        }

        return false;
    }

    @Override
    public Object removeState(Object o) {
        return null;
    }

    @Override
    public Collection<? extends Object> getStates() {
        return null;
    }

    @Override
    public State getNode(Object state) {
        return (State)this.states.get(this.getProxy(identifier));
    }

    @Override
    public Transition findTransition(Object o, Object o1, Object o2) {
        return null;
    }*/
}
