package com.cloud.messagebus;

import com.cloud.framework.messagebus.MessageBus;
import com.cloud.framework.messagebus.MessageDetector;
import com.cloud.framework.messagebus.MessageSubscriber;
import com.cloud.framework.messagebus.PublishScope;

import javax.inject.Inject;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/MessageBusTestContext.xml")
public class TestMessageBus extends TestCase {
    private static final Logger s_logger = LoggerFactory.getLogger(TestMessageBus.class);

    @Inject
    MessageBus _messageBus;

    @Test
    public void testExactSubjectMatch() {
        _messageBus.subscribe("Host", new MessageSubscriber() {

            @Override
            public void onPublishMessage(final String senderAddress, final String subject, final Object args) {
                Assert.assertEquals(subject, "Host");
            }
        });

        _messageBus.publish(null, "Host", PublishScope.LOCAL, null);
        _messageBus.publish(null, "VM", PublishScope.LOCAL, null);
        _messageBus.clearAll();
    }

    @Test
    public void testRootSubjectMatch() {
        _messageBus.subscribe("/", new MessageSubscriber() {

            @Override
            public void onPublishMessage(final String senderAddress, final String subject, final Object args) {
                Assert.assertTrue(subject.equals("Host") || subject.equals("VM"));
            }
        });

        _messageBus.publish(null, "Host", PublishScope.LOCAL, null);
        _messageBus.publish(null, "VM", PublishScope.LOCAL, null);
        _messageBus.clearAll();
    }

    @Test
    public void testMiscMatch() {
        MessageSubscriber subscriberAtParentLevel = new MessageSubscriber() {
            @Override
            public void onPublishMessage(final String senderAddress, final String subject, final Object args) {
                Assert.assertTrue(subject.startsWith(("Host")) || subject.startsWith("VM"));
            }
        };

        MessageSubscriber subscriberAtChildLevel = new MessageSubscriber() {
            @Override
            public void onPublishMessage(final String senderAddress, final String subject, final Object args) {
                Assert.assertTrue(subject.equals("Host.123"));
            }
        };

        subscriberAtParentLevel = Mockito.spy(subscriberAtParentLevel);
        subscriberAtChildLevel = Mockito.spy(subscriberAtChildLevel);

        _messageBus.subscribe("Host", subscriberAtParentLevel);
        _messageBus.subscribe("VM", subscriberAtParentLevel);
        _messageBus.subscribe("Host.123", subscriberAtChildLevel);

        _messageBus.publish(null, "Host.123", PublishScope.LOCAL, null);
        _messageBus.publish(null, "Host.321", PublishScope.LOCAL, null);
        _messageBus.publish(null, "VM.123", PublishScope.LOCAL, null);

        Mockito.verify(subscriberAtParentLevel).onPublishMessage(null, "Host.123", null);
        Mockito.verify(subscriberAtParentLevel).onPublishMessage(null, "Host.321", null);
        Mockito.verify(subscriberAtParentLevel).onPublishMessage(null, "VM.123", null);
        Mockito.verify(subscriberAtChildLevel).onPublishMessage(null, "Host.123", null);

        Mockito.reset(subscriberAtParentLevel);
        Mockito.reset(subscriberAtChildLevel);

        _messageBus.unsubscribe(null, subscriberAtParentLevel);
        _messageBus.publish(null, "Host.123", PublishScope.LOCAL, null);
        _messageBus.publish(null, "VM.123", PublishScope.LOCAL, null);

        Mockito.verify(subscriberAtChildLevel).onPublishMessage(null, "Host.123", null);
        Mockito.verify(subscriberAtParentLevel, Mockito.times(0)).onPublishMessage(null, "Host.123", null);
        Mockito.verify(subscriberAtParentLevel, Mockito.times(0)).onPublishMessage(null, "VM.123", null);

        _messageBus.clearAll();
    }

    public void testMessageDetector() {
        final MessageDetector detector = new MessageDetector();
        detector.open(_messageBus, new String[]{"VM", "Host"});

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException e) {
                        s_logger.debug("[ignored] .");
                    }
                    _messageBus.publish(null, "Host", PublishScope.GLOBAL, null);
                }
            }
        });
        thread.start();

        try {
            int count = 0;
            while (count < 2) {
                detector.waitAny(1000);
                count = count + 1;
            }
        } finally {
            detector.close();
        }

        try {
            thread.join();
        } catch (final InterruptedException e) {
            s_logger.debug("[ignored] .");
        }
    }
}
