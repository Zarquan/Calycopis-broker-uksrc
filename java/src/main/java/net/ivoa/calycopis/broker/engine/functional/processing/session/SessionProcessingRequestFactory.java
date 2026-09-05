/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (C) 2025 University of Manchester.
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
 *     "timestamp": "2026-09-05T11:13:53",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.processing.session;

import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBase;

/**
 * 
 */
public interface SessionProcessingRequestFactory
extends FactoryBase
    {

    public SessionProcessingRequest  createPrepareSessionRequest(final SimpleExecutionSessionEntity session);

    public SessionProcessingRequest  createUpdateSessionRequest(final SimpleExecutionSessionEntity session);

    public SessionProcessingRequest  createReleaseSessionRequest(final SimpleExecutionSessionEntity session);

    public SessionProcessingRequest  createCancelSessionRequest(final SimpleExecutionSessionEntity session);

    public SessionProcessingRequest  createFailSessionRequest(final SimpleExecutionSessionEntity session);

    public SessionProcessingRequest  createExpireSessionRequest(final SimpleExecutionSessionEntity session);

    }
