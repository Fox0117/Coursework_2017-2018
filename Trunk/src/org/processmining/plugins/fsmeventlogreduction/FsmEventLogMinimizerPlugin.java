package org.processmining.plugins.fsmeventlogreduction;

import org.deckfour.xes.model.XLog;
import org.processmining.framework.plugin.annotations.*;
import org.processmining.framework.plugin.*;
import org.processmining.contexts.uitopia.annotations.*;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.DictionaryTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.FSMTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.reductionalgo.FsmEventLogReductionAlgo;

// Название плагина, наименование входных-выходных объектов, тип возвращаемого значения.
@Plugin(name = "FsmEventLogMinimizer",
        parameterLabels = {"FSM Event Log"},
        returnLabels = {"FSM Minimized Event Log"},
        returnTypes = {FSMTransitionSystem.class})

public class FsmEventLogMinimizerPlugin {
    // Стандартный подход - два метода, один выполняет работу, второй создаёт (populate) необходимые конфигурации
    @UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
    // Показывает, какие параметры (по индексам) мы используем в данном методе (из parameterLabels)
    // Можно указать variantLabel
    @PluginVariant(requiredParameterLabels = {0})
    public static FSMTransitionSystem fsmEventLogReduction(final PluginContext context, XLog log){

        FSMTransitionSystem fsmTransitionSystem = new FSMTransitionSystem("ReducedLog", log, "concept:name");
        fsmTransitionSystem.minizeFSM();
        return fsmTransitionSystem;
    }
}
