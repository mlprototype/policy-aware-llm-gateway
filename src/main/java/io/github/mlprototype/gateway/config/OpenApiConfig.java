package io.github.mlprototype.gateway.config;

import io.github.mlprototype.gateway.filter.ApiKeyFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Policy-Aware Multi-LLM Gateway API",
                version = "0.1.0",
                description = "日本語利用を想定した OpenAI 互換 Gateway API。プロバイダルーティング、レート制限、コンテンツセキュリティ、監査ログを提供します。"
        )
)
@SecurityScheme(
        name = "gatewayApiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = ApiKeyFilter.API_KEY_HEADER
)
public class OpenApiConfig {
}
