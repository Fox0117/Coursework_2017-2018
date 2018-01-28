package org.processmining.plugins;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;

@Plugin(name = "Build transition system (using prefixes)", 
	parameterLabels = { "Event log" }, returnLabels = {"Transition system"}, 
	returnTypes = {TransitionSystem.class}, userAccessible = true, 
	help = "Constructs a transition system form an event log (using prefixes)")

public class PrefixBasedTSBuilder {
	static private int WINDOW_SIZE = 10;
	private XLog log;
	private TransitionSystem transitionSystem;
	
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Construct transition system using prefixes", requiredParameterLabels = { 0 })
	public TransitionSystem constructTS(PluginContext context, XLog log) {
		
		this.log = log;
		buildTransitionSystem();
		
		return transitionSystem;
	}
	
	/**
	 * Build a transition system from log using window and prefixes
	 * 
	 */
	private void buildTransitionSystem() {

		// Initialize TS 
		transitionSystem = new TransitionSystemImpl("Transition system from log "
				+ log.getAttributes().get("concept:name").toString());

		// For each trace define states and transitions 
		for (XTrace trace : log) {
			State previosState = null;
			for (int i = 0; i <= trace.size(); i++) {

				String stateName = defineStateName(trace, i);				
				State state = retrieveStateByName(stateName);
				
				if (state == null) {
					transitionSystem.addState(stateName);
					state = retrieveStateByName(stateName);
					state.setLabel(stateName);
				} 
				if (previosState != null) {
					transitionSystem.addTransition(previosState.getIdentifier(), 
							state.getIdentifier(), trace.get(i-1).getAttributes().get("concept:name"));
				}
				if(i == trace.size()) {
					state.setAccepting(true);
				}
				previosState = state;
			}
		}
	}
	
	/**
	 * Define a name of a state for the trace and index specified
	 * 
	 * @param trace
	 * @param index
	 * @return
	 */
	private String defineStateName(XTrace trace, int index) {	
		String stateName = "<";
		for (int j = WINDOW_SIZE; j >= 1; j--) {
			if (index >= j) {
				if (j < WINDOW_SIZE) {
					stateName += ",";
				}
				stateName += trace.get(index - j).getAttributes().get("concept:name");
			}
		}
		stateName += ">";
		return stateName;
	}
	
	/**
	 * Retrieve a state with the name specified from the transition system
	 * 
	 * @param name
	 * @return
	 */
	private State retrieveStateByName(String name) {
		
		for(Object identifier : transitionSystem.getStates()) {
			State state = transitionSystem.getNode(identifier);
			if((identifier != null) && (identifier.equals(name))) {
				return state;
			}
		}
		return null;
	}
}
