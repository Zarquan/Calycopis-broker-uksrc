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

package net.ivoa.calycopis.broker.engine.entities.session.simple;

/**
 * Raised when an execution session update cannot be applied.
 *
 * The exception carries enough context for the web layer to build an
 * RFC 9457 problem details response, without exposing any internal state.
 *
 */
public class InvalidExecutionSessionUpdateException
extends RuntimeException
    {

    private static final long serialVersionUID = 1L;

    private final String updateKind;
    private final String path;
    private final String value;

    public InvalidExecutionSessionUpdateException(
        final String message,
        final String updateKind,
        final String path,
        final String value
        ){
        super(message);
        this.updateKind = updateKind;
        this.path = path;
        this.value = value;
        }

    public String getUpdateKind()
        {
        return this.updateKind;
        }

    public String getPath()
        {
        return this.path;
        }

    public String getValue()
        {
        return this.value;
        }

    /**
     * An update of a type that this handler does not support.
     *
     */
    public static InvalidExecutionSessionUpdateException unknownType(
        final String updateKind
        ){
        return new InvalidExecutionSessionUpdateException(
            "Unsupported update type [" + updateKind + "].",
            updateKind,
            null,
            null
            );
        }

    /**
     * An update for a path that this handler does not support.
     *
     */
    public static InvalidExecutionSessionUpdateException unknownPath(
        final String path
        ){
        return new InvalidExecutionSessionUpdateException(
            "Unsupported update path [" + path + "].",
            null,
            path,
            null
            );
        }

    /**
     * An update whose value cannot be parsed for the given path.
     *
     */
    public static InvalidExecutionSessionUpdateException invalidValue(
        final String path,
        final String value
        ){
        return new InvalidExecutionSessionUpdateException(
            "Invalid value [" + value + "] for update path [" + path + "].",
            null,
            path,
            value
            );
        }

    /**
     * A phase transition that is not allowed from the current phase.
     *
     */
    public static InvalidExecutionSessionUpdateException invalidTransition(
        final String currentPhase,
        final String targetPhase
        ){
        return new InvalidExecutionSessionUpdateException(
            "Invalid phase transition from [" + currentPhase + "] to [" + targetPhase + "].",
            null,
            "/phase",
            targetPhase
            );
        }
    }
