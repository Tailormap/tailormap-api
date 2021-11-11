package nl.b3p.tailormap.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API application starter.
 *
 * @since 0.1
 */
@SpringBootApplication
public class TailormapApiApplication {

    /**
     * default starter method.
     *
     * @param args an array of arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TailormapApiApplication.class, args);
    }
}
