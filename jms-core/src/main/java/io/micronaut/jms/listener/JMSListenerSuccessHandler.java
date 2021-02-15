/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.jms.listener;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/***
 * Handles an action after a message has been received and processed by a {@link JMSListener}.
 *
 * @author Elliott Pope
 * @since 1.0.0.M1
 *
 * @see TransactionalJMSListenerSuccessHandler
 * @see AcknowledgingJMSListenerSuccessHandler
 */
@FunctionalInterface
public interface JMSListenerSuccessHandler {
    /***
     * Handle the successfully processed message.
     *
     * @param session - the {@link Session} the {@link JMSListener} is bound to.
     * @param message - the {@link javax.jms.Message} that was processed.
     * @throws JMSException if any exception occurs while handling the message.
     */
    void handle(Session session, Message message) throws JMSException;
}
