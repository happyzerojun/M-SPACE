package com.mspace.global.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 배포 이미지 하나로 화면 + API를 함께 내보내요 (CLAUDE.md 4장 "Docker 이미지 하나").
 * 빌드한 React 화면(frontend/dist)은 이미지 안에서 classpath:/static/에 들어가요(Dockerfile).
 *
 * <p>React가 주소를 직접 다루는 화면(/me/reservations, /admin 등)은 파일이 없으니 index.html을 돌려줘서
 * 새로고침해도 화면이 열리게 해요. /api/로 시작하는 주소는 대신 돌려주지 않아요 → 없는 API는 JSON 404 그대로예요.
 * 개발·테스트처럼 static/index.html이 없으면 아무것도 대신 돌려주지 않아서 지금 동작과 같아요.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";
    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                // 파일 이름에 해시가 붙어 바뀌면 주소도 바뀌어요. 배포 직후 옛 index.html이 남지 않게 매번 서버에 확인해요(304로 가벼워요)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            // 기본 해석(경로 검사 포함)으로 실제 파일이 있으면 그 파일
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.equals("api") || !INDEX.exists()) {
                return null;
            }
            // 확장자가 있는 주소(없는 이미지·js 등)는 화면 대신 404로 둬요 → 잘못된 파일 요청이 HTML로 응답되지 않게
            String last = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return last.contains(".") ? null : INDEX;
        }
    }
}
