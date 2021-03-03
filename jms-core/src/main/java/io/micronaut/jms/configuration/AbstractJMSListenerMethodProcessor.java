/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms.configuration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.exceptions.MessageListenerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.lang.annotation.Annotation;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * Abstract {@link ExecutableMethodProcessor} for annotations related to
 * {@link JMSListener}. Registers a {@link io.micronaut.jms.listener.JMSListener}
 * if the method annotated with {@code <T>} is part of a bean annotated with {@link JMSListener}.
 *
 * @param <T> the destination type annotation
 * @author Elliott Pope
 * @since 1.0.0
 */
public abstract class AbstractJMSListenerMethodProcessor<T extends Annotation>
        implements ExecutableMethodProcessor<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final BeanContext beanContext;

    private final DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
    private final JMSArgumentBinderRegistry jmsArgumentBinderRegistry;
    private final Class<T> clazz;

    protected AbstractJMSListenerMethodProcessor(BeanContext beanContext,
                                                 JMSArgumentBinderRegistry registry,
                                                 Class<T> clazz) {
        this.beanContext = beanContext;
        this.jmsArgumentBinderRegistry = registry;
        this.clazz = clazz;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition,
                        ExecutableMethod<?, ?> method) {

        AnnotationValue<JMSListener> listenerAnnotation = beanDefinition.getAnnotation(JMSListener.class);
        if (listenerAnnotation == null) {
            return;
        }

        String connectionFactoryName = listenerAnnotation.getRequiredValue(String.class);

        AnnotationValue<T> destinationAnnotation = method.getAnnotation(clazz);
        Assert.notNull(destinationAnnotation, () -> "Annotation not found on method " +
                method.getName() + ". Expecting annotation of type " + clazz.getName());

        registerListener(method, connectionFactoryName, beanDefinition,
                destinationAnnotation, getDestinationType());
    }

    protected abstract ExecutorService getExecutorService(AnnotationValue<T> value);

    protected abstract JMSDestinationType getDestinationType();

    private void validateArguments(ExecutableMethod<?, ?> method) {
        Stream.of(method.getArguments())
                .filter(arg ->
                        arg.isDeclaredAnnotationPresent(Body.class) ||
                                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(
                        "Methods annotated with @" + clazz.getSimpleName() +
                                " must have exactly one argument annotated with @Body" +
                                " or @Message"));
    }

    @SuppressWarnings("unchecked")
    private MessageListener generateAndBindListener(Object bean,
                                                    ExecutableMethod<?, ?> method) {

        return message -> {
            BoundExecutable boundExecutable;
            try {
                boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
            } catch (UnsatisfiedArgumentException e) {
                throw new MessageListenerException("Failed to bind the message to the listener method.", e);
            }
            if (boundExecutable != null) {
                boundExecutable.invoke(bean);
            } else {
                throw new MessageListenerException("No method bound for execution.");
            }
        };
    }

    private void registerListener(ExecutableMethod<?, ?> method,
                                  String connectionFactoryName,
                                  BeanDefinition<?> beanDefinition,
                                  AnnotationValue<T> destinationAnnotation,
                                  JMSDestinationType type) {

        validateArguments(method);

        final String destination = destinationAnnotation.getRequiredValue(String.class);
        final int acknowledgeMode = destinationAnnotation.getRequiredValue("acknowledgeMode", Integer.class);
        final boolean transacted = destinationAnnotation.getRequiredValue("transacted", Boolean.class);

        final JMSListenerRegistry registry = beanContext
                .findBean(JMSListenerRegistry.class)
                .orElseThrow(() -> new IllegalStateException("No JMSListenerRegistry configured"));

        final JMSConnectionPool connectionPool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));

        final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();

        final ExecutorService executor = getExecutorService(destinationAnnotation);

        MessageListener listener = generateAndBindListener(bean, method);
        try {
            Connection connection = connectionPool.createConnection();
            io.micronaut.jms.listener.JMSListener registeredListener = registry.register(
                    connection, type, destination, transacted, acknowledgeMode, listener, executor, true);
            // TODO: inject the success and error handlers
        } catch (JMSException e) {
            logger.error("Failed to register listener for destination " + destination, e);
        }
    }
}
