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
 *     "timestamp": "2026-10-20T11:57:26",
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

package net.ivoa.calycopis.broker.spring.webapp.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.openapi.spring.model.IvoaProblemDetails;

/**
 * Writes a problem details body from the Spring Security filter chain.
 *
 * The MVC advice uses the configured Spring message converters. The
 * security filter chain runs before MVC, so it writes the generated
 * model with its own Jackson 2 mapper. The generated model uses Jackson 2
 * annotations, so this does not depend on which Jackson converter wins
 * for the normal API responses.
 *
 */
@Slf4j
@Component
public class ProblemDetailsWriter
    {

    private final ObjectMapper mapper;

    public ProblemDetailsWriter()
        {
        this.mapper = new ObjectMapper()
            .registerModule(
                new JavaTimeModule()
                )
            .disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
            .setSerializationInclusion(
                JsonInclude.Include.NON_EMPTY
                );
        }

    public void write(
        final HttpServletResponse response,
        final IvoaProblemDetails details
        ) throws IOException
        {
        if (details.getStatus() != null)
            {
            response.setStatus(
                details.getStatus()
                );
            }
        response.setContentType(
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
            );
        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
            );
        this.mapper.writeValue(
            response.getOutputStream(),
            details
            );
        }
    }
