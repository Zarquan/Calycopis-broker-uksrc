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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.simple;

import java.util.List;
import java.util.Optional;

import net.ivoa.calycopis.broker.engine.entities.component.AbstractEntityRepository;

/**
 * The persistence interface for compute pools, free of Spring.
 *
 * It follows the same split as the identity repository: the engine declares
 * what it needs, and spring/jpa/ supplies the Spring Data implementation.
 *
 */
public interface SimpleComputeResourcePoolEntityRepository
extends AbstractEntityRepository<SimpleComputeResourcePoolEntity>
    {
    /**
     * Find a pool by its name.
     *
     */
    public Optional<SimpleComputeResourcePoolEntity> findByName(final String name);

    /**
     * List every pool.
     *
     */
    public List<SimpleComputeResourcePoolEntity> findAll();

    }

