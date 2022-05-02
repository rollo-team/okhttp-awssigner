package com.rollo.okhttp3.awssigner;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.HttpMethodName;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static com.amazonaws.auth.internal.SignerConstants.X_AMZ_DATE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * okhttp3 interceptor for signing AWS requests. Based on AWS4Signer from AWS SDK.
 *
 * @author <A href="mailto:alexey@abashev.ru">Alexey Abashev</A>
 */
public class AwsSigningInterceptor implements Interceptor {
    private final AWS4Signer signer;
    private final String serviceName;
    private final AWSCredentialsProvider credentialsProvider;

    public AwsSigningInterceptor(AWSCredentialsProvider credentialsProvider, String serviceName, String regionName) {
        this.signer = new AWS4Signer();

        this.signer.setServiceName(serviceName);
        this.signer.setRegionName(regionName);

        this.credentialsProvider = credentialsProvider;
        this.serviceName = serviceName;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        var request = chain.request();
        var url = request.url();
        var container = new DefaultRequest<>(serviceName);

        try {
            container.setEndpoint(new URI(url.scheme(), url.host(), null, null));
        } catch (URISyntaxException e) {
            throw new IOException("Not able to build URI from " + url, e);
        }

        container.setHttpMethod(HttpMethodName.fromValue(chain.request().method()));
        container.setResourcePath(url.encodedPath());
        container.setParameters(
                url.queryParameterNames().stream().collect(toMap(identity(), url::queryParameterValues))
        );

        var headers = request.headers();

        container.setHeaders(
                headers.names().stream().collect(toMap(identity(), n -> headers.values(n).get(0)))
        );

        RequestBody body = request.body();

        if (body != null) {
            Buffer sink = new Buffer();
            body.writeTo(sink);

            container.setContent(new BufferedInputStream(sink.inputStream()));
        }

        // Sign it
        signer.sign(container, credentialsProvider.getCredentials());

        Request signedRequest = request.newBuilder()
                .removeHeader(AUTHORIZATION)
                .addHeader(AUTHORIZATION, container.getHeaders().get(AUTHORIZATION))
                .addHeader(X_AMZ_DATE, container.getHeaders().get(X_AMZ_DATE))
                .build();

        return chain.proceed(signedRequest);
    }
}

