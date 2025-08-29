package com.mobifone.vdi.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;   // <<-- quan trọng

import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String EXCHANGE      = "ansible.exchange";
    public static final String INSTALL_QUEUE = "ansible.install";
    public static final String CONFIG_QUEUE  = "ansible.config";
    public static final String INSTALL_KEY   = "install";
    public static final String CONFIG_KEY    = "config";

    // ====== ConnectionFactory ======
    @Bean(name = "rabbitConnectionFactory")
    @Primary
    public ConnectionFactory rabbitConnectionFactory(RabbitProperties props) {
        CachingConnectionFactory cf = new CachingConnectionFactory(props.getHost(), props.getPort());
        cf.setUsername(props.getUsername());
        cf.setPassword(props.getPassword());
        if (props.getVirtualHost() != null) {
            cf.setVirtualHost(props.getVirtualHost());
        }
        cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        cf.setPublisherReturns(props.isPublisherReturns());
        return cf;
    }

    // ====== Exchange / Queue / Binding ======
    @Bean
    public DirectExchange ansibleExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue installQueue() {
        return new Queue(INSTALL_QUEUE, true);
    }

    @Bean
    public Queue configQueue() {
        return new Queue(CONFIG_QUEUE, true);
    }

    @Bean
    public Binding installBinding(Queue installQueue, DirectExchange ansibleExchange) {
        return BindingBuilder.bind(installQueue).to(ansibleExchange).with(INSTALL_KEY);
    }

    @Bean
    public Binding configBinding(Queue configQueue, DirectExchange ansibleExchange) {
        return BindingBuilder.bind(configQueue).to(ansibleExchange).with(CONFIG_KEY);
    }

    // ====== Converter JSON ======
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        // Cho phép convert từ header __TypeId__
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*", "com.mobifone.vdi.dto.request");
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }

    // ====== RabbitTemplate cho Publisher ======
    @Bean
    public RabbitTemplate rabbitTemplate(
            @Qualifier("rabbitConnectionFactory") ConnectionFactory connectionFactory,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    // ====== RabbitAdmin để declare queue/exchange ======
    @Bean
    public RabbitAdmin defaultRabbitAdmin(
            @Qualifier("rabbitConnectionFactory") ConnectionFactory connectionFactory
    ) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }

    // ====== Listener Container Factory cho Consumer ======
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory defaultListenerFactory(
            @Qualifier("rabbitConnectionFactory") ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter); // Gắn converter JSON cho listener
        return factory;
    }
}