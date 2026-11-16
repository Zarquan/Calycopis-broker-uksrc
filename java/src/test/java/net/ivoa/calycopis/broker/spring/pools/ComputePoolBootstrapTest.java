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
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;

/**
 * Tests for the startup seed.
 *
 * The factory here is a stand-in that remembers the pools it holds by name,
 * which is enough to check the two properties that matter: a second run creates
 * nothing, and a deployment that declares no pools still gets the default.
 *
 */
public class ComputePoolBootstrapTest
    {

    /**
     * Running the seed twice must leave one pool, not two.
     *
     */
    @Test
    public void testSeedIsIdempotent() throws Exception
        {
        StubFactory factory = new StubFactory();
        ComputePoolBootstrap bootstrap = new ComputePoolBootstrap(
            settingsOf(
                List.<ComputePoolSpec>of()
                ),
            factory
            );

        bootstrap.run(null);
        bootstrap.run(null);

        Assertions.assertEquals(
            1,
            factory.created.size(),
            "Running the seed twice must create one pool"
            );
        Assertions.assertEquals(
            ComputePoolBootstrap.DEFAULT_POOL_NAME,
            factory.created.get(0),
            "The pool created must be the default pool"
            );
        }

    /**
     * A configured list is seeded, and no default pool is added to it.
     *
     */
    @Test
    public void testSeedUsesConfiguredPools() throws Exception
        {
        StubFactory factory = new StubFactory();
        ComputePoolBootstrap bootstrap = new ComputePoolBootstrap(
            settingsOf(
                List.of(
                    specOf("alpha"),
                    specOf("beta")
                    )
                ),
            factory
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha", "beta"),
            factory.created,
            "The configured pools must be created in order"
            );
        }

    /**
     * A pool without a name is skipped rather than stopping the broker.
     *
     */
    @Test
    public void testSeedSkipsANamelessPool() throws Exception
        {
        StubFactory factory = new StubFactory();
        ComputePoolBootstrap bootstrap = new ComputePoolBootstrap(
            settingsOf(
                List.of(
                    specOf("alpha"),
                    specOf("  ")
                    )
                ),
            factory
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            List.of("alpha"),
            factory.created,
            "A pool with no name must be skipped"
            );
        }

    private static ComputePoolSpec specOf(final String name)
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(name);
        spec.setDescription("A test pool.");
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

    /**
     * A factory that behaves like the real one, without a database.
     *
     */
    static class StubFactory
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
    }
