package com.company.logicstic.shared.config;

// Các class dùng để cấu hình Swagger/OpenAPI
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

// Annotation của Spring
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Class cấu hình Swagger/OpenAPI.
 *
 * @Configuration:
 *  - Đánh dấu đây là một class cấu hình của Spring.
 *  - Spring sẽ tự động quét (scan) và khởi tạo class này khi ứng dụng chạy.
 */
@Configuration
public class OpenApiConfig {

    /**
     * @Bean:
     *  - Tạo một đối tượng OpenAPI và đưa vào Spring IoC Container.
     *  - Spring chỉ tạo 1 instance duy nhất và sử dụng lại trong toàn bộ ứng dụng.
     */
    @Bean
    public OpenAPI logisticsApi() {

        // Tạo đối tượng OpenAPI
        return new OpenAPI()

                // Thông tin hiển thị trên trang Swagger UI
                .info(new Info()

                        // Tiêu đề của API
                        .title("LogisticsX API")

                        // Mô tả ngắn về hệ thống
                        .description("Transportation Management System API")

                        // Phiên bản API
                        .version("v1.0")

                        // Thông tin người hoặc nhóm phát triển
                        .contact(new Contact()
                                // Tên team
                                .name("DANG THE VU")
                                // Email liên hệ
                                .email("[EMAIL_ADDRESS]"))

                        // License của dự án
                        .license(new License()

                                // Tên license
                                .name("MIT")))

                // Thông tin tài liệu bên ngoài (không bắt buộc)
                .externalDocs(new ExternalDocumentation()

                        // Mô tả tài liệu
                        .description("Project Documentation"));
    }
}