package org.lamport.tla.toolbox.tool.tlc.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.lamport.tla.toolbox.AbstractTLCActivator;
import org.lamport.tla.toolbox.spec.Spec;
import org.lamport.tla.toolbox.tool.tlc.handlers.OpenModelHandler;
import org.lamport.tla.toolbox.tool.tlc.launch.IConfigurationConstants;
import org.lamport.tla.toolbox.tool.tlc.launch.IModelConfigurationConstants;
import org.lamport.tla.toolbox.tool.tlc.launch.IModelConfigurationDefaults;
import org.lamport.tla.toolbox.tool.tlc.launch.TLCModelLaunchDelegate;
import org.lamport.tla.toolbox.tool.tlc.model.Model;
import org.lamport.tla.toolbox.ui.handler.IPL0ModelCreator;
import org.lamport.tla.toolbox.util.UIHelper;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class TLCUIActivator extends AbstractTLCActivator
{
    // The plug-in ID
    public static final String PLUGIN_ID = "org.lamport.tla.toolbox.tool.tlc.ui";

    // The shared instance
    private static TLCUIActivator plugin;

    private Font courierFont;
    private Font outputFont;

    /*
     * The colors used for trace row highlighting. These should be in some
     * central location containing all colors and fonts to make it easy to
     * make them changeable by preferences.
     */
    private Color changedColor;
    private Color addedColor;
    private Color deletedColor;

    // update the CNF content
    /*
    private PerspectiveAdapter perspectiveAdapter = new PerspectiveAdapter() 
    {
        public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective)
        {
            if (TLCPerspective.ID.equals(perspective.getId()))
            {
                ToolboxHandle.setToolboxNCEActive(ModelContentProvider.TLC_NCE, true);
            }
        }

        public void perspectiveDeactivated(IWorkbenchPage page, IPerspectiveDescriptor perspective)
        {
            if (TLCPerspective.ID.equals(perspective.getId()))
            {
                ToolboxHandle.setToolboxNCEActive(ModelContentProvider.TLC_NCE, false);
            }
        }
    };
    */

    /**
     * The constructor
     */
    public TLCUIActivator()
    {
    	super(PLUGIN_ID);
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;

        changedColor = new Color(null, 255, 200, 200);
        addedColor = new Color(null, 255, 255, 200);
        deletedColor = new Color(null, 240, 240, 255);
        
        context.registerService(IPL0ModelCreator.class, new IPL0ModelCreator() {
		    /**
		     * Create a new model and attach it to an existing spec. Used to auto-add modules
		     * to Spec for linearisability module generation tool. The module created has a 
		     * temporal formula behaviour spec named "Spec"
		     * 
		     * @param spec The spec to add the module to
		     * @param modelName The name of the new model
		     * @param invariants The invariants that the model should check
		     */
			public void createModel(Spec spec, String modelName, List<String> invariants) {
				if (spec == null)
		        {
		            // no spec
		        	TLCUIActivator.getDefault().logWarning("BUG: no spec");
		            return;
		        }

		        // project
		        IProject specProject = spec.getProject();

		        // get the launch manager
		        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();

		        // get the launch type (model check)
		        ILaunchConfigurationType launchConfigurationType = launchManager
		                .getLaunchConfigurationType(TLCModelLaunchDelegate.LAUNCH_CONFIGURATION_TYPE);
		        
		        try
		        {
		            // create new launch instance
		            ILaunchConfigurationWorkingCopy launchCopy = launchConfigurationType.newInstance(specProject, specProject
		                    .getName()
		                    + Model.SPEC_MODEL_DELIM + modelName);

		            launchCopy.setAttribute(IConfigurationConstants.SPEC_NAME, spec.getName());
		            // it is easier to do launchCopy.getProject().getPersistentProperty(SPEC_ROOT_FILE)
		            // launchCopy.setAttribute(SPEC_ROOT_FILE, specRootModule.getLocation().toOSString());
		            launchCopy.setAttribute(IConfigurationConstants.MODEL_NAME, modelName);
		            
		            // We want models where the behaviour spec is the temporal formuala "Spec"
		            launchCopy.setAttribute(IModelConfigurationConstants.MODEL_BEHAVIOR_CLOSED_SPECIFICATION, "Spec");
		            launchCopy.setAttribute(IModelConfigurationConstants.MODEL_BEHAVIOR_SPEC_TYPE,
		                    IModelConfigurationDefaults.MODEL_BEHAVIOR_TYPE_SPEC_CLOSED); 
		            
		            // Turn off deadlock checking
		            launchCopy.setAttribute(IModelConfigurationConstants.MODEL_CORRECTNESS_CHECK_DEADLOCK, false);
		            
		            // For some reason, the model expects invariants to have a digit on the front, namely a '1', e.g. '1ABS'
		            List<String> formattedInvariants = new ArrayList<String>();
		            for(String inv: invariants) {
		            		formattedInvariants.add("1" + inv);
		            }
		            launchCopy.setAttribute(IModelConfigurationConstants.MODEL_CORRECTNESS_INVARIANTS, formattedInvariants);
		            
		            
		            
		            ILaunchConfiguration launchSaved = launchCopy.doSave();

		            final String name = modelName;
		            final String specName = spec.getName();
		            	
		            
		            // It is important to open up any model created, even if it annoys the user,
		            // as this is the only way all the parameters get received and saved
		            UIHelper.runUIAsync((new Runnable () {
		            		public void run () {
		            			try {
									OpenModelHandler.executeInternal(name, specName);
								} catch (ExecutionException e) {
									e.printStackTrace();
								}
		            		}
		            }));
		            
		            return;

		        } catch (CoreException e)
		        {
		            TLCUIActivator.getDefault().logError("Error creating a model", e);
		        }

		        return;
			}
		}, null);
    }

    public Color getChangedColor()
    {
        return changedColor;
    }


    public Color getAddedColor()
    {
        return addedColor;
    }


    public Color getDeletedColor()
    {
        return deletedColor;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception
    {
        // IWorkbenchWindow window = UIHelper.getActiveWindow();
        /*
        if (window != null)
        {
            window.removePerspectiveListener(perspectiveAdapter);
        }*/
        if (courierFont != null)
        {
            courierFont.dispose();
        }
        if (outputFont != null)
        {
            outputFont.dispose();
        }
        
        /*
         * Remove the colors
         */
        addedColor.dispose();
        changedColor.dispose();
        deletedColor.dispose();

        
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static TLCUIActivator getDefault()
    {
        return plugin;
    }

    public synchronized Font getCourierFont()
    {
        if (courierFont == null)
        {
            courierFont = new Font(UIHelper.getShellProvider().getShell().getDisplay(), "Courier New", 11, SWT.NORMAL);
        }
        return courierFont;
    }

    /**
     * @return
     */
    public Font getOutputFont()
    {
        if (outputFont == null)
        {
            outputFont = new Font(UIHelper.getShellProvider().getShell().getDisplay(), "Courier New", 8, SWT.NORMAL);
        }
        return outputFont;
    }

    /**
     * Returns an image descriptor for the image file at the given
     * plug-in relative path
     *
     * @param path the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path)
    {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

    /**
     * @param colorRed
     * @return
     */
    public static Color getColor(int color)
    {
        return UIHelper.getShellProvider().getShell().getDisplay().getSystemColor(color);
    }
}
