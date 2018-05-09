package org.processmining.plugins.fsmeventlogreduction.reductionalgo;

import javafx.util.Pair;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.impl.*;
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

    private Map<State, Map<Object, Transition>> outEdgesByIdentifiers;
    private State dictRoot;

    private XLog resultLog;
    private String keyActivity;
    private FSMTransitionSystem fsmLog;
    private DictionaryTransitionSystem fsmDictionary;
    private ArrayList<SortedMap<Integer, Pair<Integer, String>>> foundSubs = new ArrayList<>();

    public FsmEventLogReductionAlgo(FSMTransitionSystem fsmLog, DictionaryTransitionSystem fsmDictionary,
                                    XLog log, String keyActivity){

        this.fsmDictionary = fsmDictionary;
        this.fsmLog = fsmLog;

        this.logRoot = fsmLog.getNode(",");
        this.dictRoot = fsmDictionary.getNode("");
        this.transByFromState = fsmLog.getTransByFromState();
        this.traceByToState = fsmLog.getTracesByToStates();
        this.outEdgesByIdentifiers = fsmDictionary.getOutEdgesByIdentifiers();

        //origLog = log;
        this.keyActivity = keyActivity;
        resultLog = new XLogImpl(log.getAttributes());
        for (int i = 0; i < log.size(); ++i) {
            resultLog.add(new XTraceImpl(log.get(i).getAttributes()));
            foundSubs.add(new TreeMap<>());
        }
    }

    public XLog reduceFsmTypeLog() {
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
        } else {
            setNewTransitions(processing.iterator().next(), fsmLog.getOutEdges(logRoot).iterator().next());
        }

        while (!processing.isEmpty()) {
            Iterator<StateGroup> i = processing.iterator();
            StateGroup group = i.next();
            if (group.trace.size() == 0 || group.logTrans == null || fsmLog.getOutEdges(group.logCurrState).size() < 1)
                i.remove();
            else {
                Set<StateGroup> processingCopy = new LinkedHashSet<>(processing);
                for (StateGroup groupToUni : processingCopy)
                    if (processing.contains(groupToUni))
                        unionGroups(groupToUni);

                for (StateGroup groupToMove : processing)
                    move(groupToMove);

                for (StateGroup groupToSplit : processing) {
                    if (fsmLog.getOutEdges(groupToSplit.logCurrState).size() == 1)
                        setNewTransitions(groupToSplit, fsmLog.getOutEdges(groupToSplit.logCurrState).iterator().next());
                    else
                        splitGroup(groupToSplit);
                }

                while (!toProcess.isEmpty()) {
                    splitGroup(toProcess.peek());
                    processing.add(toProcess.remove());
                }
            }
        }

        formResultingLog();
        return resultLog;
    }

    private void splitGroup(StateGroup currentGroup) {
        StateGroup otherGroup = new StateGroup(currentGroup.logCurrState, currentGroup.dictCurrState,
                null, null, new LinkedHashSet<>());

        Transition newTrans = null;
        if (currentGroup.trace.iterator().hasNext())
            newTrans = transByFromState.get(currentGroup.trace.iterator().next()).get(currentGroup.logCurrState);

        for (Iterator<Integer> i = currentGroup.trace.iterator(); i.hasNext();) {
            Integer trace = i.next();
            if (newTrans == null || !traceByToState.get(newTrans.getTarget()).contains(trace) ||
                    !newTrans.equals(transByFromState.get(trace).get(currentGroup.logCurrState))) {
                otherGroup.trace.add(trace);
                i.remove();
            }
        }

        setNewTransitions(currentGroup, newTrans);
        if (otherGroup.trace.size() > 0 && !currentGroup.trace.isEmpty())
            toProcess.add(otherGroup);

    }

    private void setNewTransitions(StateGroup group, Transition newTrans) {
        if (group.logTrans != null)
            equalLogTrans.get(group.logTrans).remove(group);

        if (group.dictTrans != null)
            equalDictTrans.get(group.dictTrans).remove(group);

        if (newTrans != null) {
            group.logTrans = newTrans;
            if (group.dictTrans != null)
                equalDictTrans.get(group.dictTrans).remove(group);

            setNextDictPos(group);
            if (!equalLogTrans.containsKey(group.logTrans))
                equalLogTrans.put(group.logTrans, new LinkedHashSet<>());

            equalLogTrans.get(group.logTrans).add(group);

            if (group.dictTrans != null)
            {
                if (!equalDictTrans.containsKey(group.dictTrans))
                    equalDictTrans.put(group.dictTrans, new LinkedHashSet<>());

                equalDictTrans.get(group.dictTrans).add(group);
            }
        }
    }

    private void setNextDictPos(StateGroup group) {
        Transition nextTrans = outEdgesByIdentifiers.get(group.dictCurrState).get(group.logTrans.getIdentifier());
        if (nextTrans != null) {
            group.dictTrans = nextTrans;
            return;
        }

        while (group.dictCurrState != dictRoot && nextTrans == null) {
            group.dictCurrState = outEdgesByIdentifiers.get(group.dictCurrState).
                    get(fsmDictionary.getSuffixLinkIdentifier()).getTarget();
            nextTrans = outEdgesByIdentifiers.get(group.dictCurrState).get(group.logTrans.getIdentifier());
        }

        group.dictTrans = nextTrans;
    }

    private void unionGroups(StateGroup currentGroup) {
        if (currentGroup.logTrans == null || currentGroup.dictTrans == null)
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

        if (currentGroup.logCurrState != null && currentGroup.dictCurrState.isAccepting())
            rememberFoundSubs(currentGroup);
    }

    private void rememberFoundSubs(StateGroup group) {
        State logState = group.logCurrState;
        State dictState = group.dictCurrState;
        rememberFoundSubsInternal(logState, dictState, group.trace);

        Transition finalLink = outEdgesByIdentifiers.get(dictState).get(fsmDictionary.getFinalSuffixLinkIdentifier());
        while (finalLink != null) {
            dictState = finalLink.getTarget();
            rememberFoundSubsInternal(logState, dictState, group.trace);
            finalLink = outEdgesByIdentifiers.get(dictState).get(fsmDictionary.getFinalSuffixLinkIdentifier());
        }
    }

    private void rememberFoundSubsInternal(State logState, State dictState, Set<Integer> traces) {
        for (int trace : traces) {
            if (fsmLog.getDistByToState(trace, logState) == null)
                continue;

            int dictDist = fsmDictionary.getDistByToState(dictState);
            int logDist = fsmLog.getDistByToState(trace, logState);
            int startPos = logDist - dictDist;
            foundSubs.get(trace).put(startPos, new Pair<>(dictDist, fsmDictionary.getTraceName(fsmDictionary.getTraceByFinalState(dictState))));
        }
    }

    private void formResultingLog() {
        for (int trace = 0; trace < foundSubs.size(); ++trace) {
            Iterator<Integer> keyIter = foundSubs.get(trace).keySet().iterator();
            int pos = 0;
            while (pos < fsmLog.getTraceLength(trace)) {
                if (keyIter.hasNext()) {
                    int key = keyIter.next();
                    while (pos < key) {
                        State state = fsmLog.getStateByDists(trace, pos);
                        Transition trans = fsmLog.getTransByFromState().get(trace).get(state);
                        addEventToResultLog(trace, trans.getIdentifier().toString());
                        ++pos;
                    }

                    if (pos == key) {
                        Pair<Integer, String> val = foundSubs.get(trace).get(key);
                        pos += val.getKey();
                        addEventToResultLog(trace, val.getValue());
                    }
                }
                else {
                    State state = fsmLog.getStateByDists(trace, pos);
                    Transition trans = fsmLog.getTransByFromState().get(trace).get(state);
                    addEventToResultLog(trace, trans.getIdentifier().toString());
                    ++pos;
                }
            }
        }
    }

    private void addEventToResultLog(int trace, String trans) {
        XEventImpl event = new XEventImpl();
        event.setAttributes(new XAttributeMapImpl());
        event.getAttributes().put(keyActivity,
                new XAttributeLiteralImpl(keyActivity, trans));
        resultLog.get(trace).add(event);
    }

}
