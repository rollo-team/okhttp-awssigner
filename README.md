# AWSv4 request signer for okhttp

[![Release](https://jitpack.io/v/rollo-team/okhttp-awssigner.svg)](https://jitpack.io/#rollo-team/okhttp-awssigner)

## What is it?

An interceptor for OkHttpClient from Square to sign requests for AWS services that require signatures.

This project aims to follow the AWSv4 signature spec described here: https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html


## Usage

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.rollo-team</groupId>
    <artifactId>okhttp-awssigner</artifactId>
    <version>1.0</version>
</dependency>
```

``` groovy
repositories {
    .............
    
    maven { url "https://jitpack.io" }
}

implementation 'com.github.rollo-team:okhttp-awssigner:1.0'

```

Interceptor should be included late in the interceptor chain, so that all headers (including `Host`) has been set by OkHttp,
before signing is invoked.

```java

String accessKey = "AKIDEXAMPLE";
String secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";

var creds = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)
);

OkHttpClient client = new OkHttpClient.Builder()
    .addNetworkInterceptor(new AwsSigningInterceptor(creds, "execute-api", "us-east-1"))
    .build();
```

of if you want to use STS AssumeRole

```java

String accessKey = "AKIDEXAMPLE";
String secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";

var tokenClient = StsClient.builder().
        credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).
        region(Region.US_EAST_1).
        build();

var creds = StsAssumeRoleCredentialsProvider.builder().
        refreshRequest(AssumeRoleRequest.builder().
                roleArn("arn:aws:iam::1234567890:role/some-role").
                roleSessionName("backend").
                build()
        ).
        stsClient(tokenClient).
        build();

OkHttpClient client = new OkHttpClient.Builder()
    .addNetworkInterceptor(new AwsSigningInterceptor(creds, "execute-api", "us-east-1"))
    .build();
```
