/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.webbeans.jsf;

import java.io.IOException;

import javax.enterprise.context.Conversation;
import javax.enterprise.inject.spi.Bean;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.util.JSFUtil;

public class WebBeansJSFFilter implements Filter
{

    public void destroy()
    {

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletResponse servletResponse = (HttpServletResponse) response;
        HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(servletResponse)
        {

            /*
             * (non-Javadoc)
             * @see
             * javax.servlet.http.HttpServletResponseWrapper#sendRedirect(java
             * .lang.String)
             */
            @Override
            public void sendRedirect(String location) throws IOException
            {
                Bean<?> bean = BeanManagerImpl.getManager().resolveByType(Conversation.class, new DefaultLiteral()).iterator().next();
                Conversation conversation = (Conversation)BeanManagerImpl.getManager().getInstance(bean,null);

                String path = location;

                if (conversation != null)
                {

                    if (!conversation.isTransient())
                    {                        
                        path = JSFUtil.getRedirectViewIdWithCid(location, conversation.getId());
                    }
                }
                                
                super.sendRedirect(path);                

            }

        };
                

        chain.doFilter(request, responseWrapper);
    }

    public void init(FilterConfig config) throws ServletException
    {

    }

}
