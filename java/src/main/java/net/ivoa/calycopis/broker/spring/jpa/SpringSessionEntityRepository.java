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
 *     "timestamp": "2026-09-04T17:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 45,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;

/**
 * 
 */
@Repository
public interface SpringSessionEntityRepository
extends SpringAbstractEntityRepository<SimpleExecutionSessionEntity>
    {
    
    public Iterable<SimpleExecutionSessionEntity> findByPhase(final IvoaSimpleExecutionSessionPhase phase);

    /**
     * Select the currently active sessions and their linked compute resources.
     * Each returned row is an Object[] containing:
     * [0] the session UUID,
     * [1] the session phase,
     * [2] the compute resource UUID,
     * [3] the max offered cores,
     * [4] the max offered memory.
     *
     */
    @Query(
        """
        SELECT
            s.uuid,
            s.phase,
            c.uuid,
            c.maxofferedcores,
            c.maxofferedmemory
        FROM
            SimpleExecutionSessionEntity s,
            SimpleComputeResourceEntity c
        WHERE
            c.session = s
        AND
            s.phase IN :phases
        """
            )
    public List<Object[]> selectActiveSessionsWithCompute(
        @Param("phases") final List<IvoaSimpleExecutionSessionPhase> phases
        );

    }
