/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
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
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.booking.compute.simple;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOfferBean;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.broker.spring.query.SpringAbstractQueryHandler;

/**
 * 
 */
@Component
public class SpringSimpleComputeReservationQueryHandlerImpl
extends SpringAbstractQueryHandler<SimpleComputeOffer>
implements SimpleComputeReservationQueryHandler
    {

    /**
     * Public constructor.
     * 
     */
    public SpringSimpleComputeReservationQueryHandlerImpl(
        final JdbcTemplate jdbcTemplate
        ){
        super(
            jdbcTemplate,
            new RowMapper<SimpleComputeOffer>()
                {
                @Override
                public SimpleComputeOffer mapRow(ResultSet resultSet, int rowNum)
                throws SQLException
                    {
                    return SimpleComputeOfferBean.create(
                        resultSet,
                        rowNum
                        );
                    }
                }
            );
        }
    }
