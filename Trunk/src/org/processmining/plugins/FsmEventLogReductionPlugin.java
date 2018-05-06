package org.processmining.plugins;

import org.deckfour.xes.model.XLog;
import org.processmining.framework.plugin.annotations.*;
import org.processmining.framework.plugin.*;
import org.processmining.contexts.uitopia.*;
import org.processmining.contexts.uitopia.annotations.*;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystem;
import org.processmining.models.graphbased.directed.transitionsystem.TransitionSystemImpl;

// Название плагина, наименование входных-выходных объектов, тип возвращаемого значения.
@Plugin(name = "FSMEventLogReduction",
		parameterLabels = {"FSM Event Log", "FSM Dictionary", "FSM Event Log Reduction Configuration"},
		returnLabels = {"Reduced FSM Event Log"},
		returnTypes = {FSMTransitionSystem.class})
// В случае, если возвращаемых объектов несколько - нужно возвращать Object[]

public class FsmEventLogReductionPlugin {
	// Стандартный подход - два метода, один выполняет работу, второй создаёт (populate) необходимые конфигурации

	// Stub
	// Организация, автор, мыло
	//TODO Проверить корректность почты
	@UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
	// Показывает, какие параметры (по индексам) мы используем в данном методе (из parameterLabels)
	// Можно указать variantLabel (сейчас не критично)
	@PluginVariant(requiredParameterLabels = { 0, 1, 2 })
	public static TransitionSystem fsmEventLogReduction(final PluginContext context,
															XLog log,
															XLog dictionary,
															final FsmEventLogReductionConfiguration config){

		//DictionaryTransitionSystem dictionaryTransitionSystem = new DictionaryTransitionSystem("TEST", dictionary, "concept:name");
		FSMTransitionSystem fsmTransitionSystem = new FSMTransitionSystem("TEST", log, "concept:name");
		fsmTransitionSystem.minizeFSM();
		return fsmTransitionSystem;//FsmEventLogReductionAlgo.reduceFsmTypeLog(log, dictionary);
	}


	// Stub
	@UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
	@PluginVariant(requiredParameterLabels = { 0, 1 })
	public static TransitionSystem fsmEventLogReduction(final PluginContext context, XLog log, XLog dictionary){
		FsmEventLogReductionConfiguration config = new FsmEventLogReductionConfiguration(new Object());
		populate(context, config);
		return fsmEventLogReduction(context, log, dictionary, config);
	}


	// Создаёт необходимые параметры/настройки и передаёт их в конфиг
	// Stub
	private static void populate(PluginContext context, FsmEventLogReductionConfiguration config) {
		config.setSomething(new Object());
	}
}

// Класс-конфигурация для исполнения метода. Может оказаться бесполезным!
// Stub
class FsmEventLogReductionConfiguration {
	Object something;

	public FsmEventLogReductionConfiguration(Object something) {
		this.something = something;
	}

	public Object getSomething() {
		return something;
	}

	public void setSomething(Object something) {
		this.something = something;
	}
}
