package com.example.jrfind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.jrfind.service.ImageStorage;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
	private final ImageStorage imageStorage;

	public WebMvcConfig(ImageStorage imageStorage) {
		this.imageStorage = imageStorage;
	}

	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 웹 브라우저에서 http://localhost:8080/images/파일명.jpg 로 요청하면
        // 업로드 폴더(jrfind.upload-dir, 기본값 ./uploads)에서 파일을 찾아서 보여줍니다.
        String uploadLocation = imageStorage.getUploadDir().toUri().toString();
        if (!uploadLocation.endsWith("/")) {
            uploadLocation += "/";
        }
        registry.addResourceHandler("/images/**")
                .addResourceLocations(uploadLocation);

        // 로고 이미지
        registry.addResourceHandler("/logo/**")
        .addResourceLocations("classpath:/logo/");
    }
}
