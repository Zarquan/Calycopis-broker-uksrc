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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.policy;

import java.util.Optional;

/**
 * Validation shared by the admin endpoints and the startup seed.
 *
 * A policy row is valid only when the fields its kind needs are present and the
 * fields for the other kinds are absent. Keeping this beside the entity means
 * the two callers cannot drift apart.
 *
 */
public final class SimpleComputePoolPolicyValidation
    {
    /**
     * Private constructor, this is a utility class.
     *
     */
    private SimpleComputePoolPolicyValidation()
        {
        }

    /**
     * Validate a policy request.
     *
     * Returns an explanation when the request is invalid, or empty when it is
     * valid.
     *
     */
    public static Optional<String> validate(
        final String kindText,
        final String name,
        final Long minimumMemory,
        final Long maximumMemory,
        final String username,
        final Integer startHour,
        final Integer endHour
        ){
        SimpleComputePoolPolicyKind kind;
        try
            {
            kind = SimpleComputePoolPolicyKind.parse(
                kindText
                );
            }
        catch (IllegalArgumentException exception)
            {
            return Optional.of(
                "Unknown compute pool policy kind [" + kindText + "]."
                );
            }
        if (name == null || name.isBlank())
            {
            return Optional.of(
                "A compute pool policy needs a name."
                );
            }
        if (minimumMemory != null && minimumMemory.longValue() <= 0L)
            {
            return Optional.of(
                "A memory floor must be positive."
                );
            }
        if (maximumMemory != null && maximumMemory.longValue() <= 0L)
            {
            return Optional.of(
                "A memory ceiling must be positive."
                );
            }
        switch (kind)
            {
            case MEMORY_FLOOR:
                if (minimumMemory == null)
                    {
                    return Optional.of(
                        "A memory-floor policy needs a minimumMemory."
                        );
                    }
                return forbidden(
                    "maximumMemory, username, startHour or endHour",
                    maximumMemory,
                    username,
                    startHour,
                    endHour
                    );
            case MEMORY_CEILING:
                if (maximumMemory == null)
                    {
                    return Optional.of(
                        "A memory-ceiling policy needs a maximumMemory."
                        );
                    }
                return forbidden(
                    "minimumMemory, username, startHour or endHour",
                    minimumMemory,
                    username,
                    startHour,
                    endHour
                    );
            case OWNER:
                if (username == null || username.isBlank())
                    {
                    return Optional.of(
                        "An owner policy needs a username."
                        );
                    }
                return forbidden(
                    "minimumMemory, maximumMemory, startHour or endHour",
                    minimumMemory,
                    maximumMemory,
                    startHour,
                    endHour
                    );
            case TIME_WINDOW:
                if (startHour == null || endHour == null)
                    {
                    return Optional.of(
                        "A time-window policy needs a startHour and an endHour."
                        );
                    }
                if (startHour.intValue() < 0 || endHour.intValue() > 24)
                    {
                    return Optional.of(
                        "A time-window hour must be between 0 and 24."
                        );
                    }
                if (startHour.intValue() >= endHour.intValue())
                    {
                    return Optional.of(
                        "A time-window must end after it starts; overnight windows are not supported."
                        );
                    }
                return forbidden(
                    "minimumMemory, maximumMemory or username",
                    minimumMemory,
                    maximumMemory,
                    username
                    );
            default:
                return Optional.of(
                    "Unknown compute pool policy kind [" + kindText + "]."
                    );
            }
        }

    /**
     * Refuse fields the chosen kind does not use.
     *
     */
    private static Optional<String> forbidden(
        final String fields,
        final Object... values
        ){
        for (Object value : values)
            {
            if (value != null)
                {
                return Optional.of(
                    "A policy of this kind must not set " + fields + "."
                    );
                }
            }
        return Optional.empty();
        }
    }
