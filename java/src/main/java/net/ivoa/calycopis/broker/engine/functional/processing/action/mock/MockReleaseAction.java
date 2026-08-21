package net.ivoa.calycopis.broker.engine.functional.processing.action.mock;

import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

@Deprecated
public class MockReleaseAction extends MockDelayAction
    {

    public MockReleaseAction(final LifecycleComponentEntity component, int delay)
        {
        super(
            component,
            IvoaLifecyclePhase.RELEASING,
            IvoaLifecyclePhase.COMPLETED,
            delay
            );
        }
    }
