package org.lamport.tla.toolbox.ui.perspective;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/**
 * Spec loaded perspective containing the editor and spec actions
 * @version $Id$
 * @author Simon Zambrovski
 */
public class SpecLoadedPerspective implements IPerspectiveFactory
{

    public final static String ID = "org.lamport.tla.toolbox.ui.perspective.specLoaded";

    public void createInitialLayout(IPageLayout layout)
    {
//    	layout.addStandaloneView(ID, true, IPageLayout.LEFT, 1.0f, layout.getEditorArea());
//    	layout.setEditorAreaVisible(false); //hide the editor in the perspective
    }
}
