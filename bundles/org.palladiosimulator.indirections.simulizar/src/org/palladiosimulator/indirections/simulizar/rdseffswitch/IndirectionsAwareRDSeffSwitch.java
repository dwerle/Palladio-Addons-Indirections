package org.palladiosimulator.indirections.simulizar.rdseffswitch;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.palladiosimulator.indirections.actions.ConsumeDataAction;
import org.palladiosimulator.indirections.actions.EmitDataAction;
import org.palladiosimulator.indirections.actions.util.ActionsSwitch;
import org.palladiosimulator.indirections.composition.DataChannelSinkConnector;
import org.palladiosimulator.indirections.composition.DataChannelSourceConnector;
import org.palladiosimulator.indirections.interfaces.IDataChannelResource;
import org.palladiosimulator.indirections.scheduler.IndirectionUtil;
import org.palladiosimulator.indirections.system.DataChannel;
import org.palladiosimulator.pcm.allocation.Allocation;
import org.palladiosimulator.pcm.allocation.AllocationContext;
import org.palladiosimulator.pcm.core.PCMRandomVariable;
import org.palladiosimulator.pcm.core.composition.AssemblyContext;
import org.palladiosimulator.pcm.core.composition.Connector;
import org.palladiosimulator.pcm.core.composition.EventChannel;
import org.palladiosimulator.pcm.parameter.VariableCharacterisation;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.repository.SinkRole;
import org.palladiosimulator.pcm.repository.SourceRole;
import org.palladiosimulator.simulizar.exceptions.PCMModelAccessException;
import org.palladiosimulator.simulizar.exceptions.PCMModelInterpreterException;
import org.palladiosimulator.simulizar.interpreter.ExplicitDispatchComposedSwitch;
import org.palladiosimulator.simulizar.interpreter.InterpreterDefaultContext;
import org.palladiosimulator.simulizar.runtimestate.SimulatedBasicComponentInstance;
import org.palladiosimulator.simulizar.utils.SimulatedStackHelper;

import de.uka.ipd.sdq.simucomframework.resources.SimulatedResourceContainer;
import de.uka.ipd.sdq.simucomframework.variables.EvaluationProxy;
import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import de.uka.ipd.sdq.simucomframework.variables.exceptions.ValueNotInFrameException;
import de.uka.ipd.sdq.simucomframework.variables.stackframe.SimulatedStackframe;
import de.uka.ipd.sdq.stoex.AbstractNamedReference;
import de.uka.ipd.sdq.stoex.analyser.visitors.StoExPrettyPrintVisitor;

public class IndirectionsAwareRDSeffSwitch extends ActionsSwitch<Object> {
	private static final Logger LOGGER = Logger.getLogger(IndirectionsAwareRDSeffSwitch.class);

	private InterpreterDefaultContext context;
	private SimulatedBasicComponentInstance basicComponentInstance;
	private ExplicitDispatchComposedSwitch<Object> parentSwitch;
	private Allocation allocation;
	private SimulatedStackframe<Object> resultStackFrame;

	private DataChannelRegistry dataChannelRegistry;

	/**
	 * copied from
	 * {@link org.palladiosimulator.simulizar.interpreter.RDSeffSwitch#RDSeffSwitch(InterpreterDefaultContext, SimulatedBasicComponentInstance)}
	 * 
	 * @param context
	 * @param basicComponentInstance
	 */
	public IndirectionsAwareRDSeffSwitch(final InterpreterDefaultContext context,
			final SimulatedBasicComponentInstance basicComponentInstance) {
		super();
		this.context = context;
		this.allocation = context.getLocalPCMModelAtContextCreation().getAllocation();
		this.resultStackFrame = new SimulatedStackframe<Object>();
		this.basicComponentInstance = basicComponentInstance;
		
		this.dataChannelRegistry = DataChannelRegistry.getInstanceFor(context);
	}

	public IndirectionsAwareRDSeffSwitch(InterpreterDefaultContext context,
			SimulatedBasicComponentInstance basicComponentInstance,
			ExplicitDispatchComposedSwitch<Object> parentSwitch) {
		this(context, basicComponentInstance);
		this.parentSwitch = parentSwitch;
	}

	/**
	 * Same as
	 * {@link SimulatedStackHelper#addParameterToStackFrame(SimulatedStackframe, EList, SimulatedStackframe)}
	 * but defaults for the parameters.
	 * 
	 * @param parameterName
	 */
	private static final void addParameterToStackFrameWithCopying(final SimulatedStackframe<Object> contextStackFrame,
			final EList<VariableUsage> parameter, String parameterName,
			final SimulatedStackframe<Object> targetStackFrame) {

		for (final VariableUsage variableUsage : parameter) {
			if (variableUsage.getVariableCharacterisation_VariableUsage().isEmpty()) {
				final AbstractNamedReference namedReference = variableUsage.getNamedReference__VariableUsage();
				// TODO: move from convention quick hack to better solution
				String[] split = namedReference.getReferenceName().split("->");
				if (split.length != 2) {
					throw new PCMModelInterpreterException(
							"If no variable chacterisations are present, name must be of form 'input->output'. Name is: "
									+ namedReference.getReferenceName());
				}

				String inputPrefix = split[0] + ".";
				String outputPrefix = split[1] + ".";

				List<Entry<String, Object>> inputs = contextStackFrame.getContents().stream()
						.filter(it -> it.getKey().startsWith(inputPrefix)).collect(Collectors.toList());

				if (inputs.size() == 0) {
					throw new PCMModelInterpreterException("Nothing found on stack frame for prefix '" + inputPrefix
							+ "'. Available: " + contextStackFrame.getContents().stream().map(it -> it.getKey())
									.collect(Collectors.joining(", ")));
				}

				inputs.forEach(it -> targetStackFrame
						.addValue(outputPrefix + it.getKey().substring(inputPrefix.length()), it.getValue()));
				continue;
			}

			for (final VariableCharacterisation variableCharacterisation : variableUsage
					.getVariableCharacterisation_VariableUsage()) {

				final PCMRandomVariable randomVariable = variableCharacterisation
						.getSpecification_VariableCharacterisation();

				final AbstractNamedReference namedReference = variableCharacterisation
						.getVariableUsage_VariableCharacterisation().getNamedReference__VariableUsage();

				final String id = new StoExPrettyPrintVisitor().doSwitch(namedReference).toString() + "."
						+ variableCharacterisation.getType().getLiteral();
				;
				if (SimulatedStackHelper.isInnerReference(namedReference)) {
					targetStackFrame.addValue(id,
							new EvaluationProxy(randomVariable.getSpecification(), contextStackFrame.copyFrame()));
				} else {
					targetStackFrame.addValue(id,
							StackContext.evaluateStatic(randomVariable.getSpecification(), contextStackFrame));
				}

				if (LOGGER.isDebugEnabled()) {
					try {
						LOGGER.debug("Added value " + targetStackFrame.getValue(id) + " for id " + id
								+ " to stackframe " + targetStackFrame);
					} catch (final ValueNotInFrameException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}
	
	// TODO move to helper
	private <K,V> Map<K,V> toMap(List<Entry<K,V>> entryList) {
		return entryList.stream()
				.collect(Collectors.toMap(it -> it.getKey(), it -> it.getValue()));
	}

	@Override
	public Object caseEmitDataAction(EmitDataAction action) {
//		System.out.println("Emit event action: " + action.getEntityName());
		IDataChannelResource dataChannelResource = getDataChannelResource(action);

		SimulatedStackframe<Object> eventStackframe = new SimulatedStackframe<Object>();
		String parameterName = IndirectionUtil
				.claimOne(
						action.getSourceRole().getEventGroup__SourceRole().getEventTypes__EventGroup())
				.getParameter__EventType().getParameterName();
		addParameterToStackFrameWithCopying(this.context.getStack().currentStackFrame(),
				action.getInputVariableUsages__CallAction(), parameterName, eventStackframe);

		// TODO: check cases in which getContents does not work
		dataChannelResource.put(this.context.getThread(), toMap(eventStackframe.getContents()));

		return true;
	}
	

	@Override
	public Object caseConsumeDataAction(ConsumeDataAction action) {
		IDataChannelResource dataChannelResource = getDataChannelResource(action);

		String randomUUID = Thread.currentThread().getName();

//		System.out.println("Trying to get (" + randomUUID + ")");
		boolean result = dataChannelResource.get(this.context.getThread(), (eventMap) -> {
			SimulatedStackframe<Object> contextStackframe = SimulatedStackHelper.createFromMap(eventMap);
			String parameterName = IndirectionUtil
					.claimOne(action.getSinkRole().getEventGroup__SinkRole().getEventTypes__EventGroup())
					.getParameter__EventType().getParameterName();
//			System.out.println("Parameter name: " + parameterName + " (" + randomUUID + ")");
			addParameterToStackFrameWithCopying(contextStackframe, action.getReturnVariableUsage__CallReturnAction(),
					parameterName, this.context.getStack().currentStackFrame());
//			System.out.println(this.context.getStack().currentStackFrame());
		});

//		System.out.println("Continuing with " + this.context.getStack().currentStackFrame() + " (" + randomUUID + ")");

		return result;
	}

	private IDataChannelResource getDataChannelResource(EmitDataAction action) {
		AssemblyContext assemblyContext = this.context.getAssemblyContextStack().peek();
		SourceRole sourceRole = action.getSourceRole();
		DataChannel dataChannel = getConnectedSinkDataChannel(assemblyContext, sourceRole);
		AllocationContext eventChannelAllocationContext = getAllocationContext(dataChannel);

		SimulatedResourceContainer resourceContainer = getSimulatedResourceContainer(dataChannel,
				eventChannelAllocationContext);
		IDataChannelResource dataChannelResource = dataChannelRegistry.getOrCreateDataChannelResource(dataChannel);
		return dataChannelResource;
	}

	private IDataChannelResource getDataChannelResource(ConsumeDataAction action) {
		AssemblyContext assemblyContext = this.context.getAssemblyContextStack().peek();
		SinkRole sinkRole = action.getSinkRole();
		DataChannel dataChannel = getConnectedSourceDataChannel(assemblyContext, sinkRole);
		AllocationContext eventChannelAllocationContext = getAllocationContext(dataChannel);

		SimulatedResourceContainer resourceContainer = getSimulatedResourceContainer(dataChannel,
				eventChannelAllocationContext);
		IDataChannelResource dataChannelResource = dataChannelRegistry.getOrCreateDataChannelResource(dataChannel);
		return dataChannelResource;
	}

	private SimulatedResourceContainer getSimulatedResourceContainer(EventChannel eventChannel,
			AllocationContext eventChannelAllocationContext) {
		List<SimulatedResourceContainer> simulatedResourceContainers = this.context.getModel().getResourceRegistry()
				.getSimulatedResourceContainers();
		return simulatedResourceContainers.stream()
				.filter(it -> it.getResourceContainerID()
						.equals(eventChannelAllocationContext.getResourceContainer_AllocationContext().getId()))
				.findAny().orElseThrow(() -> new PCMModelAccessException(
						"Could not find resource container for event channel " + eventChannel));
	}

	private AllocationContext getAllocationContext(EventChannel eventChannel) {
		return this.allocation.getAllocationContexts_Allocation().stream()
				.filter(it -> it.getEventChannel__AllocationContext() == eventChannel).findAny()
				.orElseThrow(() -> new PCMModelAccessException(
						"Could not find allocation context for event channel " + eventChannel));
	}

	private DataChannel getConnectedSinkDataChannel(AssemblyContext assemblyContext, SourceRole sourceRole) {
		EList<Connector> connectors = assemblyContext.getParentStructure__AssemblyContext()
				.getConnectors__ComposedStructure();
		List<DataChannelSourceConnector> dataChannelSourceConnectors = connectors.stream()
				.filter(DataChannelSourceConnector.class::isInstance).map(DataChannelSourceConnector.class::cast)
				.collect(Collectors.toList());

		return dataChannelSourceConnectors.stream().filter(it -> it.getSourceRole().equals(sourceRole)).findAny()
				.orElseThrow(
						() -> new PCMModelAccessException("Could not find data channel for source role " + sourceRole))
				.getDataChannel();
	}

	private DataChannel getConnectedSourceDataChannel(AssemblyContext assemblyContext, SinkRole sinkRole) {
		EList<Connector> connectors = assemblyContext.getParentStructure__AssemblyContext()
				.getConnectors__ComposedStructure();
		List<DataChannelSinkConnector> dataChannelSinkConnectors = connectors.stream()
				.filter(DataChannelSinkConnector.class::isInstance).map(DataChannelSinkConnector.class::cast)
				.collect(Collectors.toList());

		DataChannelSinkConnector sinkConnectorForRole = dataChannelSinkConnectors.stream()
				.filter(it -> it.getSinkRole().equals(sinkRole)).findAny().orElseThrow(
						() -> new PCMModelAccessException("Could not find data channel for sink role " + sinkRole));

		return sinkConnectorForRole.getDataChannel();
	}
}
