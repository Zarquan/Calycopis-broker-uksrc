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
 *     "timestamp": "2026-10-11T11:15:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.offerset;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryEntity;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactory;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryFactoryImpl;

/**
 * Unit tests for profiling entries and the OfferSetEntity ProfilingTarget.
 *
 */
public class OfferSetEntityProfilingTest
    {

    /**
     * Create an OfferSetEntity for our tests.
     *
     */
    private OfferSetEntity makeOfferSet()
        {
        Instant now = Instant.now();
        return new OfferSetEntity(
            "profiling-test",
            "OfferSetEntity profiling unit test",
            now,
            now.plusSeconds(
                60
                ),
            null
            );
        }

    /**
     * The factory sets the class and method names and the start time.
     *
     */
    @Test
    public void factorySetsNamesAndStartTime()
        {
        ProfilingEntryFactory factory = new ProfilingEntryFactoryImpl();
        ProfilingEntry entry = factory.create(
            "net.example.SomeClass",
            "someMethod"
            );

        Assertions.assertEquals(
            "net.example.SomeClass",
            entry.getClassName()
            );
        Assertions.assertEquals(
            "someMethod",
            entry.getMethodName()
            );
        Assertions.assertNotNull(
            entry.getStartTime()
            );
        Assertions.assertNull(
            entry.getEndTime()
            );
        }

    /**
     * end() sets an end time that is not before the start time.
     *
     */
    @Test
    public void endSetsEndTime()
        {
        ProfilingEntryFactory factory = new ProfilingEntryFactoryImpl();
        ProfilingEntry entry = factory.create(
            "net.example.SomeClass",
            "someMethod"
            );

        entry.end();

        Assertions.assertNotNull(
            entry.getEndTime()
            );
        Assertions.assertFalse(
            entry.getEndTime().isBefore(
                entry.getStartTime()
                )
            );
        }

    /**
     * start() sets a start time that is not before the original start time.
     *
     */
    @Test
    public void startResetsStartTime()
        {
        ProfilingEntryFactory factory = new ProfilingEntryFactoryImpl();
        ProfilingEntry entry = factory.create(
            "net.example.SomeClass",
            "someMethod"
            );
        Instant original = entry.getStartTime();

        entry.start();

        Assertions.assertNotNull(
            entry.getStartTime()
            );
        Assertions.assertFalse(
            entry.getStartTime().isBefore(
                original
                )
            );
        }

    /**
     * The OfferSetEntity collects entries, keeping their order and setting
     * itself as their parent.
     *
     */
    @Test
    public void targetCollectsEntries()
        {
        OfferSetEntity offerset = this.makeOfferSet();
        ProfilingEntryFactory factory = new ProfilingEntryFactoryImpl();
        ProfilingEntry first = factory.create(
            "net.example.SomeClass",
            "first"
            );
        ProfilingEntry second = factory.create(
            "net.example.OtherClass",
            "second"
            );

        offerset.addProfilingEntry(
            first
            );
        offerset.addProfilingEntry(
            second
            );

        List<ProfilingEntry> entries = offerset.getProfilingEntries();
        Assertions.assertEquals(
            2,
            entries.size()
            );
        Assertions.assertSame(
            first,
            entries.get(0)
            );
        Assertions.assertSame(
            second,
            entries.get(1)
            );
        Assertions.assertSame(
            offerset,
            ((ProfilingEntryEntity) first).getParent()
            );
        Assertions.assertSame(
            offerset,
            ((ProfilingEntryEntity) second).getParent()
            );
        }
    }
