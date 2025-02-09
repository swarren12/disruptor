package com.lmax.tool.disruptor;

/**
 * Default implementation of MessagePublicationListener
 */
public enum NoMessagePublicationListener implements MessagePublicationListener
{
    /**
     * Singleton {@link NoMessagePublicationListener}.
     */
    INSTANCE;

    @Override
    public void onPrePublish()
    {

    }

    @Override
    public void onPostPublish()
    {

    }
}
