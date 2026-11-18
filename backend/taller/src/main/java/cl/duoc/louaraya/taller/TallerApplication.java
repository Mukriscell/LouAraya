package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ApiExceptionHandler.class)
public class TallerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TallerApplication.class, args);
    }
}
