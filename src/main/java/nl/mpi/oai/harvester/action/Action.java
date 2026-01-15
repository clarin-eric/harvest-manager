/*
 * Copyright (C) 2015, The Max Planck Institute for
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

import nl.mpi.oai.harvester.metadata.Metadata;

import java.util.List;
import nl.mpi.oai.harvester.metadata.Record;

/**
 * This class represents an action to be performed on a metadata record; for
 * example, transforming it to a different schema or saving it to a file.
 *
 * @author Lari Lampen (MPI-PL)
 */
public interface Action {
    /**
     * Perform the specified action on the given metadata records. This may or
     * may not involve modifying the record.
     * 
     * @param records list of metadata records
     * @return true on success; false if the action failed, leaving
     *         the records unchanged
     */
    boolean perform(List<Record> records);

    /**
     * Create a copy of this action. (This is used in preference to the
     * Cloneable interface due to its well-published design issues.)
     * 
     * @return Action
     */
    Action clone();
}
