package org.processmining.plugins.fsmeventlogreduction.reductionalgo;
import flanagan.analysis.Stat;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.impl.XLogImpl;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.DictionaryTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.FSMTransitionSystem;

import java.util.*;

public class FsmEventLogReductionAlgo{
    private Map<State, Set<Integer>> traceByToState;
    private List<Map<State, Transition>> transByFromState;
    private Map<Transition, Set<StateGroup>> equalLogTrans;
    private Map<Transition, Set<StateGroup>> equalDictTrans;
    private Queue<StateGroup> toProcess;
    private Set<StateGroup> processing;
    private State logRoot;

    private Map<State, Map<Object, Transition>> outEdgesByIdentifiers = new LinkedHashMap<>();
    private State dictRoot;

    private XLog resultLog;
    private XAttributeMap xTraceAttr;
    private FSMTransitionSystem fsmLog;
    private DictionaryTransitionSystem fsmDictionary;

    public FsmEventLogReductionAlgo(FSMTransitionSystem fsmLog, DictionaryTransitionSystem fsmDictionary,
                                    XAttributeMap xLogAttr, XAttributeMap xTraceAttr){
        this.resultLog = new XLogImpl(xLogAttr);
        this.xTraceAttr = xTraceAttr;
        this.fsmDictionary = fsmDictionary;
        this.fsmLog = fsmLog;

        this.logRoot = fsmLog.getNode(",");
        this.dictRoot = fsmDictionary.getNode("");
        this.transByFromState = fsmLog.getTransByFromState();
        this.traceByToState = fsmLog.getTracesByToStates();
        this.outEdgesByIdentifiers = fsmDictionary.getOutEdgesByIdentifiers();
    }

    public XLog reduceFsmTypeLog(){
        equalLogTrans = new LinkedHashMap<>();
        equalDictTrans = new LinkedHashMap<>();
        toProcess = new LinkedList<>();
        processing = new LinkedHashSet<>();
        processing.add(new StateGroup(logRoot, dictRoot, null, null,
                fsmLog.getTracesByFromState(logRoot)));

        if (fsmLog.getOutEdges(logRoot).size() > 1) {
            splitGroup(processing.iterator().next());
            while (!toProcess.isEmpty()) {
                splitGroup(toProcess.peek());
                processing.add(toProcess.remove());
            }
        }
        else {
            setNewTransitions(processing.iterator().next(), fsmLog.getOutEdges(logRoot).iterator().next());
        }

        while (!processing.isEmpty()) {
            for (Iterator<StateGroup> i = processing.iterator(); i.hasNext();) {
                StateGroup group = i.next();
                if (group.trace.size() == 0 || group.logTrans == null || fsmLog.getOutEdges(group.logCurrState).size() < 1)
                    i.remove();
                else {
                    for (StateGroup groupToUni : processing)
                        unionGroups(groupToUni);

                    for (StateGroup groupToMove : processing)
                        move(groupToMove);

                    for (StateGroup groupToSplit : processing) {
                        if (fsmLog.getOutEdges(group.logCurrState).size() == 1)
                            setNewTransitions(groupToSplit, fsmLog.getOutEdges(group.logCurrState).iterator().next());
                        else
                            splitGroup(groupToSplit);
                    }

                    while (!toProcess.isEmpty()) {
                        splitGroup(toProcess.peek());
                        processing.add(toProcess.remove());
                    }
                }
            }
        }

        return null;
    }

    private void splitGroup(StateGroup currentGroup) {
        StateGroup otherGroup = new StateGroup(currentGroup.logCurrState, currentGroup.dictCurrState,
                null, null, new LinkedHashSet<>());
        Transition newTrans = transByFromState.get(currentGroup.trace.iterator().next()).get(currentGroup.logCurrState);
        for (Iterator<Integer> i = currentGroup.trace.iterator(); i.hasNext();) {
            Integer trace = i.next();
            if (newTrans == null || !traceByToState.get(newTrans.getTarget()).contains(trace) ||
                    !newTrans.equals(transByFromState.get(trace).get(currentGroup.logCurrState))) {
                otherGroup.trace.add(trace);
                i.remove();
            }
        }

        setNewTransitions(currentGroup, newTrans);
        if (otherGroup.trace.size() > 0)
            toProcess.add(otherGroup);

    }

    private void setNewTransitions(StateGroup group, Transition newTrans) {
        if (group.logTrans != null)
            equalLogTrans.get(group.logTrans).remove(group);

        if (group.dictTrans != null)
            equalDictTrans.get(group.dictTrans).remove(group);

        if (newTrans != null) {
            group.logTrans = newTrans;
            group.dictTrans = outEdgesByIdentifiers.get(group.dictCurrState).get(newTrans.getIdentifier());
            if (!equalLogTrans.containsKey(group.logTrans))
                equalLogTrans.put(group.logTrans, new LinkedHashSet<>());

            equalLogTrans.get(group.logTrans).add(group);
            //TODO suffix links move if dictTrans == null

            if (!equalDictTrans.containsKey(group.dictTrans))
                equalDictTrans.put(group.dictTrans, new LinkedHashSet<>());

            equalDictTrans.get(group.dictTrans).add(group);
        }
    }

    private void unionGroups(StateGroup currentGroup) {
        if (currentGroup.logTrans == null)
            return;

        Set<StateGroup> currentDictGroups = equalDictTrans.get(currentGroup.dictTrans);
        for (Iterator<StateGroup> i = equalLogTrans.get(currentGroup.logTrans).iterator(); i.hasNext();) {
            StateGroup group = i.next();
            if (group != currentGroup && currentDictGroups.contains(group)) {
                currentDictGroups.remove(group);
                i.remove();

                processing.remove(group);
                for (Integer trace : group.trace)
                    currentGroup.trace.add(trace);
            }
        }
    }

    private void move(StateGroup currentGroup) {
        if (currentGroup.logTrans == null)
        {
            processing.remove(currentGroup);
            return;
        }

        if (currentGroup.dictTrans != null)
            currentGroup.dictCurrState = currentGroup.dictTrans.getTarget();

        currentGroup.logCurrState = currentGroup.logTrans.getTarget();

        //TODO XES Result Log form
    }
}
