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
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-18T13:45:00/2026-11-18T17:00:00",
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityRepository;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyKind;

/**
 * The Spring-backed factory for compute pool policies.
 *
 */
@Component
public class SimpleComputePoolPolicyEntityFactoryImpl
implements SimpleComputePoolPolicyEntityFactory
    {
    private final SimpleComputePoolPolicyEntityRepository repository;

    public SimpleComputePoolPolicyEntityFactoryImpl(
        final SimpleComputePoolPolicyEntityRepository repository
        ){
        this.repository = repository;
        }

    @Override
    public Optional<SimpleComputePoolPolicyEntity> select(final UUID uuid)
        {
        return this.repository.findById(
            uuid
            );
        }

    @Override
    public List<SimpleComputePoolPolicyEntity> list(
        final AbstractComputeResourcePoolEntity pool
        ){
        return this.repository.findByPoolOrderByCreatedAscUuidAsc(
            pool
            );
        }

    @Override
    public Optional<SimpleComputePoolPolicyEntity> findByName(
        final AbstractComputeResourcePoolEntity pool,
        final String name
        ){
        return this.repository.findByPoolAndName(
            pool,
            name
            );
        }

    @Override
    public SimpleComputePoolPolicyEntity create(
        final AbstractComputeResourcePoolEntity pool,
        final SimpleComputePoolPolicyKind kind,
        final String name,
        final Long minimumMemory,
        final Long maximumMemory,
        final String username,
        final Integer startHour,
        final Integer endHour
        ){
        SimpleComputePoolPolicyEntity entity = new SimpleComputePoolPolicyEntity(
            pool,
            kind,
            name,
            minimumMemory,
            maximumMemory,
            username,
            startHour,
            endHour
            );
        return this.repository.save(
            entity
            );
        }

    @Override
    public SimpleComputePoolPolicyEntity update(
        final SimpleComputePoolPolicyEntity entity,
        final SimpleComputePoolPolicyKind kind,
        final String name,
        final Long minimumMemory,
        final Long maximumMemory,
        final String username,
        final Integer startHour,
        final Integer endHour
        ){
        entity.update(
            kind,
            name,
            minimumMemory,
            maximumMemory,
            username,
            startHour,
            endHour
            );
        return this.repository.save(
            entity
            );
        }

    @Override
    public void delete(final SimpleComputePoolPolicyEntity entity)
        {
        this.repository.delete(
            entity
            );
        }
    }
