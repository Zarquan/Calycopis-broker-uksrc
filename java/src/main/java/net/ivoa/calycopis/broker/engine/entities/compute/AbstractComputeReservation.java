/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (C) 2026 University of Manchester.
 *
 *     This information is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This information is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-16T05:14:00/2026-11-16T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute;

import java.net.URI;

import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponent;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMount;

/**
 * 
 */
public interface AbstractComputeReservation
    extends LifecycleComponent
    {

    /**
     * The webapp path for compute resources.
     * 
     */
    public static final URI WEBAPP_PATH = URI.create("compute/"); 

    /**
     * Get the compute pool this reservation was made from.
     *
     * Every reservation records its pool, including one that is still only
     * offered. The pool is fixed when the offer is made and cannot be changed.
     *
     */
    public AbstractComputeResourcePool getPool();

    /**
     * Get the list of volume mounts attached to this resource.
     *
     */
    public Iterable<AbstractVolumeMount> getVolumeMounts();

    }
