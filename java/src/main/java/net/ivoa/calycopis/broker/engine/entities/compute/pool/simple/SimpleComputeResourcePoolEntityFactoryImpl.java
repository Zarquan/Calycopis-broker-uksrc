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
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;

/**
 * The framework-neutral implementation of the compute pool factory.
 *
 * Spring supplies the repository through a subclass in spring/pools/, the same
 * way the identity factory is wired.
 *
 */
@Slf4j
public class SimpleComputeResourcePoolEntityFactoryImpl
extends FactoryBaseImpl
implements SimpleComputeResourcePoolEntityFactory
    {
    protected final SimpleComputeResourcePoolEntityRepository repository;

    public SimpleComputeResourcePoolEntityFactoryImpl(
        final SimpleComputeResourcePoolEntityRepository repository
        ){
        super();
        this.repository = repository;
        }

    @Override
    public Optional<SimpleComputeResourcePoolEntity> select(
        final UUID uuid
        ){
        return this.repository.findById(
            uuid
            );
        }

    @Override
    public Optional<SimpleComputeResourcePoolEntity> findByName(
        final String name
        ){
        return this.repository.findByName(
            name
            );
        }

    @Override
    public List<SimpleComputeResourcePoolEntity> list()
        {
        return this.repository.findAll();
        }

    @Override
    public SimpleComputeResourcePoolEntity create(
        final String name,
        final String description
        ){
        log.debug(
            "Creating compute pool [{}]",
            name
            );
        return this.repository.save(
            new SimpleComputeResourcePoolEntity(
                name,
                description
                )
            );
        }

    }

