package org.lamport.tla.toolbox.job;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.lamport.tla.toolbox.Activator;
import org.lamport.tla.toolbox.util.ResourceHelper;

/**
 * Creates a new TLA+ module
 * @author Simon Zambrovski
 * @version $Id$
 */
public class NewTLAModuleCreationOperation implements IWorkspaceRunnable
{
    private IPath modulePath;
    private boolean prefillForLinearisabilityEncoding;

    /**
     * @param name
     */
    public NewTLAModuleCreationOperation(IPath module)
    {
        this.modulePath = module;
        this.prefillForLinearisabilityEncoding = false;
    }
    
    public NewTLAModuleCreationOperation(IPath module, boolean prefillForLinearisabilityEncoding)
    {
        this.modulePath = module;
        this.prefillForLinearisabilityEncoding = prefillForLinearisabilityEncoding;
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.resources.IWorkspaceRunnable#run(org.eclipse.core.runtime.IProgressMonitor)
     */
    public void run(IProgressMonitor monitor) throws CoreException
    {
        String moduleFileName = modulePath.lastSegment();

        byte[] content = ResourceHelper.getEmptyModuleContent(moduleFileName).append(ResourceHelper.getModuleClosingTag()).toString().getBytes();
        
        byte[] prefill = getPrefillForLinearisabilityEncoding(moduleFileName);
		if(prefillForLinearisabilityEncoding) {
			content = prefill;
		}
		
        try
        {
        	// create parent folder unless pointing to root directory
        	if(modulePath.segmentCount() > 1) {
        		final IPath removeLastSegments = modulePath.removeLastSegments(1);
        		final File dir = new File(removeLastSegments.toOSString());
        		dir.mkdirs();
        	}
        	
            // create file
            File file = new File(modulePath.toOSString());
            if (file.createNewFile())
            {
                // successfully created
                // TODO 
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(content);
                fos.flush();
                fos.close();
            } else
            {
                throw new RuntimeException("Error creating a file");
            }
        } catch (IOException e)
        {
            throw new CoreException( new Status(Status.ERROR, Activator.PLUGIN_ID, "Error creating TLA+ file", e));
        }
        
    }
    
    private byte[] getPrefillForLinearisabilityEncoding(String moduleFileName) {
        StringBuffer prefill = ResourceHelper.getEmptyModuleContent(moduleFileName);
        prefill.append("EXTENDS Naturals" + System.lineSeparator() + System.lineSeparator());
        prefill.append("\\* Add to these default variables with your own variables" + System.lineSeparator());
        prefill.append("VARIABLE in, out" + System.lineSeparator());
        prefill.append("\\* Replace this default constant with your own constants (including all constants you want in the model)" + System.lineSeparator());
        prefill.append("CONSTANT exampleConstant" + System.lineSeparator() + System.lineSeparator());
        prefill.append("\\* Insert any custom definitions that you want to appear in all modules here (e.g. ABS0)" + System.lineSeparator());
		prefill.append(System.lineSeparator() + "\\* Always fill out the definitions below" + System.lineSeparator());
		prefill.append("ABS == TRUE" + System.lineSeparator());
		prefill.append("INV == TRUE" + System.lineSeparator());
		prefill.append("STATUS == TRUE" + System.lineSeparator());
		prefill.append("AOP == TRUE" + System.lineSeparator()  + System.lineSeparator());
		prefill.append(System.lineSeparator() + "\\* Fill out D if you are checking for non-interference" + System.lineSeparator());
		prefill.append("D == TRUE" + System.lineSeparator() + System.lineSeparator());
		prefill.append(System.lineSeparator() + "\\* Fill out the status transitions to automatically invoke potential-linearisation-point mode" + System.lineSeparator());
		prefill.append("IN_OUT == FALSE" + System.lineSeparator());
		prefill.append("IN_INOUT == FALSE" + System.lineSeparator());
		prefill.append("INOUT_OUT == FALSE" + System.lineSeparator());
		prefill.append("INOUT_IN == FALSE" + System.lineSeparator());
		prefill.append("STATUSHELPER == TRUE" + System.lineSeparator() + System.lineSeparator() + System.lineSeparator());
		prefill.append(ResourceHelper.getModuleClosingTag());
		return prefill.toString().getBytes();
    }

    /**
     * Job factory
     * @param module
     * @return
     */
    public static WorkspaceJob NEW_JOB(final IPath module)
    {
        return new WorkspaceJob("Creating TLA+ file")
        {
            NewTLAModuleCreationOperation op = new NewTLAModuleCreationOperation(module);

            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException
            {
                op.run(monitor);
                return Status.OK_STATUS;
            }
        };
        
    }
}
