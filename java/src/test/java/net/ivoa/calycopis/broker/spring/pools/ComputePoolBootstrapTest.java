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
 *     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
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
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Tests for the startup seed.
 *
 * The factories here are stand-ins that remember what they hold by name, which
 * is enough to check the properties that matter: a second run creates nothing,
 * the default pool is always seeded, and a pool without allocations gets one
 * from the configured capacity.
 *
 */
public class ComputePoolBootstrapTest
    {

    /**
     * Running the seed twice must leave one pool and one allocation, not two.
     *
     */
    @Test
    public void testSeedIsIdempotent() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.<ComputePoolSpec>of(),
            pools,
            allocations
            );

        bootstrap.run(null);
        bootstrap.run(null);

        Assertions.assertEquals(
            List.of(AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "Running the seed twice must create one pool"
            );
        Assertions.assertEquals(
            1,
            allocations.created.size(),
            "Running the seed twice must create one allocation"
            );
        Assertions.assertEquals(
            256L,
            allocations.created.get(0).cores,
            "The fallback allocation must use the configured cores"
            );
        Assertions.assertEquals(
            512L,
            allocations.created.get(0).memory,
            "The fallback allocation must use the configured memory"
            );
        Assertions.assertEquals(
            Instant.EPOCH,
            allocations.created.get(0).start,
            "The fallback allocation must start at the epoch"
            );
        Assertions.assertNull(
            allocations.created.get(0).end,
            "The fallback allocation must be open ended"
            );
        }

    /**
     * A configured list is seeded, and the default pool is added to it.
     *
     */
    @Test
    public void testSeedUsesConfiguredPoolsAndAddsTheDefault() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specOf("alpha"),
                specOf("beta")
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", "beta", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "The configured pools must be created in order, followed by the default"
            );
        Assertions.assertEquals(
            3,
            allocations.created.size(),
            "Every seeded pool must get an allocation"
            );
        }

    /**
     * A pool without a name is skipped rather than stopping the broker.
     *
     */
    @Test
    public void testSeedSkipsANamelessPool() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specOf("alpha"),
                specOf("  ")
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "A pool with no name must be skipped"
            );
        }

    /**
     * Declared allocations are seeded instead of the fallback.
     *
     */
    @Test
    public void testSeedUsesDeclaredAllocations() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specWithAllocation(
                    "alpha",
                    8L,
                    16L,
                    Instant.parse("2026-12-01T00:00:00Z"),
                    Instant.parse("2026-12-02T00:00:00Z")
                    )
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            2,
            allocations.created.size(),
            "The declared allocation and the default fallback must both be created"
            );
        AssertionRecord alpha = allocations.created.get(0);
        Assertions.assertEquals("alpha", alpha.pool);
        Assertions.assertEquals(8L, alpha.cores);
        Assertions.assertEquals(16L, alpha.memory);
        Assertions.assertEquals(
            Instant.parse("2026-12-01T00:00:00Z"),
            alpha.start
            );
        Assertions.assertEquals(
            Instant.parse("2026-12-02T00:00:00Z"),
            alpha.end
            );
        }

    /**
     * An allocation with no positive size is skipped, and the pool is kept.
     *
     */
    @Test
    public void testSeedSkipsAnInvalidAllocation() throws Exception
        {
        StubPoolFactory pools = new StubPoolFactory();
        StubAllocationFactory allocations = new StubAllocationFactory();
        ComputePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                specWithAllocation(
                    "alpha",
                    0L,
                    16L,
                    null,
                    null
                    )
                ),
            pools,
            allocations
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", AbstractComputeResourcePool.DEFAULT_POOL_NAME),
            pools.created,
            "The pool must still be created"
            );
        Assertions.assertEquals(
            1,
            allocations.created.size(),
            "Only the default pool's fallback allocation must be created"
            );
        Assertions.assertEquals(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME,
            allocations.created.get(0).pool,
            "The fallback allocation belongs to the default pool"
            );
        }

    private static ComputePoolBootstrap bootstrapOf(
        final List<ComputePoolSpec> specs,
        final SimpleComputeResourcePoolEntityFactory pools,
        final SimpleComputeAllocationEntityFactory allocations
        ){
        return new ComputePoolBootstrap(
            settingsOf(
                specs
                ),
            pools,
            allocations,
            capacityOf()
            );
        }

    private static ComputePoolSpec specOf(final String name)
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(name);
        spec.setDescription("A test pool.");
        return spec;
        }

    private static ComputePoolSpec specWithAllocation(
        final String name,
        final Long cores,
        final Long memory,
        final Instant start,
        final Instant end
        ){
        ComputePoolSpec spec = specOf(
            name
            );
        ComputeAllocationSpec allocation = new ComputeAllocationSpec();
        allocation.setCores(cores);
        allocation.setMemory(memory);
        allocation.setStart(start);
        allocation.setEnd(end);
        spec.setAllocations(
            List.of(allocation)
            );
        return spec;
        }

    private static ComputePoolsSettings settingsOf(final List<ComputePoolSpec> specs)
        {
        return new ComputePoolsSettings()
            {
            @Override
            public List<ComputePoolSpec> getCompute()
                {
                return specs;
                }
            };
        }

    private static BookingCapacitySettings capacityOf()
        {
        return new BookingCapacitySettings()
            {
            @Override
            public long getTotalAvailableCpuCores()
                {
                return 256L;
                }

            @Override
            public long getTotalAvailableCpuMemory()
                {
                return 512L;
                }
            };
        }

    /**
     * A factory that behaves like the real pool factory, without a database.
     *
     */
    static class StubPoolFactory
    implements SimpleComputeResourcePoolEntityFactory
        {
        final Map<String, SimpleComputeResourcePoolEntity> pools = new LinkedHashMap<>();
        final List<String> created = new ArrayList<>();

        @Override
        public Optional<SimpleComputeResourcePoolEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public Optional<SimpleComputeResourcePoolEntity> findByName(final String name)
            {
            return Optional.ofNullable(
                this.pools.get(name)
                );
            }

        @Override
        public List<SimpleComputeResourcePoolEntity> list()
            {
            return new ArrayList<>(
                this.pools.values()
                );
            }

        @Override
        public SimpleComputeResourcePoolEntity create(final String name, final String description)
            {
            SimpleComputeResourcePoolEntity entity = new SimpleComputeResourcePoolEntity(
                name,
                description
                );
            this.pools.put(
                name,
                entity
                );
            this.created.add(
                name
                );
            return entity;
            }
        }

    /**
     * A factory that behaves like the real allocation factory, without a
     * database.
     *
     */
    static class StubAllocationFactory
    implements SimpleComputeAllocationEntityFactory
        {
        final List<AssertionRecord> created = new ArrayList<>();

        @Override
        public Optional<SimpleComputeAllocationEntity> select(final UUID uuid)
            {
            return Optional.empty();
            }

        @Override
        public List<SimpleComputeAllocationEntity> list(final AbstractComputeResourcePoolEntity pool)
            {
            return new ArrayList<>();
            }

        @Override
        public SimpleComputeAllocationEntity create(
            final AbstractComputeResourcePoolEntity pool,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            this.created.add(
                new AssertionRecord(
                    pool.getName(),
                    cores,
                    memory,
                    start,
                    end
                    )
                );
            return new SimpleComputeAllocationEntity(
                pool,
                cores,
                memory,
                start,
                end
                );
            }

        @Override
        public SimpleComputeAllocationEntity update(
            final SimpleComputeAllocationEntity entity,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            entity.update(
                cores,
                memory,
                start,
                end
                );
            return entity;
            }

        @Override
        public void delete(final SimpleComputeAllocationEntity entity)
            {
            }

        @Override
        public boolean wouldOverCommit(
            final UUID poolUuid,
            final UUID excludedUuid,
            final long cores,
            final long memory,
            final Instant start,
            final Instant end
            ){
            return false;
            }
        }

    /**
     * What the stub allocation factory remembers about one creation.
     *
     */
    record AssertionRecord(
        String pool,
        long cores,
        long memory,
        Instant start,
        Instant end
        )
        {
        }
    }
