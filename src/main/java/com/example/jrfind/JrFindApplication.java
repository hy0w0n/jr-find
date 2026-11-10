package com.example.jrfind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 쓰지 않는 사진 파일 정리 (OrphanImageCleaner)
public class JrFindApplication {

	public static void main(String[] args) {
		SpringApplication.run(JrFindApplication.class, args);
	}

}
