/*
 * GAMA - V1.4  http://gama-platform.googlecode.com
 * 
 * (c) 2007-2011 UMI 209 UMMISCO IRD/UPMC & Partners (see below)
 * 
 * Developers :
 * 
 * - Alexis Drogoul, UMI 209 UMMISCO, IRD/UPMC (Kernel, Metamodel, GAML), 2007-2012
 * - Vo Duc An, UMI 209 UMMISCO, IRD/UPMC (SWT, multi-level architecture), 2008-2012
 * - Patrick Taillandier, UMR 6228 IDEES, CNRS/Univ. Rouen  (Batch, GeoTools & JTS), 2009-2012
 * - Beno�t Gaudou, UMR 5505 IRIT, CNRS/Univ. Toulouse 1 (Documentation, Tests), 2010-2012
 * - Phan Huy Cuong, DREAM team, Univ. Can Tho (XText-based GAML), 2012
 * - Pierrick Koch, UMI 209 UMMISCO, IRD/UPMC (XText-based GAML), 2010-2011
 * - Romain Lavaud, UMI 209 UMMISCO, IRD/UPMC (RCP environment), 2010
 * - Francois Sempe, UMI 209 UMMISCO, IRD/UPMC (EMF model, Batch), 2007-2009
 * - Edouard Amouroux, UMI 209 UMMISCO, IRD/UPMC (C++ initial porting), 2007-2008
 * - Chu Thanh Quang, UMI 209 UMMISCO, IRD/UPMC (OpenMap integration), 2007-2008
 */
package idees.gama.ui.commands;

import java.io.File;

import idees.gama.diagram.GamaFeatureProvider;
import msi.gama.kernel.model.IModel;
import msi.gama.lang.gaml.resource.GamlResource;
import msi.gama.lang.gaml.ui.GamlEditor;
import msi.gama.lang.gaml.validation.GamlJavaValidator;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.exceptions.GamaRuntimeException;

import org.eclipse.core.commands.*;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.internal.services.GraphitiInternal;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.graphiti.ui.editor.DiagramEditorFactory;
import org.eclipse.graphiti.ui.editor.DiagramEditorInput;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;

public class GenerateDiagramHandler extends AbstractHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		 final GamlEditor editor = (GamlEditor) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		 IModel model = editor.getDocument().readOnly(new IUnitOfWork<IModel, XtextResource>() {

				@Override
				public IModel exec(final XtextResource state) throws Exception {
					return editor.getValidator().build((GamlResource) state);
				}

			});
		 if (model == null)
			 return null;
		 	File file = new File(model.getProjectPath()+ "/diagrams/" + model.getName() + ".diagram");
			System.out.println("file : " + model.getProjectPath()+ "/diagrams/" + model.getName() + ".diagram");
			if (file.exists()) file.delete();
			
	 	IResource resource = editor.getResource();
	    IContainer container = resource.getProject();				
	    final IFolder modelFolder = container.getFolder(new Path("diagrams"));
		if ( !modelFolder.exists() ) {
			try {
				modelFolder.create(true, true, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		final IFile fileP = container.getFile(new Path("diagrams/" + model.getName() + ".diagram"));
			
		createDiagramEditor(fileP, model.getName(), model);
		return null;
	}
	
	
	private void createDiagramEditor (IFile file, String diagramName,final IModel gamaModel) {
		// Create the diagram
		final Diagram diagram = Graphiti.getPeCreateService().createDiagram("gamaDiagram", diagramName, true);
		URI uri = URI.createPlatformResourceURI(file.getFullPath().toString(), true);
		TransactionalEditingDomain domain = createEmfFileForDiagram(uri, diagram);

		final DiagramEditorInput editorInput = new DiagramEditorInput(EcoreUtil.getURI(diagram), domain, "idees.gama.diagram.MyGamaDiagramTypeProvider");
		Display.getCurrent().asyncExec(new Runnable() {

			@Override
			public void run() {
				IWorkbenchPage pag =
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				try {
					IEditorPart ep = pag.openEditor(editorInput, "org.eclipse.graphiti.ui.editor.DiagramEditor");
					IDiagramTypeProvider dtp = GraphitiInternal.getEmfService().getDTPForDiagram(diagram);
					GamaFeatureProvider gfp = ((GamaFeatureProvider) dtp.getFeatureProvider());
					gfp.setTypeOfModel("custom");
					gfp.setGamaModel(gamaModel);
					gfp.init();
					ep.doSave(null);
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
		});
	} 
	
	public static TransactionalEditingDomain createEmfFileForDiagram(URI uri, final Diagram diagram) {
		 // Create a resource set and EditingDomain
		 TransactionalEditingDomain editingDomain = DiagramEditorFactory.createResourceSetAndEditingDomain();
		 ResourceSet resourceSet = editingDomain.getResourceSet();

		 // Create a resource for this file.
		 final Resource resource = resourceSet.createResource(uri);
		     
		 editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain) {
		  @Override
		  protected void doExecute() {
		   resource.setTrackingModification(true);
		   resource.getContents().add(diagram);
		  }
		 });

		 return editingDomain;
		}

}
