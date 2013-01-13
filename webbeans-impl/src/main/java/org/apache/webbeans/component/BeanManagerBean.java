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
package org.apache.webbeans.component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.InjectableBeanManager;
import org.apache.webbeans.util.AnnotationUtil;
import org.apache.webbeans.util.CollectionUtil;

public class BeanManagerBean extends AbstractOwbBean<BeanManager>
{
    private BeanManager manager = null;

    public BeanManagerBean(WebBeansContext webBeansContext)
    {
        super(webBeansContext,
              WebBeansType.MANAGER,
              CollectionUtil.<Type>unmodifiableSet(BeanManager.class, Object.class),
              AnnotationUtil.DEFAULT_AND_ANY_ANNOTATION,
              Dependent.class,
              BeanManager.class,
              Collections.<Class<? extends Annotation>>emptySet());
    }

    @Override
    protected BeanManager createInstance(CreationalContext<BeanManager> creationalContext)
    {
        if (manager == null)
        {
            manager = new InjectableBeanManager(getWebBeansContext().getBeanManagerImpl());
        }

        return manager;
    }

    @Override
    protected void destroyInstance(BeanManager instance,CreationalContext<BeanManager> creationalContext)
    {
        manager = null;
    }
    
    /**
     * @see org.apache.webbeans.component.AbstractOwbBean#isPassivationCapable()
     */
    @Override
    public boolean isPassivationCapable()
    {
        return true;
    }    

}
