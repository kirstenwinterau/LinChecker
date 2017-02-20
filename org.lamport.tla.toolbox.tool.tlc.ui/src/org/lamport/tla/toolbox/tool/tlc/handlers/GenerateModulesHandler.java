package org.lamport.tla.toolbox.tool.tlc.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.lamport.tla.toolbox.tool.ToolboxHandle;
import org.lamport.tla.toolbox.util.ResourceHelper;

import org.lamport.tla.toolbox.spec.Spec;

public class GenerateModulesHandler extends AbstractHandler {

	@Override
	public boolean isEnabled() {
		Spec spec = ToolboxHandle.getCurrentSpec();
		if (spec == null || !spec.usesLinearisabilityModuleGenerator()) {
			return false;
		}
		return super.isEnabled();
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final Shell shell = HandlerUtil.getActiveShell(event);
		Spec spec = ToolboxHandle.getCurrentSpec();
		
		// No active spec
		if(spec == null) {
			openErrorDialog(shell, "Specification not found", "No active "
					+ "specification was found, please ensure a specification "
					+ "is open before trying to generate modules.");
			return null;
		}
		
		// Don't generate modules unless the user indicated our tool
		// should be enabled , for backwards compatibility
		if(!spec.usesLinearisabilityModuleGenerator()) {
			return null;
		}
		
		IFile file = spec.getRootFile();
		IPath tlaPath = file.getLocation();
		
		/*
		 * Check the list of generated modules (in moduleGenInfo.txt) and remove
		 * them, reduces clutter of files if names of generated files change due
		 * to changes in the PL/0 input
		 */
		long lastGenTime = getLastGenTime(spec);
		List<String> oldModules = readModuleList(spec, shell, false);
		// Check if the generated files were modified since last generation, warn
		// the user that their changes will be overwritten
		if(!userAcceptedOverwrite(spec, oldModules, lastGenTime, shell)) {
			return null;
		}
		deleteOldModules(spec, oldModules, shell);

		// Spec not parsed (errors exist). We delete the generated
		// modules in case copied errors are causing trouble. The 
		// user can then can correct any errors in their root module 
		// and regenerate
		if (spec.getRootModule() == null) {
			openErrorDialog(shell, "Error parsing the specification", "Errors "
					+ "exist in the current specification. Tried to remove all "
					+ "generated modules, please correct any remaining errors in "
					+ "the root module");
			return null;
		}
		
		
		IPath codePath = tlaPath.removeLastSegments(1);
		codePath = codePath.append(spec.getRootModule().getName() + ".pl0");
		
		boolean hasError = true;
		try {
			hasError = runModuleGenerator(codePath.toOSString(), shell);
		} catch (Exception e) {
			openErrorDialog(shell, "Could not invoke module generating tool", "The module generating tool " 
					+ "could not be run. Please ensure you have not delted the moduleGen.jar file and "
					+ "restart TLA+ Toolbox.");
			e.printStackTrace();
		}
		
		// The compiler had errors, nothing more to do. No reason to put the old
		// modules back as they are out of sync with the current root module. Just leave
		// the spec free of generated modules.
		if(hasError) {
			refreshProject(spec, new ArrayList<String>(), shell);
			return null;
		}
		
		/*
		 * Check the list of generated modules (in moduleGenInfo.txt) and add
		 * these to the spec
		 */
		List<String> modules = readModuleList(spec, shell, true);
		addGeneratedModulesToSpec(spec, modules);
		modules.addAll(oldModules);
		refreshProject(spec, modules, shell);
		return null;
	}
	
	private void refreshProject(Spec spec, List<String> modules, Shell shell) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		final IWorkbenchPage activePage = workbench.getActiveWorkbenchWindow().getActivePage();
		IEditorReference[] activeEditors = activePage.getEditorReferences();
		
		for(IEditorReference editor : activeEditors) {
			String name = editor.getName();
			if(editor.getName().endsWith(".tla")) name = name.substring(0, editor.getName().length() - 4);
			for(String module: modules) {
				if(module.endsWith(name) && !spec.getRootModule().getName().equals(name)) {
					activePage.closeEditor(editor.getEditor(false), false);
				}
			}
		}
		try {
			spec.getProject().refreshLocal(IResource.FORCE, new NullProgressMonitor());
		} catch (CoreException e) {
			openErrorDialog(shell, "Error cleaning project", "There may be discrepancies between the "
					+ "visible state of the specification (open editors and the spec explorer) and the "
					+ "state of the project on the file sysem. Please close all editors and refresh"
					+ "the project via the spec explorer.");
			e.printStackTrace();
		}
	}
	
	private boolean userAcceptedOverwrite(Spec spec, List<String> modules, long lastGenTime, Shell shell) {
		boolean clean = true;
		for (String moduleFileName : modules) {
			File file = new File(moduleFileName + ".tla");
			long time = file.lastModified();
			if(time > lastGenTime) {
				clean = false;
			}
		}
		if(clean) return true;
		MessageDialog dialog = new MessageDialog(shell, "Generated modules out of sync", null, "You have made changes to the generated modules. If you continue, they will be overwritten.", MessageDialog.CONFIRM, new String[] { "Okay", "Cancel" }, 0);
		if(dialog.open() != Window.OK) {
    			return false;
		}
		return dialog.getReturnCode() == Window.OK;
	}
	
	public static boolean suggestRegenerateIfOutOfSync(Spec spec, Shell shell) {
		long lastGenTime = getLastGenTime(spec);
		List<String> modules = readModuleList(spec);
		if(modules.size() == 0) return true;
		String[] parts = modules.get(0).split("/");
		if(parts.length == 0) return true;
		String name = parts[parts.length - 1];
		String specName = modules.get(0) + ".tla";
		specName = specName.replace(name + ".tla", spec.getName());
		
		File file = new File(specName + ".tla");
		File programFile = new File(specName + ".pl0");
		long time = Math.max(file.lastModified(), programFile.lastModified());
		if(time > lastGenTime) {
			MessageDialog dialog = new MessageDialog(shell, "Root specification file has been changed", null, "You have made changes to the root TLA+ file or PL/0 file since last generating. Continue model checking anyway?", MessageDialog.WARNING, new String[] { "Continue", "Cancel" }, 0);
			if(dialog.open() != Window.OK) {
	    			return false;
			}
			return dialog.getReturnCode() == Window.OK;
		}
		return true;
	}

	private void deleteOldModules(Spec spec, List<String> modules, Shell shell) {
		for (String moduleFileName : modules) {
			IFile module = ResourceHelper.getLinkedFile(spec.getProject(), 
					ResourceHelper.getModuleFileName(moduleFileName), false);
			try {
				module.delete(false, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	public static List<String> readModuleList(Spec spec) {
		return readModuleList(spec, null, true);
	}
	
	public static long getLastGenTime(Spec spec) {
		IFile file = spec.getRootFile();
		IPath tlaPath = file.getLocation();
		IPath moduleListPath = tlaPath.removeLastSegments(1);
		moduleListPath = moduleListPath.append("moduleGenInfo.txt");
		File moduleListInfo = moduleListPath.toFile();
		return moduleListInfo.lastModified();
	}
	
	public static List<String> readModuleList(Spec spec, Shell shell, boolean critical) {

		IFile file = spec.getRootFile();
		IPath tlaPath = file.getLocation();
		IPath moduleListPath = tlaPath.removeLastSegments(1);
		moduleListPath = moduleListPath.append("moduleGenInfo.txt");
		File moduleListInfo = moduleListPath.toFile();
		
		List<String> modules = new ArrayList<String>();
		BufferedReader moduleListReader = null;
		if(!(moduleListInfo).exists()) return modules;
		try {
			moduleListReader = new BufferedReader(new FileReader(moduleListInfo));
			String line = moduleListReader.readLine();
			while (line != null) {
				modules.add(moduleListPath.removeLastSegments(1).append(line).toString());
				line = moduleListReader.readLine();
			}
		} catch (Exception e) {
			if(shell != null && critical)
			openErrorDialog(shell, "Could not retrieve generated module information", "Could not retrieve information about"
					+ "generated modules from the moduleGenInfo text file in the spec root folder");
			e.printStackTrace();
		} finally {
			if (moduleListReader != null) {
				try {
					moduleListReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return modules;
	}

	private void addGeneratedModulesToSpec(Spec spec, List<String> modules) {
		for (String moduleFileName : modules) {
			spec.addModule(moduleFileName);
		}
	}

	private static boolean printStream(String streamName, InputStream ins, Shell shell, boolean showUser) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
		String line = reader.readLine();
		String messageLog = "";
		while (line != null) {
			System.out.println(String.format("%s: %s", streamName, line));
			messageLog += line + System.lineSeparator();
			line = reader.readLine();
		}
		if(showUser && !messageLog.isEmpty()) {
			openErrorDialog(shell, "Could not successfully parse PL/0 algorithm into TLA+ modules", messageLog);
		}
		return !messageLog.isEmpty();
	}

	private static boolean runModuleGenerator(String codeFilePath, Shell shell) throws Exception {
		// Percentages are used to pass file paths with spaces in them to the tool without causing trouble
		codeFilePath = codeFilePath.replace(" ", "%");
		
		// Get the location of the jar, which is stored in the root of the tool.tlc project under the name moduleGen.jar
		String jarLocation = GenerateModulesHandler.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		jarLocation = jarLocation + "../org.lamport.tla.toolbox.tool.tlc/moduleGen.jar";
		
		// Invoke the tool, ensuring output and errors are appropriately redirected
		String invocation = String.format("java -jar %s %s", jarLocation, codeFilePath);
		Process tool = Runtime.getRuntime().exec(invocation);
		printStream("Module Generator Output", tool.getInputStream(), shell, false);
		boolean hasError = printStream("Module Generator Error", tool.getErrorStream(), shell, true);
		tool.waitFor();
		System.out.println(String.format("%s has exited with code %s", invocation, tool.exitValue()));
		return hasError;
	}

	private static int openErrorDialog(Shell parentShell, String dialogTitle, String dialogMessage) {
		MessageDialog dialog = new MessageDialog(parentShell, dialogTitle, null, dialogMessage, MessageDialog.ERROR, new String[] { "Okay" }, 0);
		return dialog.open();
	}
}
