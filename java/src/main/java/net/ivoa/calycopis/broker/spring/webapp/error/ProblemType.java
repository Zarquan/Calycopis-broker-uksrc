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

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * The RFC 9457 problem types used by the broker.
 *
 * The type URIs are provisional, and are intended to become part of a
 * registered IVOA problem type namespace.
 *
 */
public enum ProblemType
    {

    INVALID_UPDATE(
        HttpStatus.BAD_REQUEST,
        "invalid-update",
        "Invalid execution session update"
        ),

    EXECUTION_REJECTED(
        HttpStatus.BAD_REQUEST,
        "execution-rejected",
        "Execution request rejected"
        ),

    SESSION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "session-not-found",
        "Execution session not found"
        ),

    OFFERSET_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "offerset-not-found",
        "Offer set not found"
        ),

    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "access-denied",
        "Access denied"
        ),

    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "unauthorized",
        "Unauthorized"
        ),

    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Internal server error"
        );

    private static final String TYPE_PREFIX = "urn:ivoa:calycopis:error:";

    private final HttpStatus status;
    private final URI type;
    private final String title;

    ProblemType(
        final HttpStatus status,
        final String slug,
        final String title
        ){
        this.status = status;
        this.type = URI.create(
            TYPE_PREFIX + slug
            );
        this.title = title;
        }

    public HttpStatus getStatus()
        {
        return this.status;
        }

    public URI getTypeUri()
        {
        return this.type;
        }

    public String getTitle()
        {
        return this.title;
        }
    }
