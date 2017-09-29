package za.org.grassroot.webapp;

import com.fasterxml.classmate.TypeResolver;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.dto.TaskMinimalDTO;

@Configuration
@EnableSwagger2
public class Swagger2Config {

    @Autowired
    TypeResolver typeResolver;

    @Bean
    public Docket grassrootApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                    .apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
                    .paths(PathSelectors.any())
                .build()
                    .additionalModels(typeResolver.resolve(TaskDTO.class),
                            typeResolver.resolve(TaskMinimalDTO.class))
                    .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("Grassroot")
                .description("REST API for Grassroot Android and Angular front ends")
                .version("v0.1")
                .build();
    }

}