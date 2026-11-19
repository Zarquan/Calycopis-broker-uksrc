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
 *     "timestamp": "2026-11-18T23:10:00",
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

package net.ivoa.calycopis.broker.engine.entities.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem.LevelEnum;

/**
 * Unit tests for persisted message values and rendering.
 *
 */
public class MessageEntityTest
    {

    @Test
    public void testValuesAndMessageAreAvailable()
        {
        MessageEntity message = new MessageEntity(
            null,
            LevelEnum.WARN,
            "urn:test",
            "Value [${value}]",
            Map.<String, Object>of(
                "value",
                "abc"
                )
            );
        Assertions.assertEquals(
            Map.of("value", "abc"),
            message.getValues(),
            "The message must keep the values supplied to its constructor"
            );
        Assertions.assertEquals(
            "Value [abc]",
            message.getMessage(),
            "The message must render its template from the values"
            );
        }

    @Test
    public void testNonStringValuesAreStringified()
        {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put(
            "number",
            Integer.valueOf(7)
            );
        values.put(
            "flag",
            Boolean.TRUE
            );
        MessageEntity message = new MessageEntity(
            null,
            LevelEnum.INFO,
            "urn:test",
            "Number [${number}] flag [${flag}]",
            values
            );
        Assertions.assertEquals(
            "7",
            message.getValues().get("number")
            );
        Assertions.assertEquals(
            "true",
            message.getValues().get("flag")
            );
        Assertions.assertEquals(
            "Number [7] flag [true]",
            message.getMessage()
            );
        }

    @Test
    public void testNullAndEmptyValues()
        {
        MessageEntity nullValues = new MessageEntity(
            null,
            LevelEnum.WARN,
            "urn:test",
            "No values [${value}]",
            null
            );
        Assertions.assertTrue(
            nullValues.getValues().isEmpty(),
            "A null values map must become an empty map"
            );
        Assertions.assertEquals(
            "No values [${value}]",
            nullValues.getMessage(),
            "An empty map must leave the template unrendered"
            );

        MessageEntity emptyValues = new MessageEntity(
            null,
            LevelEnum.WARN,
            "urn:test",
            "No values [${value}]",
            Collections.<String, Object>emptyMap()
            );
        Assertions.assertTrue(
            emptyValues.getValues().isEmpty(),
            "An empty values map must stay empty"
            );
        Assertions.assertEquals(
            "No values [${value}]",
            emptyValues.getMessage()
            );
        }

    @Test
    public void testUnknownPlaceholderRemains()
        {
        MessageEntity message = new MessageEntity(
            null,
            LevelEnum.INFO,
            "urn:test",
            "Known [${known}] unknown [${unknown}]",
            Map.<String, Object>of(
                "known",
                "x"
                )
            );
        Assertions.assertEquals(
            "Known [x] unknown [${unknown}]",
            message.getMessage(),
            "A placeholder with no matching value must remain"
            );
        }

    @Test
    public void testNullTemplateRendersNull()
        {
        Assertions.assertNull(
            MessageBean.render(
                null,
                Map.of("value", "x")
                )
            );
        MessageEntity message = new MessageEntity(
            null,
            LevelEnum.WARN,
            "urn:test",
            null,
            Map.<String, Object>of(
                "value",
                "x"
                )
            );
        Assertions.assertNull(
            message.getMessage()
            );
        }

    @Test
    public void testMessageItemBeanDelegates()
        {
        MessageEntity message = new MessageEntity(
            null,
            LevelEnum.INFO,
            "urn:test",
            "Value [${value}]",
            Map.<String, Object>of(
                "value",
                "abc"
                )
            );
        MessageItemBean bean = new MessageItemBean(
            message
            );
        Assertions.assertEquals(
            message.getValues(),
            bean.getValues()
            );
        Assertions.assertEquals(
            message.getMessage(),
            bean.getMessage()
            );
        Assertions.assertEquals(
            message.getTemplate(),
            bean.getTemplate()
            );
        }
    }
