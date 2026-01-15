/*
 * Copyright (C) 2014, The Max Planck Institute for
 * Psycholinguistics.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License is included in the file
 * LICENSE-gpl-3.0.txt. If that file is missing, see
 * <http://www.gnu.org/licenses/>.
 */

package nl.mpi.oai.harvester.action;

import nl.mpi.oai.harvester.control.ResourcePool;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.metadata.MetadataFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import nl.mpi.oai.harvester.metadata.Record;

/**
 * This class represents a sequence (or pipeline) of actions on metadata,
 * comprising an input format specification followed by a list of actions
 * to be performed sequentially.
 * 
 * @author Lari Lampen (MPI-PL), Kees Jan van de Looij (MPI-PL)
 */
public class ActionSequence {
    private static final Logger logger = LogManager.getLogger(ActionSequence.class);

    /**
     * All action sequences share this set of resource pools and reuse its
     * contents where possible. So, for example, there should only be one
     * resource pool of strip actions. Every action sequence that contains a
     * strip action then holds a reference to that pool.
     */
    private static final Map<Action, ResourcePool<Action>> pooledActions =
			new HashMap<>();

    /**
     * The input format that must be available for this sequence
     * to be applicable.
     */
    private final MetadataFormat inputFormat;

    /* The actions, in order. */
    private final List<ResourcePool<Action>> actions;

    /**
     * Create a new action sequence.
     * 
     * @param inputFormat acceptable format for harvesting the source
     * @param theActions sequence of actions to take, in order
     * @param resourcePoolSize the number of resources in the pool
     *
     */
    public ActionSequence(MetadataFormat inputFormat, Action[] theActions,
	    int resourcePoolSize) {
	this.inputFormat = inputFormat;

	actions = new ArrayList<>();

	for (Action act : theActions) {
	    actions.add(getPool(act, resourcePoolSize));
	}
    }

    /**
     * Get resource pool for this action (either previously initialized or
     * created on this call).
     */
    private synchronized static ResourcePool<Action> getPool(Action action,
	    int size) {
    	if (!pooledActions.containsKey(action)) {
	    Action[] acts = new Action[size];
	    for (int i = 0; i < size; i++) {
		acts[i] = action.clone();
	    }
	    ResourcePool<Action> pool = new ResourcePool<>(acts);
	    pooledActions.put(action, pool);
	}
	return pooledActions.get(action);
    }

    /**
     * Get the input format used in this sequence <br><br>
     *
     * @return the input format
     */
    public MetadataFormat getInputFormat() {
	return inputFormat;
    }

    public List<ResourcePool<Action>> getActions() {
        return actions;
    }
    
    
    /**
     * Perform the actions specified in the configuration<br><br>
     *
     * Note. This method does not return a value. The results of the actions
     * should be accessed using other actions within the sequence.
     *
     * @param metadata a metadata record
     */
    public void runActions(Record metadata) {
        this.runActions(new ArrayList<>(Arrays.asList(metadata)));
    }

    /**
     * Perform the actions specified in the configuration<br><br>
     *
     * Note. This method does not return a value. The results of the actions
     * should be accessed using other actions within the sequence.
     *
     * @param metadata a list of metadata records
     */
    public void runActions(List<Record> metadata) {

        // keep track of whether or not the action is the first in the sequence
        boolean firstAction = true;

        for (ResourcePool<Action> actPool : actions) {
                // claim an action in the pool
                Action action = actPool.get();

                boolean done = action.perform(metadata);
                actPool.release(action);
                if (!done) {
                        logger.error("Action " + action + " failed, terminating" +
                                        " sequence");
                        return;
                } else
                        logger.debug("Action " + action + " was performed");
                            
                actPool.release(action);
                
                if (firstAction)
                    firstAction = false;
        }
    }

    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder("read input (");
	sb.append(inputFormat);
	sb.append(")");
	if (actions != null) {
	    for (ResourcePool<Action> actPool : actions) {
		Action act = actPool.get();
		sb.append(" --> ").append(act);
		actPool.release(act);
	    }
	}
	return sb.toString();
    }
}
