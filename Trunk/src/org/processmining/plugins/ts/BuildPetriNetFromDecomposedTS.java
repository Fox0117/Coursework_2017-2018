package org.processmining.plugins.ts;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.transitionsystem.MinimalRegionConnection;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;
import org.processmining.plugins.ts.ui.TSDecomposeUI;

@Plugin(name = "Build Petri net from decomposed transition system", 
parameterLabels = { "Transition system" }, returnLabels = {"Special Transition system"}, 
returnTypes = {TransitionSystem.class}, userAccessible = true, 
help = "Build Petri net from decomposed transition system")

public class BuildPetriNetFromDecomposedTS {

	private TransitionSystem transitionSystem;
	
	private TransitionSystem regularTransitionSystem;
	
	private TransitionSystem specialTransitionSystem;
	
	private Map<Object, Set<Object>> descendants = new HashMap<Object, Set<Object>>();
	
	List<Object> specStates;
	
	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Decompose transition system"
			+ " and synthesize Petri net", requiredParameterLabels = { 0 })
	public TransitionSystem constructTS(UIPluginContext context, TransitionSystem transitionSystem) {
		
		this.transitionSystem = transitionSystem;

		decomposeTransitionSystem(context);
		
		Petrinet regularPetrinet = minePetrinet(context, regularTransitionSystem);
			
		Petrinet specialPetrinet = minePetrinet(context, specialTransitionSystem);
		
		return specialTransitionSystem;
	}
	
	
	private void constructDescendants () {
		
		boolean somethingToAdd = true;
		
		// init
		for (Object id : transitionSystem.getStates()) {
			descendants.put(id, new HashSet<Object>());
		}
		
		while(somethingToAdd) {
			somethingToAdd = false;
			for (Object id : transitionSystem.getStates()) {
				Set<Object> des = new HashSet<Object>();
				for(Transition outTransition : transitionSystem.getOutEdges(transitionSystem.getNode(id))){
					des.add(outTransition.getTarget().getIdentifier());
					des.addAll(descendants.get(outTransition.getTarget().getIdentifier()));
				}
				if(!des.containsAll(descendants.get(id)) || !descendants.get(id).containsAll(des)) {
					descendants.put(id, des);
					somethingToAdd = true;
				}
			}
		}
	} 
	
	private Petrinet minePetrinet(PluginContext context, TransitionSystem transitionSystem) {
		System.out.println("Mine Petri net for " + transitionSystem);
		Petrinet petrinet = null;
		try {
			petrinet = context.tryToFindOrConstructFirstObject(Petrinet.class, null, null, 
					transitionSystem);
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " 
		+ transitionSystem.getLabel());
			e.printStackTrace();
		}
		
		return petrinet;
	}
	
	/**
	 * Construct two transition systems out of one
	 */
	private void decomposeTransitionSystem(UIPluginContext context) {
		constructDescendants();
		TSDecomposeUI decomposeUI = new TSDecomposeUI(context);
		specStates = decomposeUI.decompose(transitionSystem);
		buildRegularTS();
		buildSpecialTS();
	}
	
	private TransitionSystem buildRegularTS() {
		regularTransitionSystem = new TransitionSystemImpl("Regular TS");
		
		Map<State, State> mapStates = new HashMap<State, State>();
		
		Map<Transition, Transition> mapTransitions = new HashMap<Transition, Transition>();

		for (Object identifier : transitionSystem.getStates()) {
			if (!specStates.contains(transitionSystem.getNode(identifier))) {
				
				regularTransitionSystem.addState(identifier);
				
				mapStates.put(transitionSystem.getNode(identifier), regularTransitionSystem.getNode(identifier));
			}
		}

		for (Transition transition : transitionSystem.getEdges()) {
			if(!specStates.contains(transition.getSource())
					&& !specStates.contains(transition.getTarget())) {
				
				regularTransitionSystem.addTransition(transition.getSource().getIdentifier(),
						transition.getTarget().getIdentifier(), transition.getIdentifier());
				
				mapTransitions.put(transition, regularTransitionSystem.findTransition(transition.getSource().getIdentifier(),
								transition.getTarget().getIdentifier(), transition.getIdentifier()));
			}
		}
		
		restoreRegularTS();
		
		return regularTransitionSystem;
	}

	private Object getStartState() {
		for (Object identifier : transitionSystem.getStates()) {
			State state = transitionSystem.getNode(identifier);
			if ((transitionSystem.getInEdges(state) == null) 
					|| (transitionSystem.getInEdges(state).size() == 0)) {
				return identifier;
			}
		}
		
		return null;
	}
	
	private Set<Object> getInStatesForRegularTS(Object identifier) {
		
		Set<Object> inStates = new HashSet<Object>();
		for (Object inIdentifier : regularTransitionSystem.getStates()) {
			if(existsPath(inIdentifier, identifier)) {
				boolean isImmediateInState = true;
				for (Object otherIdentifier : regularTransitionSystem.getStates()) {
					if(!otherIdentifier.equals(inIdentifier) && existsPath(inIdentifier, otherIdentifier)
							&& !existsPath(otherIdentifier, inIdentifier) && existsPath(otherIdentifier, identifier)) {
						isImmediateInState = false;
					}
				}
				if(isImmediateInState) {
					inStates.add(inIdentifier);
				}
			}
		}
		
		return inStates;
	}
	
	private boolean existsPath (Object id1, Object id2) {
		return descendants.get(id1).contains(id2);
	}
	
	private void restoreRegularTS() {
		
		int cnt = 0;
		for (Object identifier : regularTransitionSystem.getStates()) {		
			State state = regularTransitionSystem.getNode(identifier);
			if ((regularTransitionSystem.getInEdges(state) == null) 
					|| (regularTransitionSystem.getInEdges(state).size() == 0)) {
				if(!identifier.equals(getStartState())) {
					for(Object inState : getInStatesForRegularTS(identifier)) {
						regularTransitionSystem.addTransition(inState, identifier, "e@" + cnt);
						cnt++;
					}					
				}
			}
		}		
	}
	
	
	
	private TransitionSystem buildSpecialTS() {
		specialTransitionSystem = new TransitionSystemImpl("Special TS");
		
		Map<State, State> mapStates = new HashMap<State, State>();
		
		Map<Transition, Transition> mapTransitions = new HashMap<Transition, Transition>();

		String startState = new String("start");
		specialTransitionSystem.addState(startState);
		
		String endState = new String("end");
		specialTransitionSystem.addState(endState);
		
		
		for (Object identifier : transitionSystem.getStates()) {
			if (specStates.contains(transitionSystem.getNode(identifier))) {
				
				specialTransitionSystem.addState(identifier);
				
				mapStates.put(transitionSystem.getNode(identifier), specialTransitionSystem.getNode(identifier));
			}
		}

		for (Transition transition : transitionSystem.getEdges()) {
			if(specStates.contains(transition.getSource())
					&& specStates.contains(transition.getTarget())) {
				
				specialTransitionSystem.addTransition(transition.getSource().getIdentifier(),
						transition.getTarget().getIdentifier(), transition.getIdentifier());
				
				mapTransitions.put(transition, specialTransitionSystem.findTransition(transition.getSource().getIdentifier(),
								transition.getTarget().getIdentifier(), transition.getIdentifier()));
			}
			if(!specStates.contains(transition.getSource())
					&& specStates.contains(transition.getTarget())) {
				specialTransitionSystem.addTransition(startState, transition.getTarget().getIdentifier(), "start");
			}
			
			if(specStates.contains(transition.getSource())
					&& !specStates.contains(transition.getTarget())) {
				specialTransitionSystem.addTransition(transition.getSource().getIdentifier(), endState, "end");
			}
		}
		
		return specialTransitionSystem;
	}
	
//	private Petrinet mergePetriNets(UIPluginContext context, Petrinet regularPetrinet, Petrinet specialPetrinet) {
//		
//		Petrinet resultPetriNet = new PetrinetImpl("Unified model");
//		
//		// Add regular behavior
//		Map<Object, Object> regMap = new HashMap<Object, Object>();
//		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : regularPetrinet.getTransitions()) {
//			org.processmining.models.graphbased.directed.petrinet.elements.Transition newTransition =
//					resultPetriNet.addTransition(transition.getLabel());
//			newTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (255,223,0));
//			regMap.put(transition, newTransition);
//		}
//		for(Place place : regularPetrinet.getPlaces()) {
//			Place newPlace = resultPetriNet.addPlace(place.getLabel());
//			regMap.put(place, newPlace);
//		}
//		for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : regularPetrinet.getEdges()) {
//			if ((edge.getSource() instanceof Place) 
//					&& (edge.getTarget() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
//				resultPetriNet.addArc((Place)regMap.get(edge.getSource()), 
//						(org.processmining.models.graphbased.directed.petrinet.elements.Transition)regMap.get(edge.getTarget()));
//			}
//			if ((edge.getTarget() instanceof Place) 
//					&& (edge.getSource() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
//				resultPetriNet.addArc((org.processmining.models.graphbased.directed.petrinet.elements.Transition)regMap.get(edge.getSource()), 
//						(Place)regMap.get(edge.getTarget()));
//			}
//		}
//		
//		// Add special behavior
//				Map<Object, Object> specMap = new HashMap<Object, Object>();
//				for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : specialPetrinet.getTransitions()) {
//					org.processmining.models.graphbased.directed.petrinet.elements.Transition newTransition =
//							resultPetriNet.addTransition(transition.getLabel());
//					newTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (153,101,21));
//					specMap.put(transition, newTransition);
//				}
//				for(Place place : specialPetrinet.getPlaces()) {
//					Place newPlace = resultPetriNet.addPlace(place.getLabel());
//					specMap.put(place, newPlace);
//				}
//				for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : specialPetrinet.getEdges()) {
//					if ((edge.getSource() instanceof Place) 
//							&& (edge.getTarget() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
//						resultPetriNet.addArc((Place)specMap.get(edge.getSource()), 
//								(org.processmining.models.graphbased.directed.petrinet.elements.Transition)specMap.get(edge.getTarget()));
//					}
//					if ((edge.getTarget() instanceof Place) 
//							&& (edge.getSource() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
//						resultPetriNet.addArc((org.processmining.models.graphbased.directed.petrinet.elements.Transition)specMap.get(edge.getSource()), 
//								(Place)specMap.get(edge.getTarget()));
//					}
//				}
//		
//		RegionSet regRegions = mineRegions(context, regularTransitionSystem);
//		
//		RegionSet specRegions = mineRegions(context, specialTransitionSystem);
//
//		Map<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>> inPlaces 
//			= new HashMap<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>>();
//		Map<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>> outPlaces 
//			= new HashMap<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>>();
//		// Add escape transitions
//		for (Transition transition : transitionSystem.getEdges()) {
//
//			if (!specStates.contains(transition.getSource()) && specStates.contains(transition.getTarget())) {
//				System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
//				System.out.println(regRegions.size());
//				org.processmining.models.graphbased.directed.petrinet.elements.Transition escapeTransition = resultPetriNet
//						.addTransition(transition.getLabel());
//				inPlaces.put(escapeTransition, new HashSet<Place>());
//				outPlaces.put(escapeTransition, new HashSet<Place>());
//				for (Region region : regRegions) {
//					System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%55555555");
//					System.out.println(region);
//					State state = regularTransitionSystem.getNode(transition.getSource().getIdentifier());
//					System.out.println(state);
//					if (region.contains(state)) {
//						System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&777");
//						Place place = findCorrespondingPlace(resultPetriNet, region);
//						if (place != null) {
//							inPlaces.get(escapeTransition).add(place);
//						}
//					}
//				}
//				for (Region region : specRegions) {
//					System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%55555555");
//					System.out.println(region);
//					State state = specialTransitionSystem.getNode(transition.getTarget().getIdentifier());
//					System.out.println(state);
//					if (region.contains(state)) {
//						System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&777");
//						Place place = findCorrespondingPlace(resultPetriNet, region);
//						if (place != null) {
//							outPlaces.get(escapeTransition).add(place);
//						}
//					}
//				}
//			}
//		}
//		
//		// Add return transitions
//		for (Transition transition : transitionSystem.getEdges()) {
//			if (specStates.contains(transition.getSource()) && !specStates.contains(transition.getTarget())) {
//				System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
//				System.out.println(specRegions.size());
//				org.processmining.models.graphbased.directed.petrinet.elements.Transition returnTransition = resultPetriNet
//						.addTransition(transition.getLabel());
//				inPlaces.put(returnTransition, new HashSet<Place>());
//				outPlaces.put(returnTransition, new HashSet<Place>());
//				for (Region region : specRegions) {
//					System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%55555555");
//					System.out.println(region);
//					State state = specialTransitionSystem.getNode(transition.getSource().getIdentifier());
//					System.out.println(state);
//					if (region.contains(state)) {
//						System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&777");
//						Place place = findCorrespondingPlace(resultPetriNet, region);
//						if (place != null) {
//							System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1");
//							inPlaces.get(returnTransition).add(place);
//						}
//					}
//				}
//				for (Region region : regRegions) {
//					System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%55555555");
//					System.out.println(region);
//					State state = specialTransitionSystem.getNode(transition.getTarget().getIdentifier());
//					System.out.println(state);
//					if (region.contains(state)) {
//						System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&777");
//						Place place = findCorrespondingPlace(resultPetriNet, region);
//						if (place != null) {
//							System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1");
//							//resultPetriNet.addArc(returnTransition, place);
//							inPlaces.get(returnTransition).add(place);
//						}
//					}
//				}
//			}
//		}
//		
//		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition
//				: inPlaces.keySet()) {
//			for(Place place : inPlaces.get(transition)) {
//				resultPetriNet.addArc(place, transition);
//			}
//		}
//		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition
//				: outPlaces.keySet()) {
//			for(Place place : outPlaces.get(transition)) {
//				resultPetriNet.addArc(transition, place);
//			}
//		}
//		
//		return resultPetriNet;
//	}
	
	private RegionSet mineRegions (UIPluginContext context, TransitionSystem transitionSystem) {
		Collection<RegionSet> regionSets = null;
		try {
			regionSets = context.tryToFindOrConstructAllObjects(RegionSet.class, MinimalRegionConnection.class, 
					MinimalRegionConnection.REGIONS, transitionSystem);
			System.out.println(regionSets.size());
		} catch (ConnectionCannotBeObtained e) {
			context.log("Can't obtain connection for " + transitionSystem.getLabel());
			e.printStackTrace();
		}
		
		return regionSets.iterator().next();
	}
	
	private Place findCorrespondingPlace(Petrinet petriNet, Region region) {
		for(Place place : petriNet.getPlaces()) {
			boolean nextPlace = false;
			int inCount = 0;
			int outCount = 0;
			for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : petriNet.getInEdges(place)) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition inTransititon 
					= (org.processmining.models.graphbased.directed.petrinet.elements.Transition) edge.getSource();
				System.out.println(region.getEntering());
				System.out.println("-------------");
				System.out.println(inTransititon.getLabel());
				if(!region.getEntering().contains(inTransititon.getLabel())) {
					nextPlace = true;
					break;
				} else {
					inCount++;
				}
			}
			for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : petriNet.getOutEdges(place)) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition outTransititon 
					= (org.processmining.models.graphbased.directed.petrinet.elements.Transition) edge.getTarget();
				if(!region.getExiting().contains(outTransititon.getLabel())) {
					nextPlace = true;
					break;
				} else {
					outCount++;
				}
			}
			if ((inCount == region.getEntering().size()) 
					&& (outCount == region.getExiting().size()) && !nextPlace) {
				return place;
			}
		
		}
		return null;
	}
}
