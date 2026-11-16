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
 *     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactoryImpl;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityRepository;

/**
 * Spring wiring for the compute pool factory.
 *
 * Delegates to SimpleComputeResourcePoolEntityFactoryImpl with the injected
 * repository, following the identity factory.
 *
 */
@Component
public class SpringSimpleComputeResourcePoolEntityFactoryImpl
extends SimpleComputeResourcePoolEntityFactoryImpl
    {
    public SpringSimpleComputeResourcePoolEntityFactoryImpl(
        final SimpleComputeResourcePoolEntityRepository repository
        ){
        super(
            repository
            );
        }

    }

