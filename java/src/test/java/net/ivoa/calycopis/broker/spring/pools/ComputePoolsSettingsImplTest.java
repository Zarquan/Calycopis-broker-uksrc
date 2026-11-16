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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for the compute pool settings and the pool the bootstrap falls back to.
 *
 * The binding test covers the path from the property names in pools.yaml to the
 * settings bean. The resolve tests cover the rule that matters when a deployment
 * says nothing: one pool, called default.
 *
 */
public class ComputePoolsSettingsImplTest
    {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            PoolsSettingsConfiguration.class
            );

    /**
     * With no configuration the bean must report no pools, because an empty
     * list is what makes the bootstrap supply the default.
     *
     */
    @Test
    public void testDefaultsToNoConfiguredPools()
        {
        contextRunner.run(
            context -> {
                ComputePoolsSettings settings = context.getBean(
                    ComputePoolsSettings.class
                    );
                Assertions.assertNotNull(
                    settings.getCompute(),
                    "The configured pools must not be null"
                    );
                Assertions.assertTrue(
                    settings.getCompute().isEmpty(),
                    "The configured pools must be empty by default"
                    );
                }
            );
        }

    /**
     * A configured pool must reach the bean with its name and description.
     *
     */
    @Test
    public void testBindsConfiguredPools()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.pools.compute[0].name=alpha",
                "calycopis.broker.pools.compute[0].description=The alpha pool."
                )
            .run(
                context -> {
                    ComputePoolsSettings settings = context.getBean(
                        ComputePoolsSettings.class
                        );
                    Assertions.assertEquals(
                        1,
                        settings.getCompute().size(),
                        "One configured pool must reach the settings bean"
                        );
                    ComputePoolSpec spec = settings.getCompute().get(0);
                    Assertions.assertEquals(
                        "alpha",
                        spec.getName(),
                        "The configured pool name must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        "The alpha pool.",
                        spec.getDescription(),
                        "The configured pool description must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * No configuration means one pool, and that pool is the default.
     *
     */
    @Test
    public void testResolveSuppliesTheDefaultPool()
        {
        for (List<ComputePoolSpec> configured : Arrays.asList(
            null,
            List.<ComputePoolSpec>of()
            ))
            {
            List<ComputePoolSpec> resolved = ComputePoolBootstrap.resolve(
                configured
                );
            Assertions.assertEquals(
                1,
                resolved.size(),
                "An empty configuration must resolve to one pool"
                );
            Assertions.assertEquals(
                ComputePoolBootstrap.DEFAULT_POOL_NAME,
                resolved.get(0).getName(),
                "The fallback pool must be the default pool"
                );
            }
        }

    /**
     * A configured list is used as it is, with no default pool added.
     *
     */
    @Test
    public void testResolveKeepsConfiguredPools()
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName("alpha");
        spec.setDescription("The alpha pool.");
        List<ComputePoolSpec> configured = List.of(spec);

        List<ComputePoolSpec> resolved = ComputePoolBootstrap.resolve(
            configured
            );
        Assertions.assertSame(
            configured,
            resolved,
            "A configured list must be returned as it is"
            );
        }

    /**
     * Register the settings bean for the binding tests.
     *
     */
    @Configuration
    @EnableConfigurationProperties(ComputePoolsSettingsImpl.class)
    static class PoolsSettingsConfiguration
        {
        }
    }
