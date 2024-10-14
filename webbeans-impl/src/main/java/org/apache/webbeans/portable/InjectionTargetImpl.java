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
package org.apache.webbeans.portable;

import org.apache.webbeans.config.OWBLogConst;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.context.creational.CreationalContextImpl;
import org.apache.webbeans.exception.WebBeansException;
import org.apache.webbeans.inject.InjectableConstructor;
import org.apache.webbeans.inject.InjectableField;
import org.apache.webbeans.inject.InjectableMethod;
import org.apache.webbeans.intercept.ConstructorInterceptorInvocationContext;
import org.apache.webbeans.intercept.DefaultInterceptorHandler;
import org.apache.webbeans.intercept.InterceptorResolutionService;
import org.apache.webbeans.intercept.InterceptorResolutionService.BeanInterceptorInfo;
import org.apache.webbeans.intercept.LifecycleInterceptorInvocationContext;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.proxy.InterceptorDecoratorProxyFactory;
import org.apache.webbeans.proxy.InterceptorHandler;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;
import org.apache.webbeans.proxy.OwbInterceptorProxy;
import org.apache.webbeans.proxy.OwbNormalScopeProxy;
import org.apache.webbeans.spi.ResourceInjectionService;
import org.apache.webbeans.util.Asserts;
import org.apache.webbeans.util.ExceptionUtil;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.interceptor.InvocationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

public class InjectionTargetImpl<T> extends AbstractProducer<T> implements InjectionTarget<T>
{

    protected final WebBeansContext webBeansContext;

    protected final AnnotatedType<T> annotatedType;
    protected volatile AnnotatedConstructor<T> constructor;

    /**
     * If the InjectionTarget has a &#064;PostConstruct method, <code>null</code> if not.
     * This methods only gets used if the produced instance is not intercepted.
     * This methods must have the signature <code>void METHOD();</code>
     * They are ordered as <b>superclass first</b>.
     */
    private List<AnnotatedMethod<?>> postConstructMethods;

    /**
     * Interceptors which should get triggered for &#064;PostConstruct.
     * Ordered in parent-class first
     */
    private List<Interceptor<?>> postConstructInterceptors;

    /**
     * If the InjectionTarget has a &#064;PreDestroy method, <code>null</code> if not.
     * This methods only gets used if the produced instance is not intercepted.
     * This methods must have the signature <code>void METHOD();</code>
     * They are ordered as <b>subclass first</b>.
     */
    private List<AnnotatedMethod<?>> preDestroyMethods;

    /**
     * Interceptors which should get triggered for &#064;PreDestroy.
     * Ordered in sub-class first
     */
    private List<Interceptor<?>> preDestroyInterceptors;

    private List<Interceptor<?>> aroundConstructInterceptors;

    public InjectionTargetImpl(AnnotatedType<T> annotatedType, Set<InjectionPoint> injectionPoints, WebBeansContext webBeansContext,
                               List<AnnotatedMethod<?>> postConstructMethods, List<AnnotatedMethod<?>> preDestroyMethods)
    {
        super(injectionPoints);
        Asserts.assertNotNull(annotatedType);
        Asserts.assertNotNull(webBeansContext);
        this.annotatedType = annotatedType;
        this.webBeansContext = webBeansContext;
        this.postConstructMethods = postConstructMethods;
        this.preDestroyMethods = preDestroyMethods;
    }
    
    @Override
    protected void defineLifecycleInterceptors(Bean<T> bean, AnnotatedType<T> annotatedType, WebBeansContext webBeansContext)
    {
        BeanInterceptorInfo interceptorInfo = getInterceptorInfo();
        
        postConstructInterceptors
            = getLifecycleInterceptors(interceptorInfo.getEjbInterceptors(), interceptorInfo.getClassCdiInterceptors(), InterceptionType.POST_CONSTRUCT);

        preDestroyInterceptors
            = getLifecycleInterceptors(interceptorInfo.getEjbInterceptors(), interceptorInfo.getClassCdiInterceptors(), InterceptionType.PRE_DESTROY);

        // no more needed
        interceptorInfo.getClassCdiInterceptors().clear();

        final AnnotatedConstructor<T> ct = getConstructor();
        if (ct != null)
        {
            InterceptorResolutionService.BusinessMethodInterceptorInfo constructorInterceptorInfo =
                interceptorInfo.getConstructorInterceptorInfos().get(ct.getJavaMember());
            Interceptor<?>[] constructorEjbInterceptorArray = constructorInterceptorInfo == null ?
                null : constructorInterceptorInfo.getEjbInterceptors();
            List<Interceptor<?>> constructorEjbInterceptors = constructorEjbInterceptorArray == null ?
                Collections.<Interceptor<?>>emptyList() : asList(constructorEjbInterceptorArray);
            aroundConstructInterceptors = getLifecycleInterceptors(
                constructorEjbInterceptors,
                interceptorInfo.getConstructorCdiInterceptors(),
                InterceptionType.AROUND_CONSTRUCT);
        }
    }

    @Override
    public T produce(Map<Interceptor<?>, ?> interceptorInstances, CreationalContextImpl<T> creationalContext)
    {
        if (hasAroundConstruct())
        {
            try
            {
                Constructor<T> cons = getConstructor().getJavaMember();
                InjectableConstructor<T> injectableConstructor = new InjectableConstructor<>(cons, this, creationalContext);
                ConstructorInstanceProvider provider = new ConstructorInstanceProvider();
                ConstructorInterceptorInvocationContext<T> invocationContext = new ConstructorInterceptorInvocationContext<T>(
                        provider, aroundConstructInterceptors, interceptorInstances, cons, injectableConstructor.createParameters());
                provider.setContext(invocationContext);
                invocationContext.proceed();
                Object newInstance = invocationContext.getNewInstance();
                if (newInstance == null)
                {
                    invocationContext.directProceed();
                    newInstance = invocationContext.getNewInstance();
                }
                return (T) newInstance;
            }
            catch (Exception e) // CDI 1.0
            {
                throw ExceptionUtil.throwAsRuntimeException(e);
            }
        }
        else
        {
            return newInstance(creationalContext);
        }
    }

    @Override
    protected boolean needsProxy()
    {
        return super.needsProxy() || !postConstructInterceptors.isEmpty() || !preDestroyInterceptors.isEmpty();
    }
    
    protected boolean hasAroundConstruct()
    {
        return aroundConstructInterceptors != null && !aroundConstructInterceptors.isEmpty();
    }
    
    protected T newInstance(CreationalContextImpl<T> creationalContext)
    {
        return new InjectableConstructor<>(getConstructor().getJavaMember(), this, creationalContext).doInjection();
    }

    @Override
    public void inject(T instance, CreationalContext<T> context)
    {
        inject(instance.getClass(), unwrapProxyInstance(instance), (CreationalContextImpl<T>) context);
    }

    private void inject(Class<?> type, T instance, CreationalContextImpl<T> context)
    {
        if (type == null || type.equals(Object.class))
        {
            return;
        }
        inject(type.getSuperclass(), instance, context);
        injectFields(type, instance, context);
        injectMethods(type, instance, context);
        injectInitializerMethods(type, instance, context);
        injectResources(instance);
    }

    private void injectFields(Class<?> type, T instance, CreationalContextImpl<T> context)
    {
        for (InjectionPoint injectionPoint : getInjectionPoints())
        {
            if (injectionPoint.getMember().getDeclaringClass().equals(type))
            {
                if (injectionPoint.getMember() instanceof Field)
                {
                    new InjectableField<>((Field) injectionPoint.getMember(), instance, this, context).doInjection();
                }
            }
        }
    }

    private void injectMethods(Class<?> type, T instance, CreationalContextImpl<T> context)
    {
        Set<Member> injectedMethods = new HashSet<>();
        for (InjectionPoint injectionPoint : getInjectionPoints())
        {
            if (injectionPoint.getMember().getDeclaringClass().equals(type))
            {
                if (injectionPoint.getMember() instanceof Method
                        && !injectedMethods.contains(injectionPoint.getMember())
                        && !isProducerMethod(injectionPoint)
                        && !isDisposalMethod(injectionPoint)
                        && !isObserverMethod(injectionPoint))
                {
                    new InjectableMethod<>((Method) injectionPoint.getMember(), instance, this, context).doInjection();
                    injectedMethods.add(injectionPoint.getMember());
                }
            }
        }
    }

    /**
     * Performs injection on initializer methods, which are methods that are annotated with &#64;Inject,
     * but have no parameter and thus no injection point.
     */
    private void injectInitializerMethods(Class<?> declaringType, T instance, CreationalContextImpl<T> context)
    {
        for (AnnotatedMethod<? super T> method : webBeansContext.getAnnotatedElementFactory().getFilteredAnnotatedMethods(annotatedType))
        {
            if (method.getDeclaringType().getJavaClass().equals(declaringType) && method.isAnnotationPresent(Inject.class) && method.getParameters().isEmpty())
            {
                new InjectableMethod<>(method.getJavaMember(), instance, this, context).doInjection();
            }
        }
    }
    
    private void injectResources(T instance)
    {
        try
        {
            ResourceInjectionService service = null;
            try
            {
                service = webBeansContext.getService(ResourceInjectionService.class);
            
            }
            catch (Exception e)
            {
                // When running in tests
            }
        
            if (service != null)
            {
                service.injectJavaEEResources(instance);   
            }
        }
        catch (Exception e)
        {
            throw new WebBeansException(MessageFormat.format(
                WebBeansLoggerFacade.getTokenString(OWBLogConst.ERROR_0023), instance), e);
        }
    }

    @Override
    public void postConstruct(T instance)
    {
        Map<Interceptor<?>, ?> interceptorInstances = null;
        T internalInstance = instance;

        if (getInterceptorInfo() != null && instance instanceof OwbInterceptorProxy)
        {
            InterceptorHandler ih = getProxyFactory().getInterceptorHandler((OwbInterceptorProxy) instance);
            if (ih instanceof DefaultInterceptorHandler)
            {
                DefaultInterceptorHandler dih = (DefaultInterceptorHandler) ih;
                interceptorInstances = dih.getInstances();
                internalInstance = (T) dih.getTarget();
            }
        }
        else if (postConstructMethods == null || postConstructMethods.isEmpty())
        {
            return;
        }

        InvocationContext ic = new LifecycleInterceptorInvocationContext<>(internalInstance, InterceptionType.POST_CONSTRUCT, postConstructInterceptors,
            interceptorInstances, postConstructMethods);
        try
        {
            ic.proceed();
        }
        catch (Exception e)
        {
            throw ExceptionUtil.throwAsRuntimeException(e);
        }
    }

    @Override
    public void preDestroy(T instance)
    {
        if (instance instanceof OwbNormalScopeProxy)
        {
            instance = NormalScopeProxyFactory.unwrapInstance(instance);
        }

        Map<Interceptor<?>, ?> interceptorInstances = null;
        T internalInstance = instance;

        if (getInterceptorInfo() != null && instance instanceof OwbInterceptorProxy)
        {
            InterceptorDecoratorProxyFactory pf = webBeansContext.getInterceptorDecoratorProxyFactory();
            InterceptorHandler ih = pf.getInterceptorHandler((OwbInterceptorProxy) instance);
            if (ih instanceof DefaultInterceptorHandler)
            {
                DefaultInterceptorHandler dih = (DefaultInterceptorHandler) ih;
                interceptorInstances = dih.getInstances();
                internalInstance = (T) dih.getTarget();
            }
        }
        else if (preDestroyMethods == null || preDestroyMethods.isEmpty())
        {
            return;
        }

        InvocationContext ic = new LifecycleInterceptorInvocationContext<>(internalInstance, InterceptionType.PRE_DESTROY, preDestroyInterceptors,
            interceptorInstances, preDestroyMethods);
        try
        {
            ic.proceed();
        }
        catch (Exception e)
        {
            ExceptionUtil.throwAsRuntimeException(e);
        }
    }

    protected AnnotatedConstructor<T> getConstructor()
    {
        if (constructor == null)
        {
            constructor = createConstructor();
        }
        return constructor;
    }
    
    protected AnnotatedConstructor<T> createConstructor()
    {
        AnnotatedConstructor<T> constructor = null;
        for (InjectionPoint injectionPoint : getInjectionPoints())
        {
            if (injectionPoint.getMember() instanceof Constructor)
            {
                if (constructor == null)
                {
                    constructor = (AnnotatedConstructor<T>)((AnnotatedParameter<T>)injectionPoint.getAnnotated()).getDeclaringCallable();
                    return constructor;
                }
            }
        }

        Constructor<T> defaultConstructor = getDefaultConstructor();
        if (defaultConstructor == null)
        {
            // sometimes there is no default ct nor any injection point ct
            return null;
        }
        return new AnnotatedConstructorImpl<>(webBeansContext, defaultConstructor, annotatedType);
    }

    private Constructor<T> getDefaultConstructor()
    {
        return webBeansContext.getWebBeansUtil().getNoArgConstructor(annotatedType.getJavaClass());
    }
    
    private boolean isProducerMethod(InjectionPoint injectionPoint)
    {
        return ((AnnotatedElement)injectionPoint.getMember()).isAnnotationPresent(Produces.class);
    }

    private boolean isObserverMethod(InjectionPoint injectionPoint)
    {
        if (!(injectionPoint.getMember() instanceof Method))
        {
            return false;
        }
        Method method = (Method) injectionPoint.getMember();
        for (Annotation[] annotations : method.getParameterAnnotations())
        {
            for (Annotation annotation : annotations)
            {
                if (annotation.annotationType().equals(Observes.class) || annotation.annotationType().equals(ObservesAsync.class))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDisposalMethod(InjectionPoint injectionPoint)
    {
        if (!(injectionPoint.getMember() instanceof Method))
        {
            return false;
        }
        Method method = (Method) injectionPoint.getMember();
        for (Annotation[] annotations : method.getParameterAnnotations())
        {
            for (Annotation annotation : annotations)
            {
                if (annotation.annotationType().equals(Disposes.class))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Interceptor<?>> getLifecycleInterceptors(Collection<Interceptor<?>> ejbInterceptors, List<Interceptor<?>> cdiInterceptors, InterceptionType interceptionType)
    {
        List<Interceptor<?>> lifecycleInterceptors = new ArrayList<>();

        for (Interceptor<?> ejbInterceptor : ejbInterceptors)
        {
            if (ejbInterceptor.intercepts(interceptionType))
            {
                lifecycleInterceptors.add(ejbInterceptor);
            }
        }
        for (Interceptor<?> cdiInterceptor : cdiInterceptors)
        {
            if (cdiInterceptor.intercepts(interceptionType))
            {
                lifecycleInterceptors.add(cdiInterceptor);
            }
        }

        return lifecycleInterceptors;
    }

    private static class ConstructorInstanceProvider<T> implements Provider<T>
    {
        private ConstructorInterceptorInvocationContext<T> context;

        @Override
        public T get()
        {
            return (T) context.getNewInstance();
        }

        // this dependency sucks, we should find something a bit more sexy
        public void setContext(ConstructorInterceptorInvocationContext<T> context)
        {
            this.context = context;
        }
    }
}
