package org.processmining.plugins;

import org.processmining.framework.plugin.annotations.*;
import org.processmining.framework.plugin.*;
import org.processmining.contexts.uitopia.*;
import org.processmining.contexts.uitopia.annotations.*;

// Название плагина, наименование входных-выходных объектов, тип возвращаемого значения.
//TODO Поставить актуальные названия
@Plugin(name = "FsmEventLogReduction",
		parameterLabels = {"FSM Event Log", "FSM Event Log Reduction Configuration"},
		returnLabels = {"Reduce FSM Event Log"},
		returnTypes = {Object.class})
// В случае, если возвращаемых объектов несколько - нужно возвращать Object[]

public class FsmEventLogReductionPlugin {
	// Стандартный подход - два метода, один выполняет работу, второй создаёт (populate) необходимые конфигурации

	// Stub
	// Организация, автор, мыло
	//TODO Проверить корректность почты
	@UITopiaVariant(affiliation = "HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
	// Показывает, какие параметры (по индексам) мы используем в данном методе (из parameterLabels)
	// Можно указать variantLabel (сейчас не критично)
	@PluginVariant(requiredParameterLabels = { 0, 1, 2 })
	public static Object fsmEventLogReduction(final PluginContext context,
											  final Object firstPararm,
											  final Object secondPararm,
											  final FsmEventLogReductionConfiguration config){
		return null;
	}


	// Stub
	@UITopiaVariant(affiliation = "HSE", author = "A. Konchagin", email = "amkonchagin@hse.ru")
	@PluginVariant(requiredParameterLabels = { 0, 1 })
	public static Object fsmEventLogReduction(final PluginContext context,
											  final Object firstPararm,
											  final Object secondPararm){
		FsmEventLogReductionConfiguration config = new FsmEventLogReductionConfiguration(new Object());
		populate(context, config);
		return fsmEventLogReduction(context, firstPararm, secondPararm, config);
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
