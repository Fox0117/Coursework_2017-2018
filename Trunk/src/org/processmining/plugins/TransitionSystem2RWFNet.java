package org.processmining.plugins;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.DirectedGraphElement;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.ResetNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.impl.ResetNetImpl;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;

@Plugin(name = "Transition system to a RWF-net", 
	parameterLabels = { "Transition System" }, returnLabels = {"Petri net"}, 
	returnTypes = {ResetNet.class}, userAccessible = true, 
	help = "Converts transition system to a WF-net with reset arcs")
	public class TransitionSystem2RWFNet {

	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Transition system to a RWF-net", requiredParameterLabels = { 0 })
	public ResetNet convert(UIPluginContext context, TransitionSystem transitionSystem) {

		Progress progress = context.getProgress();
		progress.setCaption("Converting transition system to a WF-net with reset arcs");
		
		Set<State> cancellationStates = mineCancellationStates(transitionSystem);
		TransitionSystem transitionSystemWithoutFailureEdges = 
				cloneTransitionSystemWithoutFailures(transitionSystem, cancellationStates);
		
		Petrinet petrinet = null;
		try {
			petrinet = context.tryToFindOrConstructFirstObject(Petrinet.class, null, null, 
					transitionSystemWithoutFailureEdges);
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " 
		+ transitionSystemWithoutFailureEdges.getLabel());
			e.printStackTrace();
		}

		ResetNet petrinetGraph = null;
		if (petrinet != null) {
			petrinetGraph = constructResetNet(petrinet);
			addCancellationsToPetriNet(petrinetGraph, transitionSystem, transitionSystemWithoutFailureEdges,
					cancellationStates, context);
		}
		progress.setCaption("Getting WF-net Visualization");
		return petrinetGraph;
	}
	
	private ResetNet constructResetNet(Petrinet petrinet) {
		HashMap<DirectedGraphElement, DirectedGraphElement> mapping = new HashMap<DirectedGraphElement, DirectedGraphElement>();

		ResetNet resetNet = new ResetNetImpl(petrinet.getLabel());
		for (org.processmining.models.graphbased.directed.petrinet.elements.Transition t : petrinet.getTransitions()) {
			org.processmining.models.graphbased.directed.petrinet.elements.Transition copy = resetNet.addTransition(t
					.getLabel());
			copy.setInvisible(t.isInvisible());
			mapping.put(t, copy);
		}

		for (Place p : petrinet.getPlaces()) {
			Place copy = resetNet.addPlace(p.getLabel());
			mapping.put(p, copy);
		}

		for (PetrinetEdge e : petrinet.getEdges()) {
			if ((mapping.get(e.getSource()) instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)
					&& (mapping.get(e.getTarget()) instanceof Place)) {
				mapping.put(e, resetNet.addArc(
						(org.processmining.models.graphbased.directed.petrinet.elements.Transition) mapping.get(e
								.getSource()), (Place) mapping.get(e.getTarget())));
			}
			if ((mapping.get(e.getTarget()) instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)
					&& (mapping.get(e.getSource()) instanceof Place)) {
				mapping.put(e, resetNet.addArc((Place) mapping.get(e.getSource()),
						(org.processmining.models.graphbased.directed.petrinet.elements.Transition) mapping.get(e
								.getTarget())));
			}
		}
		return resetNet;
	}
	
	private Set<State> mineCancellationStates(TransitionSystem transitionSystem) {
		Set<State> cancellationStates = new HashSet<State>();
		for(State state : transitionSystem.getNodes()) {
			boolean isCancellationState = true;
			// Analyze incoming transitions
			for(Transition inTransition : transitionSystem.getInEdges(state)) {
				if ((!isTransitionWithUniqueLabel(inTransition, transitionSystem, true) 
					|| (!hasNoLessThanTwoTransitionsWithLable(inTransition, transitionSystem, true)))) {
					isCancellationState = false;
				}
			}
			// Analyze outgoing transitions
			for(Transition outTransition : transitionSystem.getOutEdges(state)) {
				if ((!isTransitionWithUniqueLabel(outTransition, transitionSystem, false) 
					|| (!allTransitionsWithLable(outTransition, transitionSystem, false)))) {
					isCancellationState = false;
				}
			}
			if(isCancellationState) {
				cancellationStates.add(state);
			}
		}
		return cancellationStates;
	}
	
	private boolean isTransitionWithUniqueLabel(Transition transition, 
			TransitionSystem transitionSystem, boolean isIncomng) {
		
		boolean hasUniqueLable = true;
		String label = transition.getLabel();
		State state = null;
		Collection<Transition> stateTransitions = null;
		if(isIncomng) {
			state = transition.getTarget();
			stateTransitions = transitionSystem.getInEdges(state);
		} else {
			state = transition.getSource();
			stateTransitions = transitionSystem.getOutEdges(transition.getSource());
		}
		for(Transition anyTransition : transitionSystem.getEdges()) {
			if(anyTransition.getLabel().equals(label)
				&& (!stateTransitions.contains(anyTransition))) {
				hasUniqueLable = false;
			}
		}
		return hasUniqueLable;
	}
	
	private boolean hasNoLessThanTwoTransitionsWithLable(Transition transition, 
			TransitionSystem transitionSystem, boolean isIncomng) {
		
		boolean twoTransitionsWithLable = false;
		String label = transition.getLabel();
		State state = null;
		Collection<Transition> stateTransitions = null;
		if(isIncomng) {
			state = transition.getTarget();
			stateTransitions = transitionSystem.getInEdges(state);
		} else {
			state = transition.getSource();
			stateTransitions = transitionSystem.getOutEdges(transition.getSource());
		}
		for(Transition anyTransition : stateTransitions) {
			if(anyTransition.getLabel().equals(label)
				&& (!anyTransition.equals(transition))) {
				twoTransitionsWithLable = true;
			}
		}
		return twoTransitionsWithLable;
	}
	
	private boolean allTransitionsWithLable(Transition transition, 
			TransitionSystem transitionSystem, boolean isIncomng) {

		boolean allTransitionsWithLable = true;
		String label = transition.getLabel();
		State state = null;
		Collection<Transition> stateTransitions = null;
		if (isIncomng) {
			state = transition.getTarget();
			stateTransitions = transitionSystem.getInEdges(state);
		} else {
			state = transition.getSource();
			stateTransitions = transitionSystem.getOutEdges(transition.getSource());
		}
		for (Transition anyTransition : stateTransitions) {
			if (!anyTransition.getLabel().equals(label)) {
				allTransitionsWithLable = false;
			}
		}
		return allTransitionsWithLable;
	}
	
	private TransitionSystem cloneTransitionSystemWithoutFailures(TransitionSystem transitionSystem,
			Set<State> cancellationSet) {
		TransitionSystem clone = new TransitionSystemImpl(transitionSystem.getLabel());
		for(State state : transitionSystem.getNodes()) {
			clone.addState(state);
		}
		for(Transition transition : transitionSystem.getEdges()) {
			if(!cancellationSet.contains(transition.getTarget())) {
				clone.addTransition(transition.getSource(), 
						transition.getTarget(), transition.getLabel());
			}
		}
		return clone;
	}
	
	private void addCancellationsToPetriNet(ResetNet petrinetGraph, 
			TransitionSystem transitionSystem, TransitionSystem transitionSystemWithoutFailureEdges,
			Set<State> cancellationStates,  UIPluginContext context) {
		for(State cancellationState : cancellationStates) {
			for(Transition inTransition : retriveInTransitionsWithDiffLabels(cancellationState, transitionSystem)) {
				addFailureTransition(inTransition, petrinetGraph, transitionSystem, 
						transitionSystemWithoutFailureEdges, context);
			}
		}
	}
	
	private void connectFailureTransition(org.processmining.models.graphbased.directed.petrinet.elements.Transition petriNetTransition,
			ResetNet petrinetGraph, TransitionSystem transitionSystem,
			TransitionSystem transitionSystemWithoutFailureEdges, UIPluginContext context) {
		Set<State> cancellationSet 
			= retrieveCancellationSet(petriNetTransition.getLabel(), transitionSystem);
		Place placeInPetriNet = findCorrespondingPlace(cancellationSet, petrinetGraph, transitionSystem);
		if(placeInPetriNet != null) {
			petrinetGraph.addArc(placeInPetriNet, petriNetTransition);
		}
		cancellationSet = retrieveCancellationSet(petriNetTransition.getLabel(), transitionSystem);
		connectByResetArcs(cancellationSet, petriNetTransition, transitionSystem,
				transitionSystemWithoutFailureEdges, petrinetGraph, context);
	}
	
	private void connectByResetArcs(Set<State> cancellationSet,
			org.processmining.models.graphbased.directed.petrinet.elements.Transition petriNetTransition,
			TransitionSystem transitionSystem, TransitionSystem transitionSystemWithoutFailureEdges,
			ResetNet petrinetGraph, UIPluginContext context) {
		RegionSet regions = retrieveRegions(transitionSystemWithoutFailureEdges, context);
		if (regions != null) {
			System.out.println("Cancellation set " + cancellationSet);
			for (Region region : regions) {
				System.out.println("Region " + region);
				Place placeInPetriNet = findCorrespondingPlace(region, petrinetGraph, 
						transitionSystemWithoutFailureEdges);
				if (placeInPetriNet != null) {
					if (regionIntersectsWithCancellationSet(region, cancellationSet)) {
						petrinetGraph.addResetArc(placeInPetriNet, petriNetTransition);
					}
				}
			}
		}
	}
	
	private boolean regionIntersectsWithCancellationSet(Region region, Set<State> cancellationSet) {
		boolean intersects = false;
		Set<String> setOfLabels = new HashSet<String>();
		for(State state : cancellationSet) {
			setOfLabels.add(state.getLabel());
		}
		for(State stateInRegion : region) {
			if(setOfLabels.contains(stateInRegion.getLabel())) {
				intersects = true;
			}
		}
		return intersects;
	}
	
	private RegionSet retrieveRegions(TransitionSystem transitionSystem, UIPluginContext context) {
		RegionSet regions = null;
		try {
			regions = context.tryToFindOrConstructFirstObject(RegionSet.class, null, null, 
					transitionSystem);
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " 
		+ transitionSystem.getLabel());
			e.printStackTrace();
		}
		return regions;
	}
	
	private Place findCorrespondingPlace(Set<State> states, ResetNet petrinetGraph, 
			TransitionSystem transitionSystem) {
		Set<String> entyLabels = retieveEntryLabels(states, transitionSystem);
		Set<String> exitLabels = retieveExitLabels(states, transitionSystem);
		for(Place place : petrinetGraph.getPlaces()) {
			boolean isTargetPlace = true;
			for(PetrinetEdge inEdge : petrinetGraph.getInEdges(place)) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition inTransition 
					= (org.processmining.models.graphbased.directed.petrinet.elements.Transition)inEdge.getSource();
				if(!entyLabels.contains(inTransition.getLabel())) {
					isTargetPlace = false;
					break;
				}
			}
			for(PetrinetEdge outEdge : petrinetGraph.getOutEdges(place)) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition outTransition 
					= (org.processmining.models.graphbased.directed.petrinet.elements.Transition)outEdge.getTarget();
				if(!exitLabels.contains(outTransition.getLabel())) {
					isTargetPlace = false;
					break;
				}
			}
			if(isTargetPlace) {
				return place;
			}
		}
		return null;
	}
	
	private Set<String> retieveExitLabels(Set<State> states, TransitionSystem transitionSystem) {
		Set<String> exitLabels = new HashSet<String>();
		for(State state : states) {
			for(Transition outTransition: transitionSystem.getOutEdges(state)) {
				if(!states.contains(outTransition.getTarget())) {
					exitLabels.add(outTransition.getLabel());
				}
			}
		}
		return exitLabels;
	}
	
	private Set<String> retieveEntryLabels(Set<State> states, TransitionSystem transitionSystem) {
		Set<String> entryLabels = new HashSet<String>();
		for(State state : states) {
			for(Transition inTransition: transitionSystem.getInEdges(state)) {
				if(!states.contains(inTransition.getSource())) {
					entryLabels.add(inTransition.getLabel());
				}
			}
		}
		return entryLabels;
	}

	private Set<State> retrieveCancellationSet(String failureLabel, TransitionSystem transitionSystem) {
		Set<State> resultSetOfStates = new HashSet<State>();
		for (Object state : transitionSystem.getNodes()) {
			if (state instanceof State) {
				for (Transition outTransition : transitionSystem.getOutEdges((State)state)) {
					if (outTransition.getLabel().equals(failureLabel)) {
						resultSetOfStates.add((State) state);
						break;
					}
				}
			}
		}
		return resultSetOfStates;
	}
	
	private Set<Transition> retriveInTransitionsWithDiffLabels(State state, 
			TransitionSystem transitionSystem) {
		Set<Transition> resultTransitions = new HashSet<Transition>();
		for(Transition inTransition : transitionSystem.getInEdges(state)) {
			boolean newLabel = true;
			for (Transition transition : resultTransitions) {
				if(inTransition.getLabel().equals(transition.getLabel())) {
					newLabel = false;
				}
			}
			if(newLabel) {
				resultTransitions.add(inTransition);
			}
		}
		return resultTransitions;
	}
	
	private void addFailureTransition(Transition transition, ResetNet petrinetGraph, 
			TransitionSystem transitionSystem, TransitionSystem transitionSystemWithoutFailureEdges, 
			UIPluginContext context) {
		org.processmining.models.graphbased.directed.petrinet.elements.Transition petriNetTransition 
			= petrinetGraph.addTransition(transition.getLabel());
		Place cancellationPlace = findCancellationPlace(transition.getTarget(), petrinetGraph,
				transitionSystem);
		if(cancellationPlace != null) {
			petrinetGraph.addArc(petriNetTransition, cancellationPlace);
		}
		connectFailureTransition(petriNetTransition, petrinetGraph, transitionSystem, 
				transitionSystemWithoutFailureEdges, context);
	}
	
	private Place findCancellationPlace(State cancellationState, ResetNet petrinetGraph,
			TransitionSystem transitionSystem) {
		Transition outTransition = transitionSystem.getOutEdges(cancellationState).iterator().next();
		String catchingLabel = outTransition.getLabel();
		for(Place place : petrinetGraph.getPlaces()) {
			PetrinetEdge outPetriNetEdge;
			Iterator placesIterator =  petrinetGraph.getOutEdges(place).iterator();
			if(placesIterator.hasNext()) {
				outPetriNetEdge = (PetrinetEdge)placesIterator.next();
			} else {
				continue;
			}
			org.processmining.models.graphbased.directed.petrinet.elements.Transition petriNetTransition 
				= (org.processmining.models.graphbased.directed.petrinet.elements.Transition)
					outPetriNetEdge.getTarget();
			if(petriNetTransition.getLabel().equals(outTransition.getLabel())) {
				return place;
			}
		}
		return null;
	}
}
