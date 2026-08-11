package dbadmin.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bildirim push'unun gectigi exchange/queue tanimlari (bkz. backend/notlar "RabbitMQ koyulacak
 * notification icin"). {@code NotificationService#create} bu exchange'e publish eder, {@code
 * RabbitNotificationListener} {@link #NOTIFICATION_QUEUE}'yu dinler — publish (DB yazimindan
 * sonra, AFTER_COMMIT) ve consume (WebSocket push) boylece ayni JVM icinde bile birbirinden
 * bagimsiz iki adima ayrilir.
 * <p>
 * Ana queue, basarisiz/tukenen mesajlarin kaybolmamasi icin {@code x-dead-letter-exchange}
 * argumaniyla DLX'e baglidir: {@code RabbitNotificationListener} bir mesaji retry'lardan sonra
 * hala isleyemezse (bkz. application.properties'teki {@code spring.rabbitmq.listener.simple.retry.*}),
 * varsayilan davranis olan reddet-ve-requeue-etme mesaji otomatik olarak DLX -> DLQ'ya dusurur.
 */
@Configuration
public class RabbitConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.push";

    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String NOTIFICATION_DLQ = "notification.dlq";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.push.dlq";

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    DirectExchange notificationDlx() {
        return new DirectExchange(NOTIFICATION_DLX);
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding notificationDlqBinding(Queue notificationDlq, DirectExchange notificationDlx) {
        return BindingBuilder.bind(notificationDlq).to(notificationDlx).with(NOTIFICATION_DLQ_ROUTING_KEY);
    }

    // Proje Jackson 3'te (tools.jackson) — spring-amqp 4.x bunun icin ayri bir sinif tasir:
    // Jackson2JsonMessageConverter (com.fasterxml, deprecated) DEGIL, JacksonJsonMessageConverter
    // (tools.jackson) kullanilmali. jjwt-jackson'in hala Jackson 2 kullanmasiyla ayni cizgide
    // (bkz. pom.xml yorumu): iki Jackson kutuphanesi paket adlari farkli oldugundan yan yana durur.
    @Bean
    MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
