package org.processmining.plugins.fsmeventlogreduction;

import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XLog;
import org.processmining.framework.plugin.annotations.*;
import org.processmining.framework.plugin.*;
import org.processmining.contexts.uitopia.annotations.*;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.DictionaryTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.FSMTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.reductionalgo.FsmEventLogReductionAlgo;

// Название плагина, наименование входных-выходных объектов, тип возвращаемого значения.
@Plugin(name = "FSMEventLogReduction",
        parameterLabels = {"FSM Event Log", "FSM Dictionary", "FSM Event Log Reduction Configuration"},
        returnLabels = {"Reduced FSM Event Log"},
        returnTypes = {XLog.class})

public class FsmEventLogReductionPlugin {
    // Стандартный подход - два метода, один выполняет работу, второй создаёт (populate) необходимые конфигурации
    @UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
    // Показывает, какие параметры (по индексам) мы используем в данном методе (из parameterLabels)
    // Можно указать variantLabel
    @PluginVariant(requiredParameterLabels = { 0, 1, 2 })
    public static XLog fsmEventLogReduction(final PluginContext context,
                                                        XLog log,
                                                        XLog dictionary,
                                                        final FsmEventLogReductionConfiguration config){

        DictionaryTransitionSystem dictionaryTransitionSystem = new DictionaryTransitionSystem("ReducedLog", dictionary, "concept:name");
        FSMTransitionSystem fsmTransitionSystem = new FSMTransitionSystem("ReducedLog", log, "concept:name");
        fsmTransitionSystem.minizeFSM();
        FsmEventLogReductionAlgo algo = new FsmEventLogReductionAlgo(fsmTransitionSystem, dictionaryTransitionSystem,
        log, "concept:name");

        return algo.reduceFsmTypeLog();
    }

    @UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
    @PluginVariant(requiredParameterLabels = { 0, 1 })
    public static XLog fsmEventLogReduction(final PluginContext context, XLog log, XLog dictionary){
        FsmEventLogReductionConfiguration config = new FsmEventLogReductionConfiguration(new Object());
        populate(context, config);
        return fsmEventLogReduction(context, log, dictionary, config);
    }


    // Создаёт необходимые параметры/настройки и передаёт их в конфиг
    private static void populate(PluginContext context, FsmEventLogReductionConfiguration config) {
        config.setSomething(new Object());
    }
}

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
