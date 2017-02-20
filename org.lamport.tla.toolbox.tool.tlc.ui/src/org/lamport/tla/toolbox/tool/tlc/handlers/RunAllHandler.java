package org.lamport.tla.toolbox.tool.tlc.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.lamport.tla.toolbox.spec.Spec;
import org.lamport.tla.toolbox.tool.ToolboxHandle;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.ModelEditor;

public class RunAllHandler extends AbstractHandler {
	@Override
	public boolean isEnabled() {
		Spec spec = ToolboxHandle.getCurrentSpec();
		if (spec == null || !spec.usesLinearisabilityModuleGenerator()) {
			return false;
		}
		return super.isEnabled();
	}
	
	public Object execute(ExecutionEvent event) throws ExecutionException {
		StartLaunchHandler.executeModel(event, true);
		return null;
	}
}
