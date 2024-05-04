package ru.krsmon.bridge.config;

import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {
    protected static TrustManager[] trustManager;

    static {
        trustManager = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(req -> req.anyRequest().authenticated())
                .httpBasic(withDefaults())
                .csrf().disable()
                .build();
    }

    @Bean
    @Primary
    @SneakyThrows
    SSLContext sslContext() {
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, trustManager, new SecureRandom());
        return sslContext;
    }

    @Bean
    @Primary
    @SneakyThrows
    OkHttpClient okHttpClient(@NonNull SSLContext sslContext) {
        return new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManager[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }

}
