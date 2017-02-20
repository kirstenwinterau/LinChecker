package org.lamport.tla.toolbox.tool.tlc.launch;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.lamport.tla.toolbox.spec.Spec;
import org.lamport.tla.toolbox.tool.IParseResult;
import org.lamport.tla.toolbox.tool.ToolboxHandle;
import org.lamport.tla.toolbox.tool.tlc.TLCActivator;
import org.lamport.tla.toolbox.tool.tlc.job.DistributedTLCJob;
import org.lamport.tla.toolbox.tool.tlc.job.ITLCJobStatus;
import org.lamport.tla.toolbox.tool.tlc.job.TLCJob;
import org.lamport.tla.toolbox.tool.tlc.job.TLCJobFactory;
import org.lamport.tla.toolbox.tool.tlc.job.TLCProcessJob;
import org.lamport.tla.toolbox.tool.tlc.model.Model;
import org.lamport.tla.toolbox.tool.tlc.model.ModelWriter;
import org.lamport.tla.toolbox.tool.tlc.model.TypedSet;
import org.lamport.tla.toolbox.tool.tlc.util.ModelHelper;
import org.lamport.tla.toolbox.util.AdapterFactory;
import org.lamport.tla.toolbox.util.ResourceHelper;
import org.lamport.tla.toolbox.util.TLAMarkerInformationHolder;
import org.lamport.tla.toolbox.util.UIHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import tla2sany.semantic.ModuleNode;
import tla2sany.semantic.OpDeclNode;
import tlc2.TLCGlobals;

/**
 * Represents a launch delegate for TLC<br>
 * The methods in this class are called in the following order:
 * <ol>
 * <li>getLaunch()</li>
 * <li>preLaunchCheck()</li>
 * <li>buildForLaunch()</li>
 * <li>finalLaunchCheck()</li>
 * <li>launch()</li>
 * </ol>
 * Some of them run on any launch type, others only in the modelcheck mode
 * 
 * @author Simon Zambrovski
 * Modified on 10 Sep 2009 to add No Spec TLC launch option.
 */
@SuppressWarnings("restriction")
public class TLCModelLaunchDelegate extends LaunchConfigurationDelegate implements IModelConfigurationConstants,
        IModelConfigurationDefaults
{
    // Mutex rule for the following jobs to run after each other
    protected MutexRule mutexRule = new MutexRule("");

    protected String specName = null;
    protected String modelName = null;
    protected String specRootFilename = null;

    /**
     * Configuration type
     */
    public static final String LAUNCH_CONFIGURATION_TYPE = "org.lamport.tla.toolbox.tool.tlc.modelCheck";
    /**
     * Mode for starting TLC
     */
    public static final String MODE_MODELCHECK = "modelcheck";
    /**
     * Mode for starting TLC and checking all auto-generated modules
     */
    public static final String MODE_MODELCHECK_ALL_GENERATED = "modelcheck_allgenerated";
    /**
     * Only generate the models but do not run TLC
     */
    public static final String MODE_GENERATE = "generate";

    /**
     * 1. method called during the launch
     */
    public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException
    {
        // delegate to the super implementation
        return super.getLaunch(configuration, mode);
    }

    /**
     * Returns whether a launch should proceed. This method is called first
     * in the launch sequence providing an opportunity for this launch delegate
     * to abort the launch.
     * 
     * <br>2. method called on launch
     * @return whether the launch should proceed
     * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#preLaunchCheck(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
     */
    public boolean preLaunchCheck(ILaunchConfiguration config, String mode, IProgressMonitor monitor)
            throws CoreException
    {
        // check and lock the model
        final Model model = config.getAdapter(Model.class);
        
    	    List<String> moduleNames = getModulesToRun(model, mode);
    	    if(moduleNames.isEmpty()) return false;
    	    for(String moduleName: moduleNames) {
    	    makeModuleRootModule(moduleName);
        // check the config existence
        if (!config.exists())
        {
            return false;
        }
        //model.clearSpec();
        //model.getSpec();
        synchronized (config)
        {
            // read out the running attribute
            if (/*model.isRunning() || */model.isLocked())
            {
                // previous run has not been completed
                // exit
                throw new CoreException(
                        new Status(
                                IStatus.ERROR,
                                TLCActivator.PLUGIN_ID,
                                "The running attribute for "
                                        + modelName
                                        + " has been set to true or that model is locked. "
                                        + "Another TLC is possible running on the same model, has been terminated non-gracefully "
                                        + "or you tried to run TLC on a locked model. Running TLC on a locked model is not possible."));
            }
        }

        try
        {
            monitor.beginTask("Reading model parameters", 1);

            // name of the specification
            specName = config.getAttribute(SPEC_NAME, EMPTY_STRING);

            // model name
            modelName = config.getAttribute(MODEL_NAME, EMPTY_STRING);
            
            // current module name
    			if(moduleName.endsWith(".tla")) {
    				moduleName = moduleName.substring(0, moduleName.length() - 4);
    			}
    			
    			List<org.lamport.tla.toolbox.spec.Module> specFiles = ToolboxHandle.getCurrentSpec().getModules();
                for(org.lamport.tla.toolbox.spec.Module module : specFiles) {
                		if(module.getModuleName().equals(moduleName)) {
                			specRootFilename = module.getAbsolutePath();
                		}
                }
            
           // specRootFilename = config.getAttribute(SPEC_ROOT_FILE,
            // EMPTY_STRING);

        } finally
        {
            // finish the monitor
            monitor.done();
        }
    	    }

        return true;
    }
    
    private List<String> getModulesToRun(Model model, String mode) {
        if(MODE_MODELCHECK_ALL_GENERATED.equals(mode)) {
			return getModulesForModel(model);
		}
        return getCurrentModule();
    }

    /**
     * Instead of building, the model files are written to the disk. 
     * The directory with the same name as the model is created as the sub-directory
     * of the spec project. (if already present, the files inside will be deleted)
     * Three new files are created: MC.tla, MC.cfg, MC.out
     * All spec modules, including the root module are copied to this directory
     *  
     * <br>3. method called on launch
     * 
     * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#buildForLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
     */
    public boolean buildForLaunch(ILaunchConfiguration config, String mode, IProgressMonitor monitor)
            throws CoreException
    {
       	final Model model = config.getAdapter(Model.class);
    	
        // generate the model here
        int STEP = 100;
        
        List<String> moduleNames = getModulesToRun(model, mode);
        if(moduleNames.isEmpty()) return false;
	    
	    for(String moduleName: moduleNames) {
	    makeModuleRootModule(moduleName);

        try
        {
            monitor.beginTask("Creating model", 30);
            // step 1
            monitor.subTask("Creating directories");

            // retrieve the project containing the specification
            IProject project = ResourceHelper.getProject(specName);
            if (project == null)
            {
                // project could not be found
                throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID,
                        "Error accessing the spec project " + specName));
            }


            List<org.lamport.tla.toolbox.spec.Module> specFiles = ToolboxHandle.getCurrentSpec().getModules();
            for(org.lamport.tla.toolbox.spec.Module module : specFiles) {
            		if(module.getModuleName().equals(moduleName)) {
            			specRootFilename = module.getAbsolutePath();
            		}
            }
            
            // retrieve the root file
            IFile specRootFile = ResourceHelper.getLinkedFile(project, specRootFilename, false);
            if (specRootFile == null)
            {
                // root module file not found
                throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID,
                        "Error accessing the root module " + specRootFilename));
            }

            // retrieve the model folder
            IFolder modelFolder = project.getFolder(modelName);
            IPath targetFolderPath = modelFolder.getProjectRelativePath().addTrailingSeparator();
            String filePrefix = (moduleNames.size() == 1 && !moduleName.equals("Init")) ? "" : ("_" + moduleName);
            IFolder subFolder = modelFolder.getFolder(filePrefix);
            
            // create the handles: MC.tla, MC.cfg and MC.out
            IFile tlaFile = project.getFile(targetFolderPath.append(filePrefix).append(ModelHelper.FILE_TLA));
            IFile cfgFile = project.getFile(targetFolderPath.append(filePrefix).append(ModelHelper.FILE_CFG));
            IFile outFile = project.getFile(targetFolderPath.append(filePrefix).append(ModelHelper.FILE_OUT));

            TLCActivator.logDebug("Writing files to: " + targetFolderPath.toOSString());

            final IFile[] files = new IFile[] { tlaFile, cfgFile, outFile };


            if(!modelFolder.exists()) {
            		modelFolder.create(IResource.DERIVED | IResource.FORCE, true, new SubProgressMonitor(monitor, STEP));
            	}
            
            if (subFolder.exists())
            {
                final IResource[] members = subFolder.members();
                // erase everything inside
                if (members.length == 0)
                {
                    monitor.worked(STEP);
                } else
                {
                    final boolean recover = config.getAttribute(LAUNCH_RECOVER, LAUNCH_RECOVER_DEFAULT);
                    final IResource[] checkpoints = config.getAdapter(Model.class).getCheckpoints(false);

                    ISchedulingRule deleteRule = ResourceHelper.getDeleteRule(members);

                    // delete files
                    ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {

                        public void run(IProgressMonitor monitor) throws CoreException
                        {
                            boolean checkFiles = recover;
                            monitor.beginTask("Deleting files", members.length);
                            // delete the members of the target
                            // directory
                            for (int i = 0; i < members.length; i++)
                            {
                                if (checkFiles)
                                {
                                    if (checkpoints.length > 0 && checkpoints[0].equals(members[i]))
                                    {
                                        // we found the recovery
                                        // directory and didn't delete
                                        // it
                                        checkFiles = false;
                                        continue;
                                    }
                                } else
                                {
                                    // delete file
                                    // either non-recovery mode
                                    // or the recovery directory already
                                    // skipped
                                    try
                                    {
                                        if (!members[i].getName().equals(ModelHelper.TE_TRACE_SOURCE))
                                        {
                                            members[i].delete(IResource.FORCE, new SubProgressMonitor(monitor, 1));
                                        }
                                    } catch (CoreException e)
                                    {
                                        // catch the exception if
                                        // deletion failed, and just
                                        // ignore this fact
                                        // FIXME this should be fixed at
                                        // some later point in time
                                        TLCActivator.logError("Error deleting a file " + members[i].getLocation(), e);
                                    }
                                }
                            }
                            monitor.done();
                        }
                    }, deleteRule, IWorkspace.AVOID_UPDATE, new SubProgressMonitor(monitor, STEP));
                }
            } else
            {
                subFolder.create(IResource.DERIVED | IResource.FORCE, true, new SubProgressMonitor(monitor, STEP));
            }
            	
            // step 2
            monitor.subTask("Copying files");
            
            // copy
            specRootFile.copy(targetFolderPath.append(filePrefix).append(specRootFile.getProjectRelativePath()), IResource.DERIVED
                    | IResource.FORCE, new SubProgressMonitor(monitor, 1));
            // find the result
            IResource specRootFileCopy = subFolder.findMember(specRootFile.getProjectRelativePath());

            // react if no result
            if (specRootFileCopy == null)
            {
                throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID, "Error copying "
                        + specRootFilename + " into " + targetFolderPath.append(filePrefix).toOSString()));
            }

            // get the list of dependent modules
            List extendedModules = ToolboxHandle.getExtendedModules(specRootFile.getName());

            // iterate and copy modules that are needed for the spec
            IFile moduleFile = null;
            for (int i = 0; i < extendedModules.size(); i++)
            {
                String module = (String) extendedModules.get(i);
				// Only take care of user modules and actually *linked* files
				// (not files defined via TLA_LIBRARY_PATH)
                if (ToolboxHandle.isUserModule(module) && ResourceHelper.isLinkedFile(project, module))
                {
                    moduleFile = ResourceHelper.getLinkedFile(project, module, false);
                    if (moduleFile != null)
                    {
                    	try {
                        moduleFile.copy(targetFolderPath.append(filePrefix).append(moduleFile.getProjectRelativePath()), IResource.DERIVED
                                | IResource.FORCE, new SubProgressMonitor(monitor, STEP / extendedModules.size()));
                    	} catch (ResourceException re) {
                    		// Trying to copy the file to the targetFolderPath produces an exception.
                    		// The most common cause is a dangling linked file in the .project metadata 
                    		// of the Toolbox project. Usually, a dangling link is the effect of copying
                    		// a single Toolbox project from one machine to the other missing modules 
                    		// extended (EXTENDS in TLA+) by a spec. The missing modules are part of
                    		// another spec which does not exist on the current machine.
                    		// We log the full exception to the Toolbox's error log (not directly visible
                    		// to the user) and raise an error dialog (see org.lamport.tla.toolbox.tool.tlc.ui.editor.ModelEditor).
                    		// We hint at how this problem can be addressed most of the time.
							TLCActivator.logError(
									String.format(
											"Error copying file %s to %s. Please correct the path to %s. \n(The first place to check is in the %s/.project file. Restart the Toolbox when you change the .project file.)",
											moduleFile.getLocation(), targetFolderPath.append(filePrefix), moduleFile.getName(),
											modelFolder.getRawLocation().removeLastSegments(1)), re);
							/*throw new CoreException(
									new Status(
											Status.ERROR,
											"org.lamport.tlc.toolbox.tool.tlc",
											String.format(
													"Error copying file %s to %s. Please correct the path to %s. \n(The first place to check is in the %s/.project file. Restart the Toolbox when you change the .project file.)",
													moduleFile.getLocation(), targetFolderPath, moduleFile.getName(),
													modelFolder.getRawLocation().removeLastSegments(1))));*/
                    	}
                    }
                    // TODO check the existence of copied files
                }
            }

            // get the scheduling rule
            ISchedulingRule fileRule = MultiRule.combine(ResourceHelper.getModifyRule(files), ResourceHelper
                    .getCreateRule(files));

            // create files
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    for (int i = 0; i < files.length; i++)
                    {
                        if (files[i].exists())
                        {
                            files[i].setContents(new ByteArrayInputStream("".getBytes()), IResource.DERIVED
                                    | IResource.FORCE, new SubProgressMonitor(monitor, 1));
                        } else
                        {
                            files[i].create(new ByteArrayInputStream("".getBytes()), IResource.DERIVED
                                    | IResource.FORCE, new SubProgressMonitor(monitor, 1));
                        }
                    }
                }

            }, fileRule, IWorkspace.AVOID_UPDATE, new SubProgressMonitor(monitor, STEP));

            monitor.worked(STEP);
            monitor.subTask("Creating contents");

            ModelWriter writer = new ModelWriter();
       
            // add the MODULE beginning and EXTENDS statement
            writer.addPrimer(ModelHelper.MC_MODEL_NAME, ResourceHelper.getModuleName(specRootFilename));

            // Sets constants to a Vector of the substitutions for the CONSTANT substitutions
            List constants = ModelHelper.deserializeAssignmentList(config.getAttribute(MODEL_PARAMETER_CONSTANTS,
                    new Vector()));

            // Sets modelValues to a TypedSet object whose value is a String array containing
            // the names of the model values declared on the Advanced model page.
            TypedSet modelValues = TypedSet.parseSet(config.getAttribute(MODEL_PARAMETER_MODEL_VALUES, EMPTY_STRING));

            // Adds to MC.cfg the CONSTANT declarations for (in order) 
            //  - The model values declared on the advanced model page.
            //  - Each CONSTANT parameter instantiated with a model value or set of model values.
            //  - SYMMETRY declarations.
            // Adds to MC.tla the CONSTANT declarations for (in order)
            //  - The model values declared on the advanced model page.
            //  - The model values introduced when instantiating a CONSTANT parameter with a set of model values.
            //  - The definitions of each CONSTANT parameter declared as a set of model values in the MC.cfg file.
            //  - Definitions of symmetry sets for the CONSTANT parameters.
            writer.addConstants(constants, modelValues, MODEL_PARAMETER_CONSTANTS, MODEL_PARAMETER_MODEL_VALUES);
            
            // The additional definitions from the Advanced model page.
            writer.addNewDefinitions(config.getAttribute(MODEL_PARAMETER_NEW_DEFINITIONS, EMPTY_STRING),
                    MODEL_PARAMETER_NEW_DEFINITIONS);

            // Adds to MC.cfg the CONSTANT declarations for CONSTANT parameters instantiated with ordinary values. 
            // Adds to MC.tla the definitions for the new symbols introduced for the aforementioned CONSTANT parameters.
            writer.addConstantsBis(constants, MODEL_PARAMETER_CONSTANTS);

            // Sets overrides to the Vector of definition overrides.
            List overrides = ModelHelper.deserializeAssignmentList(config.getAttribute(MODEL_PARAMETER_DEFINITIONS,
                    new Vector()));
            
            // For the definition overrides, it adds the definitions to the MC.tla file and the
            // overriding CONSTANT statements to the MC.cfg file.
            writer.addFormulaList(ModelWriter.createOverridesContent(overrides, ModelWriter.DEFOV_SCHEME), "CONSTANT",
                    MODEL_PARAMETER_DEFINITIONS);

            /* 
             * specType is used to write the desired spec or lack of spec to the MC files.
             * 
             * specType is also used to make sure that aspects of the model that are not relevant
             * when there is no behavior spec are not written. In particular, the following should
             * be ignored when there is no behavior spec:
             * 
             * 1.) Invariants
             * 2.) Properties
             * 3.) State constraint
             * 4.) Action constraint
             * 5.) View
             * 
             * Recover from checkpoint and TLC options should also be ignored, but
             * that is done in TLCJob.constructProgramArguments().
             */
            int specType = config.getAttribute(MODEL_BEHAVIOR_SPEC_TYPE, MODEL_BEHAVIOR_TYPE_DEFAULT);
            boolean hasSpec = specType != MODEL_BEHAVIOR_TYPE_NO_SPEC;

            if (hasSpec)
            {
                // constraint
                writer.addFormulaList(ModelWriter.createSourceContent(MODEL_PARAMETER_CONSTRAINT,
                        ModelWriter.CONSTRAINT_SCHEME, config), "CONSTRAINT", MODEL_PARAMETER_CONSTRAINT);
                // action constraint
                writer.addFormulaList(ModelWriter.createSourceContent(MODEL_PARAMETER_ACTION_CONSTRAINT,
                        ModelWriter.ACTIONCONSTRAINT_SCHEME, config), "ACTION_CONSTRAINT",
                        MODEL_PARAMETER_ACTION_CONSTRAINT);
                // Changed from incorrect "ACTION-CONSTRAINT" on 11 Sep 2009

                // view
                writer.addView(config.getAttribute(LAUNCH_VIEW, EMPTY_STRING), MODEL_PARAMETER_VIEW);
            }

            // calculator expression
           
            writer.addConstantExpressionEvaluation(model.getEvalExpression(), Model.MODEL_EXPRESSION_EVAL);

            switch (specType) {
            case MODEL_BEHAVIOR_TYPE_NO_SPEC:
                ModuleNode rootModuleNode = ModelHelper.getRootModuleNode();
                if (rootModuleNode != null)
                {
                    OpDeclNode[] vars = rootModuleNode.getVariableDecls();
                    if (vars != null && vars.length > 0)
                    {
                        String var = rootModuleNode.getVariableDecls()[0].getName().toString();
                        writer.addFormulaList(ModelWriter.createFalseInit(var), "INIT", MODEL_BEHAVIOR_NO_SPEC);
                        writer.addFormulaList(ModelWriter.createFalseNext(var), "NEXT", MODEL_BEHAVIOR_NO_SPEC);
                    }
                }
                break;
            case MODEL_BEHAVIOR_TYPE_SPEC_CLOSED:

                // the specification name-formula pair
                writer.addFormulaList(ModelWriter.createSourceContent(MODEL_BEHAVIOR_CLOSED_SPECIFICATION,
                        ModelWriter.SPEC_SCHEME, config), "SPECIFICATION", MODEL_BEHAVIOR_CLOSED_SPECIFICATION);
                break;
            case MODEL_BEHAVIOR_TYPE_SPEC_INIT_NEXT:

                // the init and next formulas
                writer.addFormulaList(ModelWriter.createSourceContent(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT,
                        ModelWriter.INIT_SCHEME, config), "INIT", MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT);
                writer.addFormulaList(ModelWriter.createSourceContent(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT,
                        ModelWriter.NEXT_SCHEME, config), "NEXT", MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT);
                break;
            }

            // // the specification name-formula pair
            // writer.addSpecDefinition(ModelHelper.createSpecificationContent(config),
            // MODEL_BEHAVIOR_CLOSED_SPECIFICATION);

            // do not write invariants and properties if there is no behavior spec
            if (hasSpec)
            {
                // invariants
                writer.addFormulaList(ModelWriter.createFormulaListContent(config.getAttribute(
                        MODEL_CORRECTNESS_INVARIANTS, new Vector()), ModelWriter.INVARIANT_SCHEME), "INVARIANT",
                        MODEL_CORRECTNESS_INVARIANTS);

                // properties
                writer.addFormulaList(ModelWriter.createFormulaListContent(config.getAttribute(
                        MODEL_CORRECTNESS_PROPERTIES, new Vector()), ModelWriter.PROP_SCHEME), "PROPERTY",
                        MODEL_CORRECTNESS_PROPERTIES);
            }

            monitor.worked(STEP);
            monitor.subTask("Writing contents");

            // write down the files
            writer.writeFiles(tlaFile, cfgFile, monitor);

            // refresh the model folder
            modelFolder.refreshLocal(IResource.DEPTH_ONE, new SubProgressMonitor(monitor, STEP));

        } finally
        {
            // make sure to complete the monitor
            monitor.done();
        }
	    }

        // we don't want to rebuild the workspace
        return false;
    }
    
    /**
     * Makes the module with the given name the root module. Requires that this module
     * exists.
     */
    private void makeModuleRootModule(String moduleName) {
		if(moduleName.endsWith(".tla")) {
			moduleName = moduleName.substring(0, moduleName.length() - 4);
		}
	    ToolboxHandle.getCurrentSpec().getRootModule().setPrimaryFileName(moduleName);
	    ToolboxHandle.getCurrentSpec().getRootModule().setName(moduleName);
    }
    
    /**
     * Check if the user is currently editing a TLA+ module, and if so sets it to be
     * the root module of the specification. This means that the model checker will now
     * run against this particular module. Used to run model checker against a single module.
     * @return
     */
    private boolean makeCurrentModuleRootModule() {
    		if(!ToolboxHandle.getCurrentSpec().usesLinearisabilityModuleGenerator()) {
    			return true;
    		}
    		// Check that the current editor is a TLA+ module editor, before we set it as the root module
    		IEditorPart editor = UIHelper.getActiveEditor();
    		if(!(editor.getClass().getName().contains("TLAEditorAndPDFViewer"))) {
			return false;
		}
		
		// Make the module currently being edited the root module so that the model checker runs against this
    		IEditorInput input = UIHelper.getActiveEditor().getEditorInput();
    		String moduleName = input.getName();
    		makeModuleRootModule(moduleName);
		return true;
    }

    /**
     * Launch the module parser on the root module and handle the errors
     *   
     * 
     * <br>4. method called on launch
     * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#finalLaunchCheck(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
     */
    public boolean finalLaunchCheck(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
            throws CoreException
    {
        monitor.beginTask("Verifying model files", 4);
        
        final Model model = configuration.getAdapter(Model.class);
        
        List<String> moduleNames = getModulesToRun(model, mode);
	    if(moduleNames.isEmpty()) return false;
	    
	    for(String moduleName: moduleNames) {
	    makeModuleRootModule(moduleName);
        //model.clearSpec();
        //model.getSpec();
        
        IProject project = ResourceHelper.getProject(specName);
        IFolder launchDir = project.getFolder(modelName);
        String filePrefix = (moduleNames.size() == 1 && !moduleName.equals("Init")) ? "" : ("_" + moduleName);
        IFile rootModule = launchDir.getFolder(filePrefix).getFile(ModelHelper.FILE_TLA);

        monitor.worked(1);
        // parse the MC file
        IParseResult parseResult = ToolboxHandle.parseModule(rootModule, new SubProgressMonitor(monitor, 1), false,
                false);
        Vector<TLAMarkerInformationHolder> detectedErrors = parseResult.getDetectedErrors();
        boolean status = !AdapterFactory.isProblemStatus(parseResult.getStatus());

        monitor.worked(1);
        // remove existing markers
        
        model.removeMarkers(Model.TLC_MODEL_ERROR_MARKER_SANY);
        monitor.worked(1);

        if (!detectedErrors.isEmpty())
        {
            TLCActivator.logDebug("Errors in model file found " + rootModule.getLocation());
        }

        FileEditorInput fileEditorInput = new FileEditorInput((IFile) rootModule);
        FileDocumentProvider fileDocumentProvider = new FileDocumentProvider();
        try
        {

            fileDocumentProvider.connect(fileEditorInput);

            // The document for manipulation of the MC.tla file
            IDocument document = fileDocumentProvider.getDocument(fileEditorInput);

            // the find/replace adapter to find texts in the document
            FindReplaceDocumentAdapter searchAdapter = new FindReplaceDocumentAdapter(document);

            for (int i = 0; i < detectedErrors.size(); i++)
            {
                // the holder has the information about the error in the MC file
                TLAMarkerInformationHolder markerHolder = (TLAMarkerInformationHolder) detectedErrors.get(i);
                String message = markerHolder.getMessage();
                if (markerHolder.getModuleName() != null)
                {
                    // the root module is MC.tla
                    if (markerHolder.getModuleName().equals(rootModule.getName()))
                    {
                        int severity = markerHolder.getSeverityError();
                        int[] coordinates = markerHolder.getCoordinates();

                        // find the error cause and install the error marker on the corresponding
                        // field
                        Hashtable props = ModelHelper.createMarkerDescription(document, searchAdapter,
                                message, severity, coordinates);
                        if (props != null)
                        {
                        	model.setMarker(props, Model.TLC_MODEL_ERROR_MARKER_SANY);
                        }

                    } else
                    {
                        // the reported error is not pointing to the MC file.
                        throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID,
                                "Fatal error during validation of the model. "
                                        + "SANY discovered an error somewhere else than the MC file. "
                                        + "This is a bug. The error message was " + message + " in the module "
                                        + markerHolder.getModuleName()));
                    }
                } else
                {
                    throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID,
                            "Fatal error during validation of the model. "
                                    + "SANY discovered an error somewhere else than the MC file. "
                                    + "This is a bug. The error message was " + message + "."));
                }

            }

        } finally
        {
            /*
             * The document provider is not needed. Always disconnect it to avoid a memory leak.
             * 
             * Keeping it connected only seems to provide synchronization of
             * the document with file changes. That is not necessary in this context.
             */
            fileDocumentProvider.disconnect(fileEditorInput);
            monitor.done();
        }

        if (MODE_GENERATE.equals(mode))
        {
            // generation is done
            // nothing to do more
            return false;
        } else
        {
            TLCActivator.logDebug("Final check for the " + mode + " mode. The result of the check is " + status);
            if(status == false) {
            		return status;
            }
        }
	    }
		return true;
    }

    /**
     * 5. method called on launch
     * Main launch method called by the platform on model launches
     */
    public void launch(ILaunchConfiguration config, String mode, ILaunch launch, IProgressMonitor monitor)
            throws CoreException
    {
    		// check the modes
        if (!MODE_MODELCHECK.equals(mode) && !MODE_MODELCHECK_ALL_GENERATED.equals(mode))
        {
            throw new CoreException(
                    new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID, "Unsupported launch mode " + mode));
        }
        Model tlcModel = config.getAdapter(Model.class);
        
        List<String> moduleNames = getModulesToRun(tlcModel, mode);
	    if(moduleNames.isEmpty()) return;
	    
	    for(String moduleName: moduleNames) {
	    makeModuleRootModule(moduleName);

        // retrieve the project containing the specification
        IProject project = ResourceHelper.getProject(specName);
        if (project == null)
        {
            // project could not be found
            throw new CoreException(new Status(IStatus.ERROR, TLCActivator.PLUGIN_ID,
                    "Error accessing the spec project " + specName));
        }

        // setup the running flag
        // from this point any termination of the run must reset the
        // flag
        Model model = config.getAdapter(Model.class);
        model.setRunning(true);

        // set the model to have the original trace shown
        config.getAdapter(Model.class).setOriginalTraceShown(true);

        // number of workers
        int numberOfWorkers = config.getAttribute(LAUNCH_NUMBER_OF_WORKERS, LAUNCH_NUMBER_OF_WORKERS_DEFAULT);

        // distributed launch (legacy launch configurations pre-dating TLC distributed functionality 
        // do not have the LAUNCH_DISTRIBUTED attribute. Then, it obviously defaults to distribution turned off.
        // Trying to lookup a non-existing attribute would cause a runtime exception.)
        // Then it could also be true or false. The legacy flag showing if "ad hoc" distribution is turned
        // on or not. Simply map it to "ad hoc" or "off".
        String cloud = "off";
        if (config.hasAttribute(LAUNCH_DISTRIBUTED)) {
        	try {
        		cloud = config.getAttribute(LAUNCH_DISTRIBUTED, LAUNCH_DISTRIBUTED_DEFAULT);
        	} catch (CoreException e) {
        		boolean distributed = config.getAttribute(LAUNCH_DISTRIBUTED, false);
        		if (distributed) {
        			cloud = "ad hoc";
        		}
        	}
        }
        
        // TLC job
        Job job = null;
        if("off".equalsIgnoreCase(cloud)) {
        	String filePrefix = (moduleNames.size() == 1 && !moduleName.equals("Init")) ? "" : ("_" + moduleName);
        	job = new TLCProcessJob(specName, modelName, launch, numberOfWorkers, filePrefix);
            // The TLC job itself does not do any file IO
            job.setRule(mutexRule);
            job.setName(modelName + " run for " + moduleName);
        } else {
        	if ("ad hoc".equalsIgnoreCase(cloud)) {
        		job = new DistributedTLCJob(specName, modelName, launch, numberOfWorkers);
                job.setRule(mutexRule);
        	} else {
        		numberOfWorkers = config.getAttribute(LAUNCH_DISTRIBUTED_NODES_COUNT, LAUNCH_DISTRIBUTED_NODES_COUNT_DEFAULT);
                //final IProject iproject = ResourceHelper.getProject(specName);
                final IFolder launchDir = project.getFolder(modelName);
                final File file = launchDir.getRawLocation().makeAbsolute().toFile();

				final BundleContext bundleContext = FrameworkUtil.getBundle(
						TLCModelLaunchDelegate.class).getBundleContext();
				final ServiceReference<IExtensionRegistry> serviceReference = bundleContext
						.getServiceReference(IExtensionRegistry.class);
				final IExtensionRegistry registry = bundleContext
						.getService(serviceReference);
				final IConfigurationElement[] elements = registry
						.getConfigurationElementsFor("org.lamport.tla.toolx.tlc.job");
				for (IConfigurationElement element : elements) {
					final DummyProcess process = new DummyProcess(launch);
					launch.addProcess(process);
					final TLCJobFactory factory = (TLCJobFactory) element
							.createExecutableExtension("clazz");
					final Properties props = new Properties();
					props.put(TLCJobFactory.MAIN_CLASS, tlc2.TLC.class.getName());
					// Add model and spec name to properties to make the model
					// checker run easily identifiable in the result email.
					props.put(TLCJobFactory.MODEL_NAME, model.getName());
					props.put(TLCJobFactory.SPEC_NAME, model.getSpec().getName());
					if (numberOfWorkers > 1) {
						props.put(TLCJobFactory.MAIN_CLASS, tlc2.tool.distributed.TLCServer.class.getName());
					}
					props.put(TLCJobFactory.MAIL_ADDRESS, config.getAttribute(
							LAUNCH_DISTRIBUTED_RESULT_MAIL_ADDRESS, "tlc@localhost"));
					
					// The parameters below are the only one currently useful with CloudDistributedTLC
					final StringBuffer tlcParams = new StringBuffer();
					
			        // fp seed offset (decrease by one to map from [1, 64] interval to [0, 63] array address
			        final int fpSeedOffset = launch.getLaunchConfiguration().getAttribute(LAUNCH_FP_INDEX, LAUNCH_FP_INDEX_DEFAULT);
			        tlcParams.append("-fp ");
			        tlcParams.append(String.valueOf(fpSeedOffset - 1));
		        	    tlcParams.append(" ");
			        
			        // add maxSetSize argument if not equal to the default
			        // code added by LL on 9 Mar 2012
			        final int maxSetSize = launch.getLaunchConfiguration().getAttribute(
			                LAUNCH_MAXSETSIZE, TLCGlobals.setBound);
			        if (maxSetSize != TLCGlobals.setBound) {
			        	tlcParams.append("-maxSetSize ");
			        	tlcParams.append(String.valueOf(maxSetSize));
			        	tlcParams.append(" ");
			        }
			        
			        boolean checkDeadlock = config.getAttribute(IModelConfigurationConstants.MODEL_CORRECTNESS_CHECK_DEADLOCK,
							IModelConfigurationDefaults.MODEL_CORRECTNESS_CHECK_DEADLOCK_DEFAULT);
					if (!checkDeadlock) {
						tlcParams.append("-deadlock");
					}

					job = factory.getTLCJob(cloud, file, numberOfWorkers, props, tlcParams.toString());
					job.addJobChangeListener(new WithStatusJobChangeListener(process, config.getAdapter(Model.class)));
					break;
				}
				// Notify the user that something went wrong and ask him to report it. This code path
				// is usually taken if the Toolbox distribution is incomplete. Thus, the exception should
				// never happen at the user's end. Anyway, try to give a clue what might be wrong.
                if (job == null) {
                	throw new CoreException(
                			new Status(
                					IStatus.ERROR,
                					TLCActivator.PLUGIN_ID,
                					String.format(
                							"The distribution mode '%s' selected in the \"How to run?\" section caused "
                									+ "an error. Check the Toolbox's \"Installation Details\" if the "
                									+ "'JCloud distributed TLC provider' is installed. If not, this is a bug "
                									+ "and should be reported to the Toolbox authors. Thank you for "
                									+ "your help and sorry for the inconvenience."
                									+ "\n\n"
                									+ "In the meantime, try running the Toolbox in non-distributed mode "
                									+ "by setting \"Run in distributed mode\" to 'off'. "
                									+ "You might have to 'Repair' your model via the \"Spec Explorer\" first.",
                									cloud)));
                	// "Repairing" the model here with ModelHelper.recoverModel(config) does not work. The 
                	// markers indicating a broken model have not been installed at this point.
                }
        	}
        }
        job.setPriority(Job.LONG);
        job.setUser(true);

        String filePrefix = (moduleNames.size() == 1 && !moduleName.equals("Init")) ? "" : ("_" + moduleName);
        // setup the job change listener
        TLCJobChangeListener tlcJobListener = new TLCJobChangeListener(config.getAdapter(Model.class), filePrefix);
        job.addJobChangeListener(tlcJobListener);
        job.schedule();
	    }
    }
    
	// A DummyProcess instance has to be attached to the corresponding ILaunch
	// when the Job launched neither creates an IProcess nor IDebugTarget. In
    // this case the call to ILaunch#isTerminated will always evaluate to false
    // regardless of the real job's state.
	// Remember to call DummyProcess#setTerminated upon completion of the Job
	// with e.g. a IJobChangeListener.
	class DummyProcess implements IProcess {
		private final ILaunch launch;
		private boolean termiated = false;
		
		public DummyProcess(ILaunch aLaunch) {
			this.launch = aLaunch;
		}

		public void setTerminated() {
			this.termiated = true;
		}
		
		public void terminate() throws DebugException {
		}

		public boolean isTerminated() {
			return termiated;
		}

		public boolean canTerminate() {
			return !termiated;
		}

		public <T> T getAdapter(Class<T> adapter) {
			return null;
		}

		public String getAttribute(String key) {
			return null;
		}
		
		public void setAttribute(String key, String value) {
		}

		public IStreamsProxy getStreamsProxy() {
			return null;
		}

		public ILaunch getLaunch() {
			return launch;
		}

		public String getLabel() {
			return getClass().getSimpleName();
		}

		public int getExitValue() throws DebugException {
			return 0;
		}
	}

	// This opens up a dialog at the end of the job showing the status message
    class WithStatusJobChangeListener extends SimpleJobChangeListener {

		private final Model model;
		private final DummyProcess process;

		public WithStatusJobChangeListener(DummyProcess process, Model model) {
			this.process = process;
			this.model = model;
		}

		public void done(IJobChangeEvent event) {
			super.done(event);
			
			// TLC models seem to require some clean-up
			model.setLocked(false);
			model.setRunning(false);
			model.recover();
			
			process.setTerminated();

			final IStatus status = event.getJob().getResult();
			final String message = status.getMessage();
			if (status instanceof ITLCJobStatus) {
				final ITLCJobStatus result = (ITLCJobStatus) status;
				final URL url = result.getURL();
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						boolean yesOpenBrowser = MessageDialog
								.openConfirm(
										Display.getDefault().getActiveShell(),
										"Cloud TLC",
										message
												+ "\n\nClick OK to open a status page in your browser. "
												+ "Click Cancel to ignore the status page (you can still go there later).");
						if (yesOpenBrowser) {
							try {
								PlatformUI.getWorkbench().getBrowserSupport()
								.getExternalBrowser().openURL(url);
							} catch (PartInitException doesNotHappen) {
								doesNotHappen.printStackTrace();
							}
						}
					}
				});
			}
		}
    }
    
    /**
     * listens to the termination of the TLC run 
     */
    class TLCJobChangeListener extends SimpleJobChangeListener
    {
        private Model model;
        private String filePrefix;

        /**
         * Constructs the change listener
         * @param model the config to modify after the job completion
         */
        public TLCJobChangeListener(Model model, String filePrefix)
        {
            this.model = model;
            this.filePrefix = filePrefix;
            model.setFilePrefix(filePrefix);
        }

        public void done(IJobChangeEvent event)
        {
            super.done(event);

            /*
             * After the job has completed, new check points
             * may have been created in the file system but
             * not yet recognized by the eclipse resource
             * framework. The following method
             * is called with the flag to refresh. This synch's
             * the resource framework with the new checkpoints (if any).
             * 
             * This refresh operation is wrapped in a job. The following
             * explains why. To read about jobs in general, check out:
             * http://www.eclipse.org/articles/Article-Concurrency/jobs-api.html.
             * 
             * The refresh operation is a long running job. This means
             * that it cannot be run within the running code of another
             * job whose scheduling rule does not encompass the scheduling rule
             * of the refresh operation. This method (done()) seems to be
             * called during the running of the TLC job. Therefore, simply
             * calling ModelHelper.getCheckpoints(config, true) results
             * in an exception because getCheckpoints calls the refresh
             * operation which has a scheduling rule that is not included
             * by the TLC job scheduling rule.
             * 
             * To solve this problem, we wrap the call to getCheckpoints()
             * in another job, set the scheduling rule to be a scheduling rule
             * for refreshing the model folder, and schedule that wrapping job
             * to be run later.
             */
            IProject project = ResourceHelper.getProject(specName);
            IFolder modelFolder = project.getFolder(modelName);
			// Determines if we are in run-all mode
            if(filePrefix != null && !filePrefix.isEmpty()) {
            	    boolean mcError = false;
	            modelFolder = project.getFolder(modelName).getFolder(filePrefix);
	            mcError = checkForError(modelFolder);
				if(mcError) {
					final String name = event.getJob().getName();
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							MessageDialog
							.openError(
									Display.getDefault().getActiveShell(),
									name + " had errors", "There were errors running '" + name + "'. See the error explorer for more information. The remainder of the batch run has been cancelled.");
						}
					});
					// Cancel other jobs in case we are in run all mode
					Job[] allJobs = Job.getJobManager().find(TLCJob.AllJobsMatcher);
					for(Job job: allJobs) {
						job.cancel();
					}
				}
			}
            WorkspaceJob refreshJob = new WorkspaceJob("") {

                public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException
                {
                	model.getCheckpoints(true);
                    return Status.OK_STATUS;
                }
            };
            refreshJob.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().refreshRule(modelFolder));
            refreshJob.schedule();

            // make the model modification in order to make it runnable again
            if (event.getJob() instanceof TLCProcessJob) {
            	Assert.isTrue(event.getJob() instanceof TLCProcessJob);
            	Assert.isNotNull(event.getResult());
            	TLCProcessJob tlcJob = (TLCProcessJob) event.getJob();
            	if (event.getResult().isOK())
            	{
            		int autoLockTime = model.getAutoLockTime();
            		// auto lock time is in minutes, getTLCStartTime() and getTLCEndTime()
            		// are in milliseconds
            		if (tlcJob.getTlcEndTime() - tlcJob.getTlcStartTime() > autoLockTime * 60 * 1000)
            		{
            			// length of job execution exceeded a certain length of time
            			// should lock
            			model.setLocked(true);
            		}
            	}
				if (!Status.CANCEL_STATUS.equals(event.getJob().getResult()) && tlcJob.getExitValue() > 0) {
					// if TLC crashed with exit value > 0 and the user did *not*
					// click cancel, mark the job as crashed.
					model.setStale();
				}
            }
            model.setRunning(false);
       }
    }

    /**
     * A listener writing the job state to the System.out
     */
    class SimpleJobChangeListener extends JobChangeAdapter
    {

        public void done(IJobChangeEvent event)
        {
            String jobName = event.getJob().getName();
            String status = null;
            if (event.getResult().isOK())
            {
                status = "Done";
            } else
            {
                // analyze the cause
                switch (event.getResult().getSeverity()) {
                case IStatus.CANCEL:
                    status = "Cancelled";
                    break;
                case IStatus.ERROR:
                    status = "Error";
                    break;
                default:
                    status = "Unknown";
                    break;
                }
            }
            TLCActivator.logDebug("Job '" + jobName + "' terminated with status: { " + status + " }");
        }
    };

    /**
     * A simple mutex rule 
     */
    class MutexRule implements ISchedulingRule
    {
    	    String name = "";
    	    public MutexRule(String name) {
    	    	    this.name = name;
    	    }
        public boolean isConflicting(ISchedulingRule rule)
        {
        	    return this == rule;
            //return !this.name.equals("") && (rule instanceof MutexRule && (((MutexRule) rule).name.equals(this.name)));
        }

        public boolean contains(ISchedulingRule rule)
        {
        	    return this == rule;
            	//return !this.name.equals("") && (rule instanceof MutexRule && (((MutexRule) rule).name.equals(this.name)));
        }
    }
    
    private static List<String> getModulesForModel(Model model) {
	    	Spec spec = ToolboxHandle.getCurrentSpec();
	    List<String> moduleNames = readModuleList(spec);
	    List<String> relevantModuleNames = new ArrayList<String>();
	    for(String moduleName : moduleNames) {
		    String[] nameParts = moduleName.split("/");
		    moduleName = nameParts[nameParts.length - 1];
		    if(model.getName().endsWith("Init") && moduleName.endsWith("Init")) {
			    ;
		    } else if (model.getName().endsWith("Interference") && moduleName.endsWith("D")) {
		        	;
		    } else if(model.getName().endsWith("Simulation") && !moduleName.endsWith("D") && !moduleName.equals("Init")) {
		        ;
		    } else {
			    continue;
		    }
		    relevantModuleNames.add(moduleName);
	    }
	    return relevantModuleNames;
    }

    //TODO: Don't duplicate this (changed it to give just file name not path)
	private static List<String> readModuleList(Spec spec) {
		IFile file = spec.getRootFile();
		IPath tlaPath = file.getLocation();
		IPath moduleListPath = tlaPath.removeLastSegments(1);
		moduleListPath = moduleListPath.append("moduleGenInfo.txt");
		File moduleListInfo = moduleListPath.toFile();
		
		List<String> modules = new ArrayList<String>();
		BufferedReader moduleListReader = null;
		try {
			moduleListReader = new BufferedReader(new FileReader(moduleListInfo));
			String line = moduleListReader.readLine();
			while (line != null) {
				modules.add(line);
				line = moduleListReader.readLine();
			}
		} catch (Exception e) {
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

    private static void makeModuleRootModule(Spec spec, String moduleName) {
        if(moduleName.endsWith(".tla")) {
	        moduleName = moduleName.substring(0, moduleName.length() - 4);
        }
        ToolboxHandle.getCurrentSpec().getRootModule().setPrimaryFileName(moduleName);
        ToolboxHandle.getCurrentSpec().getRootModule().setName(moduleName);
    }
    
    private List<String> getCurrentModule()  {
    	    List<String> modules = new ArrayList<String>();
	    	if(!ToolboxHandle.getCurrentSpec().usesLinearisabilityModuleGenerator()) {
				return modules;
			}
			// Check that the current editor is a TLA+ module editor, before we set it as the root module
			IEditorPart editor = UIHelper.getActiveEditor();
			if(!(editor.getClass().getName().contains("TLAEditorAndPDFViewer"))) {
			return modules;
		}
		
		// Make the module currently being edited the root module so that the model checker runs against this
		IEditorInput input = UIHelper.getActiveEditor().getEditorInput();
		String moduleName = input.getName();
	    	modules.add(moduleName);
        return modules;
    }
    
    private boolean checkForError(IFolder modelFolder) {
        	boolean mcError = false;
    	    IFile output = modelFolder.getFile("MC.out");
        File outFile = output.getLocation().toFile();
        BufferedReader br = null;
        try {
			br = new BufferedReader(new FileReader(outFile));
			String line = null;
			mcError = true;
			boolean first = true;
			while((line = br.readLine()) != null) {
				if((first && line.trim().isEmpty()) || line.contains("No error")) {
			        mcError = false;
				}
				first = false;
			}
			if(first) mcError = false;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
        return mcError;
    }
}
