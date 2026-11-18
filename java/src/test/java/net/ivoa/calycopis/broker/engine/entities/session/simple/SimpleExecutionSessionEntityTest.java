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
 *     "interval": "2026-11-17T17:55:00/2026-11-17T20:30:00",
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

package net.ivoa.calycopis.broker.engine.entities.session.simple;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;

/**
 * Unit tests for the session live-phase predicate.
 *
 */
public class SimpleExecutionSessionEntityTest
    {

    /**
     * The phases that hold capacity are live.
     *
     */
    @Test
    public void testLivePhases()
        {
        IvoaSimpleExecutionSessionPhase[] live = {
            IvoaSimpleExecutionSessionPhase.OFFERED,
            IvoaSimpleExecutionSessionPhase.ACCEPTED,
            IvoaSimpleExecutionSessionPhase.WAITING,
            IvoaSimpleExecutionSessionPhase.PREPARING,
            IvoaSimpleExecutionSessionPhase.AVAILABLE,
            IvoaSimpleExecutionSessionPhase.RUNNING,
            IvoaSimpleExecutionSessionPhase.RELEASING
            };
        for (IvoaSimpleExecutionSessionPhase phase : live)
            {
            Assertions.assertTrue(
                SimpleExecutionSessionEntity.isLive(phase),
                "The phase " + phase + " must be live"
                );
            }
        }

    /**
     * INITIAL and the terminal phases do not hold capacity.
     *
     */
    @Test
    public void testNonLivePhases()
        {
        IvoaSimpleExecutionSessionPhase[] nonLive = {
            IvoaSimpleExecutionSessionPhase.INITIAL,
            IvoaSimpleExecutionSessionPhase.REJECTED,
            IvoaSimpleExecutionSessionPhase.EXPIRED,
            IvoaSimpleExecutionSessionPhase.COMPLETED,
            IvoaSimpleExecutionSessionPhase.FAILED,
            IvoaSimpleExecutionSessionPhase.CANCELLED
            };
        for (IvoaSimpleExecutionSessionPhase phase : nonLive)
            {
            Assertions.assertFalse(
                SimpleExecutionSessionEntity.isLive(phase),
                "The phase " + phase + " must not be live"
                );
            }
        Assertions.assertFalse(
            SimpleExecutionSessionEntity.isLive(null),
            "A null phase must not be live"
            );
        }
    }
