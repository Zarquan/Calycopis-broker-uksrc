package net.ivoa.calycopis.broker.engine.functional.processing.action.mock;

import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;

@Deprecated
public class MockPrepareAction extends MockDelayAction
    {

    public MockPrepareAction(final LifecycleComponentEntity component, int delay)
        {
        super(
            component,
            IvoaLifecyclePhase.PREPARING,
            IvoaLifecyclePhase.AVAILABLE,
            delay
            );
        }
    }
