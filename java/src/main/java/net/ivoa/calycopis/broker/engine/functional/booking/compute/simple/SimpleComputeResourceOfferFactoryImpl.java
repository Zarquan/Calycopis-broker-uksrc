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
 *     "name": "ChatGPT",
 *     "contribution": {
 *       "value": 90,
 *       "units": "%"
 *       }
*     },
 *     {
 *     "interval": "2026-10-11T11:15:00/2026-10-11T11:34:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 30,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;

import org.threeten.extra.Interval;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;

/**
 *
 *
 */
@Slf4j
public class SimpleComputeResourceOfferFactoryImpl
extends FactoryBaseImpl
implements SimpleComputeResourceOfferFactory
    {

    private final ProfilingEntryFactory profilingEntryFactory;

    private final SimpleComputeResourceQueryHandler queryHandler ;
    
    /**
     * Public constructor.
     *
     */
    public SimpleComputeResourceOfferFactoryImpl(
        final ProfilingEntryFactory profilingEntryFactory,
        final SimpleComputeResourceQueryHandler queryHandler
        ){
        this.profilingEntryFactory = profilingEntryFactory;
        this.queryHandler = queryHandler ;
        }

    @Override
    public Iterable<SimpleComputeResourceOffer> generate(Interval requestStart, Duration requestDuration, Long requestMinCores, Long requestMemory, int requestLimit, ProfilingTarget target)
        {
        //
        // Start profiling this method.
        ProfilingEntry profiling = profilingEntryFactory.create(
            this.getClass().getName(),
            "generate"
            );
        target.addProfilingEntry(
            profiling
            );

        String query = SimpleComputeResourceQuery.build(
            requestStart,
            requestDuration,
            requestMinCores,
            requestMemory,
            requestLimit
            );
        
        Iterable<SimpleComputeResourceOffer> offers = queryHandler.query(
            query
            );

        //
        // Stop profiling this method.
        profiling.end();

        return offers;
        }
    }
