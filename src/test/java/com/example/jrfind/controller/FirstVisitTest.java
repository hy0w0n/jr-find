package com.example.jrfind.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

// 로그인 전 첫 방문(세션 없음)에도 화면이 끝까지 내려오는지 실제 서버로 확인
// 화면 중간의 폼에서 CSRF 토큰(세션에 저장)을 처음 만들 때 응답을 이미 보내기 시작했으면 세션 쿠키를 붙일 수 없어 화면이 끊긴다.
// MockMvc 는 응답을 나눠 보내지 않아 이 문제가 재현되지 않으므로 실제 서버(내장 Tomcat)로 요청한다.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FirstVisitTest {

	@LocalServerPort
	private int port;

	@ParameterizedTest
	@ValueSource(strings = { "/", "/items", "/items/new", "/lost-items", "/search?q=%E5%82%98", "/map", "/login", "/signup" })
	void 세션이_없어도_화면이_끝까지_내려온다(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();

		// 응답이 중간에 끊기면 여기서 IOException
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("</html>");
	}
}
