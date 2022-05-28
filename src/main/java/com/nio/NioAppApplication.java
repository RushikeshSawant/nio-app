package com.nio;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class NioAppApplication {

  public static void main(String[] args) {
    System.out.println("CPUs: " + Runtime.getRuntime().availableProcessors());
    System.out.println("Memory: " + Runtime.getRuntime().maxMemory() / 1_000_000.0);
    SpringApplication.run(NioAppApplication.class, args);
  }
}

@Configuration
class Config {

  @Bean
  public WebClient webClient() {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofMinutes(5))
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS)));
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }

  @Bean
  GreetingHandler greetingHandler(WebClient webClient) {
    return new GreetingHandler(webClient);
  }

  @Bean
  RouterFunction<?> routerFunction(GreetingHandler greetingHandler) {
    return RouterFunctions.route().GET("/hello", greetingHandler::hello).build();
  }
}

class GreetingHandler {

  private final WebClient webClient;
  private static final AtomicLong count = new AtomicLong(0);

  GreetingHandler(WebClient webClient) {
    this.webClient = webClient;
  }

  public Mono<ServerResponse> hello(ServerRequest request) {
    System.out.println("Remaining: " + count.incrementAndGet());
    return webClient
        .get()
        .uri(
            String.format(
                "http://host.docker.internal:8080/slow?time=%s", request.queryParam("time").get()))
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(s -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(s))
        .doFinally(signalType -> System.out.println("Remaining: " + count.decrementAndGet()));
  }
}
