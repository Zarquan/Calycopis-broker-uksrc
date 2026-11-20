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
 *     "timestamp": "2026-11-20T17:13:00",
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

package net.ivoa.calycopis.broker.spring.metrics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.metrics.ComputePoolMetricsRow;
import net.ivoa.calycopis.broker.spring.query.SpringAbstractQueryHandler;

/**
 * Spring-managed query handler for the compute pool metrics.
 *
 */
@Component
public class SpringComputePoolMetricsQueryHandlerImpl
extends SpringAbstractQueryHandler<ComputePoolMetricsRow>
implements ComputePoolMetricsQueryHandler
    {

    /**
     * Public constructor.
     *
     */
    public SpringComputePoolMetricsQueryHandlerImpl(
        final JdbcTemplate jdbcTemplate
        ){
        super(
            jdbcTemplate,
            new RowMapper<ComputePoolMetricsRow>()
                {
                @Override
                public ComputePoolMetricsRow mapRow(
                    final ResultSet resultSet,
                    final int rowNum
                    )
                throws SQLException
                    {
                    return new ComputePoolMetricsRow(
                        UUID.fromString(
                            resultSet.getString(
                                "pooluuid"
                                )
                            ),
                        resultSet.getString(
                            "poolname"
                            ),
                        resultSet.getLong(
                            "allocatedcores"
                            ),
                        resultSet.getLong(
                            "reservedcores"
                            ),
                        resultSet.getLong(
                            "allocatedmemory"
                            ),
                        resultSet.getLong(
                            "reservedmemory"
                            )
                        );
                    }
                }
            );
        }
    }
