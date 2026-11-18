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
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-18T13:45:00/2026-11-18T17:00:00",
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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;

import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;

/**
 * Unit tests for the pool policy decisions.
 *
 */
public class SimpleComputePoolPolicyTest
    {

    /**
     * Criteria with fixed values.
     *
     */
    private static class TestCriteria
    implements SimpleComputeResourceCriteria
        {
        private final OfferSetRequestParserContext request;
        private final long memory;
        private final Interval startInterval;

        private TestCriteria(
            final OfferSetRequestParserContext request,
            final long memory,
            final Interval startInterval
            ){
            this.request = request;
            this.memory = memory;
            this.startInterval = startInterval;
            }

        @Override
        public OfferSetRequestParserContext getRequest()
            {
            return this.request;
            }

        @Override
        public AbstractComputeReservationValidator.Result getResource()
            {
            return null;
            }

        @Override
        public int getOfferLimit()
            {
            return 4;
            }

        @Override
        public long getPrepareSeconds()
            {
            return 0L;
            }

        @Override
        public Interval getStartInterval()
            {
            return this.startInterval;
            }

        @Override
        public Duration getExecutionDuration()
            {
            return Duration.ofHours(2);
            }

        @Override
        public long getMinCores()
            {
            return 1L;
            }

        @Override
        public long getMemory()
            {
            return this.memory;
            }
        }

    private static OfferSetRequestParserContext contextOf(
        final String username,
        final Interval startInterval
        ){
        OfferSetRequestParserContext context = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            new IdentityEntity(username, null)
            );
        if (startInterval != null)
            {
            context.setStartInterval(
                startInterval
                );
            }
        return context;
        }

    private static List<Message> reasonsOf(final PoolPolicyResult result)
        {
        List<Message> reasons = new ArrayList<Message>();
        for (Message message : result.getReasons())
            {
            reasons.add(message);
            }
        return reasons;
        }

    /**
     * A floor accepts its own value and declines one below it.
     *
     */
    @Test
    public void testMemoryFloorBoundary()
        {
        MemoryFloorPolicy policy = new MemoryFloorPolicy(
            "floor",
            64L
            );

        PoolPolicyResult accepted = policy.accepts(
            new TestCriteria(null, 64L, null)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.ACCEPTED,
            accepted.getResult(),
            "The floor is inclusive"
            );
        Assertions.assertTrue(
            accepted.getReasons().isEmpty(),
            "An acceptance carries no reasons"
            );

        PoolPolicyResult declined = policy.accepts(
            new TestCriteria(null, 63L, null)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.DECLINED,
            declined.getResult(),
            "Memory below the floor must decline"
            );
        Assertions.assertEquals(
            1,
            reasonsOf(declined).size(),
            "A decline must carry its reason"
            );
        Assertions.assertEquals(
            MemoryFloorPolicy.REASON_TYPE,
            reasonsOf(declined).get(0).getType(),
            "The reason must identify the floor policy"
            );
        }

    /**
     * A ceiling accepts its own value and declines one above it.
     *
     */
    @Test
    public void testMemoryCeilingBoundary()
        {
        MemoryCeilingPolicy policy = new MemoryCeilingPolicy(
            "ceiling",
            64L
            );

        Assertions.assertEquals(
            PoolPolicyResultEnum.ACCEPTED,
            policy.accepts(
                new TestCriteria(null, 64L, null)
                ).getResult(),
            "The ceiling is inclusive"
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.DECLINED,
            policy.accepts(
                new TestCriteria(null, 65L, null)
                ).getResult(),
            "Memory above the ceiling must decline"
            );
        }

    /**
     * An owner policy accepts the named owner and declines another.
     *
     */
    @Test
    public void testOwnerPolicy()
        {
        OwnerPolicy policy = new OwnerPolicy(
            "owner",
            "alice"
            );

        OfferSetRequestParserContext alice = contextOf("alice", null);
        Assertions.assertEquals(
            PoolPolicyResultEnum.ACCEPTED,
            policy.accepts(
                new TestCriteria(alice, 1L, null)
                ).getResult(),
            "The named owner must be accepted"
            );

        OfferSetRequestParserContext bob = contextOf("bob", null);
        PoolPolicyResult declined = policy.accepts(
            new TestCriteria(bob, 1L, null)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.DECLINED,
            declined.getResult(),
            "Another owner must be declined"
            );
        Assertions.assertTrue(
            reasonsOf(declined).get(0).getTemplate().contains("bob"),
            "The reason must name the actual owner"
            );
        }

    /**
     * A time window includes its start hour and excludes its end hour.
     *
     */
    @Test
    public void testTimeWindowBoundaries()
        {
        TimeWindowPolicy policy = new TimeWindowPolicy(
            "office",
            9,
            17
            );

        Interval atStart = Interval.of(
            Instant.parse("2026-11-18T09:00:00Z"),
            Duration.ofHours(1)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.ACCEPTED,
            policy.accepts(
                new TestCriteria(contextOf("alice", atStart), 1L, atStart)
                ).getResult(),
            "The start hour is inclusive"
            );

        Interval atEnd = Interval.of(
            Instant.parse("2026-11-18T17:00:00Z"),
            Duration.ofHours(1)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.DECLINED,
            policy.accepts(
                new TestCriteria(contextOf("alice", atEnd), 1L, atEnd)
                ).getResult(),
            "The end hour is exclusive"
            );

        Interval before = Interval.of(
            Instant.parse("2026-11-18T08:00:00Z"),
            Duration.ofHours(1)
            );
        Assertions.assertEquals(
            PoolPolicyResultEnum.DECLINED,
            policy.accepts(
                new TestCriteria(contextOf("alice", before), 1L, before)
                ).getResult(),
            "A start before the window must decline"
            );
        }

    /**
     * The shared validator refuses combinations that do not fit the kind.
     *
     */
    @Test
    public void testValidationRejectsInvalidConfiguration()
        {
        Assertions.assertTrue(
            SimpleComputePoolPolicyValidation.validate(
                "memory-floor",
                "floor",
                null,
                null,
                null,
                null,
                null
                ).isPresent(),
            "A floor without a minimum is invalid"
            );
        Assertions.assertTrue(
            SimpleComputePoolPolicyValidation.validate(
                "memory-floor",
                "floor",
                64L,
                128L,
                null,
                null,
                null
                ).isPresent(),
            "A floor must not carry a ceiling"
            );
        Assertions.assertTrue(
            SimpleComputePoolPolicyValidation.validate(
                "time-window",
                "office",
                null,
                null,
                null,
                17,
                9
                ).isPresent(),
            "An overnight window is invalid"
            );
        Assertions.assertTrue(
            SimpleComputePoolPolicyValidation.validate(
                "not-a-kind",
                "bad",
                null,
                null,
                null,
                null,
                null
                ).isPresent(),
            "An unknown kind is invalid"
            );
        Assertions.assertTrue(
            SimpleComputePoolPolicyValidation.validate(
                "memory-floor",
                "floor",
                -1L,
                null,
                null,
                null,
                null
                ).isPresent(),
            "A negative floor is invalid"
            );
        }

    /**
     * The entity refuses an invalid configuration too.
     *
     */
    @Test
    public void testEntityRejectsInvalidConfiguration()
        {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new SimpleComputePoolPolicyEntity(
                null,
                SimpleComputePoolPolicyKind.TIME_WINDOW,
                "office",
                null,
                null,
                null,
                17,
                9
                ),
            "The entity must refuse an overnight window"
            );
        }
    }
