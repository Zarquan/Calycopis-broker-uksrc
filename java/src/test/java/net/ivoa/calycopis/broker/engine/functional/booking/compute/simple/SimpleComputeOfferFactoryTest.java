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
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.booking.compute.simple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.functional.testing.TestingSettings;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;

/**
 * Unit tests for the offer factory's pool selection and post-processing.
 *
 */
public class SimpleComputeOfferFactoryTest
    {

    /**
     * A test pool with an identifier, as a persisted pool would have.
     *
     */
    private static class TestPool
    extends SimpleComputeResourcePoolEntity
        {
        private TestPool()
            {
            super(
                "test-pool",
                "test-pool"
                );
            this.uuid = UUID.randomUUID();
            }
        }

    /**
     * The test pool every generated offer belongs to.
     *
     */
    private final TestPool pool = new TestPool();

    /**
     * A query handler that returns fixed offers, ignoring the SQL text.
     *
     */
    private static class StubQueryHandler
    implements SimpleComputeReservationQueryHandler
        {
        private final List<SimpleComputeOffer> offers;

        private StubQueryHandler(final List<SimpleComputeOffer> offers)
            {
            this.offers = offers;
            }

        @Override
        public UUID getUuid()
            {
            return UUID.randomUUID();
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query)
            {
            return this.offers;
            }

        @Override
        public Iterable<SimpleComputeOffer> query(final String query, final Map<String, Object> params)
            {
            return this.offers;
            }
        }

    /**
     * A pool factory that returns one test pool, or none when asked to.
     *
     */
    private static class StubPoolFactory
    implements SimpleComputeResourcePoolEntityFactory
        {
        private final SimpleComputeResourcePoolEntity pool;
        private final boolean found;

        private StubPoolFactory(final SimpleComputeResourcePoolEntity pool, final boolean found)
            {
            this.pool = pool;
            this.found = found;
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> findByName(final String name)
            {
            if (!this.found)
                {
                return Optional.empty();
                }
            if (!AbstractComputeResourcePool.DEFAULT_POOL_NAME.equals(name))
                {
                return Optional.empty();
                }
            return Optional.of(this.pool);
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> list()
            {
            List<SimpleComputeResourcePoolEntity> pools = new ArrayList<SimpleComputeResourcePoolEntity>();
            if (this.found)
                {
                pools.add(this.pool);
                }
            return pools;
            }

        @Override
        public SimpleComputeResourcePoolEntity create(final String name, final String description)
            {
            throw new UnsupportedOperationException(
                "The stub pool factory does not create pools"
                );
            }
        }

    /**
     * A profiling entry that records enough for the factory to use it.
     *
     */
    private static class StubProfilingEntry
    implements ProfilingEntry
        {
        private final String className;
        private final String methodName;
        private Instant startTime = Instant.now();
        private Instant endTime = null;

        private StubProfilingEntry(final String className, final String methodName)
            {
            this.className = className;
            this.methodName = methodName;
            }

        @Override
        public String getClassName()
            {
            return this.className;
            }

        @Override
        public String getMethodName()
            {
            return this.methodName;
            }

        @Override
        public Instant getStartTime()
            {
            return this.startTime;
            }

        @Override
        public Instant getEndTime()
            {
            return this.endTime;
            }

        @Override
        public void start()
            {
            this.startTime = Instant.now();
            }

        @Override
        public void end()
            {
            this.endTime = Instant.now();
            }
        }

    /**
     * A profiling factory that creates stub entries.
     *
     */
    private static class StubProfilingEntryFactory
    implements ProfilingEntryFactory
        {
        @Override
        public UUID getUuid()
            {
            return UUID.randomUUID();
            }

        @Override
        public ProfilingEntry create(final String className, final String methodName)
            {
            return new StubProfilingEntry(className, methodName);
            }
        }

    /**
     * A profiling target that collects the entries it is given.
     *
     */
    private static class StubProfilingTarget
    implements ProfilingTarget
        {
        private final List<ProfilingEntry> entries = new ArrayList<ProfilingEntry>();

        @Override
        public void addProfilingEntry(final ProfilingEntry entry)
            {
            this.entries.add(entry);
            }

        @Override
        public List<ProfilingEntry> getProfilingEntries()
            {
            return this.entries;
            }
        }

    /**
     * A testing-settings stub with the mechanism switched off.
     *
     */
    private static class StubTestingSettings
    implements TestingSettings
        {
        @Override
        public boolean isEnabled()
            {
            return false;
            }

        @Override
        public long getBookingQueryToSaveDelaySeconds()
            {
            return 0L;
            }
        }

    /**
     * Build a raw offer from the query handler, without a pool.
     *
     */
    private SimpleComputeOffer makeQueryOffer(final Instant start)
        {
        return new SimpleComputeOfferBean(
            "offer-0",
            Interval.of(
                start,
                Duration.ofSeconds(0)
                ),
            Duration.ofSeconds(360L),
            1L,
            1L
            );
        }

    /**
     * Generate offers through the factory and return them as a list.
     *
     */
    private List<SimpleComputeOffer> generate(
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final List<SimpleComputeOffer> queryOffers
        ){
        SimpleComputeOfferFactoryImpl factory = new SimpleComputeOfferFactoryImpl(
            new StubProfilingEntryFactory(),
            new StubQueryHandler(queryOffers),
            poolFactory,
            new StubTestingSettings()
            );
        OfferSetRequestParserContextImpl request = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            null
            );
        request.setStartInterval(
            Interval.of(
                Instant.now().plusSeconds(3600L),
                Duration.ofSeconds(86400L)
                )
            );
        request.setExecutionDuration(
            Duration.ofSeconds(300L)
            );
        request.addMinCores(1L);
        request.addMinMemory(1L);

        Iterable<SimpleComputeOffer> generated = factory.generate(
            request,
            10,
            new StubProfilingTarget()
            );
        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer offer : generated)
            {
            offers.add(offer);
            }
        return offers;
        }

    /**
     * Every offer returned by the factory carries the resolved default pool.
     *
     */
    @Test
    public void testGenerateUsesTheDefaultPool()
        {
        Instant start = Instant.now().plusSeconds(7200L);
        List<SimpleComputeOffer> queryOffers = new ArrayList<SimpleComputeOffer>();
        queryOffers.add(
            makeQueryOffer(start)
            );
        queryOffers.add(
            makeQueryOffer(start.plusSeconds(720L))
            );

        List<SimpleComputeOffer> offers = generate(
            new StubPoolFactory(this.pool, true),
            queryOffers
            );

        Assertions.assertEquals(
            2,
            offers.size(),
            "The factory must return both query offers"
            );
        for (SimpleComputeOffer offer : offers)
            {
            Assertions.assertSame(
                this.pool,
                offer.getPool(),
                "Every generated offer must carry the resolved pool"
                );
            }
        }

    /**
     * A missing default pool still yields no offers.
     *
     */
    @Test
    public void testMissingPoolYieldsNoOffers()
        {
        List<SimpleComputeOffer> queryOffers = new ArrayList<SimpleComputeOffer>();
        queryOffers.add(
            makeQueryOffer(Instant.now().plusSeconds(7200L))
            );

        List<SimpleComputeOffer> offers = generate(
            new StubPoolFactory(this.pool, false),
            queryOffers
            );

        Assertions.assertTrue(
            offers.isEmpty(),
            "A missing default pool must yield no offers"
            );
        }
    }
