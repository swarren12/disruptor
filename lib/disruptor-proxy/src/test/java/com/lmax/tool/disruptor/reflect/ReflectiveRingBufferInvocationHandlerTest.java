package com.lmax.tool.disruptor.reflect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;

public class ReflectiveRingBufferInvocationHandlerTest
{
    interface Foo
    {
    }

    @Test
    public void shouldReturnToStringOfInvokerWhenCallingToStringOnProxy() throws Exception
    {
        final ReflectiveRingBufferInvocationHandler invocationHandler = new ReflectiveRingBufferInvocationHandler(null, null, null, null, null);

        Foo proxy = (Foo) Proxy.newProxyInstance(Foo.class.getClassLoader(), new Class[]{Foo.class}, invocationHandler);

        assertThat(proxy.toString(), startsWith("com.lmax.tool.disruptor.reflect.ReflectiveRingBufferInvocationHandler@"));
    }
}