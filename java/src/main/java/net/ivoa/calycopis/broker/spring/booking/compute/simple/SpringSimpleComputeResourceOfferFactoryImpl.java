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
 *     "timestamp": "2026-05-30T12:07:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-11T11:15:00/2026-10-11T11:34:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 15,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-15T05:23:00/2026-10-15T05:27:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-15T05:50:00/2026-10-15T05:57:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.spring.booking.compute.simple;

import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceOfferFactoryImpl;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

/**
 * Spring-managed SimpleComputeResourceOfferFactory bean.
 *
 */
@Component
public class SpringSimpleComputeResourceOfferFactoryImpl
    extends SimpleComputeResourceOfferFactoryImpl
    {

    public SpringSimpleComputeResourceOfferFactoryImpl(
        final ProfilingEntryFactory profilingEntryFactory,
        final SimpleComputeResourceQueryHandler queryHandler,
        final BookingCapacitySettings bookingCapacitySettings,
        final TestingSettings testingSettings
        ){
        super(
            profilingEntryFactory,
            queryHandler,
            bookingCapacitySettings,
            testingSettings
            );
        }

    }
