package org.processmining.plugins.fsmeventlogreduction.reductionalgo;

import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;

import java.util.Set;

public class StateGroup {
    public State logCurrState;
    public State dictCurrState;
    public Transition logTrans;
    public Transition dictTrans;
    public Set<Integer> trace;

    public StateGroup(State logCurrState, State dictCurrState, Transition logTrans, Transition dictTrans, Set<Integer> trace) {
        this.dictCurrState = dictCurrState;
        this.dictTrans = dictTrans;
        this.logCurrState = logCurrState;
        this.logTrans = logTrans;
        this.trace = trace;
    }
}
