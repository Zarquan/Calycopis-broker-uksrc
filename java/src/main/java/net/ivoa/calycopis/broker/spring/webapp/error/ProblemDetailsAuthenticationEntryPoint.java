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

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.ivoa.calycopis.openapi.spring.model.IvoaProblemDetails;

/**
 * Writes a 401 problem details response for the security filter chain.
 *
 */
public class ProblemDetailsAuthenticationEntryPoint
implements AuthenticationEntryPoint
    {

    private static final String DETAIL =
        "Authentication is required to access this resource.";

    private final ProblemDetailsFactory factory;
    private final ProblemDetailsWriter writer;
    private final String scheme;

    public ProblemDetailsAuthenticationEntryPoint(
        final ProblemDetailsFactory factory,
        final ProblemDetailsWriter writer,
        final String scheme
        ){
        this.factory = factory;
        this.writer = writer;
        this.scheme = scheme;
        }

    @Override
    public void commence(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final AuthenticationException exception
        ) throws IOException
        {
        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            this.scheme + " realm=\"Calycopis\""
            );
        IvoaProblemDetails details = this.factory.create(
            ProblemType.UNAUTHORIZED,
            DETAIL,
            request
            );
        this.writer.write(
            response,
            details
            );
        }
    }
