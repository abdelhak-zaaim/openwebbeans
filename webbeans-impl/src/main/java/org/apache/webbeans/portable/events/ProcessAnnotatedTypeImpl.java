/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.portable.events;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.configurator.AnnotatedTypeConfiguratorImpl;

import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

/**
 * Default implementation of the {@link ProcessAnnotatedType}.
 * 
 * @param <X> bean class info
 */
public class ProcessAnnotatedTypeImpl<X> extends EventBase implements ProcessAnnotatedType<X>, AfterObserver
{
    private final WebBeansContext webBeansContext;


    /**Annotated Type*/
    private AnnotatedType<X> annotatedType;
    
    /**veto or not*/
    private boolean veto;
    
    /**
     * This field gets set to <code>true</code> when a custom AnnotatedType
     * got set in an Extension. In this case we must now take this modified
     * AnnotatedType for our further processing!
     */
    private boolean modifiedAnnotatedType;

    /**
     * Gets set when one makes use the AnnotatedTypeConfigurator
     */
    private AnnotatedTypeConfiguratorImpl configurator;

    /**
     * Creates a new instance with the given annotated type.
     * 
     * @param annotatedType annotated type
     */
    public ProcessAnnotatedTypeImpl(WebBeansContext webBeansContext, AnnotatedType<X> annotatedType)
    {
        this.webBeansContext = webBeansContext;
        this.annotatedType = annotatedType;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotatedType<X> getAnnotatedType()
    {
        checkState();

        if (configurator == null)
        {
            return annotatedType;
        }
        else
        {
            return configurator.getNewAnnotatedType();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAnnotatedType(AnnotatedType<X> type)
    {
        checkState();
        if (configurator != null)
        {
            throw new IllegalStateException("You can't call " +
                    "setAnnotatedType() and configureAnnotatedType()");
        }

        annotatedType = type;
        modifiedAnnotatedType = true;
    }
    
    /**
     * Returns sets or not.
     * 
     * @return set or not
     */
    public boolean isModifiedAnnotatedType()
    {
        return modifiedAnnotatedType || configurator != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void veto()
    {
        checkState();

        veto = true;
    }

    @Override
    public AnnotatedTypeConfigurator<X> configureAnnotatedType()
    {
        checkState();
        if (modifiedAnnotatedType)
        {
            throw new IllegalStateException("You can't call " +
                    "setAnnotatedType() and configureAnnotatedType()");
        }

        if (configurator == null)
        {
            configurator = new AnnotatedTypeConfiguratorImpl(webBeansContext, annotatedType);
        }

        return configurator;
    }

    /**
     * Returns veto status.
     * 
     * @return veto status
     */
    public boolean isVeto()
    {
        return veto;
    }

    @Override
    public void afterObserver()
    {
        if (configurator != null)
        {
            annotatedType = configurator.getNewAnnotatedType();
            configurator = null;
        }
        else if (modifiedAnnotatedType)
        {
            modifiedAnnotatedType = false;
        }
    }

    @Override
    public String toString()
    {
        return "ProcessAnnotatedTypeImpl{" +
            "annotatedType=" + annotatedType.getJavaClass().getName() +
            '}';
    }
}
