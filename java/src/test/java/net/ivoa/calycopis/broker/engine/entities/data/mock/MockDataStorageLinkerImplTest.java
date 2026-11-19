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
 *     "timestamp": "2026-11-18T23:15:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.data.mock;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceValidator;
import net.ivoa.calycopis.broker.engine.functional.validator.Validator.ResultEnum;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleDataResource;

/**
 * Unit tests for the storage-linker warning values.
 *
 */
public class MockDataStorageLinkerImplTest
    {

    private static final String STORAGE_REFERENCE = "ivo://example.org/storage/one";

    private static Message firstMessage(final OfferSetRequestParserContext context)
        {
        for (Message message : context.getMessages())
            {
            return message;
            }
        return null;
        }

    private static void assertStorageMessage(final OfferSetRequestParserContext context)
        {
        Message message = firstMessage(
            context
            );
        Assertions.assertNotNull(
            message,
            "The storage linker must record a warning"
            );
        Assertions.assertEquals(
            "urn:storage-required",
            message.getType()
            );
        Assertions.assertEquals(
            STORAGE_REFERENCE,
            message.getValues().get("storageref"),
            "The warning must carry the requested storage reference"
            );
        Assertions.assertEquals(
            "Unable to assign storage resource [${storageref}]",
            message.getTemplate(),
            "The warning template must use a message placeholder"
            );
        Assertions.assertEquals(
            "Unable to assign storage resource [ivo://example.org/storage/one]",
            message.getMessage(),
            "The warning message must render the storage reference"
            );
        }

    @Test
    public void testAcceptedResultWithNullObject()
        {
        MockDataStorageLinkerImpl linker = new MockDataStorageLinkerImpl(
            null
            );
        IvoaSimpleDataResource requested = new IvoaSimpleDataResource();
        requested.setStorage(
            STORAGE_REFERENCE
            );
        OfferSetRequestParserContext context = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            (IdentityEntity) null
            );
        linker.acceptResult(
            new AbstractStorageResourceValidator.ResultBean(
                ResultEnum.ACCEPTED
                ),
            requested,
            new IvoaSimpleDataResource(),
            context
            );
        assertStorageMessage(
            context
            );
        }

    @Test
    public void testUnexpectedResultState()
        {
        MockDataStorageLinkerImpl linker = new MockDataStorageLinkerImpl(
            null
            );
        IvoaSimpleDataResource requested = new IvoaSimpleDataResource();
        requested.setStorage(
            STORAGE_REFERENCE
            );
        OfferSetRequestParserContext context = new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            (IdentityEntity) null
            );
        linker.acceptResult(
            new AbstractStorageResourceValidator.ResultBean(
                ResultEnum.FAILED
                ),
            requested,
            new IvoaSimpleDataResource(),
            context
            );
        assertStorageMessage(
            context
            );
        }
    }
