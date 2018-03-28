/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.codecentric.boot.admin.server.notify;

import de.codecentric.boot.admin.server.config.AdminServerNotifierAutoConfiguration;
import de.codecentric.boot.admin.server.domain.entities.Instance;
import de.codecentric.boot.admin.server.domain.entities.InstanceRepository;
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent;
import de.codecentric.boot.admin.server.domain.values.InstanceId;
import de.codecentric.boot.admin.server.domain.values.Registration;
import de.codecentric.boot.admin.server.domain.values.StatusInfo;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.thymeleaf.TemplateEngine;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Kevin Peters
 */
public class ThymeleafMailNotifierTest {
    private final Instance instance = Instance.create(InstanceId.of("-id-"))
        .register(Registration.create("App", "http://health").build());
    private MailSender sender;
    private ThymeleafMailNotifier notifier;
    private InstanceRepository repository;

    private static String EXPECTED_MAIL_TEXT = "<!DOCTYPE html>" + System.lineSeparator() +
        "<html>" + System.lineSeparator() +
        "<head>" + System.lineSeparator() +
        "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" + System.lineSeparator() +
        "</head>" + System.lineSeparator() +
        "<body>" + System.lineSeparator() +
        "<span>App</span> (<span>-id-</span>)" + System.lineSeparator() +
        "status changed from <span>UNKNOWN</span> to <span>DOWN</span>" + System.lineSeparator() +
        "<br />" + System.lineSeparator() +
        "<span>http://health</span>" + System.lineSeparator() +
        "</body>" + System.lineSeparator() +
        "</html>" + System.lineSeparator();

    @Before
    public void setup() {
        final TemplateEngine templateEngine = new AdminServerNotifierAutoConfiguration.MailNotifierConfiguration().emailTemplateEngine();
        repository = mock(InstanceRepository.class);
        when(repository.find(instance.getId())).thenReturn(Mono.just(instance));

        sender = mock(MailSender.class);

        notifier = new ThymeleafMailNotifier(sender, repository, templateEngine);
        notifier.setTo(new String[]{"foo@bar.com"});
        notifier.setCc(new String[]{"bar@foo.com"});
        notifier.setFrom("SBA <no-reply@example.com>");
        notifier.setSubject("#{instance.id} is #{event.statusInfo.status}");
    }

    @Test
    public void test_onApplicationEvent_with_Thymeleaf_template() {
        StepVerifier.create(notifier.notify(
            new InstanceStatusChangedEvent(instance.getId(), instance.getVersion(), StatusInfo.ofDown())))
            .verifyComplete();

        final SimpleMailMessage expected = new SimpleMailMessage();
        expected.setTo(new String[]{"foo@bar.com"});
        expected.setCc(new String[]{"bar@foo.com"});
        expected.setFrom("SBA <no-reply@example.com>");

        expected.setText(EXPECTED_MAIL_TEXT);
        expected.setSubject("-id- is DOWN");

        verify(sender).send(eq(expected));
    }
}
