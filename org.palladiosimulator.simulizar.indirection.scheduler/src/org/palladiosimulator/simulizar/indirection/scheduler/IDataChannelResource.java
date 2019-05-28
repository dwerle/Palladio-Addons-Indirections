package org.palladiosimulator.simulizar.indirection.scheduler;

import java.util.Queue;

import org.palladiosimulator.pcm.core.composition.AssemblyContext;

import de.uka.ipd.sdq.scheduler.ISchedulableProcess;
import de.uka.ipd.sdq.scheduler.processes.IWaitingProcess;
import de.uka.ipd.sdq.scheduler.sensors.IPassiveResourceSensor;
import de.uka.ipd.sdq.simucomframework.variables.stackframe.SimulatedStackframe;

public interface IDataChannelResource {
	    public abstract void put(ISchedulableProcess process, SimulatedStackframe<Object> eventStackframe);
	    public abstract SimulatedStackframe<Object> get(ISchedulableProcess process);

	    /**
	     * Getter for the model element of the assembly context.
	     * 
	     * @return The assembly context of the passive resource.
	     */
	    public AssemblyContext getAssemblyContext();

	    /**
	     * Name of the resource.
	     * 
	     * @return
	     */
	    public String getName();

	    /**
	     * Unique identifier of the resource.
	     * 
	     * @return
	     */
	    public String getId();

	    /**
	     * Returns the maximal number of instances that can be acquired at the same time.
	     */
	    public long getCapacity();

	    /**
	     * Returns the number of remaining instances.
	     */
	    public long getAvailable();

	    /**
	     * Adds the given observer. Observers get notified when a process acquired or released this
	     * resource.
	     */
	    public void addObserver(IPassiveResourceSensor observer);

	    /**
	     * Removes the given observer
	     */
	    public void removeObserver(IPassiveResourceSensor observer);

	    /**
	     * Returns a queue containing the waiting processes for the passive resource.
	     */
	    public Queue<IWaitingProcess> getWaitingProcesses();

}
