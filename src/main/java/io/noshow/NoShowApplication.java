package io.noshow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NoShowApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoShowApplication.class, args);
    }
}
