package io.github.raphonzius.lvc.virtualareas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VirtualAreasApplication {
    static void main(String[] args) {
        SpringApplication.run(VirtualAreasApplication.class, args);
    }
}
