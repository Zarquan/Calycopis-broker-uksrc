/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
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
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.AbstractResourceOffer;

/**
 * A SimpleComputeResourcesOffer bean implementation.
 * 
 */
public interface SimpleComputeOffer
extends AbstractResourceOffer
    {
    /**
     * The minimum number of CPU cores.
     *  
     */
    public Long getMinCores();

    /**
     * The maximum number of CPU cores.
     *  
     */
    public Long getMaxCores();

    /**
     * The minimum amount of memory in GiB.
     *  
     */
    public Long getMinMemory();

    /**
     * The maximum amount of memory in GiB.
     *  
     */
    public Long getMaxMemory();

    /**
     * The compute pool this offer came from.
     *
     * The offer factory stamps this on every offer it returns, so the
     * reservation built from the offer records the same pool. It may be null
     * only on a row bean that has not passed through the factory.
     *
     */
    public AbstractComputeResourcePoolEntity getPool();

    }
