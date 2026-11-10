package com.example.jrfind.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

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
        // 업로드 파일명은 저장할 때마다 새로 만들므로(같은 이름에 다른 사진이 들어가지 않는다) 브라우저가 1년 동안 다시 받지 않게
        registry.addResourceHandler("/images/**")
                .addResourceLocations(uploadLocation)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        // 로고 이미지: 주소에 파일 내용의 해시를 붙이고(logo3_1-<해시>.png) 1년 동안 다시 받지 않게
        registry.addResourceHandler("/logo/**")
                .addResourceLocations("classpath:/logo/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
    }
}
