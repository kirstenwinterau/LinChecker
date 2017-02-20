package org.lamport.tla.toolbox.ui.handler;

import java.util.List;

import org.lamport.tla.toolbox.spec.Spec;

public interface IPL0ModelCreator {
	void createModel(Spec spec, String modelName, List<String> invariants);
}
