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
 *     },
 *     {
 *     "timestamp": "2026-11-14T13:32:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
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
 *     },
 *     {
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
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
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;

/**
 *
 *
 */
@Slf4j
public class SimpleComputeOfferFactoryImpl
extends FactoryBaseImpl
implements SimpleComputeOfferFactory
    {

    private final ProfilingEntryFactory profilingEntryFactory;

    private final SimpleComputeReservationQueryHandler queryHandler ;

    private final SimpleComputeResourcePoolEntityFactory poolFactory ;

    private final TestingSettings testingSettings ;

    /**
     * Public constructor.
     *
     */
    public SimpleComputeOfferFactoryImpl(
        final ProfilingEntryFactory profilingEntryFactory,
        final SimpleComputeReservationQueryHandler queryHandler,
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final TestingSettings testingSettings
        ){
        this.profilingEntryFactory = profilingEntryFactory;
        this.queryHandler = queryHandler ;
        this.poolFactory = poolFactory ;
        this.testingSettings = testingSettings ;
        }

    @Override
    public Iterable<SimpleComputeOffer> generate(
        final OfferSetRequestParserContext request,
        final int requestLimit,
        final ProfilingTarget target
        ){
        //
        // Start profiling this method.
        ProfilingEntry profiling = profilingEntryFactory.create(
            this.getClass().getName(),
            "generate"
            );
        target.addProfilingEntry(
            profiling
            );

        //
        // The new offer's own preparation is part of its reservation, so the
        // booking query has to hold that time before the offered available
        // window. Use the maximum preparation across the compute validator
        // results, because the compute resources prepare in parallel and the
        // session's preparation is finished when the slowest one is ready.
        // This is the same value the compute resource entity stores, rather
        // than the session total, which also includes the executable, data
        // and storage preparation. See issue #158.
        //
        long prepareSeconds = computePrepareSeconds(
            request
            );

        //
        // Reserve the whole of the new offer's reservation, not just its
        // available window. The release tail is fixed by the compute resource
        // entity, so the booking query and the stored windows agree.
        //
        long reserveAfterSeconds = AbstractComputeReservationEntity.RELEASE_START_GAP_SECONDS
            + AbstractComputeReservationEntity.RELEASE_DURATION_SECONDS;

        log.debug(
            "Booking query reserving [{}]s before and [{}]s after the available window",
            prepareSeconds,
            reserveAfterSeconds
            );

        //
        // Resolve the pools the request path books against, in offer order.
        //
        List<SimpleComputeResourcePoolEntity> pools = this.poolFactory.listEnabled();
        if (pools.isEmpty())
            {
            log.warn(
                "No enabled compute pools, declining the booking query [{}]",
                "urn:no-enabled-compute-pools"
                );
            request.addWarning(
                "urn:no-enabled-compute-pools",
                "The broker has no enabled compute pools, so no offers can be made."
                );
            profiling.end();
            return new ArrayList<SimpleComputeOffer>();
            }

        int effectiveLimit = requestLimit;
        if (effectiveLimit <= 0)
            {
            effectiveLimit = SimpleComputeReservationQuery.QUERY_LIMIT;
            }

        SimpleComputeResourceCriteria criteria = new SimpleComputeResourceCriteriaBean(
            request,
            primaryComputeResult(request),
            effectiveLimit,
            prepareSeconds
            );

        List<SimpleComputeOffer> poolOffers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeResourcePoolEntity pool : pools)
            {
            Iterable<SimpleComputeOffer> offers = pool.makeOffers(
                criteria,
                queryHandler
                );
            for (SimpleComputeOffer offer : offers)
                {
                poolOffers.add(
                    offer
                    );
                }
            }

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
        Instant rangeStart = SimpleComputeReservationQuery.getRangeStartInstant(
            request.getStartInterval()
            );

        Instant windowEnd = null;
        if (request.getStartInterval() != null)
            {
            windowEnd = request.getStartInterval().getEnd();
            }

        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer poolOffer : poolOffers)
            {
            //
            // An offer whose whole window is in the past cannot be used.
            //
            if (SimpleComputeOfferBean.hasEnded(poolOffer, rangeStart))
                {
                log.warn(
                    "Dropping an offer whose availability window has already ended [{}][{}]",
                    poolOffer.getOfferStartInstant(),
                    poolOffer.getOfferDuration()
                    );
                continue;
                }
            //
            // The requested start interval is the window the client will accept
            // a start in. The query searches far enough ahead to find a free run
            // long enough for the requested duration, so an offer can start
            // later than the window allows and has to be dropped here.
            //
            SimpleComputeOfferBean clamped = SimpleComputeOfferBean.clamp(
                poolOffer,
                rangeStart
                );
            if ((windowEnd != null) && SimpleComputeOfferBean.startsOutsideWindow(clamped, windowEnd))
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
        // Offer in start order, and cap the merged set at the effective request
        // limit. Each pool returns up to its own limit, so the merged set can
        // be larger than the request allows.
        //
        offers.sort(
            Comparator.comparing(
                SimpleComputeOffer::getOfferStartInstant
                )
            );
        if (offers.size() > effectiveLimit)
            {
            offers = new ArrayList<SimpleComputeOffer>(
                offers.subList(
                    0,
                    effectiveLimit
                    )
                );
            }

        //
        // Stop profiling this method.
        profiling.end();

        return offers;
        }

    /**
     * Get the maximum preparation duration across the compute validator results.
     *
     */
    private long computePrepareSeconds(final OfferSetRequestParserContext request)
        {
        long prepareSeconds = 0L;
        for (AbstractComputeReservationValidator.Result computeResult : request.getComputeValidatorResults())
            {
            Long resultPrepareSeconds = computeResult.getPrepareDuration();
            if ((resultPrepareSeconds != null) && (resultPrepareSeconds > prepareSeconds))
                {
                prepareSeconds = resultPrepareSeconds;
                }
            }
        log.debug("Compute prepare [{}]", prepareSeconds);
        return prepareSeconds;
        }

    /**
     * Get the first compute validator result, the resource being bid for.
     *
     */
    private AbstractComputeReservationValidator.Result primaryComputeResult(final OfferSetRequestParserContext request)
        {
        List<AbstractComputeReservationValidator.Result> results = request.getComputeValidatorResults();
        if ((results == null) || results.isEmpty())
            {
            return null;
            }
        return results.get(0);
        }
    }
