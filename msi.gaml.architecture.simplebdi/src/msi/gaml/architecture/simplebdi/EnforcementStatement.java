package msi.gaml.architecture.simplebdi;

import msi.gama.common.interfaces.IKeyword;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.example;
import msi.gama.precompiler.GamlAnnotations.facet;
import msi.gama.precompiler.GamlAnnotations.facets;
import msi.gama.precompiler.GamlAnnotations.inside;
import msi.gama.precompiler.GamlAnnotations.symbol;
import msi.gama.precompiler.IConcept;
import msi.gama.precompiler.ISymbolKind;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.descriptions.IDescription;
import msi.gaml.expressions.IExpression;
import msi.gaml.operators.Cast;
import msi.gaml.statements.AbstractStatement;
import msi.gaml.types.IType;

@symbol (
		name = EnforcementStatement.ENFORCEMENT,
		kind = ISymbolKind.SINGLE_STATEMENT,
		with_sequence = false,
		concept = { IConcept.BDI })
@inside (
		kinds = { ISymbolKind.BEHAVIOR, ISymbolKind.SEQUENCE_STATEMENT })
@facets (
		value = { @facet (
				name = IKeyword.NAME,
				type = IType.ID,
				optional = true,
				doc = @doc ("the identifier of the enforcement")),
				@facet (
						name = IKeyword.WHEN,
						type = IType.BOOL,
						optional = true,
						doc = @doc ("A boolean value to enforce only with a certain condition")),
				@facet (
						name = EnforcementStatement.NORM,
						type = IType.STRING,
						optional = true,
						doc = @doc ("The norm to enforce")),
				@facet (
						name = EnforcementStatement.OBLIGATION,
						type = PredicateType.id,
						optional = true,
						doc = @doc ("The obligation to enforce")),
				@facet (
						name = EnforcementStatement.LAW,
						type = IType.STRING,
						optional = true,
						doc = @doc ("The law to enforce")),
				@facet (
						name = EnforcementStatement.SANCTION,
						type = IType.STRING,
						optional = true,
						doc = @doc ("The sanction to apply if the norm is violated")),
				@facet (
						name = EnforcementStatement.REWARD,
						type = IType.STRING,
						optional = true,
						doc = @doc ("The positive sanction to apply if the norm has been followed")) })
@doc (
		value = "applay a sanction if the norm specified is violated, or a reward if the norm is applied by the perceived agent",
		examples = {
				@example ("focus var:speed /*where speed is a variable from a species that is being perceived*/") })

// statement servant ?? controler les normes pour appliquer des sanctions, sur le mood??le du focus
public class EnforcementStatement extends AbstractStatement {

	public static final String ENFORCEMENT = "enforcement";
	public static final String NORM = "norm";
	public static final String SANCTION = "sanction";
	public static final String REWARD = "reward";
	public static final String OBLIGATION = "obligation";
	public static final String LAW = "law";

	final IExpression nameExpr;
	final IExpression when;
	final IExpression norm;
	final IExpression sanction;
	final IExpression reward;
	final IExpression obligation;
	final IExpression law;

	public EnforcementStatement(final IDescription desc) {
		super(desc);
		nameExpr = getFacet(IKeyword.NAME);
		when = getFacet(IKeyword.WHEN);
		norm = getFacet(EnforcementStatement.NORM);
		sanction = getFacet(EnforcementStatement.SANCTION);
		reward = getFacet(EnforcementStatement.REWARD);
		obligation = getFacet(EnforcementStatement.OBLIGATION);
		law = getFacet(EnforcementStatement.LAW);
	}

	@Override
	protected Object privateExecuteIn(final IScope scope) throws GamaRuntimeException {
		Object retour = null;
		if (when == null || Cast.asBool(scope, when.value(scope))) {
			final IAgent[] stack = scope.getAgentsStack();
			final IAgent mySelfAgent = stack[stack.length - 2];
			IScope scopeMySelf = null;
			if (mySelfAgent != null) {
				scopeMySelf = mySelfAgent.getScope().copy("in EnforcementStatement");
				scopeMySelf.push(mySelfAgent);
			}
			if (norm != null) {
				// on recherche la norme avec le m??me nom chez l'autre et on regarde si elle est viol??e
				Norm normToTest = null;
				// Am??liorable en temps de calcul
				for (final Norm tempNorm : SimpleBdiArchitecture.getNorms(scope)) {
					if (tempNorm.getName().equals(norm.value(scopeMySelf))) {
						normToTest = tempNorm;
					}
				}
				if (normToTest != null) {
					if (normToTest.getViolated() && sanction != null) {
						// On applique la sanction
						Sanction sanctionToExecute = null;
						// Am??liorable en temps de calcul
						for (final Sanction tempSanction : SimpleBdiArchitecture.getSanctions(scopeMySelf)) {
							if (tempSanction.getName().equals(sanction.value(scopeMySelf))) {
								sanctionToExecute = tempSanction;
							}
						}
						// Ici, la sanction est ex??cut??e dans le contexte de l'agent controleur car la sanction est
						// indirecte contre une norme sociale
						// return sanctionToExecute.getSanctionStatement().executeOn(scopeMySelf);
						if (sanctionToExecute != null) {
							retour = sanctionToExecute.getSanctionStatement().executeOn(scopeMySelf);
						}
					} else if (normToTest.getApplied() && reward != null) {
						// on applique le reward
						Sanction rewardToExecute = null;
						// Am??liorable en temps de calcul
						for (final Sanction tempReward : SimpleBdiArchitecture.getSanctions(scopeMySelf)) {
							if (tempReward.getName().equals(reward.value(scopeMySelf))) {
								rewardToExecute = tempReward;
							}
						}
						// Ici, le reward est ex??cut?? dans le contexte de l'agent controleur car la sanction est
						// indirecte contre une norme sociale
						// return rewardToExecute.getSanctionStatement().executeOn(scopeMySelf);
						if (rewardToExecute != null) {
							retour = rewardToExecute.getSanctionStatement().executeOn(scopeMySelf);
						}
					}
				}
			}
			if (obligation != null) {
				// on regarde si la base des obligations de l'autre est vide, si non, on regarde s'il a appliqu?? une
				// norme portant sur l'obligation ?? v??rifi??e.
				// Les sanctions et rewards seront ici appliqu??es dans le cadre de l'agent control?? car directe
				final MentalState tempObligation = new MentalState("obligation", (Predicate) obligation.value(scope));
				if (SimpleBdiArchitecture.hasObligation(scope, tempObligation)) {
					// si ma norme en cours r??pond ?? l'obligation , reward, sinon punition.
					Norm tempNorm = null;// new Norm ((NormStatement)scope.getAgent().getAttribute("CURRENT_NORM"));
					for (final Norm testNorm : SimpleBdiArchitecture.getNorms(scope)) {
						if (testNorm.getObligation(scope) != null
								&& testNorm.getObligation(scope).equals(tempObligation.getPredicate())) {
							tempNorm = testNorm;
						}
					}
					if (tempNorm != null && !tempNorm.getSanctioned()) {
						if (reward != null && tempNorm.getApplied()) {// &&
																		// tempNorm.getNormStatement()!=null
																		// &&
																		// tempNorm.getObligation(scope)!=null
																		// &&
																		// tempNorm.getObligation(scope).equals(tempObligation.getPredicate())){
							Sanction rewardToExecute = null;
							// Am??liorable en temps de calcul
							for (final Sanction tempReward : SimpleBdiArchitecture.getSanctions(scopeMySelf)) {
								if (tempReward.getName().equals(reward.value(scopeMySelf))) {
									rewardToExecute = tempReward;
								}
							}
							// Ici, le reward est ex??cut?? dans le contexte de l'agent controleur car la sanction est
							// indirecte contre une norme sociale
							// return rewardToExecute.getSanctionStatement().executeOn(scopeMySelf);
							if (rewardToExecute != null) {
								retour = rewardToExecute.getSanctionStatement().executeOn(scopeMySelf);
							}
							tempNorm.sanctioned();
						} else {
							if (sanction != null/* && tempNorm.getNormStatement()==null) */ || sanction != null
									&& tempNorm.getViolated()) {// &&
																// tempNorm.getNormStatement()!=null
																// &&
																// tempNorm.getObligation(scope)!=null
																// &&
																// !tempNorm.getObligation(scope).equals(tempObligation.getPredicate()))){
								Sanction sanctionToExecute = null;
								// Am??liorable en temps de calcul
								for (final Sanction tempSanction : SimpleBdiArchitecture.getSanctions(scopeMySelf)) {
									if (tempSanction.getName().equals(sanction.value(scopeMySelf))) {
										sanctionToExecute = tempSanction;
									}
								}
								// Ici, le reward est ex??cut?? dans le contexte de l'agent controleur car la sanction est
								// indirecte contre une norme sociale
								// return sanctionToExecute.getSanctionStatement().executeOn(scopeMySelf);
								if (sanctionToExecute != null) {
									retour = sanctionToExecute.getSanctionStatement().executeOn(scopeMySelf);
								}
								tempNorm.sanctioned();
							}
						}
					}
				}
			}
			if (law != null) {
				// on recherche la norme avec le m??me nom chez l'autre et on regarde si elle est viol??e
				LawStatement lawToTest = null;
				Double obedienceValue = (Double) scope.getAgent().getAttribute("obedience");
				Boolean isViolated = true;
				// Am??liorable en temps de calcul
				for (final LawStatement tempLaw : SimpleBdiArchitecture.getLaws(scope)) {
					if (tempLaw.getName().equals(law.value(scopeMySelf))) {
						lawToTest = tempLaw;
					}
				}
				if (lawToTest != null) {
					if (lawToTest.getContextExpression() == null
							|| msi.gaml.operators.Cast.asBool(scope, lawToTest.getContextExpression().value(scope))) {
						if (lawToTest.getBeliefExpression() == null
								|| lawToTest.getBeliefExpression().value(scope) == null
								|| SimpleBdiArchitecture.hasBelief(scope, new MentalState("Belief",
										(Predicate) lawToTest.getBeliefExpression().value(scope)))) {
							if (lawToTest.getObligationExpression() == null
									|| lawToTest.getObligationExpression().value(scope) == null
									|| SimpleBdiArchitecture.hasObligation(scope, new MentalState("Obligation",
											(Predicate) lawToTest.getObligationExpression().value(scope)))) {
								if(lawToTest.getThreshold() == null
										|| lawToTest.getThreshold().value(scope) == null
										|| obedienceValue>= (Double) lawToTest.getThreshold().value(scope)) {
									isViolated = false;
								if (reward != null) {
									Sanction rewardToExecute = null;
									// Am??liorable en temps de calcul
									for (final Sanction tempReward : SimpleBdiArchitecture.getSanctions(scopeMySelf)) {
										if (tempReward.getName().equals(reward.value(scopeMySelf))) {
											rewardToExecute = tempReward;
										}
									}
									if (rewardToExecute != null) {
										retour = rewardToExecute.getSanctionStatement().executeOn(scopeMySelf);
									}
								}
							} 
						}
					} 
					} if(isViolated) {
								if (sanction != null) {
									Sanction sanctionToExecute = null;
									// Am??liorable en temps de calcul
									for (final Sanction tempSanction : SimpleBdiArchitecture
											.getSanctions(scopeMySelf)) {
										if (tempSanction.getName().equals(sanction.value(scopeMySelf))) {
											sanctionToExecute = tempSanction;
										}
									}
									if (sanctionToExecute != null) {
										retour = sanctionToExecute.getSanctionStatement().executeOn(scopeMySelf);
									}
								}
							}
				}
			}
		
			GAMA.releaseScope(scopeMySelf);
		}
		return retour;
	}

}
