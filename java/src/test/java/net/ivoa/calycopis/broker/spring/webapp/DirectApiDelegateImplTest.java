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

package net.ivoa.calycopis.broker.spring.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.spring.security.IdentityResolver;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ErrorResponseSettings;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractExecutionSession;
import net.ivoa.calycopis.openapi.spring.model.IvoaComponentMetadata;
import net.ivoa.calycopis.openapi.spring.model.IvoaExecutionRequest;
import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem;
import net.ivoa.calycopis.openapi.spring.model.IvoaScheduledExecutionSession;

/**
 * Tests for the direct execution 400 response modes.
 *
 */
public class DirectApiDelegateImplTest
    {

    private Platform platform;
    private IdentityResolver identityResolver;
    private OfferSetEntityFactory offerSetFactory;
    private SimpleExecutionSessionEntity entity;
    private IvoaScheduledExecutionSession bean;
    private NativeWebRequest request;

    @BeforeEach
    public void setup()
        {
        this.platform = Mockito.mock(
            Platform.class
            );
        this.identityResolver = Mockito.mock(
            IdentityResolver.class
            );
        this.offerSetFactory = Mockito.mock(
            OfferSetEntityFactory.class
            );
        this.entity = Mockito.mock(
            SimpleExecutionSessionEntity.class
            );

        IvoaMessageItem message = new IvoaMessageItem();
        message.setMessage(
            "Rejected for test"
            );
        IvoaComponentMetadata meta = new IvoaComponentMetadata();
        meta.addMessagesItem(
            message
            );
        this.bean = new IvoaScheduledExecutionSession();
        this.bean.setMeta(
            meta
            );

        Mockito.when(
            this.platform.getOfferSetEntityFactory()
            ).thenReturn(
                this.offerSetFactory
                );
        Mockito.when(
            this.offerSetFactory.direct(
                any(IvoaExecutionRequest.class),
                any()
                )
            ).thenReturn(
                this.entity
                );
        Mockito.when(
            this.entity.getUuid()
            ).thenReturn(
                null
                );
        Mockito.when(
            this.entity.makeBean(
                any()
                )
            ).thenReturn(
                this.bean
                );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest(
            "POST",
            "/direct"
            );
        servletRequest.setServerName(
            "localhost"
            );
        servletRequest.setServerPort(
            8082
            );
        servletRequest.setContextPath(
            ""
            );
        this.request = new ServletWebRequest(
            servletRequest
            );
        }

    @Test
    public void rejectedExecutionKeepsSessionBodyByDefault()
        {
        ErrorResponseSettings settings = Mockito.mock(
            ErrorResponseSettings.class
            );
        Mockito.when(
            settings.isDirectExecutionProblemDetails()
            ).thenReturn(
                false
                );
        DirectApiDelegateImpl delegate = new DirectApiDelegateImpl(
            this.request,
            this.platform,
            this.identityResolver,
            settings
            );

        ResponseEntity<IvoaAbstractExecutionSession> response = delegate.directExecution(
            new IvoaExecutionRequest()
            );
        assertEquals(
            HttpStatus.BAD_REQUEST,
            response.getStatusCode()
            );
        assertSame(
            this.bean,
            response.getBody()
            );
        }

    @Test
    public void rejectedExecutionCanReturnProblemDetails()
        {
        ErrorResponseSettings settings = Mockito.mock(
            ErrorResponseSettings.class
            );
        Mockito.when(
            settings.isDirectExecutionProblemDetails()
            ).thenReturn(
                true
                );
        DirectApiDelegateImpl delegate = new DirectApiDelegateImpl(
            this.request,
            this.platform,
            this.identityResolver,
            settings
            );

        BrokerProblemException exception = assertThrows(
            BrokerProblemException.class,
            () -> delegate.directExecution(
                new IvoaExecutionRequest()
                )
            );
        assertEquals(
            ProblemType.EXECUTION_REJECTED,
            exception.getProblemType()
            );
        assertEquals(
            "Rejected for test",
            exception.getMessage()
            );
        assertTrue(
            exception.getMessages() != null && exception.getMessages().size() == 1,
            "The problem must carry the failed session messages"
            );
        }
    }
