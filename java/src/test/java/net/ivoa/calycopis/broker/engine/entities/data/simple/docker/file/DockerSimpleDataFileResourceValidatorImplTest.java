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
 *     "timestamp": "2026-11-19T17:40:00",
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

package net.ivoa.calycopis.broker.engine.entities.data.simple.docker.file;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContextImpl;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;

/**
 * Unit tests for local file location validation.
 *
 */
public class DockerSimpleDataFileResourceValidatorImplTest
    {

    private DockerSimpleDataFileResourceValidatorImpl makeValidator()
        {
        return new DockerSimpleDataFileResourceValidatorImpl(
            null,
            null
            );
        }

    private OfferSetRequestParserContext makeContext()
        {
        return new OfferSetRequestParserContextImpl(
            new IvoaExecutionRequest(),
            null
            );
        }

    private Message firstMessage(
        final OfferSetRequestParserContext context
        ){
        for (Message message : context.getMessages())
            {
            return message;
            }
        return null;
        }

    @Test
    public void acceptsAbsoluteFilePath()
        {
        OfferSetRequestParserContext context = makeContext();
        Assertions.assertTrue(
            makeValidator().validateLocation(
                "file:///var/calycopis/data/random.dat",
                context
                ),
            "An absolute file path must be accepted"
            );
        Assertions.assertFalse(
            context.getMessages().iterator().hasNext(),
            "A valid location must not add a message"
            );
        }

    @Test
    public void acceptsEncodedAbsoluteFilePath()
        {
        OfferSetRequestParserContext context = makeContext();
        Assertions.assertTrue(
            makeValidator().validateLocation(
                "file:///var/calycopis/data/random%20file.dat",
                context
                ),
            "An encoded absolute file path must be accepted"
            );
        Assertions.assertFalse(
            context.getMessages().iterator().hasNext(),
            "A valid location must not add a message"
            );
        }

    @Test
    public void rejectsInvalidFileLocations()
        {
        String[] locations = {
            "file://",
            "file://None",
            "file:///",
            "file://relative",
            "file://[bad"
            };
        for (String location : locations)
            {
            OfferSetRequestParserContext context = makeContext();
            Assertions.assertFalse(
                makeValidator().validateLocation(
                    location,
                    context
                    ),
                "Location [" + location + "] must be rejected"
                );
            Message message = firstMessage(
                context
                );
            Assertions.assertNotNull(
                message,
                "Location [" + location + "] must add a message"
                );
            Assertions.assertEquals(
                "urn:invalid-value",
                message.getType()
                );
            Assertions.assertEquals(
                location,
                message.getValues().get("location")
                );
            Assertions.assertEquals(
                "File data location [" + location + "] must be a file URI with an absolute path",
                message.getMessage()
                );
            }
        }
    }
