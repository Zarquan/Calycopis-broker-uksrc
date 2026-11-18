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

package net.ivoa.calycopis.broker.engine.entities.message;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem.LevelEnum;

/**
 * A plain message that does not need a persistence parent.
 *
 * A pool policy builds its reasons as these, before an offer set exists to own
 * them. The offer set copies them into MessageEntity objects when it claims
 * them, which is when a parent is available.
 *
 */
public class MessageBean
implements Message
    {
    private final String type;
    private final Instant time;
    private final LevelEnum level;
    private final String template;
    private final Map<String, String> values;
    private final String message;

    /**
     * A message with no template values.
     *
     */
    public MessageBean(
        final LevelEnum level,
        final String type,
        final String template
        ){
        this(
            level,
            type,
            template,
            Collections.emptyMap()
            );
        }

    /**
     * A message with template values.
     *
     */
    public MessageBean(
        final LevelEnum level,
        final String type,
        final String template,
        final Map<String, String> values
        ){
        this.type = type;
        this.time = Instant.now();
        this.level = level;
        this.template = template;
        this.values = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>(values)
            );
        this.message = render(
            template,
            this.values
            );
        }

    /**
     * Apply the values to a template.
     *
     */
    private static String render(
        final String template,
        final Map<String, String> values
        ){
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet())
            {
            result = result.replace(
                "${" + entry.getKey() + "}",
                entry.getValue()
                );
            }
        return result;
        }

    @Override
    public String getType()
        {
        return this.type;
        }

    @Override
    public Instant getTime()
        {
        return this.time;
        }

    @Override
    public LevelEnum getLevel()
        {
        return this.level;
        }

    @Override
    public String getTemplate()
        {
        return this.template;
        }

    @Override
    public Map<String, String> getValues()
        {
        return this.values;
        }

    @Override
    public String getMessage()
        {
        return this.message;
        }
    }
