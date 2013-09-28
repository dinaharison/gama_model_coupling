/**
 * Created by drogoul, 19 janv. 2012
 * 
 */
package msi.gama.gui.swt.controls;

import msi.gama.gui.swt.IGamaIcons;
import msi.gama.gui.swt.SwtGui;
import msi.gama.runtime.GAMA;
import msi.gaml.operators.Maths;

/**
 * The class SimulationSpeedContributionItem.
 * 
 * @author drogoul
 * @since 19 janv. 2012
 * 
 */
public class SimulationSpeedContributionItem extends SpeedContributionItem {

	/**
	 * 
	 */
	public SimulationSpeedContributionItem() {
		super("Adjust simulation speed", GAMA.getClock().getDelay(), new IPositionChangeListener() {

			@Override
			public void positionChanged(final double position) {
				GAMA.getClock().setDelay(position);
			}

		}, new IToolTipProvider() {

			@Override
			public String getToolTipText(final double value) {
				return "Minimum duration of a cycle " + Maths.opTruncate((1000 - 1000 * value) / 1000, 3) + " s";
			}

		}, IGamaIcons.TOOLBAR_KNOB.image(), IGamaIcons.TOOLBAR_KNOB.image(), SwtGui.getOkColor());
	}

	/**
	 * @param id
	 */
	public SimulationSpeedContributionItem(final String id) {
		this();
		setId(id);
	}

}
