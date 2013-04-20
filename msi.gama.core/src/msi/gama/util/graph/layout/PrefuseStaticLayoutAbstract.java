package msi.gama.util.graph.layout;


import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import msi.gama.metamodel.shape.GamaPoint;
import msi.gama.metamodel.shape.ILocation;
import msi.gama.metamodel.shape.IShape;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.graph.GamaGraph;
import msi.gama.util.graph.GraphUtilsPrefuse;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.layout.Layout;
import prefuse.data.Graph;
import prefuse.render.DefaultRendererFactory;
import prefuse.visual.VisualItem;
import prefuse.visual.VisualTable;

import com.vividsolutions.jts.geom.Envelope;

public abstract class PrefuseStaticLayoutAbstract implements IStaticLayout {

	public static final String PREFUSE_GRAPH = "g";

	private int count_measures = 20;	// TODO adapt to the size of the graph
	private Map<VisualItem,Double> lastNode2measures = null;
	
	protected Random random = new Random();
	
	private Logger logger = Logger.getLogger(getClass().getName());
	
	private void resetThermometer(int nbtuples) {
		lastNode2measures = null;
		count_measures = 
				Math.min(
						Math.max(
								nbtuples/20, // ideal: measure 5% of nodes
								10  						// measure at least 10, even if the network is small ! 
						),
						nbtuples-1							// but don't try to measure more than existing..;
						);
	}

	private Double insertThermometer(Visualization viz) {
		
		try {
				
		if (lastNode2measures == null) {
		
			VisualTable tuples = (VisualTable)viz.getVisualGroup(PREFUSE_GRAPH+".nodes");
			int nbtuples = tuples.getTupleCount();
			
			lastNode2measures = new HashMap<VisualItem, Double>(count_measures);
			
			// let's select n random nodes that will be used for measurement
			while (lastNode2measures.size() < count_measures) {
				VisualItem i = tuples.getItem(random.nextInt(nbtuples));
				lastNode2measures.put(i, i.getX()+i.getY());
			}
			
			// no previous reference, the difference (and temperature) is infinite !
			return Double.POSITIVE_INFINITY;
						
		} else {
			
			Map<VisualItem,Double> newMeasures = new HashMap<VisualItem, Double>(count_measures);

			double temperature = 0.0;

			for (VisualItem i : lastNode2measures.keySet()) {
				double prev = lastNode2measures.get(i);
				double novel = i.getX()+i.getY();
				newMeasures.put(i, novel);
				temperature += Math.pow(prev - novel, 2);
			}
			
			lastNode2measures = newMeasures;
			
			logger.fine("temperature = "+temperature);
			return temperature;
		}
	
		} catch (RuntimeException e) {
			return Double.POSITIVE_INFINITY; // in case of error, we just have no measure...
		}
	}
	
	/**
	 * Takes a prefuse graph and applies a prefuse layout, with a max time for execution; the layout is bounded
	 * according to parameters.
	 * @param prefuseGraph
	 * @param prefuseLayout
	 * @param bounds
	 * @param maxtime
	 */
	private void renderLayout(Graph prefuseGraph, Layout prefuseLayout, Rectangle2D bounds, long maxtime) {
			
		// configure the layout
        prefuseLayout.setGroup(PREFUSE_GRAPH);
        prefuseLayout.setLayoutBounds(bounds);
        prefuseLayout.setMargin(0, 0, 0, 0);
        //prefuseLayout.setDuration(maxtime);
        prefuseLayout.setStepTime(0);
		
		// create the visualization required to drive a layout
		Visualization viz = new Visualization();
		viz.addGraph(PREFUSE_GRAPH, prefuseGraph);
		viz.setVisible(PREFUSE_GRAPH, null, true);
		
		//viz.setInteractive(PREFUSE_GRAPH, null, false); // no interactivity there
		
        ActionList actionsLayout = new ActionList(ActionList.INFINITY);
        actionsLayout.add(prefuseLayout);
        
        viz.putAction("layout", actionsLayout);
        
        viz.setRendererFactory(new DefaultRendererFactory());

        Display display = new Display(viz);
        display.setSize(
        		(int)Math.ceil(bounds.getWidth()), 
        		(int)Math.ceil(bounds.getHeight())
        		); // set display size

        
        // init positions
        Iterator itPrefuseNodes = viz.getVisualGroup(PREFUSE_GRAPH+".nodes").tuples();
        while (itPrefuseNodes.hasNext()) {
        	VisualItem prefuseNode = (VisualItem)itPrefuseNodes.next();        
        	IShape gamaNode = (IShape)prefuseNode.get(GraphUtilsPrefuse.PREFUSE_ATTRIBUTE_GAMA_OBJECT);
        	prefuseNode.setX(gamaNode.getLocation().getX());
        	prefuseNode.setY(gamaNode.getLocation().getY());
        	
        }
        
        // actually run layout
		viz.run("layout");
		resetThermometer(prefuseGraph.getNodeCount());
		long timeBegin = System.currentTimeMillis();
		long sleepTime = 20;
		System.err.println("layout !");
		Double temperature = Double.POSITIVE_INFINITY;
        while (
        		(System.currentTimeMillis() - timeBegin < maxtime) 
        		&& 
        		(temperature > 0.1)
        		) {
      
        	// compute temperature
        	temperature = insertThermometer(viz);
        	
        	try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
        	System.err.print(".");
      
        }
        
        // stop and end layout
        //viz.cancel("layout");
        viz.cancel("layout");
    	logger.fine("layout finished in: "+(System.currentTimeMillis() - timeBegin ));
        
        
        // retrieve the resulting coordinates
        itPrefuseNodes = viz.getVisualGroup(PREFUSE_GRAPH+".nodes").tuples();
        		
        while (itPrefuseNodes.hasNext()) {
        	VisualItem prefuseNode = (VisualItem)itPrefuseNodes.next();
        		
        	IShape gamaNode = (IShape)prefuseNode.get(GraphUtilsPrefuse.PREFUSE_ATTRIBUTE_GAMA_OBJECT);
        	
        	ILocation newloc = new GamaPoint(
        			prefuseNode.getX(),
        			prefuseNode.getY()
        			);
        	gamaNode.setLocation(newloc);
        	
        }
        
        // free memory
        viz.removeGroup(PREFUSE_GRAPH);
        prefuseGraph.clear();
        
	}
	
	/**
	 * Takes a prefuse graph and applies a prefuse layout, with a max time for execution.
	 * Layout will use the space defined by the world agent found through the gama scope.
	 * @param prefuseGraph
	 * @param prefuseLayout
	 * @param scope
	 * @param maxtime
	 */
	private void renderLayout(Graph prefuseGraph, Layout prefuseLayout, IScope scope, long maxtime) {
		
		Envelope envelope = scope.getWorldScope().getEnvelope();
				
		Rectangle bounds = new Rectangle(
				(int)Math.floor(envelope.getMinX()), 
				(int)Math.floor(envelope.getMinY()),
				(int)Math.ceil(envelope.getWidth()),
				(int)Math.ceil(envelope.getHeight())
				);
		
		renderLayout(prefuseGraph, prefuseLayout, bounds, maxtime);
		
	}
	
	
	/**
	 * The actual creation of the prefuse layout to be used by the layout process.
	 * @param timeout
	 * @param options
	 * @return
	 */
	protected abstract Layout createLayout(long timeout, Map<String,Object> options);
	
	
	/**
	 * Returns a concise name for this layout
	 * @return
	 */
	protected abstract String getLayoutName();
	
	/**
	 * returns the name of options that could be accepted by the layout
	 * @return
	 */
	protected abstract Collection<String> getLayoutOptions();
	
	protected Logger getLayoutLogger() {
		return 	Logger.getLogger(getLayoutName());

	}
	@Override
	public void doLayoutOneShot(IScope scope, GamaGraph<?, ?> graph, long timeout, Map<String,Object> options) {
			
		renderLayout(
				GraphUtilsPrefuse.getPrefuseGraphFromGamaGraphForVisu(graph), 
				createLayout(timeout, options), 
				scope, 
				timeout);
		
		// warn the user of the options that were provided but not used
		Set<String> uselessOptions = new HashSet<String>(options.keySet());
		uselessOptions.removeAll(getLayoutOptions());		
		if (!uselessOptions.isEmpty()) {
			StringBuffer sb = new StringBuffer();
			sb.append("layout: ").append(getLayoutName());
			sb.append(" ignored some of the options that were provided: ");
			sb.append(uselessOptions);
			sb.append(" (as a reminder, this layout accepts the following options: ");
			sb.append(getLayoutOptions()).append(")");
			GAMA.reportError(
					new GamaRuntimeException(
							sb.toString(),
							true
							)
					);
		}
	}

}

