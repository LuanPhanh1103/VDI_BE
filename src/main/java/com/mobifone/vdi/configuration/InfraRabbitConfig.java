package com.mobifone.vdi.configuration;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.AcknowledgeMode;

@Configuration
@EnableRabbit
public class InfraRabbitConfig {

    public static final String INFRA_QUEUE = "hoanganh_queue";

    @Bean(name = "infraConnectionFactory")
    public ConnectionFactory infraConnectionFactory(
            @Value("${infra.rabbitmq.host}") String host,
            @Value("${infra.rabbitmq.port}") int port,
            @Value("${infra.rabbitmq.username}") String user,
            @Value("${infra.rabbitmq.password}") String pass,
            @Value("${infra.rabbitmq.virtual-host:/}") String vhost
    ) {
        CachingConnectionFactory cf = new CachingConnectionFactory(host, port);
        cf.setUsername(user);
        cf.setPassword(pass);
        cf.setVirtualHost(vhost);
        return cf;
    }

    // ❗ Admin của broker B – KHÔNG declare gì
    @Bean(name = "infraRabbitAdmin")
    public RabbitAdmin infraRabbitAdmin(
            @Qualifier("infraConnectionFactory") ConnectionFactory cf
    ) {
        RabbitAdmin admin = new RabbitAdmin(cf);
        admin.setAutoStartup(false);                 // không declare trên broker B
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }

    @Bean(name = "infraJacksonConverter")
    public MessageConverter infraJacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ✅ Factory dành riêng cho listener infra
    @Bean(name = "infraListenerFactory")
    public SimpleRabbitListenerContainerFactory infraListenerFactory(
            @Qualifier("infraConnectionFactory") ConnectionFactory cf,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("infraJacksonConverter") MessageConverter converter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, cf);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setPrefetchCount(10);
        factory.setMessageConverter(null);

        factory.setMissingQueuesFatal(false);
        factory.setMismatchedQueuesFatal(false);
        factory.setContainerCustomizer(container ->
                container.setFailedDeclarationRetryInterval(5000L)
        );
        return factory;
    }

    // ❌ KHÔNG declare queue/exchange/binding nào ở đây
}
