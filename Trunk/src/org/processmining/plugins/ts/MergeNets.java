package org.processmining.plugins.ts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetImpl;
import org.processmining.models.graphbased.directed.transitionsystem.State;
import org.processmining.models.graphbased.directed.transitionsystem.Transition;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.regions.Region;
import org.processmining.models.graphbased.directed.transitionsystem.regions.RegionSet;
import org.processmining.plugins.transitionsystem.miner.TSMinerPayload;

@Plugin(name = "Merge Petri nets discovered from parts of TS", 
parameterLabels = { "Unified transition system", "Special transition system",
		"Petri net 1", "Petri net 2",
		"Region set 1", "Region set 2" }, returnLabels = {"Petri net"}, 
returnTypes = {Petrinet.class}, userAccessible = true, 
help = "Merge Petri nets discovered from parts of TS")

public class MergeNets {

	@UITopiaVariant(affiliation = "HSE", author = "A. Kalenkova", email = "akalenkova@hse.ru")
	@PluginVariant(variantLabel = "Merge Petri nets discovered from parts of TS", requiredParameterLabels = { 0, 1, 2, 3, 4, 5})
	public Petrinet constructTS(PluginContext context, TransitionSystem unifiedTransitionSystem,
			TransitionSystem specialTransitionSystem, Petrinet petriet1, Petrinet petrinet2,
			RegionSet regionSet1, RegionSet regionSet2) {
		
		Petrinet resultNet =  mergePetriNets(petriet1, petrinet2, regionSet1, regionSet2, unifiedTransitionSystem, specialTransitionSystem);
		
		removeRedundantTransitions(resultNet);
		return resultNet;
	}
	
	private Petrinet mergePetriNets(Petrinet petrinet1, Petrinet petrinet2, RegionSet regionSet1, RegionSet regionSet2,
			TransitionSystem unifiedTransitionSystem, TransitionSystem specialTransitionSystem) {
		
		Petrinet resultPetriNet = new PetrinetImpl("Unified model");
		
		// Add regular behavior
		Map<Object, Object> regMap = new HashMap<Object, Object>();
		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : petrinet1.getTransitions()) {
			org.processmining.models.graphbased.directed.petrinet.elements.Transition newTransition =
					resultPetriNet.addTransition(transition.getLabel());
			newTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (255,223,0));
			regMap.put(transition, newTransition);
		}
		for(Place place : petrinet1.getPlaces()) {
			Place newPlace = resultPetriNet.addPlace(place.getLabel());
			regMap.put(place, newPlace);
		}
		for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : petrinet1.getEdges()) {
			if ((edge.getSource() instanceof Place) 
					&& (edge.getTarget() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
				resultPetriNet.addArc((Place)regMap.get(edge.getSource()), 
						(org.processmining.models.graphbased.directed.petrinet.elements.Transition)regMap.get(edge.getTarget()));
			}
			if ((edge.getTarget() instanceof Place) 
					&& (edge.getSource() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
				resultPetriNet.addArc((org.processmining.models.graphbased.directed.petrinet.elements.Transition)regMap.get(edge.getSource()), 
						(Place)regMap.get(edge.getTarget()));
			}
		}
		
		// Add special behavior
				Map<Object, Object> specMap = new HashMap<Object, Object>();
				for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : petrinet2.getTransitions()) {
					org.processmining.models.graphbased.directed.petrinet.elements.Transition newTransition =
							resultPetriNet.addTransition(transition.getLabel());
					newTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (153,101,21));
					specMap.put(transition, newTransition);
				}
				for(Place place : petrinet2.getPlaces()) {
					Place newPlace = resultPetriNet.addPlace(place.getLabel());
					specMap.put(place, newPlace);
				}
				for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : petrinet2.getEdges()) {
					if ((edge.getSource() instanceof Place) 
							&& (edge.getTarget() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
						resultPetriNet.addArc((Place)specMap.get(edge.getSource()), 
								(org.processmining.models.graphbased.directed.petrinet.elements.Transition)specMap.get(edge.getTarget()));
					}
					if ((edge.getTarget() instanceof Place) 
							&& (edge.getSource() instanceof org.processmining.models.graphbased.directed.petrinet.elements.Transition)) {
						resultPetriNet.addArc((org.processmining.models.graphbased.directed.petrinet.elements.Transition)specMap.get(edge.getSource()), 
								(Place)specMap.get(edge.getTarget()));
					}
				}
		
		Map<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>> inPlaces 
			= new HashMap<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>>();
		Map<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>> outPlaces 
			= new HashMap<org.processmining.models.graphbased.directed.petrinet.elements.Transition, Set<Place>>();
		
		Collection<String> specStates = new ArrayList<String>();
		for(Object state : specialTransitionSystem.getStates()) {
			for(Object uniState : unifiedTransitionSystem.getStates()) {
				if ((uniState instanceof TSMinerPayload) && (state instanceof TSMinerPayload)) {
					if ((uniState.toString()).equals(state.toString())) {
						specStates.add(uniState.toString());
					}
				}
			}
		}
		// Add escape transitions
		for (Transition transition : unifiedTransitionSystem.getEdges()) {
			if (!specStates.contains(transition.getSource().getIdentifier().toString()) 
					&& specStates.contains(transition.getTarget().getIdentifier().toString())) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition escapeTransition = resultPetriNet
						.addTransition(transition.getLabel());
				escapeTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (255,153,153));
				inPlaces.put(escapeTransition, new HashSet<Place>());
				outPlaces.put(escapeTransition, new HashSet<Place>());
				for (Region region : regionSet1) {
					State state = unifiedTransitionSystem.getNode(transition.getSource().getIdentifier());
					if(region.contains(state)) {
						Place place = findCorrespondingPlace(resultPetriNet, region);
						if (place != null) {
							inPlaces.get(escapeTransition).add(place);
						}
					}
				}
				for (Region region : regionSet2) {
					State state = specialTransitionSystem.getNode(transition.getTarget().getIdentifier());
					if (region.contains(state)) {
						Place place = findCorrespondingPlace(resultPetriNet, region);
						if (place != null) {
							outPlaces.get(escapeTransition).add(place);
						}
					}
				}
			}
		}
		// Add return transitions
		for (Transition transition : unifiedTransitionSystem.getEdges()) {
			if (specStates.contains(transition.getSource().getIdentifier().toString())
					&& !specStates.contains(transition.getTarget().getIdentifier().toString())) {
				org.processmining.models.graphbased.directed.petrinet.elements.Transition returnTransition = resultPetriNet
						.addTransition(transition.getLabel());
				returnTransition.getAttributeMap().put(AttributeMap.FILLCOLOR, new Color (153,255,153));
				inPlaces.put(returnTransition, new HashSet<Place>());
				outPlaces.put(returnTransition, new HashSet<Place>());
				for (Region region : regionSet2) {
					State state = unifiedTransitionSystem.getNode(transition.getSource().getIdentifier());
					if (region.contains(state)) {
						Place place = findCorrespondingPlace(resultPetriNet, region);
						if (place != null) {
							inPlaces.get(returnTransition).add(place);
						}
					}
				}
				for (Region region : regionSet1) {
					State state = unifiedTransitionSystem.getNode(transition.getTarget().getIdentifier());
					if (region.contains(state)) {
						Place place = findCorrespondingPlace(resultPetriNet, region);
						if (place != null) {
							outPlaces.get(returnTransition).add(place);
						}
					}
				}
			}
		}
		
		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition
				: inPlaces.keySet()) {
			for(Place place : inPlaces.get(transition)) {
				resultPetriNet.addArc(place, transition);
			}
		}
		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition
				: outPlaces.keySet()) {
			for(Place place : outPlaces.get(transition)) {
				resultPetriNet.addArc(transition, place);
			}
		}
		
		return resultPetriNet;
	}

	private Place findCorrespondingPlace(Petrinet petriNet, Region region) {
		for(Place place : petriNet.getPlaces()) {
			if (place.getLabel().equals(region.toString())) {
				return place;
			}
		}
		return null;
	}
	
private void removeRedundantTransitions(Petrinet petrinet) {
		
		Set<org.processmining.models.graphbased.directed.petrinet.elements.Transition> transitionToRemove 
		= new HashSet<org.processmining.models.graphbased.directed.petrinet.elements.Transition>();
		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : petrinet.getTransitions()) {
			if(transition.getLabel().contains("e@") ||  transition.getLabel().equals("start") || transition.getLabel().equals("end")) {
				transitionToRemove.add(transition);
			}
		}
		for(org.processmining.models.graphbased.directed.petrinet.elements.Transition transition : transitionToRemove) {
			petrinet.removeTransition(transition);
		}
		
		// remove hanging places
		Set<Place> placesToRemove = new HashSet<Place>();
		for (Place place : petrinet.getPlaces()) {
			if (((petrinet.getInEdges(place) == null) || (petrinet.getInEdges(place).size() == 0)) 
			&&  ((petrinet.getOutEdges(place) == null) || (petrinet.getOutEdges(place).size() == 0))) {
				placesToRemove.add(place);
			}
		}
		for (Place place : placesToRemove) {
			petrinet.removePlace(place);
		}
	}

}
