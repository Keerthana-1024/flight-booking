package com.flights.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            URI uri = URI.create(redisUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 6379;
            String userInfo = uri.getUserInfo();
            boolean useTls = redisUrl.startsWith("rediss://");

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(host);
            config.setPort(port);

            if (userInfo != null && userInfo.contains(":")) {
                String password = userInfo.substring(userInfo.indexOf(':') + 1);
                if (!password.isEmpty()) {
                    config.setPassword(password);
                }
                String username = userInfo.substring(0, userInfo.indexOf(':'));
                if (!username.isEmpty() && !username.equals("default")) {
                    config.setUsername(username);
                }
            }

            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                    LettuceClientConfiguration.builder()
                            .commandTimeout(Duration.ofSeconds(5));

            if (useTls) {
                builder.useSsl().disablePeerVerification();
            }

            return new LettuceConnectionFactory(config, builder.build());
        } catch (Exception e) {
            // Fallback to local Redis
            return new LettuceConnectionFactory("localhost", 6379);
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
