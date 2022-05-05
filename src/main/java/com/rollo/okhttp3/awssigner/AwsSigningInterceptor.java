package com.rollo.okhttp3.awssigner;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Response;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Map.Entry;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * okhttp3 interceptor for signing AWS requests. Based on Aws4Signer from AWS SDK v2.
 *
 * @author <A href="mailto:alexey@abashev.ru">Alexey Abashev</A>
 */
public class AwsSigningInterceptor implements Interceptor {
    private final Logger log = LoggerFactory.getLogger(AwsSigningInterceptor.class);
    private final AwsCredentialsProvider credentialsProvider;
    private final String serviceName;
    private final Region region;

    public AwsSigningInterceptor(AwsCredentialsProvider credentialsProvider, String serviceName, String regionName) {
        this.credentialsProvider = credentialsProvider;
        this.serviceName = serviceName;
        this.region = Region.of(regionName);
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        var signer = Aws4Signer.create();
        var request = chain.request();
        var url = request.url();
        var headers = request.headers();

        log.debug("Original headers\n{}", headers);

        var container = SdkHttpFullRequest.builder().
                protocol(url.scheme()).
                host(url.host()).
                port(url.port()).
                method(SdkHttpMethod.fromValue(chain.request().method())).
                encodedPath(url.encodedPath()).
                rawQueryParameters(
                    url.queryParameterNames().stream().collect(toMap(identity(), url::queryParameterValues))
                ).headers(
                    headers.names().stream().collect(toMap(identity(), headers::values))
                );

        if (request.body() != null) {
            Buffer sink = new Buffer();
            request.body().writeTo(sink);

            container.contentStreamProvider(() -> new BufferedInputStream(sink.inputStream()));
        }

        var newHeaders = signer.sign(
                container.build(),
                Aws4SignerParams.builder().
                        signingRegion(region).
                        signingName(serviceName).
                        awsCredentials(credentialsProvider.resolveCredentials()).
                        build()
        ).headers().entrySet().stream().collect(toMap(Entry::getKey, e -> e.getValue().get(0)));

        log.debug("New headers\n{}", newHeaders);

        return chain.proceed(request.newBuilder().headers(Headers.of(newHeaders)).build());
    }
}
