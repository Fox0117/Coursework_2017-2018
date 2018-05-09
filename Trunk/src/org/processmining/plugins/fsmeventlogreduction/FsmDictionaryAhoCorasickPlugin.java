package org.processmining.plugins.fsmeventlogreduction;

import org.deckfour.xes.model.XLog;
import org.processmining.framework.plugin.annotations.*;
import org.processmining.framework.plugin.*;
import org.processmining.contexts.uitopia.annotations.*;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.DictionaryTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.fsmbasedstructures.FSMTransitionSystem;
import org.processmining.plugins.fsmeventlogreduction.reductionalgo.FsmEventLogReductionAlgo;

// Название плагина, наименование входных-выходных объектов, тип возвращаемого значения.
@Plugin(name = "FsmDictionaryAhoCorasickPlugin",
        parameterLabels = {"FSM Dictionary"},
        returnLabels = {"FSMDictionaryAhoCorasick"},
        returnTypes = {DictionaryTransitionSystem.class})

public class FsmDictionaryAhoCorasickPlugin {
    // Стандартный подход - два метода, один выполняет работу, второй создаёт (populate) необходимые конфигурации
    @UITopiaVariant(affiliation = "NRU HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
    // Показывает, какие параметры (по индексам) мы используем в данном методе (из parameterLabels)
    // Можно указать variantLabel
    @PluginVariant(requiredParameterLabels = {0})
    public static DictionaryTransitionSystem fsmEventLogReduction(final PluginContext context, XLog dictionary){

        DictionaryTransitionSystem dictionaryTransitionSystem =
                new DictionaryTransitionSystem("ReducedLog", dictionary, "concept:name");
        return dictionaryTransitionSystem;
    }
}
