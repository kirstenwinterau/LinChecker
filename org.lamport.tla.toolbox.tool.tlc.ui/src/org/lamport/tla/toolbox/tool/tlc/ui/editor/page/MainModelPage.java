package org.lamport.tla.toolbox.tool.tlc.ui.editor.page;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import javax.mail.internet.AddressException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.HyperlinkGroup;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.IMessageManager;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;
import org.lamport.tla.toolbox.tool.tlc.launch.IConfigurationConstants;
import org.lamport.tla.toolbox.tool.tlc.launch.IConfigurationDefaults;
import org.lamport.tla.toolbox.tool.tlc.model.Assignment;
import org.lamport.tla.toolbox.tool.tlc.model.TypedSet;
import org.lamport.tla.toolbox.tool.tlc.ui.TLCUIActivator;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.DataBindingManager;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.ModelEditor;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.part.ValidateableConstantSectionPart;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.part.ValidateableSectionPart;
import org.lamport.tla.toolbox.tool.tlc.ui.editor.part.ValidateableTableSectionPart;
import org.lamport.tla.toolbox.tool.tlc.ui.preference.ITLCPreferenceConstants;
import org.lamport.tla.toolbox.tool.tlc.ui.preference.TLCPreferenceInitializer;
import org.lamport.tla.toolbox.tool.tlc.ui.util.DirtyMarkingListener;
import org.lamport.tla.toolbox.tool.tlc.ui.util.FormHelper;
import org.lamport.tla.toolbox.tool.tlc.ui.util.SemanticHelper;
import org.lamport.tla.toolbox.tool.tlc.util.ModelHelper;
import org.lamport.tla.toolbox.util.HelpButton;
import org.lamport.tla.toolbox.util.IHelpConstants;
import org.lamport.tla.toolbox.util.ResourceHelper;
import org.lamport.tla.toolbox.util.UIHelper;

import tla2sany.semantic.ModuleNode;
import util.TLCRuntime;

/**
 * Main model page represents information for most users
 * <br>
 * This class is a a sub-class of the BasicFormPage and is used to represent the first tab of the
 * multi-page-editor which is used to edit the model files.  
 * 
 * 
 * @author Simon Zambrovski
 * This is the FormPage class for the Model Overview tabbed page of
 * the model editor.
 */
public class MainModelPage extends BasicFormPage implements IConfigurationConstants, IConfigurationDefaults
{
    public static final String ID = "MainModelPage";
    public static final String TITLE = "Model Overview";

    private Button noSpecRadio; // re-added on 10 Sep 2009
    private Button closedFormulaRadio;
    private Button initNextFairnessRadio;
	private SourceViewer commentsSource;
    private SourceViewer initFormulaSource;
    private SourceViewer nextFormulaSource;
    // private SourceViewer fairnessFormulaSource;
    private SourceViewer specSource;
    private Button checkDeadlockButton;
    private Spinner workers;
    /**
	 * Spinner to set the number of (expected) distributed FPSets.
	 */
    private Spinner distributedFPSetCountSpinner;
    private Spinner distributedNodesCountSpinner;
    private Combo networkInterfaceCombo;
    private Scale maxHeapSize;
    private TableViewer invariantsTable;
    private TableViewer propertiesTable;
    private TableViewer constantTable;
    private ModifyListener widgetActivatingListener = new ModifyListener() {
        // select the section (radio button) the text field belong to
        public void modifyText(ModifyEvent e)
        {
            if (e.widget == specSource.getControl())
            {
                noSpecRadio.setSelection(false);
                closedFormulaRadio.setSelection(true);
                initNextFairnessRadio.setSelection(false);
            } else if (e.widget == initFormulaSource.getControl() || e.widget == nextFormulaSource.getControl()
            /* || e.widget == fairnessFormulaSource.getControl() */)
            {
                noSpecRadio.setSelection(false);
                closedFormulaRadio.setSelection(false);
                initNextFairnessRadio.setSelection(true);
            }
        }
    };

    private ImageHyperlink runLink;
    private ImageHyperlink generateLink;

    /**
     * section expanding adapter
     * {@link Hyperlink#getHref()} must deliver the section id as described in {@link DataBindingManager#bindSection(ExpandableComposite, String, String)}
     */
    protected HyperlinkAdapter sectionExpandingAdapter = new HyperlinkAdapter() {
        public void linkActivated(HyperlinkEvent e)
        {
            String sectionId = (String) e.getHref();
            // first switch to the page (and construct it if not yet
            // constructed)
            getEditor().setActivePage(AdvancedModelPage.ID);
            // then expand the section
            expandSection(sectionId);
        }
    };
    private Button checkpointButton;
    private Text checkpointIdText;

    /*
     * Checkbox and input box for distributed model checking
     * 
     * combo: choose distribution and cloud to run on
     * text: additional vm arguments (e.g. -Djava.rmi...) 
     * text: pre-flight script
     */
    private Combo distributedCombo;
    private Text resultMailAddressText;
    
    // The widgets to display the checkpoint size and
    // the delete button.
    private Label chkpointSizeLabel;
    private Text checkpointSizeText;
    private Button chkptDeleteButton;
    
	/**
	 * Used to interpolate y-values for memory scale
	 */
	private final Interpolator linearInterpolator;
	private Composite distributedOptions;
	
    /**
     * constructs the main model page 
     * @param editor
     */
    public MainModelPage(FormEditor editor)
    {
        super(editor, MainModelPage.ID, MainModelPage.TITLE);
        this.helpId = IHelpConstants.MAIN_MODEL_PAGE;
        this.imagePath = "icons/full/choice_sc_obj.gif";

		// available system memory
		final long phySysMem = TLCRuntime.getInstance().getAbsolutePhysicalSystemMemory(1.0d);
		
		// 0.) Create LinearInterpolator with two additional points 0,0 and 1,0 which
		int s = 0;
		double[] x = new double[6];
		double[] y = new double[x.length];
		
		// base point
		y[s] = 0d;
		x[s++] = 0d;

		// 1.) Minumum TLC requirements 
		// Use hard-coded minfpmemsize value * 4 * 10 regardless of how big the
		// model is. *4 because .25 mem is used for FPs
		double lowerLimit = ( (TLCRuntime.MinFpMemSize / 1024 / 1024 * 4d) / phySysMem) / 2;
		x[s] = lowerLimit;
		y[s++] = 0d;
		
		// a.)
		// Current bloat in software is assumed to grow according to Moore's law => 
		// 2^((Year-1993)/ 2)+2)
		// (1993 as base results from a statistic of windows OS memory requirements)
		final int currentYear = Calendar.getInstance().get(Calendar.YEAR);
		double estimateSoftwareBloatInMBytes = Math.pow(2, ((currentYear - 1993) / 2) + 2);
		
		// 2.) Optimal range 
		x[s] = lowerLimit * 2d;
		y[s++] = 1.0d;
		x[s] = 1.0d - (estimateSoftwareBloatInMBytes / phySysMem);
		y[s++] = 1.0d;
		
		// 3.) Calculate OS reserve
		double upperLimit = 1.0d - (estimateSoftwareBloatInMBytes / phySysMem) / 2;
		x[s] = upperLimit;
		y[s++] = 0d;
		
		// base point
		x[s] = 1d;
		y[s] = 0d;
		
		linearInterpolator = new Interpolator(x, y);
}

    /**
     * @see BasicFormPage#loadData()
     */
    protected void loadData() throws CoreException
    {
        int specType = getModel().getAttribute(MODEL_BEHAVIOR_SPEC_TYPE, MODEL_BEHAVIOR_TYPE_DEFAULT);

        // set up the radio buttons
        setSpecSelection(specType);

        // closed spec
        String modelSpecification = getModel().getAttribute(MODEL_BEHAVIOR_CLOSED_SPECIFICATION, EMPTY_STRING);
        Document closedDoc = new Document(modelSpecification);
        this.specSource.setDocument(closedDoc);

        // init
        String modelInit = getModel().getAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT, EMPTY_STRING);
        Document initDoc = new Document(modelInit);
        this.initFormulaSource.setDocument(initDoc);

        // next
        String modelNext = getModel().getAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT, EMPTY_STRING);
        Document nextDoc = new Document(modelNext);
        this.nextFormulaSource.setDocument(nextDoc);

        // fairness
        // String modelFairness =
        // getModel().getAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_FAIRNESS,
        // EMPTY_STRING);
        // Document fairnessDoc = new Document(modelFairness);
        // this.fairnessFormulaSource.setDocument(fairnessDoc);

        // number of workers
        workers.setSelection(getModel().getAttribute(LAUNCH_NUMBER_OF_WORKERS, LAUNCH_NUMBER_OF_WORKERS_DEFAULT));

        // max JVM heap size
        final int defaultMaxHeapSize = TLCUIActivator.getDefault().getPreferenceStore().getInt(
                ITLCPreferenceConstants.I_TLC_MAXIMUM_HEAP_SIZE_DEFAULT);
        final int maxHeapSizeValue = getModel().getAttribute(LAUNCH_MAX_HEAP_SIZE, defaultMaxHeapSize);
        maxHeapSize.setSelection(maxHeapSizeValue);
        
        // check deadlock
        boolean checkDeadlock = getModel().getAttribute(MODEL_CORRECTNESS_CHECK_DEADLOCK,
                MODEL_CORRECTNESS_CHECK_DEADLOCK_DEFAULT);
        this.checkDeadlockButton.setSelection(checkDeadlock);

        // invariants
        List<String> serializedList = getModel().getAttribute(MODEL_CORRECTNESS_INVARIANTS, new Vector<String>());
        FormHelper.setSerializedInput(invariantsTable, serializedList);

        // properties
        serializedList = getModel().getAttribute(MODEL_CORRECTNESS_PROPERTIES, new Vector<String>());
        FormHelper.setSerializedInput(propertiesTable, serializedList);

        // constants from the model
        List<String> savedConstants = getModel().getAttribute(MODEL_PARAMETER_CONSTANTS, new Vector<String>());
        FormHelper.setSerializedInput(constantTable, savedConstants);

        // recover from the checkpoint
        boolean recover = getModel().getAttribute(LAUNCH_RECOVER, LAUNCH_RECOVER_DEFAULT);
        this.checkpointButton.setSelection(recover);
        
        /*
         * Distributed mode
         */
        String cloud = "off";
        try {
			cloud = getModel().getAttribute(LAUNCH_DISTRIBUTED, LAUNCH_DISTRIBUTED_DEFAULT);
        } catch (CoreException e) {
        	// LAUNCH_DISTRIBUTED might still be stored in a legacy format. The user is
        	// opening an old model.
        	boolean distributed = getModel().getAttribute(LAUNCH_DISTRIBUTED, false);
        	if (distributed) {
        		cloud = "ad hoc";
        	}
        }
        final String[] items = distributedCombo.getItems();
        for (int i = 0; i < items.length; i++) {
			final String string = items[i];
			if (cloud.equals(string)) {
				distributedCombo.select(i);
				break;
			}
		}

		if (cloud.equalsIgnoreCase("aws-ec2") || cloud.equalsIgnoreCase("Azure")) {
			MainModelPage.this.putOnTopOfStack("jclouds", false, false);
			String email = getModel().getAttribute(LAUNCH_DISTRIBUTED_RESULT_MAIL_ADDRESS, LAUNCH_DISTRIBUTED_RESULT_MAIL_ADDRESS_DEFAULT);
			resultMailAddressText.setText(email);
		} else if(cloud.equalsIgnoreCase("ad hoc")) {
			MainModelPage.this.putOnTopOfStack("ad hoc", false, true);
		} else {
			MainModelPage.this.putOnTopOfStack("off", true, true);
		}
        
        // distribute FPSet count
        distributedFPSetCountSpinner.setSelection(getModel().getAttribute(LAUNCH_DISTRIBUTED_FPSET_COUNT, LAUNCH_DISTRIBUTED_FPSET_COUNT_DEFAULT));

        // distribute FPSet count
        distributedNodesCountSpinner.setSelection(getModel().getAttribute(LAUNCH_DISTRIBUTED_NODES_COUNT, LAUNCH_DISTRIBUTED_NODES_COUNT_DEFAULT));
        
        // comments/description/notes
        String commentsStr = getModel().getAttribute(MODEL_COMMENTS, EMPTY_STRING);
       	commentsSource.setDocument(new Document(commentsStr));
    }

    public void validatePage(boolean switchToErrorPage)
    {
        if (getManagedForm() == null)
        {
            return;
        }

        DataBindingManager dm = getDataBindingManager();
        IMessageManager mm = getManagedForm().getMessageManager();
        ModelEditor modelEditor = (ModelEditor) getEditor();

        // The following comment was apparently written by Simon:
           // delete the messages
           // this is now done in validateRunnable
           // in ModelEditor
           // resetAllMessages(false);
        // validateRunnable is in ModelEditor.  I believe it is executed only when
        // the user executes the Run or Validate Model command.
        // Errors that the validatePage method checks for should be cleared
        // whenever the method is called.  However, calling resetAllMessages
        // seems to be the wrong way to do it because error messages from all
        // pages are reported on each page.  Hence, that would require validating
        // all pages whenever any one is validated.  See the ModelEditor.removeErrorMessage
        // method for a further discussion of this problem.
        // Comments added by LL on 21 Mar 2013.
        
        // getting the root module node of the spec
        // this can be null!
        ModuleNode rootModuleNode = SemanticHelper.getRootModuleNode();

        // setup the names from the current page
        getLookupHelper().resetModelNames(this);

        // constants in the table
        List<Assignment> constants = (List<Assignment>) constantTable.getInput();
        // merge constants with currently defined in the specobj, if any
        if (rootModuleNode != null)
        {
            List<Assignment> toDelete = ModelHelper.mergeConstantLists(constants, ModelHelper.createConstantsList(rootModuleNode));
            if (!toDelete.isEmpty())
            {
                // if constants have been removed, these should be deleted from
                // the model too
                SectionPart constantSection = dm.getSection(dm.getSectionForAttribute(MODEL_PARAMETER_CONSTANTS));
                if (constantSection != null)
                {
                    // mark the constants dirty
                    constantSection.markDirty();
                }
            }
            constantTable.setInput(constants);
        }

        // The following string is used to test whether two differently-typed model
        // values appear in symmetry sets (sets of model values declared to be symmetric).
        // It is set to the type of the first typed model value found in a symmetry set.
        String symmetryType = null;
        // boolean symmetryUsed = false;
        // iterate over the constants
        if(constants == null) constants = new ArrayList<Assignment>();
        for (int i = 0; i < constants.size(); i++)
        {
            Assignment constant = (Assignment) constants.get(i);

            List<String> values = Arrays.asList(constant.getParams());
            // check parameters
            validateId(MODEL_PARAMETER_CONSTANTS, values, "param1_", "A parameter name");

            // the constant is still in the list
            if (constant.getRight() == null || EMPTY_STRING.equals(constant.getRight()))
            {
                // right side of assignment undefined
                modelEditor.addErrorMessage(constant.getLabel(), "Provide a value for constant " + constant.getLabel(),
                        this.getId(), IMessageProvider.ERROR, UIHelper.getWidget(dm
                                .getAttributeControl(MODEL_PARAMETER_CONSTANTS)));
                setComplete(false);
                expandSection(dm.getSectionForAttribute(MODEL_PARAMETER_CONSTANTS));

            } else
            {   // Following added by LL on 21 Mar 2013
                modelEditor.removeErrorMessage(constant.getLabel(), UIHelper.getWidget(dm
                                .getAttributeControl(MODEL_PARAMETER_CONSTANTS)));
                if (constant.isSetOfModelValues())
                {
                    TypedSet modelValuesSet = TypedSet.parseSet(constant.getRight());

                    if (constant.isSymmetricalSet())
                    {
                        boolean hasTwoTypes = false; // set true if this symmetry set has two differently-typed model
                        // values.
                        String typeString = null; // set to the type of the first typed model value in this symmetry
                        // set.
                        if (modelValuesSet.hasType())
                        {
                            typeString = modelValuesSet.getType();
                        } else
                        {
                            for (int j = 0; j < modelValuesSet.getValues().length; j++)
                            {
                                String thisTypeString = TypedSet.getTypeOfId(modelValuesSet.getValues()[j]);
                                if (thisTypeString != null)
                                {
                                    if (typeString != null && !typeString.equals(thisTypeString))
                                    {
                                        hasTwoTypes = true;
                                    } else
                                    {
                                        typeString = thisTypeString;
                                    }
                                }
                            }
                        }
                        if (hasTwoTypes
                                || (symmetryType != null && typeString != null && !typeString.equals(symmetryType)))
                        {
                            modelEditor.addErrorMessage(constant.getLabel(),
                                    "Two differently typed model values used in symmetry sets.",
                                    this.getId()/*constant*/, IMessageProvider.ERROR, UIHelper.getWidget(dm
                                            .getAttributeControl(MODEL_PARAMETER_CONSTANTS)));
                            setComplete(false);
                            expandSection(dm.getSectionForAttribute(MODEL_PARAMETER_CONSTANTS));
                        } else
                        {
                            if (typeString != null)
                            {
                                symmetryType = typeString;
                            }
                        }

                        // symmetry can be used for only one set of model values

                    }
                    if (modelValuesSet.getValueCount() > 0)
                    {
                        // there were values defined
                        // check if those are numbers?
                        /*
                         * if (modelValuesSet.hasANumberOnlyValue()) {
                         * mm.addMessage("modelValues1",
                         * "A model value can not be an number", modelValuesSet,
                         * IMessageProvider.ERROR, constantTable.getTable());
                         * setComplete(false); }
                         */

                        List<String> mvList = modelValuesSet.getValuesAsList();
                        // check list of model values
                        validateUsage(MODEL_PARAMETER_CONSTANTS, mvList, "modelValues2_", "A model value",
                                "Constant Assignment", true);
                        // check if the values are correct ids
                        validateId(MODEL_PARAMETER_CONSTANTS, mvList, "modelValues2_", "A model value");

                        // get widget for model values assigned to constant
                        Control widget = UIHelper.getWidget(dm.getAttributeControl(MODEL_PARAMETER_CONSTANTS));
                        // check if model values are config file keywords
                        for (int j = 0; j < mvList.size(); j++)
                        {
                            String value = (String) mvList.get(j);
                            if (SemanticHelper.isConfigFileKeyword(value))
                            {
                                modelEditor.addErrorMessage(value, "The toolbox cannot handle the model value " + value
                                        + ".", this.getId(), IMessageProvider.ERROR, widget);
                                setComplete(false);
                            }
                        }
                    } else
                    {
                        // This made an error by LL on 15 Nov 2009
                        modelEditor.addErrorMessage(constant.getLabel(),
                                "The set of model values should not be empty.", this.getId(), IMessageProvider.ERROR,
                                UIHelper.getWidget(dm.getAttributeControl(MODEL_PARAMETER_CONSTANTS)));
                        setComplete(false);
                    }
                }
            }

            // the constant identifier is a config file keyword
            if (SemanticHelper.isConfigFileKeyword(constant.getLabel()))
            {
                modelEditor.addErrorMessage(constant.getLabel(), "The toolbox cannot handle the constant identifier "
                        + constant.getLabel() + ".", this.getId(), IMessageProvider.ERROR, UIHelper.getWidget(dm
                        .getAttributeControl(MODEL_PARAMETER_CONSTANTS)));
                setComplete(false);
            }
        }

        // iterate over the constants again, and check if the parameters are used as Model Values
        for (int i = 0; i < constants.size(); i++)
        {
            Assignment constant = (Assignment) constants.get(i);
            List<String> values = Arrays.asList(constant.getParams());
            // check list of parameters
            validateUsage(MODEL_PARAMETER_CONSTANTS, values, "param1_", "A parameter name", "Constant Assignment",
                    false);
        }

        // number of workers
    	int number = workers.getSelection();
        if (number > Runtime.getRuntime().availableProcessors())
        {
            modelEditor.addErrorMessage("strangeNumber1", "Specified number of workers is " + number
                    + ". The number of processors available on the system is "
                    + Runtime.getRuntime().availableProcessors()
                    + ".\n The number of workers should not exceed the number of processors.",
                    this.getId(), IMessageProvider.WARNING, UIHelper.getWidget(dm
					        .getAttributeControl(LAUNCH_NUMBER_OF_WORKERS)));
            expandSection(SEC_HOW_TO_RUN);
        } else {
        	modelEditor.removeErrorMessage("strangeNumber1", UIHelper.getWidget(dm
			        .getAttributeControl(LAUNCH_NUMBER_OF_WORKERS)));
        }
        
		// legacy value?
		// better handle legacy models
		try {
			final int defaultMaxHeapSize = TLCUIActivator
					.getDefault()
					.getPreferenceStore()
					.getInt(ITLCPreferenceConstants.I_TLC_MAXIMUM_HEAP_SIZE_DEFAULT);
			final int legacyValue = getModel().getAttribute(
					LAUNCH_MAX_HEAP_SIZE, defaultMaxHeapSize);
			// old default, silently convert to new default
			if (legacyValue == 500) {
				getModel().setAttribute(
						LAUNCH_MAX_HEAP_SIZE, TLCPreferenceInitializer.MAX_HEAP_SIZE_DEFAULT);
				maxHeapSize.setSelection(TLCPreferenceInitializer.MAX_HEAP_SIZE_DEFAULT);
			} else if (legacyValue >= 100) {
				modelEditor
						.addErrorMessage(
								"strangeNumber1",
								"Found legacy value for physically memory of ("
										+ legacyValue
										+ "mb) that needs manual conversion. 25% is a safe setting on most computers.",
								this.getId(), IMessageProvider.WARNING,
								maxHeapSize);
				setComplete(false);
				expandSection(SEC_HOW_TO_RUN);
			}
		} catch (CoreException e) {
			TLCUIActivator.getDefault().logWarning("Faild to read heap value",
					e);
		}
        
        // max heap size
		// color the scale according to OS and TLC requirements
        int maxHeapSizeValue = maxHeapSize.getSelection();
		double x = maxHeapSizeValue / 100d;
		float y = (float) linearInterpolator.interpolate(x);
		maxHeapSize.setBackground(new Color(Display.getDefault(), new RGB(
				120 * y, 1 - y, 1f)));

        // fill the checkpoints
        updateCheckpoints();

        // recover from checkpoint
        if (checkpointButton.getSelection())
        {
            if (EMPTY_STRING.equals(checkpointIdText.getText()))
            {
                modelEditor.addErrorMessage("noChckpoint", "No checkpoint data found", this.getId(),
                        IMessageProvider.ERROR, UIHelper.getWidget(dm.getAttributeControl(LAUNCH_RECOVER)));
                setComplete(false);
                expandSection(SEC_HOW_TO_RUN);
            }
        }
        
        // The following code added by LL and DR on 10 Sep 2009.
        // Reset the enabling and selection of spec type depending on the number number
        // of variables in the spec.
        // This code needs to be modified when we modify the model launcher
        // to allow the No Spec option to be selected when there are variables.
        if (rootModuleNode != null)
        {
            if (rootModuleNode.getVariableDecls().length == 0)
            {
                setHasVariables(false);

                // set selection to the NO SPEC field
                if (!noSpecRadio.getSelection())
                {
                    // mark dirty so that changes must be written to config file
                    setSpecSelection(MODEL_BEHAVIOR_TYPE_NO_SPEC);
                    dm.getSection(dm.getSectionForAttribute(MODEL_BEHAVIOR_NO_SPEC)).markDirty();

                }
            } else
            {
                setHasVariables(true);

                // if there are variables, the user
                // may still want to choose no spec
                // so the selection is not changed
                //
                // if (noSpecRadio.getSelection())
                // {
                // // mark dirty so that changes must be written to config file
                // dm.getSection(dm.getSectionForAttribute(MODEL_BEHAVIOR_CLOSED_SPECIFICATION)).markDirty();
                // // set selection to the default
                // setSpecSelection(MODEL_BEHAVIOR_TYPE_DEFAULT);
                // }
            }
        }

        // This code disables or enables sections
        // depending on whether whether no spec is selected
        // or not.
        // This must occur after the preceeding code in case
        // that code changes the selection.
        Section whatToCheckSection = dm.getSection(SEC_WHAT_TO_CHECK).getSection();

        if (noSpecRadio.getSelection())
        {
            whatToCheckSection.setExpanded(false);
            whatToCheckSection.setEnabled(false);

        } else
        {
            whatToCheckSection.setExpanded(true);
            whatToCheckSection.setEnabled(true);
        }

        // The following code is not needed now because we automatically change
        // the selection to No Spec if there are no variables.
        //
        // if (selectedAttribute != null) {
        // // the user selected to use a spec
        // // check if there are variables declared
        // if (rootModuleNode != null
        // && rootModuleNode.getVariableDecls().length == 0) {
        // // no variables => install an error
        // mm.addMessage("noVariables",
        // "There were no variables declared in the root module",
        // null, IMessageProvider.ERROR, UIHelper.getWidget(dm
        // .getAttributeControl(selectedAttribute)));
        // setComplete(false);
        // expandSection(dm.getSectionForAttribute(selectedAttribute));
        // }
        // }

        // check if the selected fields are filled
        if (closedFormulaRadio.getSelection() && specSource.getDocument().get().trim().equals(""))
        {
            modelEditor.addErrorMessage("noSpec", "The formula must be provided", this.getId(), IMessageProvider.ERROR,
                    UIHelper.getWidget(dm.getAttributeControl(MODEL_BEHAVIOR_CLOSED_SPECIFICATION)));
            setComplete(false);
            expandSection(dm.getSectionForAttribute(MODEL_BEHAVIOR_CLOSED_SPECIFICATION));
        } else if (initNextFairnessRadio.getSelection())
        {
            String init = initFormulaSource.getDocument().get().trim();
            String next = nextFormulaSource.getDocument().get().trim();

            if (init.equals(""))
            {
                modelEditor.addErrorMessage("noInit", "The Init formula must be provided", this.getId(),
                        IMessageProvider.ERROR, UIHelper.getWidget(dm
                                .getAttributeControl(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT)));
                setComplete(false);
                expandSection(dm.getSectionForAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT));
            }
            if (next.equals(""))
            {
                modelEditor.addErrorMessage("noNext", "The Next formula must be provided", this.getId(),
                        IMessageProvider.ERROR, UIHelper.getWidget(dm
                                .getAttributeControl(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT)));
                setComplete(false);
                expandSection(dm.getSectionForAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT));
            }
        }

        mm.setAutoUpdate(true);

        super.validatePage(switchToErrorPage);
    }

    /**
     * This method is used to enable and disable UI widgets depending on the fact if the specification 
     * has variables. 
     * @param hasVariables true if the spec contains variables
     */
    private void setHasVariables(boolean hasVariables)
    {

        // the no spec option can be selected if there
        // are variables or no variables
        // this.noSpecRadio.setEnabled(!hasVariables);
        this.closedFormulaRadio.setEnabled(hasVariables);
        this.initNextFairnessRadio.setEnabled(hasVariables);

        // the input fields are enabled only if there are variables
        this.initFormulaSource.getControl().setEnabled(hasVariables);
        this.nextFormulaSource.getControl().setEnabled(hasVariables);
        this.specSource.getControl().setEnabled(hasVariables);
    }

    /**
     * This method sets the selection on the 
     * @param selectedFormula
     */
    private void setSpecSelection(int specType)
    {
        switch (specType) {
        case MODEL_BEHAVIOR_TYPE_NO_SPEC:
            this.noSpecRadio.setSelection(true);
            this.initNextFairnessRadio.setSelection(false);
            this.closedFormulaRadio.setSelection(false);
            break;
        case MODEL_BEHAVIOR_TYPE_SPEC_CLOSED:
            this.noSpecRadio.setSelection(false);
            this.initNextFairnessRadio.setSelection(false);
            this.closedFormulaRadio.setSelection(true);
            break;
        case MODEL_BEHAVIOR_TYPE_SPEC_INIT_NEXT:
            this.noSpecRadio.setSelection(false);
            this.initNextFairnessRadio.setSelection(true);
            this.closedFormulaRadio.setSelection(false);
            break;
        default:
            throw new IllegalArgumentException("Wrong spec type, this is a bug");
        }

    }

    /**
     * Save data back to model
     */
	public void commit(boolean onSave)
    {
		final String comments = FormHelper.trimTrailingSpaces(commentsSource.getDocument().get());
		getModel().setAttribute(MODEL_COMMENTS, comments);
        
        // TLCUIActivator.getDefault().logDebug("Main page commit");
        // closed formula
        String closedFormula = FormHelper.trimTrailingSpaces(this.specSource.getDocument().get());
        getModel().setAttribute(MODEL_BEHAVIOR_CLOSED_SPECIFICATION, closedFormula);

        // init formula
        String initFormula = FormHelper.trimTrailingSpaces(this.initFormulaSource.getDocument().get());
        getModel().setAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT, initFormula);

        // next formula
        String nextFormula = FormHelper.trimTrailingSpaces(this.nextFormulaSource.getDocument().get());
        getModel().setAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT, nextFormula);

        // fairness formula
        // String fairnessFormula =
        // FormHelper.trimTrailingSpaces(this.fairnessFormulaSource.getDocument().get());
        // getModel().setAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_FAIRNESS,
        // fairnessFormula);

        // mode
        int specType;
        if (this.closedFormulaRadio.getSelection())
        {
            specType = MODEL_BEHAVIOR_TYPE_SPEC_CLOSED;
        } else if (this.initNextFairnessRadio.getSelection())
        {
            specType = MODEL_BEHAVIOR_TYPE_SPEC_INIT_NEXT;
        } else if (this.noSpecRadio.getSelection())
        {
            specType = MODEL_BEHAVIOR_TYPE_NO_SPEC;
        } else
        {
            specType = MODEL_BEHAVIOR_TYPE_DEFAULT;
        }

        getModel().setAttribute(MODEL_BEHAVIOR_SPEC_TYPE, specType);

        // number of workers
        getModel().setAttribute(LAUNCH_NUMBER_OF_WORKERS, workers.getSelection());

        int maxHeapSizeValue = TLCUIActivator.getDefault().getPreferenceStore().getInt(
                ITLCPreferenceConstants.I_TLC_MAXIMUM_HEAP_SIZE_DEFAULT);
        maxHeapSizeValue = maxHeapSize.getSelection();
        getModel().setAttribute(LAUNCH_MAX_HEAP_SIZE, maxHeapSizeValue);

        // recover from deadlock
        boolean recover = this.checkpointButton.getSelection();
        getModel().setAttribute(LAUNCH_RECOVER, recover);

        // check deadlock
        boolean checkDeadlock = this.checkDeadlockButton.getSelection();
        getModel().setAttribute(MODEL_CORRECTNESS_CHECK_DEADLOCK, checkDeadlock);

        // run in distributed mode
        String distributed = this.distributedCombo.getItem(this.distributedCombo.getSelectionIndex());
        getModel().setAttribute(LAUNCH_DISTRIBUTED, distributed);
        
        String resultMailAddress = this.resultMailAddressText.getText();
        getModel().setAttribute(LAUNCH_DISTRIBUTED_RESULT_MAIL_ADDRESS, resultMailAddress);
        
        // distributed FPSet count
        getModel().setAttribute(LAUNCH_DISTRIBUTED_FPSET_COUNT, distributedFPSetCountSpinner.getSelection());

        // distributed FPSet count
        getModel().setAttribute(LAUNCH_DISTRIBUTED_NODES_COUNT, distributedNodesCountSpinner.getSelection());
        
        // network interface
        final String iface = this.networkInterfaceCombo.getItem(this.networkInterfaceCombo.getSelectionIndex());
        getModel().setAttribute(LAUNCH_DISTRIBUTED_INTERFACE, iface);

        // invariants
        List<String> serializedList = FormHelper.getSerializedInput(invariantsTable);
        getModel().setAttribute(MODEL_CORRECTNESS_INVARIANTS, serializedList);

        // properties
        serializedList = FormHelper.getSerializedInput(propertiesTable);
        getModel().setAttribute(MODEL_CORRECTNESS_PROPERTIES, serializedList);

        // constants
        List<String> constants = FormHelper.getSerializedInput(constantTable);
        getModel().setAttribute(MODEL_PARAMETER_CONSTANTS, constants);

        // variables
        String variables = ModelHelper.createVariableList(SemanticHelper.getRootModuleNode());
        getModel().setAttribute(MODEL_BEHAVIOR_VARS, variables);

        super.commit(onSave);
    }

    /**
     * Checks if checkpoint information changed 
     */
    private void updateCheckpoints()
    {
        IResource[] checkpoints = null;
        try
        {
            // checkpoint id
            checkpoints = getModel().getCheckpoints(false);
        } catch (CoreException e)
        {
            TLCUIActivator.getDefault().logError("Error checking chekpoint data", e);
        }

        if (checkpoints != null && checkpoints.length > 0)
        {
            this.checkpointIdText.setText(checkpoints[0].getName());
        } else
        {
            this.checkpointIdText.setText(EMPTY_STRING);
        }

        if ((checkpoints == null) || (checkpoints.length == 0))
        {
            checkpointSizeText.setVisible(false);
            chkpointSizeLabel.setVisible(false);
            chkptDeleteButton.setVisible(false);
        } else
        {
            checkpointSizeText.setText(String.valueOf(ResourceHelper.getSizeOfJavaFileResource(checkpoints[0]) / 1000));
            checkpointSizeText.setVisible(true);
            chkpointSizeLabel.setVisible(true);
            chkptDeleteButton.setVisible(true);
        }
    }

    /**
     * Creates the UI
     * This method is called to create the widgets and arrange them on the page
     * 
     * Its helpful to know what the standard SWT widgets look like.
     * Pictures can be found at http://www.eclipse.org/swt/widgets/
     * 
     * Layouts are used throughout this method.
     * A good explanation of layouts is given in the article
     * http://www.eclipse.org/articles/article.php?file=Article-Understanding-Layouts/index.html
     */
    protected void createBodyContent(IManagedForm managedForm)
    {
        DataBindingManager dm = getDataBindingManager();
        int sectionFlags = Section.TITLE_BAR | Section.DESCRIPTION | Section.TREE_NODE;
        FormToolkit toolkit = managedForm.getToolkit();
        Composite body = managedForm.getForm().getBody();

        GridData gd;
        TableWrapData twd;

        Section section;
        GridLayout layout;

        /*
         * Comments/notes section spanning two columns
         */
        Composite top = toolkit.createComposite(body);
        top.setLayout(FormHelper.createFormTableWrapLayout(false, 2));
        twd = new TableWrapData(TableWrapData.FILL_GRAB);
        twd.colspan = 2;
        top.setLayoutData(twd);
        
        section = FormHelper.createSectionComposite(top, "Model description", "", toolkit, sectionFlags
                | Section.EXPANDED, getExpansionListener());
        
        final ValidateableSectionPart commentsPart = new ValidateableSectionPart(section, this, SEC_COMMENTS);
        managedForm.addPart(commentsPart);
        final DirtyMarkingListener commentsListener = new DirtyMarkingListener(commentsPart, true);

        final Composite commentsArea = (Composite) section.getClient();
        commentsArea.setLayout(new TableWrapLayout());

        commentsSource = FormHelper.createFormsSourceViewer(toolkit, commentsArea, SWT.V_SCROLL | SWT.WRAP);
        // layout of the source viewer
        twd = new TableWrapData(TableWrapData.FILL_GRAB);
        twd.heightHint = 60;
        commentsSource.addTextListener(commentsListener);
        commentsSource.getTextWidget().setLayoutData(twd);
        commentsSource.getTextWidget().addFocusListener(focusListener);
        toolkit.paintBordersFor(commentsArea);

        dm.bindAttribute(MODEL_COMMENTS, commentsSource, commentsPart);

        /*
         * Because the two Composite objects `left' and `right' are added to the
         * object `body' in this order, `left' is displayed to the left of `right'.
         */
        // left
        Composite left = toolkit.createComposite(body);
        twd = new TableWrapData(TableWrapData.FILL_GRAB);
        twd.grabHorizontal = true;
        left.setLayout(new GridLayout(1, false));
        left.setLayoutData(twd);

        // right
        Composite right = toolkit.createComposite(body);
        twd = new TableWrapData(TableWrapData.FILL_GRAB);
        twd.grabHorizontal = true;
        right.setLayoutData(twd);
        right.setLayout(new GridLayout(1, false));

        // ------------------------------------------
        // what is the spec
        section = FormHelper.createSectionComposite(left, "What is the behavior spec?", "", toolkit, sectionFlags
                | Section.EXPANDED, getExpansionListener());
        // only grab horizontal space
        gd = new GridData(GridData.FILL_HORIZONTAL);
        section.setLayoutData(gd);

        Composite behaviorArea = (Composite) section.getClient();
        layout = new GridLayout();
        layout.numColumns = 2;
        behaviorArea.setLayout(layout);

        ValidateableSectionPart behaviorPart = new ValidateableSectionPart(section, this, SEC_WHAT_IS_THE_SPEC);
        managedForm.addPart(behaviorPart);
        DirtyMarkingListener whatIsTheSpecListener = new DirtyMarkingListener(behaviorPart, true);
        // split formula option
        initNextFairnessRadio = toolkit.createButton(behaviorArea, "Initial predicate and next-state relation",
                SWT.RADIO);
        initNextFairnessRadio.addFocusListener(focusListener);

        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        initNextFairnessRadio.setLayoutData(gd);
        initNextFairnessRadio.addSelectionListener(whatIsTheSpecListener);
        initNextFairnessRadio.addFocusListener(focusListener);

        // init
        toolkit.createLabel(behaviorArea, "Init:");
        initFormulaSource = FormHelper.createFormsSourceViewer(toolkit, behaviorArea, SWT.NONE | SWT.SINGLE);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 18;
        initFormulaSource.getTextWidget().setLayoutData(gd);
        initFormulaSource.getTextWidget().addModifyListener(whatIsTheSpecListener);
        initFormulaSource.getTextWidget().addModifyListener(widgetActivatingListener);
        initFormulaSource.getTextWidget().addFocusListener(focusListener);
        dm.bindAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_INIT, initFormulaSource, behaviorPart);

        // next
        toolkit.createLabel(behaviorArea, "Next:");
        nextFormulaSource = FormHelper.createFormsSourceViewer(toolkit, behaviorArea, SWT.NONE | SWT.SINGLE);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 18;
        nextFormulaSource.getTextWidget().setLayoutData(gd);
        nextFormulaSource.getTextWidget().addModifyListener(whatIsTheSpecListener);
        nextFormulaSource.getTextWidget().addModifyListener(widgetActivatingListener);
        nextFormulaSource.getTextWidget().addFocusListener(focusListener);
        dm.bindAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_NEXT, nextFormulaSource, behaviorPart);

        // fairness
        // toolkit.createLabel(behaviorArea, "Fairness:");
        // fairnessFormulaSource = FormHelper.createSourceViewer(toolkit,
        // behaviorArea, SWT.NONE | SWT.SINGLE);
        // gd = new GridData(GridData.FILL_HORIZONTAL);
        // gd.heightHint = 18;
        // fairnessFormulaSource.getTextWidget().setLayoutData(gd);
        // fairnessFormulaSource.getTextWidget().addModifyListener(whatIsTheSpecListener);
        // fairnessFormulaSource.getTextWidget().addModifyListener(widgetActivatingListener);
        // dm.bindAttribute(MODEL_BEHAVIOR_SEPARATE_SPECIFICATION_FAIRNESS,
        // fairnessFormulaSource, behaviorPart);

        // closed formula option
        closedFormulaRadio = toolkit.createButton(behaviorArea, "Temporal formula", SWT.RADIO);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        closedFormulaRadio.setLayoutData(gd);
        closedFormulaRadio.addSelectionListener(whatIsTheSpecListener);

        // spec
        Label specLabel = toolkit.createLabel(behaviorArea, "");
        // changed from "Spec:" 10 Sep 09
        gd = new GridData();
        gd.verticalAlignment = SWT.TOP;
        specLabel.setLayoutData(gd);
        specSource = FormHelper.createFormsSourceViewer(toolkit, behaviorArea, SWT.V_SCROLL);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 55;
        specSource.getTextWidget().setLayoutData(gd);
        specSource.getTextWidget().addModifyListener(whatIsTheSpecListener);
        specSource.getTextWidget().addModifyListener(widgetActivatingListener);
        dm.bindAttribute(MODEL_BEHAVIOR_CLOSED_SPECIFICATION, specSource, behaviorPart);

        noSpecRadio = toolkit.createButton(behaviorArea, "No Behavior Spec", SWT.RADIO);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        noSpecRadio.setLayoutData(gd);
        noSpecRadio.addSelectionListener(whatIsTheSpecListener);
        dm.bindAttribute(MODEL_BEHAVIOR_NO_SPEC, noSpecRadio, behaviorPart);

        // ------------------------------------------
        // what to check
        section = FormHelper.createSectionComposite(left, "What to check?", "", toolkit, sectionFlags
                | Section.EXPANDED, getExpansionListener());
        // only grab horizontal space
        gd = new GridData(GridData.FILL_HORIZONTAL);
        section.setLayoutData(gd);

        Composite toBeCheckedArea = (Composite) section.getClient();
        layout = new GridLayout();
        layout.numColumns = 1;
        toBeCheckedArea.setLayout(layout);

        checkDeadlockButton = toolkit.createButton(toBeCheckedArea, "Deadlock", SWT.CHECK);

        ValidateableSectionPart toBeCheckedPart = new ValidateableSectionPart(section, this, SEC_WHAT_TO_CHECK);
        managedForm.addPart(toBeCheckedPart);
        DirtyMarkingListener whatToCheckListener = new DirtyMarkingListener(toBeCheckedPart, true);
        checkDeadlockButton.addSelectionListener(whatToCheckListener);

        // Invariants
        ValidateableTableSectionPart invariantsPart = new ValidateableTableSectionPart(toBeCheckedArea, "Invariants",
                "Formulas true in every reachable state.", toolkit, sectionFlags, this, SEC_WHAT_TO_CHECK_INVARIANTS);
        managedForm.addPart(invariantsPart);
        invariantsTable = invariantsPart.getTableViewer();
        dm.bindAttribute(MODEL_CORRECTNESS_INVARIANTS, invariantsTable, invariantsPart);
        /*TableViewer tableViewer = invariantsPart.getTableViewer();
        Vector input = ((Vector) tableViewer.getInput());
        input.add(formula);
        tableViewer.setInput(input);
        if (tableViewer instanceof CheckboxTableViewer)
        {
            ((CheckboxTableViewer) tableViewer).setChecked(formula, true);
        }
        this.doMakeDirty();*/
        
        // Properties

        // The following code added by LL on 29 May 2010 to expand the Property section
        // and reset the MODEL_PROPERTIES_EXPAND property to "" if that property has 
        // been set to a non-"" value.
        int propFlags = sectionFlags;
        try
        {
            if (!((String) getModel().getAttribute(MODEL_PROPERTIES_EXPAND, "")).equals("")) {
               propFlags = propFlags | Section.EXPANDED;
               getModel().setAttribute(MODEL_PROPERTIES_EXPAND, "");
            }
        } catch (CoreException e)
        {
            // I don't know why such an exception might occur, but there's no
            // great harm if it does. LL
        	e.printStackTrace();
        }
        ValidateableTableSectionPart propertiesPart = new ValidateableTableSectionPart(toBeCheckedArea, "Properties",
                "Temporal formulas true for every possible behavior.", toolkit, propFlags, this,
                SEC_WHAT_TO_CHECK_PROPERTIES);
        managedForm.addPart(propertiesPart);
        propertiesTable = propertiesPart.getTableViewer();
        dm.bindAttribute(MODEL_CORRECTNESS_PROPERTIES, propertiesTable, propertiesPart);

        // ------------------------------------------
        // what is the model

        // Constants
        ValidateableConstantSectionPart constantsPart = new ValidateableConstantSectionPart(right,
                "What is the model?", "Specify the values of declared constants.", toolkit, sectionFlags
                        | Section.EXPANDED, this, SEC_WHAT_IS_THE_MODEL);
        managedForm.addPart(constantsPart);
        constantTable = constantsPart.getTableViewer();
        dm.bindAttribute(MODEL_PARAMETER_CONSTANTS, constantTable, constantsPart);
        Composite parametersArea = (Composite) constantsPart.getSection().getClient();
        HyperlinkGroup group = new HyperlinkGroup(parametersArea.getDisplay());

        // TESTING XXXXXX
        // managedForm.removePart(constantsPart);
        // Control saved = right.getChildren()[0] ;
        // constantTable.getTable().setSize(1000, 1000);
        // constantTable.getTable().setVisible(false);
        //        
        // System.out.println("GetSize returns " +
        // constantTable.getTable().getSize().x);
        // right.getChildren()[0].setVisible(false);
        // parametersArea.setVisible(false);

        // create a composite to put the text into
        Composite linksPanelToAdvancedPage = toolkit.createComposite(parametersArea);
        gd = new GridData();
        gd.horizontalSpan = 2;

        linksPanelToAdvancedPage.setLayoutData(gd);
        linksPanelToAdvancedPage.setLayout(new FillLayout(SWT.VERTICAL));

        // first line with hyperlinks
        Composite elementLine = toolkit.createComposite(linksPanelToAdvancedPage);
        elementLine.setLayout(new FillLayout(SWT.HORIZONTAL));

        // the text
        toolkit.createLabel(elementLine, "Advanced parts of the model:");

        // the hyperlinks
        Hyperlink hyper;

        hyper = toolkit.createHyperlink(elementLine, "Additional definitions,", SWT.NONE);
        hyper.setHref(SEC_NEW_DEFINITION);
        hyper.addHyperlinkListener(sectionExpandingAdapter);

        hyper = toolkit.createHyperlink(elementLine, "Definition override,", SWT.NONE);
        hyper.setHref(SEC_DEFINITION_OVERRIDE);
        hyper.addHyperlinkListener(sectionExpandingAdapter);

        // second line with hyperlinks
        Composite elementLine2 = toolkit.createComposite(linksPanelToAdvancedPage);
        elementLine2.setLayout(new FillLayout(SWT.HORIZONTAL));

        hyper = toolkit.createHyperlink(elementLine2, "State constraints,", SWT.NONE);
        hyper.setHref(SEC_STATE_CONSTRAINT);
        hyper.addHyperlinkListener(sectionExpandingAdapter);

        hyper = toolkit.createHyperlink(elementLine2, "Action constraints,", SWT.NONE);
        hyper.setHref(SEC_ACTION_CONSTRAINT);
        hyper.addHyperlinkListener(sectionExpandingAdapter);

        hyper = toolkit.createHyperlink(elementLine2, "Additional model values.", SWT.NONE);
        hyper.setHref(SEC_MODEL_VALUES);
        hyper.addHyperlinkListener(sectionExpandingAdapter);

        // ------------------------------------------
        // run tab
        section = FormHelper.createSectionComposite(right, "How to run?", "TLC Parameters", toolkit, sectionFlags,
                getExpansionListener());
        gd = new GridData(GridData.FILL_HORIZONTAL);
        section.setLayoutData(gd);

        final Composite howToRunArea = (Composite) section.getClient();
        group = new HyperlinkGroup(howToRunArea.getDisplay());
        layout = new GridLayout(2, true);
        howToRunArea.setLayout(layout);

        ValidateableSectionPart howToRunPart = new ValidateableSectionPart(section, this, SEC_HOW_TO_RUN);
        managedForm.addPart(howToRunPart);

        DirtyMarkingListener howToRunListener = new DirtyMarkingListener(howToRunPart, true);

        /*
         * Workers Spinner
         */
        
        // label workers
        toolkit.createLabel(howToRunArea, "Number of worker threads:");

        // field workers
        workers = new Spinner(howToRunArea, SWT.NONE);
        workers.addSelectionListener(howToRunListener);
        workers.addFocusListener(focusListener);
        gd = new GridData();
        gd.horizontalIndent = 10;
        gd.widthHint = 40;
        workers.setLayoutData(gd);
        
        workers.setMinimum(1);
        workers.setPageIncrement(1);
        workers.setToolTipText("Determines how many threads will be spawned working on the next state relation.");
        workers.setSelection(IConfigurationDefaults.LAUNCH_NUMBER_OF_WORKERS_DEFAULT);

        dm.bindAttribute(LAUNCH_NUMBER_OF_WORKERS, workers, howToRunPart);
        
        /*
         * MapHeap Scale
         */
        
        // max heap size label
        toolkit.createLabel(howToRunArea, "Fraction of physical memory allocated to TLC:");

		// Create a composite inside the right "cell" of the "how to run"
		// section grid layout to fit the scale and the maxHeapSizeFraction
		// label into a single row.
        final Composite maxHeapScale = new Composite(howToRunArea, SWT.NONE);
        layout = new GridLayout(2, false);
        maxHeapScale.setLayout(layout);

        // field max heap size
        int defaultMaxHeapSize = TLCUIActivator.getDefault().getPreferenceStore().getInt(
                ITLCPreferenceConstants.I_TLC_MAXIMUM_HEAP_SIZE_DEFAULT);
        maxHeapSize = new Scale(maxHeapScale, SWT.NONE);
        maxHeapSize.addSelectionListener(howToRunListener);
        maxHeapSize.addFocusListener(focusListener);
        gd = new GridData();
        gd.horizontalIndent = 0;
        gd.widthHint = 250;
        maxHeapSize.setLayoutData(gd);
        maxHeapSize.setMaximum(99);
        maxHeapSize.setMinimum(1);
        maxHeapSize.setPageIncrement(5);
        maxHeapSize.setSelection(defaultMaxHeapSize);
        maxHeapSize.setToolTipText("Specifies the heap size of the Java VM that runs TLC.");

        dm.bindAttribute(LAUNCH_MAX_HEAP_SIZE, maxHeapSize, howToRunPart);
        
        // label next to the scale showing the current fraction selected
		final TLCRuntime instance = TLCRuntime.getInstance();
		long memory = instance.getAbsolutePhysicalSystemMemory(defaultMaxHeapSize / 100d);
		final Label maxHeapSizeFraction = toolkit.createLabel(maxHeapScale,
				defaultMaxHeapSize + "%" + " (" + memory + " mb)");
        maxHeapSize.addPaintListener(new PaintListener() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.PaintListener#paintControl(org.eclipse.swt.events.PaintEvent)
			 */
			public void paintControl(PaintEvent e) {
				// update the label
				int value = ((Scale) e.getSource()).getSelection();
				final TLCRuntime instance = TLCRuntime.getInstance();
				long memory = instance.getAbsolutePhysicalSystemMemory(value / 100d);
				maxHeapSizeFraction.setText(value + "%" + " (" + memory + " mb)");
			}
		});

        
//        // label workers
//        toolkit.createLabel(howToRunArea, "Number of worker threads:");
//
//        // field workers
//        workers = toolkit.createText(howToRunArea, "1");
//        workers.addModifyListener(howToRunListener);
//        workers.addFocusListener(focusListener);
//        gd = new GridData();
//        gd.horizontalIndent = 10;
//        gd.widthHint = 40;
//        workers.setLayoutData(gd);
//
//        dm.bindAttribute(LAUNCH_NUMBER_OF_WORKERS, workers, howToRunPart);
        
        /*
         * run from the checkpoint.  Checkpoint help button added by LL on 17 Jan 2013
         */
        Composite ckptComp = new Composite(howToRunArea, SWT.NONE) ;
        layout = new GridLayout(2, true);
        ckptComp.setLayout(layout);
        
        gd = new GridData();
        gd.horizontalSpan = 2;
        gd.verticalIndent = 20;
        ckptComp.setLayoutData(gd);

        checkpointButton = toolkit.createButton(ckptComp, "Recover from checkpoint", SWT.CHECK);
        checkpointButton.addSelectionListener(howToRunListener);
        checkpointButton.addFocusListener(focusListener);
        HelpButton.helpButton(ckptComp, "model/overview-page.html#checkpoint") ;

        toolkit.createLabel(howToRunArea, "Checkpoint ID:");

        checkpointIdText = toolkit.createText(howToRunArea, "");
        checkpointIdText.setEditable(false);
        gd = new GridData();
        gd.horizontalIndent = 10;
        gd.widthHint = 100;
        checkpointIdText.setLayoutData(gd);
        dm.bindAttribute(LAUNCH_RECOVER, checkpointButton, howToRunPart);

        chkpointSizeLabel = toolkit.createLabel(howToRunArea, "Checkpoint size (kbytes):");
        checkpointSizeText = toolkit.createText(howToRunArea, "");
        gd = new GridData();
        gd.horizontalIndent = 10;
        gd.widthHint = 100;
        checkpointSizeText.setLayoutData(gd);
        chkptDeleteButton = toolkit.createButton(howToRunArea, "Delete Checkpoint", SWT.PUSH);
        chkptDeleteButton.addSelectionListener(new SelectionListener() {

            /* (non-Javadoc)
             * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetSelected(SelectionEvent e)
            {
                final IResource[] checkpoints;
                try
                {
                    checkpoints = getModel().getCheckpoints(false);

                    if ((checkpoints != null) && checkpoints.length > 0)
                    {
                        ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {

                            public void run(IProgressMonitor monitor) throws CoreException
                            {
                                checkpoints[0].delete(true, new SubProgressMonitor(monitor, 1));

                            }
                        }, null);
                    }
                } catch (CoreException e1)
                {
                    return;
                }

            }

            /* (non-Javadoc)
             * @see org.eclipse.swt.events.SelectionListener#widgetDefaultSelected(org.eclipse.swt.events.SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e)
            {
            }
        });
        chkptDeleteButton.addFocusListener(focusListener);
        
        /*
         * Distribution.  Help button added by LL on 17 Jan 2013
         */
        Composite distComp = new Composite(howToRunArea, SWT.NONE) ;
        layout = new GridLayout(3, true);
        distComp.setLayout(layout);
        
        gd = new GridData();
        gd.horizontalSpan = 2;
        distComp.setLayoutData(gd);
        
        toolkit.createLabel(distComp, "Run in distributed mode");
        distributedCombo = new Combo(distComp, SWT.READ_ONLY);
        distributedCombo.setItems(new String[] {"off", "ad hoc", "aws-ec2", "Azure"});
        distributedCombo.select(0);
        HelpButton.helpButton(distComp, "model/distributed-mode.html") ;
        distributedCombo.addSelectionListener(howToRunListener);
		distributedCombo.setToolTipText("If other than 'off' selected, state computation will be performed by (remote) workers.");
		distributedCombo.addFocusListener(focusListener);
		
		distributedOptions = new Composite(howToRunArea, SWT.NONE);
		final StackLayout stackLayout = new StackLayout();
		distributedOptions.setLayout(stackLayout);
		
        gd = new GridData();
        gd.horizontalSpan = 2;
        distributedOptions.setLayoutData(gd);
        
		// No distribution has no options
		final Composite offComposite = new Composite(distributedOptions, SWT.NONE);
		distributedOptions.setData("off", offComposite);
		stackLayout.topControl = offComposite;
		
		/*
		 * Composite wrapping number of distributed FPSet and iface when ad hoc selected
		 */
        final Composite builtInOptions = new Composite(distributedOptions, SWT.NONE);
        layout = new GridLayout(2, true);
        builtInOptions.setLayout(layout);
        gd = new GridData();
        gd.horizontalSpan = 2;
        builtInOptions.setLayoutData(gd);
		distributedOptions.setData("ad hoc", builtInOptions);
		
		/*
		 * Server interface/hostname (This text shows the hostname detected by the Toolbox under which TLCServer will listen
		 */
		// composite
        final Composite networkInterface = new Composite(builtInOptions, SWT.NONE) ;
        layout = new GridLayout(2, true);
        networkInterface.setLayout(layout);
        gd = new GridData();
        gd.horizontalSpan = 2;
        networkInterface.setLayoutData(gd);
		
        // label
        toolkit.createLabel(networkInterface, "Master's network address:");

        // field
        networkInterfaceCombo = new Combo(networkInterface, SWT.NONE);
        networkInterfaceCombo.addSelectionListener(howToRunListener);
        networkInterfaceCombo.addFocusListener(focusListener);
        gd = new GridData();
        gd.horizontalIndent = 10;
        networkInterfaceCombo.setLayoutData(gd);
        
        networkInterfaceCombo.setToolTipText("IP address to which workers (and distributed fingerprint sets) will connect.");
        networkInterfaceCombo.addSelectionListener(howToRunListener);
        networkInterfaceCombo.addFocusListener(focusListener);
        try {
        	final Comparator<InetAddress> comparator = new Comparator<InetAddress>() {
        		// Try to "guess" the best possible match.
        		public int compare(InetAddress o1, InetAddress o2) {
        			// IPv4 < IPv6 (v6 is less common even today)
        			if (o1 instanceof Inet4Address && o2 instanceof Inet6Address) {
        				return -1;
        			} else if (o1 instanceof Inet6Address && o2 instanceof Inet4Address) {
        				return 1;
        			}
        			
        			// anything < LoopBack (loopback only useful if master and worker are on the same host)
        			if (!o1.isLoopbackAddress() && o2.isLoopbackAddress()) {
        				return -1;
        			} else if (o1.isLoopbackAddress() && !o2.isLoopbackAddress()) {
        				return 1;
        			}
        			
        			// Public Addresses < Non-private RFC 1918 (I guess this is questionable)
        			if (!o1.isSiteLocalAddress() && o2.isSiteLocalAddress()) {
        				return -1;
        			} else if (o1.isSiteLocalAddress() && !o2.isSiteLocalAddress()) {
        				return 1;
        			}
        			
        			return 0;
        		}
        	};

        	// Get all IP addresses of the host and sort them according to the Comparator.
        	final List<InetAddress> addresses = new ArrayList<InetAddress>(); 
			final Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
			while (nets.hasMoreElements()) {
				final NetworkInterface iface = (NetworkInterface) nets.nextElement();
				final Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
				while (inetAddresses.hasMoreElements()) {
					final InetAddress addr = inetAddresses.nextElement();
					// Cannot connect to a multicast address
					if (addr.isMulticastAddress()) {
						continue;
					}
					addresses.add(addr);
				}
			}
			
			// Add the sorted IP addresses and select the first one which -
			// according to the comparator - is assumed to be the best match.
			Collections.sort(addresses, comparator);
			for (InetAddress inetAddress : addresses) {
				networkInterfaceCombo.add(inetAddress.getHostAddress());
			}
			networkInterfaceCombo.select(0);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}

        dm.bindAttribute(LAUNCH_DISTRIBUTED_INTERFACE, networkInterfaceCombo, howToRunPart);

		/*
		 * Distributed FPSet count
		 */

		// composite
        final Composite distributedFPSetCount = new Composite(builtInOptions, SWT.NONE);
        layout = new GridLayout(2, false);
        distributedFPSetCount.setLayout(layout);
        gd = new GridData();
        gd.horizontalSpan = 2;
        distributedFPSetCount.setLayoutData(gd);
		
        // label
        toolkit.createLabel(distributedFPSetCount, "Number of distributed fingerprint sets (zero for single built-in set):");

        // field
        distributedFPSetCountSpinner = new Spinner(distributedFPSetCount, SWT.NONE);
        distributedFPSetCountSpinner.addSelectionListener(howToRunListener);
        distributedFPSetCountSpinner.addFocusListener(focusListener);
        gd = new GridData();
        gd.grabExcessHorizontalSpace = true;
        gd.horizontalIndent = 10;
        gd.widthHint = 40;
        distributedFPSetCountSpinner.setLayoutData(gd);
        
        distributedFPSetCountSpinner.setMinimum(0);
        distributedFPSetCountSpinner.setMaximum(64); // Haven't really tested this many distributed fpsets
        distributedFPSetCountSpinner.setPageIncrement(1);
        distributedFPSetCountSpinner.setToolTipText("Determines how many distributed FPSets will be expected by the TLCServer process");
        distributedFPSetCountSpinner.setSelection(IConfigurationDefaults.LAUNCH_DISTRIBUTED_FPSET_COUNT_DEFAULT);

        dm.bindAttribute(LAUNCH_DISTRIBUTED_FPSET_COUNT, distributedFPSetCountSpinner, howToRunPart);
        
		/*
		 * Composite wrapping all widgets related to jclouds
		 */
        final Composite jcloudsOptions = new Composite(distributedOptions, SWT.NONE);
        layout = new GridLayout(2, true);
        jcloudsOptions.setLayout(layout);
        gd = new GridData();
        gd.horizontalSpan = 2;
        jcloudsOptions.setLayoutData(gd);

 		/*
 		 * Distributed nodes count
 		 */

 		// composite
         final Composite distributedNodesCount = new Composite(jcloudsOptions, SWT.NONE);
         layout = new GridLayout(2, false);
         distributedNodesCount.setLayout(layout);
         gd = new GridData();
         gd.horizontalSpan = 2;
         distributedNodesCount.setLayoutData(gd);
 		
         // label
         toolkit.createLabel(distributedNodesCount, "Number of compute nodes to use:");

         // field
         distributedNodesCountSpinner = new Spinner(distributedNodesCount, SWT.NONE);
         distributedNodesCountSpinner.addSelectionListener(howToRunListener);
         distributedNodesCountSpinner.addFocusListener(focusListener);
         gd = new GridData();
         gd.grabExcessHorizontalSpace = true;
         gd.horizontalIndent = 10;
         gd.widthHint = 40;
         distributedNodesCountSpinner.setLayoutData(gd);
         
         distributedNodesCountSpinner.setMinimum(1);
         distributedNodesCountSpinner.setMaximum(64); // Haven't really tested this many distributed fpsets
         distributedNodesCountSpinner.setPageIncrement(1);
		distributedNodesCountSpinner.setToolTipText(
				"Determines how many compute nodes/VMs will be launched. More VMs means faster results and higher costs.");
         distributedNodesCountSpinner.setSelection(IConfigurationDefaults.LAUNCH_DISTRIBUTED_NODES_COUNT_DEFAULT);

         dm.bindAttribute(LAUNCH_DISTRIBUTED_NODES_COUNT, distributedNodesCountSpinner, howToRunPart);
		
		/*
		 * Result mail address input
		 */
        final Composite resultAddress = new Composite(jcloudsOptions, SWT.NONE) ;
        layout = new GridLayout(2, true);
        resultAddress.setLayout(layout);
        
        gd = new GridData();
        gd.horizontalSpan = 2;
        resultAddress.setLayoutData(gd);
        
		toolkit.createLabel(resultAddress, "Result mailto address:");
		resultMailAddressText = toolkit.createText(resultAddress, "", SWT.BORDER);
		resultMailAddressText.setMessage("my-name@my-domain.org"); // hint
		resultMailAddressText.addKeyListener(new KeyAdapter() {
			
			private final ModelEditor modelEditor = (ModelEditor) getEditor();

			@Override
			public void keyPressed(KeyEvent e) {
				super.keyPressed(e);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				super.keyReleased(e);
				try {
					String text = resultMailAddressText.getText();
					new javax.mail.internet.InternetAddress(text, true);
				} catch (AddressException exp) {
					modelEditor.addErrorMessage("emailAddressInvalid",
							"Invalid email address", getId(),
							IMessageProvider.ERROR, resultMailAddressText);
					return;
				}
				modelEditor.removeErrorMessage("emailAddressInvalid", resultMailAddressText);
			}
		});
        gd = new GridData();
        gd.grabExcessHorizontalSpace = true;
        gd.horizontalIndent = 10;
        gd.widthHint = 200;
        resultMailAddressText.setLayoutData(gd);
        resultMailAddressText.addModifyListener(howToRunListener);
        dm.bindAttribute(LAUNCH_DISTRIBUTED_RESULT_MAIL_ADDRESS, resultMailAddressText, howToRunPart);
		
		distributedOptions.setData("jclouds", jcloudsOptions);

        distributedCombo.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				int selectionIndex = distributedCombo.getSelectionIndex();
				String item = distributedCombo.getItem(selectionIndex);
				if (item.equalsIgnoreCase("aws-ec2") || item.equalsIgnoreCase("Azure")) {
					MainModelPage.this.putOnTopOfStack("jclouds", false, false);
				} else if(item.equalsIgnoreCase("ad hoc")) {
					MainModelPage.this.putOnTopOfStack("ad hoc", false, true);
				} else {
					MainModelPage.this.putOnTopOfStack("off", true, true);
				}
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
        });

        /*
         * run link
         */
        runLink = toolkit.createImageHyperlink(howToRunArea, SWT.NONE);
        runLink.setImage(createRegisteredImage("icons/full/lrun_obj.gif"));
        runLink.addHyperlinkListener(new HyperlinkAdapter() {
            public void linkActivated(HyperlinkEvent e)
            {
                doRun();
            }
        });
        runLink.setText("Run TLC");
        gd = new GridData();
        gd.horizontalSpan = 2;
        gd.widthHint = 200;
        gd.verticalIndent = 20;
        runLink.setLayoutData(gd);
        group.add(runLink);

        generateLink = toolkit.createImageHyperlink(howToRunArea, SWT.NONE);
        generateLink.setImage(createRegisteredImage("icons/full/debugt_obj.gif"));
        generateLink.addHyperlinkListener(new HyperlinkAdapter() {
            public void linkActivated(HyperlinkEvent e)
            {
                doGenerate();
            }
        });
        generateLink.setText("Validate model");
        gd = new GridData();
        gd.horizontalSpan = 2;
        gd.widthHint = 200;
        generateLink.setLayoutData(gd);
        group.add(generateLink);

        // add listeners propagating the changes of the elements to the changes
        // of the
        // parts to the list to be activated after the values has been loaded
        dirtyPartListeners.add(commentsListener);
        dirtyPartListeners.add(whatIsTheSpecListener);
        dirtyPartListeners.add(whatToCheckListener);
        dirtyPartListeners.add(howToRunListener);
    }

    private void putOnTopOfStack(final String id, boolean enableWorker, boolean enableMaxHeap) {
		workers.setEnabled(enableWorker);
		maxHeapSize.setEnabled(enableMaxHeap);
		
		final Composite composite = (Composite) distributedOptions.getData(id);
		final StackLayout stackLayout = (StackLayout) distributedOptions.getLayout();
		stackLayout.topControl = composite;
		distributedOptions.layout();
    }

    /**
     * On a refresh, the checkpoint information is re-read 
     */
    public void refresh()
    {
        super.refresh();
        updateCheckpoints();
    }
    
    /**
     * Interpolates based on LinearInterpolation
     */
    private class Interpolator {

    	private final double[] yCoords, xCoords;

    	public Interpolator(double[] x, double[] y) {
    		this.xCoords = x;
    		this.yCoords = y;
    	}

		public double interpolate(double x) {
			for (int i = 1; i < xCoords.length; i++) {
				if (x < xCoords[i]) {
					return yCoords[i] - (yCoords[i] - yCoords[i - 1])
							* (xCoords[i] - x) / (xCoords[i] - xCoords[i - 1]);
				}
			}
			return 0d;
		}
    }
}
