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
 *     },
 *     {
 *     "interval": "2026-10-13T10:19:00/2026-10-13T10:35:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 25,
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
 *       "value": 15,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-15T10:30:00/2026-10-15T17:00:00",
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
package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.threeten.extra.Interval;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

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

    private final BookingCapacitySettings bookingCapacitySettings ;

    private final TestingSettings testingSettings ;

    /**
     * Public constructor.
     *
     */
    public SimpleComputeResourceOfferFactoryImpl(
        final ProfilingEntryFactory profilingEntryFactory,
        final SimpleComputeResourceQueryHandler queryHandler,
        final BookingCapacitySettings bookingCapacitySettings,
        final TestingSettings testingSettings
        ){
        this.profilingEntryFactory = profilingEntryFactory;
        this.queryHandler = queryHandler ;
        this.bookingCapacitySettings = bookingCapacitySettings ;
        this.testingSettings = testingSettings ;
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
            requestLimit,
            bookingCapacitySettings.getTotalAvailableCpuCores(),
            bookingCapacitySettings.getTotalAvailableCpuMemory()
            );
        
        Iterable<SimpleComputeResourceOffer> queryOffers = queryHandler.query(
            query
            );

        //
        // If the testing mechanism is enabled, hold the window between the
        // booking query and the offer save open for the configured time.
        //
        // The offer set is saved at the end of OfferSetEntityFactoryImpl.create,
        // after stageTwo has run this query and built the sessions, so a second
        // request that arrives during this delay can reach its own query before
        // the first request's reservations are committed. That is the atomicity
        // race in #156, and this delay is what makes it reproducible.
        //
        if (testingSettings.isEnabled())
            {
            long delaySeconds = testingSettings.getBookingQueryToSaveDelaySeconds();
            if (delaySeconds > 0)
                {
                log.warn(
                    "Testing delay, holding the booking query to offer save window for [{}] seconds",
                    delaySeconds
                    );
                try {
                    Thread.sleep(
                        Duration.ofSeconds(
                            delaySeconds
                            ).toMillis()
                        );
                    }
                catch (InterruptedException ouch)
                    {
                    Thread.currentThread().interrupt();
                    log.error(
                        "Testing delay interrupted [{}][{}]",
                        ouch.getClass().getSimpleName(),
                        ouch.getMessage()
                        );
                    }
                }
            }

        //
        // Post-process the offers, moving a start time that is before the
        // requested start time forward to the requested start time, dropping an
        // offer whose window has already ended, and dropping an offer that
        // starts outside the window the client asked for.
        //
        Instant rangeStart = SimpleComputeResourceQuery.getRangeStartInstant(
            requestStart
            );

        Instant windowEnd = null;
        if (requestStart != null)
            {
            windowEnd = requestStart.getEnd();
            }

        List<SimpleComputeResourceOffer> offers = new ArrayList<SimpleComputeResourceOffer>();
        for (SimpleComputeResourceOffer queryOffer : queryOffers)
            {
            //
            // An offer whose whole window is in the past cannot be used.
            //
            if (SimpleComputeResourceOfferBean.hasEnded(queryOffer, rangeStart))
                {
                log.warn(
                    "Dropping an offer whose availability window has already ended [{}][{}]",
                    queryOffer.getOfferStartInstant(),
                    queryOffer.getOfferDuration()
                    );
                continue;
                }
            //
            // The requested start interval is the window the client will accept
            // a start in. The query searches far enough ahead to find a free run
            // long enough for the requested duration, so an offer can start
            // later than the window allows and has to be dropped here.
            //
            SimpleComputeResourceOfferBean clamped = SimpleComputeResourceOfferBean.clamp(
                queryOffer,
                rangeStart
                );
            if ((windowEnd != null) && SimpleComputeResourceOfferBean.startsOutsideWindow(clamped, windowEnd))
                {
                log.debug(
                    "Dropping an offer that starts outside the requested start window [{}][{}]",
                    clamped.getOfferStartInstant(),
                    windowEnd
                    );
                continue;
                }
            offers.add(
                clamped
                );
            }

        //
        // Stop profiling this method.
        profiling.end();

        return offers;
        }
    }
