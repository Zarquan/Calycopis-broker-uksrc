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

/**
 * Settings for the RFC 9457 evaluation.
 *
 */
public interface ErrorResponseSettings
    {

    /**
     * The default for the direct-execution problem details flag.
     *
     * The default keeps the existing 400 + AbstractExecutionSession
     * behaviour. The flag switches that response to a problem details
     * document for the evaluation.
     *
     */
    public static final boolean DEFAULT_DIRECT_EXECUTION_PROBLEM_DETAILS = false;

    /**
     * Whether a rejected direct execution returns a problem details document.
     * Bound from calycopis.broker.errors.direct-execution-problem-details.
     *
     */
    public boolean isDirectExecutionProblemDetails();
    }
