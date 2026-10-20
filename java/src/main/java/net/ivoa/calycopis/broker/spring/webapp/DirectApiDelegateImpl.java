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
 *     "timestamp": "2026-03-24T15:00:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-21T10:54:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-26T16:50:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 2,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-30T06:47:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 8,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-30T07:55:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 15,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-10-20T11:57:26",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.webapp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.spring.security.IdentityResolver;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ErrorResponseSettings;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;
import net.ivoa.calycopis.openapi.spring.api.DirectApiDelegate;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractExecutionSession;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem;

@Slf4j
@Service
public class DirectApiDelegateImpl
extends BaseDelegateImpl
implements DirectApiDelegate
    {

    private Platform platform ;
    private ErrorResponseSettings errorResponseSettings ;

    @Autowired
    public DirectApiDelegateImpl(
        final NativeWebRequest request,
        final Platform platform,
        final IdentityResolver identityResolver,
        final ErrorResponseSettings errorResponseSettings
        ){
        super(request, identityResolver);
        this.platform = platform ;
        this.platform.initialize();
        this.errorResponseSettings = errorResponseSettings;
        }

    @Override
    public ResponseEntity<IvoaAbstractExecutionSession> directExecution(
        IvoaExecutionRequest request
        ){
        log.debug("directExecutionPost(IvoaExecutionRequest)");
        IdentityEntity identity = this.getIdentity();
        //
        // Process the request to create a new execution session.
        SimpleExecutionSessionEntity entity = platform.getOfferSetEntityFactory().direct(request, identity);
        log.debug("Session entity [{}][{}][{}]", entity.getUuid(), entity.getPhase(), entity.getClass().getSimpleName());

        IvoaAbstractExecutionSession bean = entity.makeBean(
            this.getURIBuilder()
            );

        log.debug("Session bean [{}][{}]", bean.getMeta().getUuid(), bean.getMeta().getUrl());
        //
        // If we got a real session, then return a redirect to the session details.
        if (entity.getUuid() != null)
            {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(
                bean.getMeta().getUrl()
                );
            return new ResponseEntity<IvoaAbstractExecutionSession>(
                bean,
                headers,
                HttpStatus.SEE_OTHER
                );
            }
        //
        // If the processing failed, return an error response with the details of the failure.
        else {
            if (this.errorResponseSettings.isDirectExecutionProblemDetails())
                {
                List<IvoaMessageItem> messages = null;
                if (bean.getMeta() != null)
                    {
                    messages = bean.getMeta().getMessages();
                    }
                String detail = "The execution request was rejected.";
                if (messages != null && !messages.isEmpty())
                    {
                    IvoaMessageItem first = messages.get(0);
                    if (first.getMessage() != null && !first.getMessage().isBlank())
                        {
                        detail = first.getMessage();
                        }
                    }
                throw new BrokerProblemException(
                    ProblemType.EXECUTION_REJECTED,
                    detail,
                    messages
                    );
                }
            return new ResponseEntity<IvoaAbstractExecutionSession>(
                bean,
                HttpStatus.BAD_REQUEST
                );
            }
        }
    }
