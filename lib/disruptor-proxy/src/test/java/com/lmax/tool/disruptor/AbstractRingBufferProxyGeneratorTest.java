/*
 * Copyright 2015-2016 LMAX Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.lmax.tool.disruptor;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lmax.tool.disruptor.ValidationConfig.ExceptionHandler.NOT_REQUIRED;
import static com.lmax.tool.disruptor.ValidationConfig.ProxyInterface.NO_ANNOTATION;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractRingBufferProxyGeneratorTest
{
    private static final int ITERATIONS = 3;
    private final GeneratorType generatorType;
    private final CountingDropListener dropListener = new CountingDropListener();
    private final CountingMessagePublicationListener messagePublicationListener = new CountingMessagePublicationListener();

    protected AbstractRingBufferProxyGeneratorTest(final GeneratorType generatorType)
    {
        this.generatorType = generatorType;
    }

    private static final class ConcreteClass
    {

    }

    @Test
    public void shouldBlowUpIfSuppliedClassIsNotAnInterface() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor =
                createDisruptor(1024);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType, new ConfigurableValidator(false, true));

        assertThrows(
                IllegalStateException.class,
                () -> ringBufferProxyGenerator.createRingBufferProxy(ConcreteClass.class, disruptor, OverflowStrategy.DROP, new ConcreteClass())
        );
    }

    @Test
    public void shouldThrowExceptionIfDisruptorInstanceDoesNotHaveAnExceptionHandler() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor =
                new Disruptor<>(new RingBufferProxyEventFactory(), 1024, Thread::new);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType);
        final ListenerImpl implementation = new ListenerImpl();

        assertThrows(
                IllegalStateException.class,
                () -> ringBufferProxyGenerator.createRingBufferProxy(Listener.class, disruptor, OverflowStrategy.DROP, implementation)
        );
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldNotValidateRingBufferProxyAnnotationByDefaultToPreserveBackwardsCompatibility() throws Exception
    {
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator generator = generatorFactory.create(generatorType);
        generator.createRingBufferProxy(MyDisruptorProxyWithoutTheDisruptorAnnotation.class,
                createDisruptor(1024), OverflowStrategy.DROP, new StubImplementationForInterface());
    }

    @Test
    public void shouldValidateRingBufferProxyAnnotationIfConfiguredThatWay() throws Exception
    {
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator generator = generatorFactory.newProxy(generatorType, new ConfigurableValidator(true, false));

        assertThrows(
                IllegalStateException.class,
                () -> generator.createRingBufferProxy(MyDisruptorProxyWithoutTheDisruptorAnnotation.class,
                        createDisruptor(1024), OverflowStrategy.DROP, new StubImplementationForInterface())
        );
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldValidateExceptionHandlerByDefaultToPreserveBackwardsCompatibility() throws Exception
    {
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator generator = generatorFactory.create(generatorType);
        final Disruptor<ProxyMethodInvocation> disruptor = new Disruptor<>(new RingBufferProxyEventFactory(), 1024, Thread::new);

        assertThrows(
                IllegalStateException.class,
                () -> generator.createRingBufferProxy(MyDisruptorProxyWithoutTheDisruptorAnnotation.class, disruptor,
                        OverflowStrategy.DROP, new StubImplementationForInterface())
        );
    }

    @Test
    public void shouldNotValidateExceptionHandlerIfConfiguredThatWay() throws Exception
    {
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator generator = generatorFactory.newProxy(generatorType, new ConfigurableValidator(false, false));
        final Disruptor<ProxyMethodInvocation> disruptor = new Disruptor<>(new RingBufferProxyEventFactory(), 1024, Thread::new);
        generator.createRingBufferProxy(MyDisruptorProxyWithoutTheDisruptorAnnotation.class, disruptor,
                OverflowStrategy.DROP, new StubImplementationForInterface());
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    public void shouldProxy()
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(1024);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType);

        final ListenerImpl implementation = new ListenerImpl();
        final Listener listener = ringBufferProxyGenerator.createRingBufferProxy(Listener.class, disruptor, OverflowStrategy.DROP, implementation);
        disruptor.start();

        for (int i = 0; i < 3; i++)
        {
            listener.onString("single string " + i);
            listener.onFloatAndInt((float) i, i);
            listener.onVoid();
            listener.onObjectArray(new Double[]{(double) i});
            listener.onMixedMultipleArgs(0, 1, "a", "b", 2);
        }

        RingBuffer<ProxyMethodInvocation> ringBuffer = disruptor.getRingBuffer();
        while (ringBuffer.getMinimumGatingSequence() != ringBuffer.getCursor())
        {
            // Spin
        }

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(implementation.getLastStringValue(), is("single string 2"));
        assertThat(implementation.getLastFloatValue(), is((float) 2));
        assertThat(implementation.getLastIntValue(), is(2));
        assertThat(implementation.getVoidInvocationCount(), is(3));
        assertThat(implementation.getMixedArgsInvocationCount(), is(3));
        assertThat(implementation.getLastDoubleArray(), is(equalTo(new Double[]{(double) 2})));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    public void shouldProxyMultipleImplementations()
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(1024);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType);

        final ListenerImpl[] implementations = new ListenerImpl[]
                {
                        new ListenerImpl(), new ListenerImpl()
                };

        final Listener listener = ringBufferProxyGenerator.createRingBufferProxy(Listener.class, disruptor, OverflowStrategy.DROP, implementations);
        disruptor.start();

        for (int i = 0; i < ITERATIONS; i++)
        {
            listener.onString("single string " + i);
            listener.onFloatAndInt((float) i, i);
            listener.onVoid();
            listener.onObjectArray(new Double[]{(double) i});
            listener.onMixedMultipleArgs(0, 1, "a", "b", 2);
        }

        RingBuffer<ProxyMethodInvocation> ringBuffer = disruptor.getRingBuffer();
        while (ringBuffer.getMinimumGatingSequence() != ringBuffer.getCursor())
        {
            // Spin
        }

        disruptor.shutdown();
        Executors.newCachedThreadPool().shutdown();

        for (ListenerImpl implementation : implementations)
        {
            assertThat(implementation.getLastStringValue(), is("single string 2"));
            assertThat(implementation.getLastFloatValue(), is((float) 2));
            assertThat(implementation.getLastIntValue(), is(2));
            assertThat(implementation.getVoidInvocationCount(), is(3));
            assertThat(implementation.getMixedArgsInvocationCount(), is(3));
            assertThat(implementation.getLastDoubleArray(), is(equalTo(new Double[]{(double) 2})));
        }
    }

    @Test
    public void shouldNotifyOnPreAndPostPublish() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(1024);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType,
                new ConfigurableValidator(NO_ANNOTATION, NOT_REQUIRED), dropListener, messagePublicationListener);

        final ListenerImpl implementation = new ListenerImpl();
        final Listener listener = ringBufferProxyGenerator.createRingBufferProxy(Listener.class, disruptor, OverflowStrategy.DROP, implementation);
        disruptor.start();

        for (int i = 0; i < 3; i++)
        {
            listener.onVoid();
        }

        RingBuffer<ProxyMethodInvocation> ringBuffer = disruptor.getRingBuffer();
        while (ringBuffer.getMinimumGatingSequence() != ringBuffer.getCursor())
        {
            // Spin
        }

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(messagePublicationListener.getPreCount(), is(3));
        assertThat(messagePublicationListener.getPostCount(), is(3));
    }

    @Test
    public void shouldDropMessagesIfRingBufferIsFull() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(4);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType, new ConfigurableValidator(false, true), dropListener);

        final CountDownLatch latch = new CountDownLatch(1);
        final BlockingOverflowTest implementation = new BlockingOverflowTest(latch);
        final OverflowTest listener = ringBufferProxyGenerator.createRingBufferProxy(OverflowTest.class, disruptor, OverflowStrategy.DROP, implementation);
        disruptor.start();

        for (int i = 0; i < 8; i++)
        {
            listener.invoke();
        }

        latch.countDown();

        Thread.sleep(250L);

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(implementation.getInvocationCount(), is(4));
        assertThat(dropListener.getDropCount(), is(4));
    }

    @Test
    public void shouldReportBatchSize() throws Exception
    {

        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(8);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator =
                generatorFactory.newProxy(generatorType, new ConfigurableValidator(false, true), dropListener);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final BatchSizeTrackingHandler batchSizeTracker = new BatchSizeTrackingHandler(latch1, latch2);
        final OverflowTest listener = ringBufferProxyGenerator.createRingBufferProxy(OverflowTest.class, disruptor, OverflowStrategy.BLOCK, batchSizeTracker);
        disruptor.start();

        for (int i = 0; i < 8; i++)
        {
            listener.invoke();
            latch1.await();
        }

        latch2.countDown();

        Thread.sleep(250L);

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(batchSizeTracker.getInvocationCount(), is(8));
        assertThat(batchSizeTracker.getMaxBatchSize(), is(7));
    }

    @Test
    public void shouldNotNotifyOnPostPublishForDroppedMessages() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(4);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType, new ConfigurableValidator(false, true), dropListener, messagePublicationListener);

        final CountDownLatch latch = new CountDownLatch(1);
        final BlockingOverflowTest implementation = new BlockingOverflowTest(latch);
        final OverflowTest listener = ringBufferProxyGenerator.createRingBufferProxy(OverflowTest.class, disruptor, OverflowStrategy.DROP, implementation);
        disruptor.start();

        for (int i = 0; i < 8; i++)
        {
            listener.invoke();
        }

        latch.countDown();

        Thread.sleep(250L);

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(implementation.getInvocationCount(), is(4));
        assertThat(dropListener.getDropCount(), is(4));
        assertThat(messagePublicationListener.getPreCount(), is(8));
        assertThat(messagePublicationListener.getPostCount(), is(4));
    }

    @Test
    public void shouldNotifyBatchListenerImplementationOfEndOfBatch() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(4);
        final RingBufferProxyGeneratorFactory generatorFactory = new RingBufferProxyGeneratorFactory();
        final RingBufferProxyGenerator ringBufferProxyGenerator = generatorFactory.newProxy(generatorType);

        final BatchAwareListenerImpl implementation = new BatchAwareListenerImpl();
        final Listener listener = ringBufferProxyGenerator.createRingBufferProxy(Listener.class, disruptor, OverflowStrategy.DROP, implementation);
        disruptor.start();

        listener.onString("foo1");
        listener.onString("foo2");
        listener.onString("foo3");
        listener.onString("foo4");


        long timeoutAt = System.currentTimeMillis() + 2000L;

        while (implementation.getBatchCount() == 0 && System.currentTimeMillis() < timeoutAt)
        {
            Thread.sleep(1);
        }

        final int firstBatchCount = implementation.getBatchCount();
        assertThat(firstBatchCount, is(not(0)));

        listener.onVoid();
        listener.onVoid();
        listener.onVoid();

        timeoutAt = System.currentTimeMillis() + 2000L;

        while (implementation.getBatchCount() == firstBatchCount && System.currentTimeMillis() < timeoutAt)
        {
            Thread.sleep(1);
        }

        disruptor.shutdown();
        Executors.newSingleThreadExecutor().shutdown();

        assertThat(implementation.getBatchCount() > firstBatchCount, is(true));
    }

    @Test
    public void shouldAllowProxyToInheritMethodsFromOtherInterfaces() throws Exception
    {
        final Disruptor<ProxyMethodInvocation> disruptor = createDisruptor(1024);
        disruptor.handleExceptionsWith(new ThrowExceptionHandler());

        final CountDownLatch bothMethodsAreCalled = new CountDownLatch(2);
        final ProxyWhichExtendsAnotherInterface catDog = new RingBufferProxyGeneratorFactory()
                .newProxy(generatorType)
                .createRingBufferProxy(ProxyWhichExtendsAnotherInterface.class,
                        disruptor,
                        OverflowStrategy.DROP, new ProxyWhichExtendsAnotherInterface()
                        {
                            @Override
                            public void meow(final String meow, final int age)
                            {
                                bothMethodsAreCalled.countDown();
                            }

                            @Override
                            public void interitedMethodBark(final boolean bark)
                            {
                                bothMethodsAreCalled.countDown();
                            }
                        });
        disruptor.start();

        catDog.meow("meow", 3);
        catDog.interitedMethodBark(true);

        assertTrue(bothMethodsAreCalled.await(2, TimeUnit.SECONDS));
    }

    public interface AnotherInterface
    {
        void interitedMethodBark(boolean bark);
    }

    @DisruptorProxy
    public interface ProxyWhichExtendsAnotherInterface extends AnotherInterface
    {
        void meow(String meow, int age);
    }

    private static final class BlockingOverflowTest implements OverflowTest
    {
        private final CountDownLatch blocker;
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        private BlockingOverflowTest(final CountDownLatch blocker)
        {
            this.blocker = blocker;
        }

        @Override
        public void invoke()
        {
            try
            {
                blocker.await();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException();
            }
            invocationCount.incrementAndGet();
        }

        int getInvocationCount()
        {
            return invocationCount.get();
        }
    }

    @DisruptorProxy
    public interface OverflowTest
    {
        void invoke();
    }

    public interface MyDisruptorProxyWithoutTheDisruptorAnnotation
    {
    }

    private Disruptor<ProxyMethodInvocation> createDisruptor(final int ringBufferSize)
    {
        final Disruptor<ProxyMethodInvocation> disruptor = new Disruptor<>(new RingBufferProxyEventFactory(), ringBufferSize, Thread::new);
        disruptor.handleExceptionsWith(new FatalExceptionHandler());
        return disruptor;
    }

    private static final class StubImplementationForInterface implements MyDisruptorProxyWithoutTheDisruptorAnnotation
    {
    }

    private static final class ThrowExceptionHandler implements ExceptionHandler
    {
        @Override
        public void handleEventException(final Throwable ex, final long sequence, final Object event)
        {
            throw new RuntimeException("fail " + ex.getMessage());
        }

        @Override
        public void handleOnStartException(final Throwable ex)
        {
            throw new RuntimeException("fail " + ex.getMessage());
        }

        @Override
        public void handleOnShutdownException(final Throwable ex)
        {
            throw new RuntimeException("fail " + ex.getMessage());
        }
    }

    private static class BatchSizeTrackingHandler implements OverflowTest, BatchSizeListener
    {
        private final CountDownLatch latch1;
        private final CountDownLatch latch2;
        private int invocationCount;
        private int maxBatchSize;

        BatchSizeTrackingHandler(final CountDownLatch latch1, final CountDownLatch latch2)
        {

            this.latch1 = latch1;
            this.latch2 = latch2;
        }

        @Override
        public void invoke()
        {
            invocationCount++;

            latch1.countDown();
            try
            {
                latch2.await();
            }
            catch (final InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onEndOfBatch(final int batchSize)
        {
            maxBatchSize = Math.max(maxBatchSize, batchSize);
        }

        public int getMaxBatchSize()
        {
            return maxBatchSize;
        }

        public int getInvocationCount()
        {
            return invocationCount;
        }
    }
}
