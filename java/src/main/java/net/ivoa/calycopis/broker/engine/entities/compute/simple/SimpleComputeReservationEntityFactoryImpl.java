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
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 15,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-16T05:14:00/2026-11-16T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.simple;

import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.AbstractEntityRepository;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntityFactoryImpl;

/**
 * A SimpleComputeReservationEntity Factory implementation.
 *
 */
@Slf4j
public abstract class SimpleComputeReservationEntityFactoryImpl
extends AbstractComputeReservationEntityFactoryImpl
implements SimpleComputeReservationEntityFactory
    {

    /**
     * Protected constructor.
     *
     */
    protected SimpleComputeReservationEntityFactoryImpl(
        final AbstractEntityRepository<AbstractComputeReservationEntity> repository
        ){
        super(
            repository
            );
        }
    
    @Override
    public URI getKind()
        {
        return SimpleComputeReservation.KIND_DISCRIMINATOR;
        }
    }

